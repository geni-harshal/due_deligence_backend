package com.entitycheck.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Transforms raw Probe42 comprehensive response.
 * Strategy: Pass through raw JSON data as-is to the frontend.
 * The frontend reads directly from the Probe42 API structure
 * matching the Excel field mapping exactly.
 */
@Service
public class ReportDataService {

    private static final Logger log = LoggerFactory.getLogger(ReportDataService.class);
    private final ObjectMapper mapper;

    public ReportDataService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Pass through raw Probe42 data to frontend.
     * Adds metadata and the entire data node as-is.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> transformToReport(JsonNode raw, String companyName, String cin) {
        Map<String, Object> report = new LinkedHashMap<>();

        // Add metadata
        JsonNode metadata = raw.path("metadata");
        if (!metadata.isMissingNode()) {
            report.put("metadata", mapper.convertValue(metadata, Map.class));
        }

        // Pass through ALL data as-is - frontend reads from exact API paths
        JsonNode data = raw.path("data");
        if (!data.isMissingNode()) {
            Map<String, Object> dataMap = mapper.convertValue(data, Map.class);
            report.putAll(dataMap);
        }

        // Add fallback identifiers if missing
        if (!report.containsKey("company") || report.get("company") == null) {
            report.put("company", Map.of("legal_name", companyName, "cin", cin));
        }

        log.info("Transformed report for {} ({}) — {} top-level keys", companyName, cin, report.size());
        return report;
    }
}