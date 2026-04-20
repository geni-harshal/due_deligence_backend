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

    @Value("${python.model.url}")
    private String pythonModelUrl;

    public CreditReportService(RawComprehensiveDataRepository rawRepository,
                               CreditReportRepository creditReportRepository,
                               OrderRepository orderRepository,
                               ObjectMapper objectMapper) {
        this.rawRepository = rawRepository;
        this.creditReportRepository = creditReportRepository;
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Asynchronously generates a credit report for the given order.
     * The report is fetched from the Python model and stored in the database.
     * This method does not return a value; any errors are logged.
     */
    @Async
    @Transactional
    public void generateCreditReport(Long orderId) {
        log.info("Starting async credit report generation for orderId: {}", orderId);

        try {
            // 1. Get Order
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));
            log.debug("Order found: {}", order.getOrderNumber());

            // 2. Fetch latest raw JSON version
            RawComprehensiveData latestRaw = rawRepository
                    .findTopByOrder_IdOrderByVersionDesc(orderId)
                    .orElseThrow(() -> new RuntimeException("No raw data found for order"));
            log.debug("Latest raw data version: {}, fetchedAt: {}", latestRaw.getVersion(), latestRaw.getFetchedAt());

            String rawJson = latestRaw.getRawJson();
            log.debug("Raw JSON length: {} characters", rawJson.length());

            // 3. Prepare request to Python model
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> requestEntity = new HttpEntity<>(rawJson, headers);

            // 4. Call Python model
            log.info("Calling Python model at URL: {}", pythonModelUrl);
            ResponseEntity<String> response = restTemplate.postForEntity(pythonModelUrl, requestEntity, String.class);
            log.info("Python model responded with status: {}", response.getStatusCode());

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Python model returned non-2xx status: {} - body: {}", response.getStatusCode(), response.getBody());
                throw new RuntimeException("Python model returned error: " + response.getStatusCode());
            }

            String reportJson = response.getBody();
            log.info("Received report JSON of length: {}", reportJson != null ? reportJson.length() : 0);

            // 5. Store in DB
            CreditReport report = new CreditReport();
            report.setOrder(order);
            report.setVersion(latestRaw.getVersion());
            report.setReportData(reportJson);
            report.setStatus("generated");
            creditReportRepository.save(report);
            log.info("Credit report saved with id: {} for orderId: {}", report.getId(), orderId);

        } catch (Exception e) {
            log.error("Credit report generation failed for orderId {}: {}", orderId, e.getMessage(), e);
            // Optionally save a failure record
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