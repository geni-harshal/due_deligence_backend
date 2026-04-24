package com.entitycheck.service;

import com.entitycheck.model.CreditReport;
import com.entitycheck.model.GeneratedDocument;
import com.entitycheck.model.Order;
import com.entitycheck.model.OrderStatus;
import com.entitycheck.model.RawComprehensiveData;
import com.entitycheck.repository.CreditReportRepository;
import com.entitycheck.repository.GeneratedDocumentRepository;
import com.entitycheck.repository.OrderRepository;
import com.entitycheck.repository.RawComprehensiveDataRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CreditReportService {

    private static final Logger log = LoggerFactory.getLogger(CreditReportService.class);

    private final RawComprehensiveDataRepository rawRepository;
    private final CreditReportRepository creditReportRepository;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final PdfGenerationService pdfGenerationService;
    private final GeneratedDocumentRepository documentRepository;
    private final AuditLogService auditLogService;

    @Value("${python.model.url}")
    private String pythonModelUrl;

    public CreditReportService(
            RawComprehensiveDataRepository rawRepository,
            CreditReportRepository creditReportRepository,
            OrderRepository orderRepository,
            ObjectMapper objectMapper,
            PdfGenerationService pdfGenerationService,
            GeneratedDocumentRepository documentRepository,
            AuditLogService auditLogService) {
        this.rawRepository = rawRepository;
        this.creditReportRepository = creditReportRepository;
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
        this.pdfGenerationService = pdfGenerationService;
        this.documentRepository = documentRepository;
        this.auditLogService = auditLogService;
    }

    @Async
    public void generateCreditReport(Long orderId) {
        log.info("Starting async credit report generation for orderId: {}", orderId);

        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));
            updateOrderStatus(
                    order,
                    OrderStatus.CREDIT_REPORT_GENERATION_IN_PROGRESS,
                    "credit_report_generation_started",
                    "Credit report generation started");

            RawComprehensiveData latestRaw = rawRepository
                    .findTopByOrder_IdOrderByVersionDesc(orderId)
                    .orElseThrow(() -> new RuntimeException("No raw data found for order"));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> requestEntity = new HttpEntity<>(latestRaw.getRawJson(), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(pythonModelUrl, requestEntity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Python model returned error: " + response.getStatusCode());
            }

            String reportJson = response.getBody();
            log.info("Credit report JSON received for order {} ({} chars)", orderId,
                    reportJson != null ? reportJson.length() : 0);

            CreditReport report = new CreditReport();
            report.setOrder(order);
            report.setVersion(latestRaw.getVersion());
            report.setReportData(reportJson);
            report.setStatus("GENERATED");
            creditReportRepository.save(report);
            updateOrderStatus(
                    order,
                    OrderStatus.CREDIT_REPORT_GENERATED,
                    "credit_report_generation_completed",
                    "Credit report generated and stored");

            try {
                updateOrderStatus(
                        order,
                        OrderStatus.PDF_GENERATION_IN_PROGRESS,
                        "pdf_generation_started",
                        "PDF generation started");
                Map<String, Object> reportDataMap = objectMapper.readValue(
                        reportJson,
                        new TypeReference<Map<String, Object>>() {
                        });
                byte[] pdfBytes = pdfGenerationService.generatePdf(reportDataMap);
                log.info("PDF bytes received for order {}: {}", orderId, pdfBytes.length);
                storePdf(order, latestRaw.getVersion(), pdfBytes);
            } catch (Exception pdfEx) {
                updateOrderStatus(
                        order,
                        OrderStatus.PDF_GENERATION_FAILED,
                        "pdf_generation_failed",
                        "PDF generation failed: " + pdfEx.getMessage());
                log.error("PDF generation failed for order {}: {}", orderId, pdfEx.getMessage(), pdfEx);
            }

        } catch (Exception ex) {
            log.error("Credit report generation failed for orderId {}: {}", orderId, ex.getMessage(), ex);
            try {
                orderRepository.findById(orderId).ifPresent(order -> updateOrderStatus(
                        order,
                        OrderStatus.CREDIT_REPORT_GENERATION_FAILED,
                        "credit_report_generation_failed",
                        "Credit report generation failed: " + ex.getMessage()));
            } catch (Exception ignored) {
                log.warn("Failed to persist credit report failure status for order {}", orderId);
            }
        }
    }

    private void storePdf(Order order, Integer version, byte[] pdfBytes) {
        GeneratedDocument doc = documentRepository
                .findByOrderIdAndDocumentType(order.getId(), "credit_report")
                .orElse(new GeneratedDocument());
        doc.setOrder(order);
        doc.setDocumentType("credit_report");
        doc.setPdfBase64(Base64.getEncoder().encodeToString(pdfBytes));
        doc.setFileName(String.format("Credit_Report_%s_v%d.pdf", order.getOrderNumber(), version));
        doc.setStatus("ready");
        documentRepository.save(doc);
        log.info("PDF stored in generated_documents for order {} as {}", order.getId(), doc.getFileName());
        updateOrderStatus(order, OrderStatus.PDF_GENERATED, "pdf_generation_completed", "PDF generated and stored");
        if (order.getCompletedAt() == null) {
            order.setCompletedAt(OffsetDateTime.now());
        }
        updateOrderStatus(order, OrderStatus.COMPLETED, "order_completed", "Order completed after PDF generation");
    }

    private void updateOrderStatus(Order order, OrderStatus next, String action, String message) {
        if (order.getStatus() == next) {
            return;
        }
        OrderStatus previous = order.getStatus();
        order.setStatus(next);
        orderRepository.save(order);
        log.info("Order {} status transition: {} -> {}", order.getId(), previous, next);
        auditLogService.logStatusChange(
                order,
                previous,
                next,
                action,
                message,
                "system",
                Map.of(
                        "orderId", order.getId(),
                        "orderNumber", order.getOrderNumber()));
    }

    public Map<String, Object> getLatestCreditReport(Long orderId) {
        CreditReport report = creditReportRepository
                .findTopByOrderIdOrderByVersionDesc(orderId)
                .orElse(null);
        if (report == null) {
            return null;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("versionNumber", report.getVersion());
        result.put("status", report.getStatus());
        result.put("createdAt", report.getCreatedAt().toString());
        try {
            result.put("reportData", objectMapper.readValue(report.getReportData(), Object.class));
        } catch (Exception e) {
            result.put("reportData", report.getReportData());
        }
        return result;
    }
}
