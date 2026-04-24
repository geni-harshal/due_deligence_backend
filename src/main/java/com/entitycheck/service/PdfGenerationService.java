package com.entitycheck.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PdfGenerationService {

    private static final Logger log = LoggerFactory.getLogger(PdfGenerationService.class);
    private final RestTemplate restTemplate;

    @Value("${pdf.service.url}")
    private String pdfServiceUrl;

    public PdfGenerationService() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10_000);
        requestFactory.setReadTimeout(180_000);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public byte[] generatePdf(Map<String, Object> reportData) {
        Map<String, Object> normalized = normalizeForPdf(reportData);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_PDF));
        headers.setConnection("close");
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(Map.of("reportData", normalized), headers);

        ResourceAccessException lastResourceException = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                ResponseEntity<byte[]> response = restTemplate.postForEntity(pdfServiceUrl, request, byte[].class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    return response.getBody();
                }
                throw new RuntimeException("PDF service returned " + response.getStatusCode());
            } catch (ResourceAccessException ex) {
                lastResourceException = ex;
                log.warn("PDF service call attempt {} failed: {}", attempt, ex.getMessage());
                if (attempt == 1) {
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("PDF generation interrupted", ie);
                    }
                }
            }
        }

        if (lastResourceException != null) {
            throw new RuntimeException("PDF service is unreachable or reset the connection", lastResourceException);
        }
        throw new RuntimeException("PDF generation failed");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeForPdf(Map<String, Object> input) {
        if (input == null) {
            return Map.of();
        }

        Map<String, Object> base = input;
        Object reportObj = input.get("report");
        if (reportObj instanceof Map<?, ?> reportMap) {
            base = (Map<String, Object>) reportMap;
        }

        Map<String, Object> normalized = new LinkedHashMap<>(base);

        // Frontend report components expect _meta; python output currently provides meta.
        if (!normalized.containsKey("_meta") && normalized.get("meta") instanceof Map<?, ?> meta) {
            normalized.put("_meta", meta);
        }

        // Some components expect string lists/maps to exist; keep original payload untouched otherwise.
        return normalized;
    }
}
