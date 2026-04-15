package com.entitycheck.controller;

import com.entitycheck.model.*;
import com.entitycheck.repository.*;
import com.entitycheck.service.ComprehensiveDataService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api/client")
public class ClientOrderController {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ClientProductRepository clientProductRepository;
    private final GeneratedDocumentRepository generatedDocumentRepository;
    private final ObjectMapper objectMapper;
    private final ComprehensiveDataService comprehensiveDataService;

    public ClientOrderController(UserRepository userRepository,
                                  OrderRepository orderRepository,
                                  ProductRepository productRepository,
                                  ClientProductRepository clientProductRepository,
                                  GeneratedDocumentRepository generatedDocumentRepository,
                                  ObjectMapper objectMapper,
                                  ComprehensiveDataService comprehensiveDataService) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.clientProductRepository = clientProductRepository;
        this.generatedDocumentRepository = generatedDocumentRepository;
        this.objectMapper = objectMapper;
        this.comprehensiveDataService = comprehensiveDataService;
    }

    // ── GET /api/client/entitlements ──
    @GetMapping("/entitlements")
    public ResponseEntity<List<Map<String, Object>>> getEntitlements() {
        User user = getCurrentUser();
        if (user.getClientCompany() == null) {
            return ResponseEntity.ok(List.of());
        }

        List<ClientProduct> cps = clientProductRepository.findByClientCompanyId(user.getClientCompany().getId());
        List<Map<String, Object>> result = new ArrayList<>();
        for (ClientProduct cp : cps) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", cp.getId());
            e.put("clientCompanyId", cp.getClientCompany().getId());
            e.put("clientCompanyName", cp.getClientCompany().getName());
            e.put("productId", cp.getProduct().getId());
            e.put("productName", cp.getProduct().getName());
            e.put("code", cp.getProduct().getCode()); // frontend looks for .code
            e.put("productCode", cp.getProduct().getCode());
            e.put("grantedAt", cp.getGrantedAt() != null ? cp.getGrantedAt().toString() : null);
            result.add(e);
        }

        // If no entitlements exist yet, provide a default DDR entitlement for demo
        if (result.isEmpty()) {
            Product ddr = productRepository.findAll().stream()
                    .filter(p -> "DDR".equals(p.getCode()))
                    .findFirst().orElse(null);
            if (ddr != null) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("id", 0);
                e.put("productId", ddr.getId());
                e.put("productName", ddr.getName());
                e.put("code", ddr.getCode());
                e.put("productCode", ddr.getCode());
                result.add(e);
            }
        }

        return ResponseEntity.ok(result);
    }

    // ── GET /api/client/stats ──
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        User user = getCurrentUser();
        Long companyId = user.getClientCompany() != null ? user.getClientCompany().getId() : null;
        Map<String, Object> stats = new LinkedHashMap<>();
        if (companyId != null) {
            long total = orderRepository.countByClientCompanyId(companyId);
            long completed = orderRepository.countByClientCompanyIdAndStatus(companyId, OrderStatus.COMPLETED);
            long pending = total - completed;
            stats.put("totalOrders", total);
            stats.put("completedOrders", completed);
            stats.put("pendingOrders", pending);
            stats.put("ordersThisMonth", total); // simplified
        }
        return ResponseEntity.ok(stats);
    }

    // ── GET /api/client/orders ──
    @GetMapping("/orders")
    public ResponseEntity<List<Map<String, Object>>> listOrders() {
        User user = getCurrentUser();
        if (user.getClientCompany() == null) {
            return ResponseEntity.ok(List.of());
        }

        List<Order> orders = orderRepository.findByClientCompanyIdWithDetails(user.getClientCompany().getId());
        List<Map<String, Object>> result = orders.stream().map(this::orderToMap).toList();
        return ResponseEntity.ok(result);
    }

    // ── POST /api/client/orders ──
    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody Map<String, Object> body) {
        User user = getCurrentUser();
        if (user.getClientCompany() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User has no associated client company");
        }

        Long productId = toLong(body.get("productId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> selectedCompany = (Map<String, Object>) body.get("selectedCompany");
        String notes = (String) body.getOrDefault("notes", "");

        if (productId == null || selectedCompany == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productId and selectedCompany are required");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        // Build the order
        Order order = new Order();
        order.setClientCompany(user.getClientCompany());
        order.setProduct(product);
        order.setSubjectName(strVal(selectedCompany.get("companyName")));
        order.setSubjectType(strVal(selectedCompany.getOrDefault("companyType", "Company")));

        // Store subject details as JSON
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("cin", strVal(selectedCompany.get("cin")));
        details.put("city", strVal(selectedCompany.get("city")));
        details.put("state", strVal(selectedCompany.get("state")));
        details.put("status", strVal(selectedCompany.get("status")));
        try {
            order.setSubjectDetails(objectMapper.writeValueAsString(details));
        } catch (Exception e) {
            order.setSubjectDetails("{}");
        }

        order.setNotes(notes);
        order.setStatus(OrderStatus.ORDER_PLACED);
        order.setPriority(Priority.NORMAL);

        // Generate order number: ORD-YYYYMM-XXXXX
        String yearMonth = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String rand = String.format("%05d", ThreadLocalRandom.current().nextInt(100000));
        order.setOrderNumber("ORD-" + yearMonth + "-" + rand);

        Order saved = orderRepository.save(order);

        // Advance to pending_data_fetch
        saved.setStatus(OrderStatus.PENDING_DATA_FETCH);
        saved = orderRepository.save(saved);

        Map<String, Object> response = new LinkedHashMap<>(orderToMap(saved));
        String identifier = comprehensiveDataService.resolveIdentifier(saved);
        try {
            Map<String, Object> fetched = comprehensiveDataService.fetchAndStoreFresh(saved, identifier, "client_auto");
            saved.setStatus(OrderStatus.DATA_FETCHED);
            saved = orderRepository.save(saved);

            response.clear();
            response.putAll(orderToMap(saved));
            response.put("autoFetchStatus", "success");
            response.put("autoFetchMessage", "Order is processed and data is fetched successfully.");
            response.put("latestSnapshot", comprehensiveDataService.getLatest(saved.getId()));
            response.put("versions", comprehensiveDataService.getVersions(saved.getId()));
            response.put("fetchedSummary", fetched);
        } catch (RuntimeException ex) {
            response.put("autoFetchStatus", "failed");
            response.put(
                    "autoFetchMessage",
                    "Order was placed successfully, but automatic data fetch failed. Operations can use re-fetch."
            );
            response.put("autoFetchError", ex.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    // ── GET /api/client/orders/{id}/download-pdf ──
    @GetMapping("/orders/{id}/download-pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        User user = getCurrentUser();
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        // Tenant isolation
        if (user.getClientCompany() == null ||
                !order.getClientCompany().getId().equals(user.getClientCompany().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Report not yet completed");
        }

        GeneratedDocument doc = generatedDocumentRepository
                .findByOrderIdAndDocumentType(id, "due_diligence_report")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF not found"));

        if (!"ready".equals(doc.getStatus()) || doc.getPdfBase64() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF not ready");
        }

        byte[] pdfBytes = Base64.getDecoder().decode(doc.getPdfBase64());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.builder("attachment")
                        .filename(doc.getFileName() != null ? doc.getFileName() : "report.pdf")
                        .build()
        );
        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    // ── Helpers ──

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        return userRepository.findByEmailWithCompany(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private Map<String, Object> orderToMap(Order o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("orderNumber", o.getOrderNumber());
        m.put("clientCompanyId", o.getClientCompany() != null ? o.getClientCompany().getId() : null);
        m.put("clientCompanyName", o.getClientCompany() != null ? o.getClientCompany().getName() : null);
        m.put("productId", o.getProduct() != null ? o.getProduct().getId() : null);
        m.put("productName", o.getProduct() != null ? o.getProduct().getName() : null);
        m.put("productCode", o.getProduct() != null ? o.getProduct().getCode() : null);
        m.put("subjectName", o.getSubjectName());
        m.put("subjectType", o.getSubjectType());
        m.put("subjectDetails", parseJson(o.getSubjectDetails()));
        m.put("status", o.getStatus().name().toLowerCase());
        m.put("priority", o.getPriority() != null ? o.getPriority().name().toLowerCase() : "normal");
        m.put("notes", o.getNotes());
        m.put("createdAt", o.getCreatedAt() != null ? o.getCreatedAt().toString() : null);
        m.put("updatedAt", o.getUpdatedAt() != null ? o.getUpdatedAt().toString() : null);
        m.put("completedAt", o.getCompletedAt() != null ? o.getCompletedAt().toString() : null);
        return m;
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return json;
        }
    }

    private String strVal(Object o) {
        return o != null ? String.valueOf(o) : "";
    }

    private Long toLong(Object o) {
        if (o instanceof Number) return ((Number) o).longValue();
        if (o instanceof String) {
            try { return Long.parseLong((String) o); } catch (Exception e) { return null; }
        }
        return null;
    }
}
