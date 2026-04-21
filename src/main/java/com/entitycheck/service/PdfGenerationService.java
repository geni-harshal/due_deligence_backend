package com.entitycheck.service;

import com.entitycheck.model.*;
import com.entitycheck.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;

@Service
public class PdfGenerationService {

    private static final Logger log = LoggerFactory.getLogger(PdfGenerationService.class);

    private final OrderRepository orderRepository;
    private final CreditReportRepository creditReportRepository;
    private final GeneratedDocumentRepository documentRepository;
    private final ObjectMapper objectMapper;
    private final GeniReportPdfGenerator geniPdfGenerator;

    @Value("${app.storage.pdf-path:./storage/reports}")
    private String pdfStoragePath;

    public PdfGenerationService(OrderRepository orderRepository,
            CreditReportRepository creditReportRepository,
            GeneratedDocumentRepository documentRepository,
            ObjectMapper objectMapper,
            GeniReportPdfGenerator geniPdfGenerator) {
        this.orderRepository = orderRepository;
        this.creditReportRepository = creditReportRepository;
        this.documentRepository = documentRepository;
        this.objectMapper = objectMapper;
        this.geniPdfGenerator = geniPdfGenerator;
    }

    @Async
    @Transactional
    public void generatePdfFromReport(Long orderId) {
        log.info("Starting PDF generation for orderId: {}", orderId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        order.setStatus(OrderStatus.PDF_GENERATING);
        orderRepository.save(order);

        try {
            CreditReport report = creditReportRepository
                    .findTopByOrderIdOrderByVersionDesc(orderId)
                    .orElseThrow(() -> new RuntimeException("No credit report found for order " + orderId));
            if (!"GENERATED".equals(report.getStatus())) {
                throw new RuntimeException("Credit report not ready (status: " + report.getStatus() + ")");
            }

            Map<String, Object> reportData = objectMapper.readValue(
                    report.getReportData(),
                    new TypeReference<Map<String, Object>>() {
                    });
            byte[] pdfBytes = geniPdfGenerator.generatePdf(reportData, order.getOrderNumber(), order.getSubjectName());

            String fileName = "DDR-" + order.getOrderNumber() + ".pdf";
            Path orderDir = Paths.get(pdfStoragePath, orderId.toString());
            Files.createDirectories(orderDir);
            Path filePath = orderDir.resolve(fileName);
            Files.write(filePath, pdfBytes);

            GeneratedDocument doc = documentRepository
                    .findByOrderIdAndDocumentType(orderId, "due_diligence_report")
                    .orElseGet(() -> {
                        GeneratedDocument gd = new GeneratedDocument();
                        gd.setOrder(order);
                        gd.setDocumentType("due_diligence_report");
                        return gd;
                    });
            doc.setStatus("ready");
            doc.setFilePath(filePath.toString());
            doc.setFileName(fileName);
            documentRepository.save(doc);

            order.setStatus(OrderStatus.PDF_GENERATED);
            orderRepository.save(order);

            log.info("PDF generated and saved for orderId: {} at {}", orderId, filePath);

        } catch (Exception e) {
            log.error("PDF generation failed for orderId {}: {}", orderId, e.getMessage(), e);
            order.setStatus(OrderStatus.REPORT_GENERATED); // fallback
            orderRepository.save(order);
        }
    }
}