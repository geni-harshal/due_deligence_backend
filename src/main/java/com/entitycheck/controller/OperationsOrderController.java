package com.entitycheck.controller;

import com.entitycheck.model.*;
import com.entitycheck.probe.Probe42Client;
import com.entitycheck.repository.*;
import com.entitycheck.service.DecisionEngineService;
import com.entitycheck.service.OrderPdfService;
import com.entitycheck.service.ReportDataService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.entitycheck.service.ComprehensiveDataService;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/api/operations")
public class OperationsOrderController {
    private final ComprehensiveDataService comprehensiveDataService;

    private static final Logger log = LoggerFactory.getLogger(OperationsOrderController.class);

    private final OrderRepository orderRepository;
    private final ClientCompanyRepository clientCompanyRepository;
    private final ProviderSearchSnapshotRepository snapshotRepository;
    private final AnalystEnrichmentRepository enrichmentRepository;
    private final GeneratedDocumentRepository documentRepository;
    private final Probe42Client probe42Client;
    private final ReportDataService reportDataService;
    private final DecisionEngineService decisionEngineService;
    private final OrderPdfService orderPdfService;
    private final ObjectMapper objectMapper;
    
public OperationsOrderController(
        OrderRepository orderRepository,
        ClientCompanyRepository clientCompanyRepository,
        ProviderSearchSnapshotRepository snapshotRepository,
        AnalystEnrichmentRepository enrichmentRepository,
        GeneratedDocumentRepository documentRepository,
        Probe42Client probe42Client,
        ReportDataService reportDataService,
        DecisionEngineService decisionEngineService,
        OrderPdfService orderPdfService,
        ObjectMapper objectMapper,
        ComprehensiveDataService comprehensiveDataService
) {
    this.orderRepository = orderRepository;
    this.clientCompanyRepository = clientCompanyRepository;
    this.snapshotRepository = snapshotRepository;
    this.enrichmentRepository = enrichmentRepository;
    this.documentRepository = documentRepository;
    this.probe42Client = probe42Client;
    this.reportDataService = reportDataService;
    this.decisionEngineService = decisionEngineService;
    this.orderPdfService = orderPdfService;
    this.objectMapper = objectMapper;
    this.comprehensiveDataService = comprehensiveDataService;
}
    // ── GET /api/operations/orders ──
    @GetMapping("/orders")
    public ResponseEntity<List<Map<String, Object>>> listOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long clientCompanyId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {

        List<Order> orders = orderRepository.findAllWithDetails();

        // Apply filters
        if (status != null && !status.isBlank() && !"all".equals(status)) {
            orders = orders.stream()
                    .filter(o -> o.getStatus().name().equalsIgnoreCase(status))
                    .toList();
        }
        if (clientCompanyId != null) {
            orders = orders.stream()
                    .filter(o -> o.getClientCompany() != null &&
                            o.getClientCompany().getId().equals(clientCompanyId))
                    .toList();
        }
        if (q != null && !q.isBlank()) {
            String lower = q.toLowerCase();
            orders = orders.stream()
                    .filter(o -> (o.getSubjectName() != null && o.getSubjectName().toLowerCase().contains(lower)) ||
                            (o.getOrderNumber() != null && o.getOrderNumber().toLowerCase().contains(lower)))
                    .toList();
        }

        List<Map<String, Object>> result = orders.stream().map(o -> {
            Map<String, Object> m = orderToMap(o);
            // Add agingDays for ops queue
            if (o.getCreatedAt() != null) {
                m.put("agingDays", ChronoUnit.DAYS.between(o.getCreatedAt(), OffsetDateTime.now()));
            } else {
                m.put("agingDays", 0);
            }
            return m;
        }).toList();

        return ResponseEntity.ok(result);
    }

