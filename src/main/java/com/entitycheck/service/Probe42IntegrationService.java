package com.entitycheck.service;

import com.entitycheck.probe.Probe42Client;
import com.entitycheck.api.dto.CompanySearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class Probe42IntegrationService {

    private final Probe42Client probe42Client;

    public Probe42IntegrationService(Probe42Client probe42Client) {
        this.probe42Client = probe42Client;
    }

    public JsonNode fetchComprehensive(String identifier) {
        try {
            return probe42Client.getComprehensiveByIdentifier(identifier);
        } catch (Exception e) {
            throw new RuntimeException("Probe42 fetch failed for identifier: " + identifier, e);
        }
    }

    public List<CompanySearchResult> searchByNamePrefix(String query, int limit) {
        try {
            return probe42Client.searchByNamePrefix(query, limit);
        } catch (Exception e) {
            throw new RuntimeException("Probe42 search failed for query: " + query, e);
        }
    }
}
