package com.entitycheck.controller;

import com.entitycheck.model.ClientProduct;
import com.entitycheck.model.GeneratedDocument;
import com.entitycheck.model.Order;
import com.entitycheck.model.OrderStatus;
import com.entitycheck.model.Product;
import com.entitycheck.model.User;
import com.entitycheck.repository.ClientProductRepository;
import com.entitycheck.repository.GeneratedDocumentRepository;
import com.entitycheck.repository.OrderRepository;
import com.entitycheck.repository.ProductRepository;
import com.entitycheck.repository.UserRepository;
import com.entitycheck.service.ComprehensiveDataService;
import com.entitycheck.service.CreditReportService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api/client")
public class ClientOrderController {

    private static final Logger log = LoggerFactory.getLogger(ClientOrderController.class);

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ClientProductRepository clientProductRepository;
    private final GeneratedDocumentRepository generatedDocumentRepository;
    private final ObjectMapper objectMapper;
    private final ComprehensiveDataService comprehensiveDataService;
    private final CreditReportService creditReportService;

    public ClientOrderController(
            UserRepository userRepository,
            OrderRepository orderRepository,
            ProductRepository productRepository,
            ClientProductRepository clientProductRepository,
            GeneratedDocumentRepository generatedDocumentRepository,
            ObjectMapper objectMapper,
            ComprehensiveDataService comprehensiveDataService,
            CreditReportService creditReportService) {

        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.clientProductRepository = clientProductRepository;
        this.generatedDocumentRepository = generatedDocumentRepository;
        this.objectMapper = objectMapper;
        this.comprehensiveDataService = comprehensiveDataService;
        this.creditReportService = creditReportService;
    }

    @PostConstruct
    public void init() {
        log.info("ClientOrderController initialized");
    }

    @GetMapping("/entitlements")
    public ResponseEntity<List<Map<String, Object>>> getEntitlements() {
        User user = getCurrentUser();
        if (user.getClientCompany() == null) {
            return ResponseEntity.ok(List.of());
        }

        List<ClientProduct> mappings = clientProductRepository.findByClientCompanyId(user.getClientCompany().getId());
        List<Map<String, Object>> data = mappings.stream().map(cp -> {
            Product p = cp.getProduct();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", cp.getId());
            m.put("productId", p != null ? p.getId() : null);
            m.put("code", p != null ? p.getCode() : null);
            m.put("name", p != null ? p.getName() : null);
            m.put("description", p != null ? p.getDescription() : null);
            m.put("grantedAt", cp.getGrantedAt() != null ? cp.getGrantedAt().toString() : null);
            return m;
        }).toList();

        return ResponseEntity.ok(data);
    }

    @GetMapping("/orders")
    public ResponseEntity<List<Map<String, Object>>> listMyOrders() {
        User user = getCurrentUser();
        if (user.getClientCompany() == null) {
            return ResponseEntity.ok(List.of());
        }

        List<Order> orders = orderRepository.findByClientCompanyIdWithDetails(user.getClientCompany().getId());
        List<Map<String, Object>> data = orders.stream().map(o -> {
            Map<String, Object> m = orderToMap(o);
            generatedDocumentRepository.findByOrderIdAndDocumentType(o.getId(), "due_diligence_report")
                    .ifPresent(doc -> {
                        m.put("pdfStatus", doc.getStatus());
                        m.put("pdfFileName", doc.getFileName());
                        m.put("previewUrl", "/api/client/orders/" + o.getId() + "/preview-pdf");
                        m.put("downloadUrl", "/api/client/orders/" + o.getId() + "/download-pdf");
                    });
            return m;
        }).toList();

        return ResponseEntity.ok(data);
    }