    // ── GET /api/operations/orders/{id} ──
    @GetMapping("/orders/{id}")
    public ResponseEntity<Map<String, Object>> getOrder(@PathVariable Long id) {
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        Map<String, Object> result = orderToMap(order);
        result.put("latestSnapshot", comprehensiveDataService.getLatest(id));
        result.put("versions", comprehensiveDataService.getVersions(id));

        // Attach provider search snapshot
        snapshotRepository.findByOrderId(id).ifPresent(snap -> {
            try {
                Map<String, Object> transformed =
                        objectMapper.readValue(
                                snap.getTransformedReportJson(),
                                new TypeReference<Map<String, Object>>() {}
                        );

                Map<String, Object> snapshotData = new LinkedHashMap<>();

                // exact frontend-friendly shape
                Object versionInfo = transformed.get("versionInfo");
                if (versionInfo == null) {
                    versionInfo = Map.of(
                            "version", 1,
                            "provider", "PROBE42",
                            "fetchedAt", OffsetDateTime.now().toString(),
                            "fetchedBy", "operations"
                    );
                }

                snapshotData.put("versionInfo", versionInfo);
                snapshotData.put("metadata", transformed.get("metadata"));

                Object data = transformed.get("data");
                if (data == null) {
                    Map<String, Object> fallback = new LinkedHashMap<>(transformed);
                    fallback.remove("metadata");
                    fallback.remove("versionInfo");
                    data = fallback;
                }

                snapshotData.put("data", data);
                result.put("providerSearchSnapshot", snapshotData);

            } catch (Exception e) {
                log.warn("Failed to parse snapshot for order {}", id, e);
            }
        });

        // Attach analyst enrichment
        enrichmentRepository.findByOrderId(id).ifPresent(enrich -> {
            Map<String, Object> enrichData = new LinkedHashMap<>();
            enrichData.put("investigationSummary", enrich.getInvestigationSummary());
            enrichData.put("analystComments", enrich.getAnalystComments());
            enrichData.put("recommendationNotes", enrich.getRecommendationNotes());
            enrichData.put("redFlags", parseJsonList(enrich.getRedFlagsJson()));
            enrichData.put("isDraft", enrich.isDraft());
            if (enrich.getDecisionOutputsJson() != null) {
                try {
                    enrichData.put("decisionOutputs", objectMapper.readValue(
                            enrich.getDecisionOutputsJson(), new TypeReference<Map<String, Object>>() {}));
                } catch (Exception e) {
                    log.warn("Failed to parse decision outputs for order {}", id, e);
                }
            }
            result.put("analystEnrichment", enrichData);
        });

        // Attach generated documents
        List<GeneratedDocument> docs = documentRepository.findByOrderId(id);
        List<Map<String, Object>> docList = docs.stream().map(d -> {
            Map<String, Object> dm = new LinkedHashMap<>();
            dm.put("id", d.getId());
            dm.put("documentType", d.getDocumentType());
            dm.put("status", d.getStatus());
            dm.put("fileName", d.getFileName());
            dm.put("url", "/api/operations/orders/" + id + "/download-pdf");
            return dm;
        }).toList();
        result.put("generatedDocuments", docList);

        return ResponseEntity.ok(result);
    }

