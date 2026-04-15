package com.entitycheck.service;

import com.entitycheck.model.Order;
import com.entitycheck.model.ProviderSearchSnapshot;
import com.entitycheck.model.RawComprehensiveData;
import com.entitycheck.repository.ProviderSearchSnapshotRepository;
import com.entitycheck.repository.RawComprehensiveDataRepository;
import com.entitycheck.api.dto.CompanySearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ComprehensiveDataService {

    private final Probe42IntegrationService probe42IntegrationService;
    private final RawComprehensiveDataRepository rawRepository;
    private final ProviderSearchSnapshotRepository latestCacheRepository;
    private final ReportDataService reportDataService;
    private final ObjectMapper objectMapper;

    public ComprehensiveDataService(
            Probe42IntegrationService probe42IntegrationService,
            RawComprehensiveDataRepository rawRepository,
            ProviderSearchSnapshotRepository latestCacheRepository,
            ReportDataService reportDataService,
            ObjectMapper objectMapper) {
        this.probe42IntegrationService = probe42IntegrationService;
        this.rawRepository = rawRepository;
        this.latestCacheRepository = latestCacheRepository;
        this.reportDataService = reportDataService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> fetchAndStoreFresh(Order order, String identifier, String fetchedBy) {
        String effectiveIdentifier = normalizeIdentifier(identifier);
        JsonNode raw = probe42IntegrationService.fetchComprehensive(effectiveIdentifier);

        if ((raw == null || raw.isNull()) && order != null && order.getSubjectName() != null && !order.getSubjectName().isBlank()) {
            List<CompanySearchResult> candidates = probe42IntegrationService.searchByNamePrefix(order.getSubjectName(), 5);
            for (CompanySearchResult candidate : candidates) {
                if (candidate == null || candidate.cin() == null || candidate.cin().isBlank()) {
                    continue;
                }
                effectiveIdentifier = candidate.cin();
                raw = probe42IntegrationService.fetchComprehensive(effectiveIdentifier);
                if (raw != null && !raw.isNull()) {
                    break;
                }
            }
        }

        if (raw == null || raw.isNull()) {
            throw new RuntimeException("Probe42 returned no data for identifier: " + identifier + ". Please use a valid CIN/PAN/LLPIN.");
        }

        try {
            String rawJson = objectMapper.writeValueAsString(raw);

            Map<String, Object> transformed = reportDataService.transformToReport(
                    raw,
                    order.getSubjectName(),
                    effectiveIdentifier
            );
            String transformedJson = objectMapper.writeValueAsString(transformed);

            int nextVersion = rawRepository
                    .findTopByOrder_IdOrderByVersionDesc(order.getId())
                    .map(v -> v.getVersion() + 1)
                    .orElse(1);

            String companyName = extractCompanyName(raw, order.getSubjectName());

            RawComprehensiveData snapshot = new RawComprehensiveData();
            snapshot.setOrder(order);
            snapshot.setVersion(nextVersion);
            snapshot.setProvider("PROBE42");
            snapshot.setCin(effectiveIdentifier);
            snapshot.setCompanyName(companyName);
            snapshot.setRawJson(rawJson);
            snapshot.setTransformedJson(transformedJson);
            snapshot.setFetchedBy(fetchedBy);
            snapshot.setFetchedAt(OffsetDateTime.now());

            RawComprehensiveData saved = rawRepository.save(snapshot);

            ProviderSearchSnapshot latestCache = latestCacheRepository.findByOrderId(order.getId())
                    .orElseGet(() -> {
                        ProviderSearchSnapshot s = new ProviderSearchSnapshot();
                        s.setOrder(order);
                        return s;
                    });

            latestCache.setRawResultsJson(rawJson);
            latestCache.setTransformedReportJson(transformedJson);
            latestCache.setFetchedAt(saved.getFetchedAt());
            latestCacheRepository.save(latestCache);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("orderId", order.getId());
            response.put("version", saved.getVersion());
            response.put("provider", saved.getProvider());
            response.put("cin", saved.getCin());
            response.put("requestedIdentifier", identifier);
            response.put("companyName", saved.getCompanyName());
            response.put("fetchedAt", saved.getFetchedAt().toString());
            response.put("rawResults", raw);
            response.put("report", transformed);

            return response;
        } catch (Exception e) {
            throw new RuntimeException("Failed to store fresh comprehensive data", e);
        }
    }

    public Map<String, Object> getLatest(Long orderId) {
        RawComprehensiveData latest = rawRepository.findTopByOrder_IdOrderByVersionDesc(orderId).orElse(null);
        if (latest == null) return null;

        return snapshotToMap(latest);
    }

    public List<Map<String, Object>> getVersions(Long orderId) {
        List<RawComprehensiveData> versions = rawRepository.findByOrder_IdOrderByVersionDesc(orderId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (RawComprehensiveData s : versions) {
            out.add(snapshotVersionToMap(s));
        }
        return out;
    }

    public String resolveIdentifier(Order order) {
        if (order == null) return "";

        if (order.getSubjectDetails() != null && !order.getSubjectDetails().isBlank()) {
            try {
                Map<String, Object> details = objectMapper.readValue(
                        order.getSubjectDetails(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}
                );

                String cin = normalizeIdentifier(strVal(details.get("cin")));
                if (!cin.isBlank()) return cin;

                String identifier = normalizeIdentifier(strVal(details.get("identifier")));
                if (!identifier.isBlank()) return identifier;

                String pan = normalizeIdentifier(strVal(details.get("pan")));
                if (!pan.isBlank()) return pan;

                String llpin = normalizeIdentifier(strVal(details.get("llpin")));
                if (!llpin.isBlank()) return llpin;

                String bid = normalizeIdentifier(strVal(details.get("bid")));
                if (!bid.isBlank()) return bid;
            } catch (Exception ignored) {
            }
        }

        return normalizeIdentifier(order.getSubjectName());
    }

    public Map<String, Object> fetchAndStore(String cin) {
    try {
        JsonNode raw = probe42IntegrationService.fetchComprehensive(cin);

        if (raw == null || raw.isNull()) {
            return Map.of("error", "No data found for CIN: " + cin);
        }

        String rawJson = objectMapper.writeValueAsString(raw);

        Map<String, Object> transformed = reportDataService.transformToReport(
                raw,
                extractCompanyName(raw, cin),
                cin
        );

        return Map.of(
                "cin", cin,
                "rawResults", raw,
                "report", transformed,
                "fetchedAt", OffsetDateTime.now().toString()
        );
    } catch (Exception e) {
        return Map.of("error", "Failed to fetch data: " + e.getMessage());
    }
}

public Map<String, Object> getData(String cin) {
    try {
        JsonNode raw = probe42IntegrationService.fetchComprehensive(cin);

        if (raw == null || raw.isNull()) {
            return Map.of("error", "No data found for CIN: " + cin);
        }

        Map<String, Object> transformed = reportDataService.transformToReport(
                raw,
                extractCompanyName(raw, cin),
                cin
        );

        return Map.of(
                "cin", cin,
                "rawResults", raw,
                "report", transformed
        );
    } catch (Exception e) {
        return Map.of("error", "Failed to retrieve data: " + e.getMessage());
    }
}

public Map<String, Object> refreshData(String cin) {
    return fetchAndStore(cin);
}

public boolean exists(String cin) {
    try {
        JsonNode raw = probe42IntegrationService.fetchComprehensive(cin);
        return raw != null && !raw.isNull();
    } catch (Exception e) {
        return false;
    }
}

    private Map<String, Object> snapshotToMap(RawComprehensiveData s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("version", s.getVersion());
        m.put("provider", s.getProvider());
        m.put("cin", s.getCin());
        m.put("companyName", s.getCompanyName());
        m.put("fetchedAt", s.getFetchedAt() != null ? s.getFetchedAt().toString() : null);
        m.put("fetchedBy", s.getFetchedBy());

        try {
            if (s.getRawJson() != null) {
                m.put("rawResults", objectMapper.readValue(s.getRawJson(), Object.class));
            }
            if (s.getTransformedJson() != null) {
                m.put("report", objectMapper.readValue(s.getTransformedJson(), Object.class));
            }
        } catch (Exception ignored) {
        }
        return m;
    }

    private Map<String, Object> snapshotSummaryToMap(RawComprehensiveData s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("version", s.getVersion());
        m.put("provider", s.getProvider());
        m.put("cin", s.getCin());
        m.put("companyName", s.getCompanyName());
        m.put("fetchedAt", s.getFetchedAt() != null ? s.getFetchedAt().toString() : null);
        m.put("fetchedBy", s.getFetchedBy());
        return m;
    }

    private Map<String, Object> snapshotVersionToMap(RawComprehensiveData s) {
        Map<String, Object> m = snapshotSummaryToMap(s);
        try {
            if (s.getTransformedJson() != null) {
                m.put("report", objectMapper.readValue(s.getTransformedJson(), Object.class));
            }
        } catch (Exception ignored) {
        }
        return m;
    }

    private String strVal(Object o) {
        return o != null ? String.valueOf(o) : "";
    }

    private String normalizeIdentifier(String value) {
        if (value == null) return "";
        return value.trim().toUpperCase();
    }

    private String extractCompanyName(JsonNode raw, String fallback) {
        String fromCompany = raw.path("data").path("company").path("legal_name").asText("");
        String fromPnp = raw.path("data").path("pnp").path("legal_name").asText("");
        String fromRoot = raw.path("data").path("legal_name").asText("");
        if (!fromCompany.isBlank()) return fromCompany;
        if (!fromPnp.isBlank()) return fromPnp;
        if (!fromRoot.isBlank()) return fromRoot;
        return fallback;
    }
}
