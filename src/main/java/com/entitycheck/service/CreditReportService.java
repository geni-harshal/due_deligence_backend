package com.entitycheck.service;

import com.entitycheck.model.*;
import com.entitycheck.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class CreditReportService {

    private static final Logger log = LoggerFactory.getLogger(CreditReportService.class);

    private final RawComprehensiveDataRepository rawRepository;
    private final CreditReportRepository creditReportRepository;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final PdfGenerationService pdfGenerationService;

    @Value("${python.model.url}")
    private String pythonModelUrl;

    public CreditReportService(RawComprehensiveDataRepository rawRepository,
                               CreditReportRepository creditReportRepository,
                               OrderRepository orderRepository,
                               ObjectMapper objectMapper,
                               PdfGenerationService pdfGenerationService) {
        this.rawRepository = rawRepository;
        this.creditReportRepository = creditReportRepository;
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
        this.pdfGenerationService = pdfGenerationService;
    }

    @Async
    @Transactional
    public void generateCreditReport(Long orderId) {
        log.info("Starting async credit report generation for orderId: {}", orderId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        order.setStatus(OrderStatus.REPORT_GENERATING);
        orderRepository.save(order);

        try {
            RawComprehensiveData latestRaw = rawRepository
                    .findTopByOrder_IdOrderByVersionDesc(orderId)
                    .orElseThrow(() -> new RuntimeException("No raw data found for order " + orderId));
            String rawJson = latestRaw.getRawJson();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> requestEntity = new HttpEntity<>(rawJson, headers);

            log.info("Calling Python model at URL: {}", pythonModelUrl);
            ResponseEntity<String> response = restTemplate.postForEntity(pythonModelUrl, requestEntity, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Python model returned error: " + response.getStatusCode());
            }
            String reportJson = response.getBody();

            CreditReport report = new CreditReport();
            report.setOrder(order);
            report.setVersion(latestRaw.getVersion());
            report.setReportData(reportJson);
            report.setStatus("GENERATED");
            creditReportRepository.save(report);

            order.setStatus(OrderStatus.REPORT_GENERATED);
            orderRepository.save(order);

            log.info("Credit report generated and saved for orderId: {}", orderId);

            // Trigger PDF generation
            pdfGenerationService.generatePdfFromReport(orderId);

        } catch (Exception e) {
            log.error("Credit report generation failed for orderId {}: {}", orderId, e.getMessage(), e);
            order.setStatus(OrderStatus.DATA_FETCHED); // fallback
            orderRepository.save(order);
        }
    }

    public Map<String, Object> getLatestCreditReport(Long orderId) {
        CreditReport report = creditReportRepository
                .findTopByOrderIdOrderByVersionDesc(orderId)
                .orElse(null);
        if (report == null) return null;

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