    @GetMapping("/orders/{id}/credit-report")
    public ResponseEntity<Map<String, Object>> getMyCreditReport(@PathVariable Long id) {
        User user = getCurrentUser();
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (user.getClientCompany() == null ||
                !order.getClientCompany().getId().equals(user.getClientCompany().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        Map<String, Object> report = creditReportService.getLatestCreditReport(id);
        if (report == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(report);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        User user = getCurrentUser();
        if (user.getClientCompany() == null) {
            return ResponseEntity.ok(Map.of(
                    "totalOrders", 0,
                    "pendingOrders", 0,
                    "completedOrders", 0,
                    "ordersThisMonth", 0));
        }

        List<Order> orders = orderRepository.findByClientCompanyIdWithDetails(user.getClientCompany().getId());
        OffsetDateTime now = OffsetDateTime.now();

        long pending = orders.stream()
                .filter(o -> o.getStatus() != OrderStatus.COMPLETED && o.getStatus() != OrderStatus.CANCELLED)
                .count();
        long completed = orders.stream().filter(o -> o.getStatus() == OrderStatus.COMPLETED).count();
        long thisMonth = orders.stream()
                .filter(o -> o.getCreatedAt() != null
                        && o.getCreatedAt().getYear() == now.getYear()
                        && o.getCreatedAt().getMonth() == now.getMonth())
                .count();

        return ResponseEntity.ok(Map.of(
                "totalOrders", orders.size(),
                "pendingOrders", pending,
                "completedOrders", completed,
                "ordersThisMonth", thisMonth));
    }

    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody Map<String, Object> body) {
        User user = getCurrentUser();
        if (user.getClientCompany() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current user has no client company");
        }

        Long productId = toLong(body.get("productId"));
        if (productId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productId is required");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid productId"));

        boolean entitled = clientProductRepository.findByClientCompanyId(user.getClientCompany().getId()).stream()
                .anyMatch(cp -> cp.getProduct() != null && Objects.equals(cp.getProduct().getId(), productId));
        if (!entitled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Client is not entitled to this product");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> selectedCompany = body.get("selectedCompany") instanceof Map
                ? (Map<String, Object>) body.get("selectedCompany")
                : Map.of();

        String subjectName = strVal(selectedCompany.get("companyName"));
        if (subjectName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "selectedCompany.companyName is required");
        }

        String cin = strVal(selectedCompany.get("cin"));
        String entityType = strVal(body.get("entityType"));
        if (entityType.isBlank()) {
            entityType = "Company";
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("cin", cin);
        details.put("city", strVal(selectedCompany.get("city")));
        details.put("state", strVal(selectedCompany.get("state")));
        details.put("status", strVal(selectedCompany.get("status")));
        details.put("companyType", strVal(selectedCompany.get("companyType")));

        Order order = new Order();
        order.setOrderNumber(generateOrderNumber());
        order.setClientCompany(user.getClientCompany());
        order.setProduct(product);
        order.setSubjectName(subjectName);
        order.setSubjectType(entityType);
        order.setNotes(strVal(body.get("notes")));
        try {
            order.setSubjectDetails(objectMapper.writeValueAsString(details));
        } catch (Exception e) {
            order.setSubjectDetails("{}");
        }
        order.setStatus(OrderStatus.ORDER_PLACED);
        orderRepository.save(order);

        String autoFetchStatus = "pending";
        String autoFetchMessage = "Order created. Data fetch has not started.";
        if (!cin.isBlank()) {
            try {
                comprehensiveDataService.fetchAndStoreFresh(order, cin, "client");
                order.setStatus(OrderStatus.DATA_FETCHED);
                orderRepository.save(order);
                creditReportService.generateCreditReport(order.getId());

                autoFetchStatus = "success";
                autoFetchMessage = "Company data fetched. Credit report generation started.";
            } catch (Exception ex) {
                log.warn("Auto-fetch failed for order {}: {}", order.getId(), ex.getMessage());
                order.setStatus(OrderStatus.PENDING_DATA_FETCH);
                orderRepository.save(order);
                autoFetchStatus = "failed";
                autoFetchMessage = "Order created, but Probe42 fetch failed. Operations can retry fetch.";
            }
        }

        Map<String, Object> response = orderToMap(order);
        response.put("autoFetchStatus", autoFetchStatus);
        response.put("autoFetchMessage", autoFetchMessage);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/orders/{id}/preview-pdf")
    public ResponseEntity<byte[]> previewPdf(@PathVariable Long id) {
        User user = getCurrentUser();
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (user.getClientCompany() == null ||
                !order.getClientCompany().getId().equals(user.getClientCompany().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        GeneratedDocument doc = generatedDocumentRepository
                .findByOrderIdAndDocumentType(id, "due_diligence_report")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF not found"));

        if (!"ready".equals(doc.getStatus()) || doc.getFilePath() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF not ready");
        }

        try {
            byte[] pdfBytes = Files.readAllBytes(Paths.get(doc.getFilePath()));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.inline().filename(doc.getFileName()).build());
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (IOException e) {
            log.error("Failed to read PDF file: {}", doc.getFilePath(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read PDF file");
        }
    }

    @GetMapping("/orders/{id}/download-pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        User user = getCurrentUser();
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (user.getClientCompany() == null ||
                !order.getClientCompany().getId().equals(user.getClientCompany().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        GeneratedDocument doc = generatedDocumentRepository
                .findByOrderIdAndDocumentType(id, "due_diligence_report")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF not found"));

        if (!"ready".equals(doc.getStatus()) || doc.getFilePath() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF not ready");
        }

        try {
            byte[] pdfBytes = Files.readAllBytes(Paths.get(doc.getFilePath()));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(
                    ContentDisposition.builder("attachment")
                            .filename(doc.getFileName() != null ? doc.getFileName() : "report.pdf")
                            .build());
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (IOException e) {
            log.error("Failed to read PDF file: {}", doc.getFilePath(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read PDF file");
        }
    }

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
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return json;
        }
    }

    private String strVal(Object o) {
        return o != null ? String.valueOf(o) : "";
    }

    private Long toLong(Object o) {
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        if (o instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private String generateOrderNumber() {
        String yyMm = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        int random = ThreadLocalRandom.current().nextInt(10000, 99999);
        return "ORD-" + yyMm + "-" + random;
    }
}