    // ── POST /api/operations/orders/{id}/fetch-data ──
@PostMapping("/orders/{id}/fetch-data")
public ResponseEntity<Map<String, Object>> fetchData(@PathVariable Long id) {
    Order order = orderRepository.findByIdWithDetails(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

    String identifier = comprehensiveDataService.resolveIdentifier(order);
    if (identifier == null || identifier.isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No CIN/identifier found for this order");
    }

    Map<String, Object> saved;
    try {
        saved = comprehensiveDataService.fetchAndStoreFresh(
                order,
                identifier,
                "operations"
        );
    } catch (RuntimeException ex) {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    order.setStatus(OrderStatus.DATA_FETCHED);
    orderRepository.save(order);

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("status", "data_fetched");
    response.put("latest", saved);
    response.put("latestSnapshot", comprehensiveDataService.getLatest(id));
    response.put("versions", comprehensiveDataService.getVersions(id));

    return ResponseEntity.ok(response);
}
    // ── PUT /api/operations/orders/{id}/enrichment ──
    @PutMapping("/orders/{id}/enrichment")
    public ResponseEntity<Map<String, Object>> saveEnrichment(@PathVariable Long id,
                                                               @RequestBody Map<String, Object> body) {
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        String investigationSummary = strVal(body.get("investigationSummary"));
        String analystComments = strVal(body.get("analystComments"));
        String recommendationNotes = strVal(body.get("recommendationNotes"));
        @SuppressWarnings("unchecked")
        List<String> redFlags = body.get("redFlags") instanceof List ? (List<String>) body.get("redFlags") : List.of();
        boolean isDraft = Boolean.TRUE.equals(body.get("isDraft"));

        // Upsert enrichment
        AnalystEnrichment enrichment = enrichmentRepository.findByOrderId(id)
                .orElseGet(() -> {
                    AnalystEnrichment ae = new AnalystEnrichment();
                    ae.setOrder(order);
                    return ae;
                });

        enrichment.setInvestigationSummary(investigationSummary);
        enrichment.setAnalystComments(analystComments);
        enrichment.setRecommendationNotes(recommendationNotes);
        enrichment.setDraft(isDraft);
        try {
            enrichment.setRedFlagsJson(objectMapper.writeValueAsString(redFlags));
        } catch (Exception e) {
            enrichment.setRedFlagsJson("[]");
        }
        enrichmentRepository.save(enrichment);

        // Advance status if not draft
        if (!isDraft && (order.getStatus() == OrderStatus.DATA_FETCHED ||
                order.getStatus() == OrderStatus.PENDING_DATA_FETCH)) {
            order.setStatus(OrderStatus.IN_PROGRESS);
            orderRepository.save(order);
        }

        return ResponseEntity.ok(Map.of("status", "saved", "isDraft", isDraft));
    }

    // ── POST /api/operations/orders/{id}/run-models ──
    @PostMapping("/orders/{id}/run-models")
    public ResponseEntity<Map<String, Object>> runModels(@PathVariable Long id) {
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        // Get the transformed report data
        Map<String, Object> latest = comprehensiveDataService.getLatest(id);

        if (latest == null || latest.get("report") == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No company data fetched yet. Please fetch data first."
            );
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> reportData =
        (Map<String, Object>) latest.get("report");

        // Run decision engine
        Map<String, Object> decisionOutputs = decisionEngineService.execute(reportData);

        // Store decision outputs in analyst enrichment
        AnalystEnrichment enrichment = enrichmentRepository.findByOrderId(id)
                .orElseGet(() -> {
                    AnalystEnrichment ae = new AnalystEnrichment();
                    ae.setOrder(order);
                    return ae;
                });
        try {
            enrichment.setDecisionOutputsJson(objectMapper.writeValueAsString(decisionOutputs));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize decision outputs");
        }
        enrichmentRepository.save(enrichment);

        // Update order status
        order.setStatus(OrderStatus.MODEL_EXECUTED);
        orderRepository.save(order);

        return ResponseEntity.ok(decisionOutputs);
    }

    // ── POST /api/operations/orders/{id}/generate-pdf ──
    @PostMapping("/orders/{id}/generate-pdf")
    public ResponseEntity<Map<String, Object>> generatePdf(@PathVariable Long id) {
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        // Get report data
       Map<String, Object> latest = comprehensiveDataService.getLatest(id);

if (latest == null || latest.get("report") == null) {
    throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "No company data fetched yet. Please fetch data first."
    );
}

@SuppressWarnings("unchecked")
Map<String, Object> reportData =
        (Map<String, Object>) latest.get("report");

        // Get enrichment
        AnalystEnrichment enrichment = enrichmentRepository.findByOrderId(id).orElse(null);
        Map<String, Object> enrichmentMap = null;
        Map<String, Object> decisionOutputs = null;
        if (enrichment != null) {
            enrichmentMap = new LinkedHashMap<>();
            enrichmentMap.put("investigationSummary", enrichment.getInvestigationSummary());
            enrichmentMap.put("analystComments", enrichment.getAnalystComments());
            enrichmentMap.put("recommendationNotes", enrichment.getRecommendationNotes());
            enrichmentMap.put("redFlags", parseJsonList(enrichment.getRedFlagsJson()));
            if (enrichment.getDecisionOutputsJson() != null) {
                try {
                    decisionOutputs = objectMapper.readValue(enrichment.getDecisionOutputsJson(),
                            new TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
                    log.warn("Failed to parse decision outputs for PDF generation", e);
                }
            }
        }

        // Generate PDF
        byte[] pdfBytes = orderPdfService.generateDDRPdf(
                order.getOrderNumber(),
                order.getSubjectName(),
                reportData,
                enrichmentMap,
                decisionOutputs
        );

        String pdfBase64 = Base64.getEncoder().encodeToString(pdfBytes);
        String fileName = "DDR-" + order.getOrderNumber() + ".pdf";

        // Upsert generated document
        GeneratedDocument doc = documentRepository.findByOrderIdAndDocumentType(id, "due_diligence_report")
                .orElseGet(() -> {
                    GeneratedDocument gd = new GeneratedDocument();
                    gd.setOrder(order);
                    gd.setDocumentType("due_diligence_report");
                    return gd;
                });
        doc.setStatus("ready");
        doc.setPdfBase64(pdfBase64);
        doc.setFileName(fileName);
        documentRepository.save(doc);

        // Update order status
        order.setStatus(OrderStatus.PDF_GENERATED);
        orderRepository.save(order);

        return ResponseEntity.ok(Map.of(
                "status", "ready",
                "fileName", fileName,
                "documentId", doc.getId()
        ));
    }

    // ── POST /api/operations/orders/{id}/publish ──
    @PostMapping("/orders/{id}/publish")
    public ResponseEntity<Map<String, Object>> publish(@PathVariable Long id) {
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        // Verify PDF exists
        GeneratedDocument doc = documentRepository.findByOrderIdAndDocumentType(id, "due_diligence_report")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No PDF generated. Please generate PDF first."));

        if (!"ready".equals(doc.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PDF is not ready");
        }

        // Verify investigation summary exists
        AnalystEnrichment enrichment = enrichmentRepository.findByOrderId(id).orElse(null);
        if (enrichment == null || enrichment.getInvestigationSummary() == null ||
                enrichment.getInvestigationSummary().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Investigation summary is required before publishing");
        }

        // Mark as completed
        order.setStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(OffsetDateTime.now());
        orderRepository.save(order);

        log.info("Order {} published successfully. Client {} notified (mock).",
                order.getOrderNumber(),
                order.getClientCompany() != null ? order.getClientCompany().getName() : "N/A");

        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "message", "Report published and client notified",
                "completedAt", order.getCompletedAt().toString()
        ));
    }

    // ── GET /api/operations/client-companies ──
    @GetMapping("/client-companies")
    public ResponseEntity<List<Map<String, Object>>> listClientCompanies() {
        List<ClientCompany> companies = clientCompanyRepository.findAll();
        List<Map<String, Object>> result = companies.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("slug", c.getSlug());
            m.put("status", c.getStatus() != null ? c.getStatus().name().toLowerCase() : "active");
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }

    // ── GET /api/operations/stats ──
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        List<Order> all = orderRepository.findAll();
        long pending = all.stream().filter(o ->
                o.getStatus() == OrderStatus.ORDER_PLACED ||
                        o.getStatus() == OrderStatus.PENDING_DATA_FETCH ||
                        o.getStatus() == OrderStatus.DATA_FETCHED).count();
        long inProgress = all.stream().filter(o ->
                o.getStatus() == OrderStatus.IN_PROGRESS ||
                        o.getStatus() == OrderStatus.MODEL_EXECUTED ||
                        o.getStatus() == OrderStatus.PDF_GENERATED).count();
        long completed = all.stream().filter(o -> o.getStatus() == OrderStatus.COMPLETED).count();

        return ResponseEntity.ok(Map.of(
                "pendingOrders", pending,
                "inProgressOrders", inProgress,
                "completedToday", completed, // simplified
                "avgCompletionHours", 24
        ));
    }

    // ── Helpers ──

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

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String strVal(Object o) {
        return o != null ? String.valueOf(o) : "";
    }
}
