package com.entitycheck.api;

import com.entitycheck.api.dto.CompanySearchRequest;
import com.entitycheck.api.dto.CompanySearchResponse;
import com.entitycheck.api.dto.CompanySearchResult;
import com.entitycheck.api.dto.LaunchTokenExchangeRequest;
import com.entitycheck.api.dto.SsoSessionResponse;
import com.entitycheck.probe.Probe42Client;
import com.entitycheck.service.PdfService;
import com.entitycheck.service.ReportMapper;
import com.entitycheck.service.ComprehensiveDataService;
import com.entitycheck.storage.ReportCacheEntity;
import com.entitycheck.storage.ReportCacheRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api")
@Validated
public class ReportController {
  private static final Pattern PAN_PATTERN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");
  private final Probe42Client probe42Client;
  private final ReportMapper reportMapper;
  private final PdfService pdfService;
  private final ReportCacheRepository cacheRepository;
  private final ObjectMapper objectMapper;
  private final ComprehensiveDataService comprehensiveDataService;
  private final Map<String, Map<String, Object>> memoryReports = new ConcurrentHashMap<>();
  private final Map<String, Map<String, Object>> taskStatus = new ConcurrentHashMap<>();

  @Value("${support.contact-number:}")
  private String supportContactNumber;

  @Value("${master-hub.jwt-secret:replace-this-before-production}")
  private String masterHubJwtSecret;

  @Value("${master-hub.jwt-algo:HS256}")
  private String masterHubJwtAlgo;

  @Value("${master-hub.allowed-tenants:}")
  private String masterHubAllowedTenants;

  @Value("${master-hub.solution-id:due_diligence_platform}")
  private String masterHubSolutionId;

  @Value("${master-hub.introspection-url:http://127.0.0.1:8011/auth/introspect-launch-token}")
  private String masterHubIntrospectionUrl;

  @Value("${master-hub.introspection-key:master-hub-introspection-key}")
  private String masterHubIntrospectionKey;

  @Value("${master-hub.introspection-enabled:true}")
  private boolean masterHubIntrospectionEnabled;

  public ReportController(
      Probe42Client probe42Client,
      ReportMapper reportMapper,
      PdfService pdfService,
      ReportCacheRepository cacheRepository,
      ObjectMapper objectMapper,
      ComprehensiveDataService comprehensiveDataService
  ) {
    this.probe42Client = probe42Client;
    this.reportMapper = reportMapper;
    this.pdfService = pdfService;
    this.cacheRepository = cacheRepository;
    this.objectMapper = objectMapper;
    this.comprehensiveDataService = comprehensiveDataService;
  }

  @GetMapping("/")
  public Map<String, Object> health() {
    return Map.of(
        "message", "EntityCheck Java API",
        "stack", "Node + Java + PostgreSQL",
        "timestamp", OffsetDateTime.now().toString()
    );
  }

  @PostMapping("/search-companies")
  public ResponseEntity<CompanySearchResponse> searchCompanies(@Valid @RequestBody CompanySearchRequest request) {
    try {
      String query = request.company_name().trim();
      List<CompanySearchResult> all = new ArrayList<>();

      // Direct PAN path (partnership/proprietorship) should not go through name prefix search.
      if (PAN_PATTERN.matcher(query.toUpperCase()).matches()) {
        JsonNode raw = probe42Client.getComprehensiveByIdentifier(query.toUpperCase());
        if (raw != null) {
          JsonNode company = raw.path("data").path("company");
          JsonNode pnp = raw.path("data").path("pnp");
          String name = firstNonBlank(
              company.path("legal_name").asText(""),
              pnp.path("legal_name").asText(""),
              raw.path("data").path("legal_name").asText(""),
              raw.path("data").path("name").asText(""),
              raw.path("data").path("entity_name").asText(""),
              raw.path("name").asText(""),
              query.toUpperCase()
          );
          all = List.of(
              new CompanySearchResult(
                  name,
                  query.toUpperCase(),
                  "PAN",
                  company.path("registered_address").path("full_address").asText(""),
                  company.path("registered_address").path("state").asText(""),
                  firstNonBlank(company.path("efiling_status").asText(""), pnp.path("status").asText("ACTIVE")),
                  company.path("incorporation_date").asText(""),
                  pnp.isMissingNode() ? "Company" : "Partnership",
                  100.0
              )
          );
        }
      }

      if (all.isEmpty()) {
        all = probe42Client.searchByNamePrefix(query, 25);
      }

      // PAN fallback for proprietorship/partnership or direct identifier entry.
      if (all.isEmpty() && PAN_PATTERN.matcher(query.toUpperCase()).matches()) {
        JsonNode raw = probe42Client.getComprehensiveByIdentifier(query.toUpperCase());
        if (raw != null) {
          JsonNode company = raw.path("data").path("company");
          JsonNode pnp = raw.path("data").path("pnp");
          String name = firstNonBlank(
              company.path("legal_name").asText(""),
              pnp.path("legal_name").asText(""),
              raw.path("data").path("legal_name").asText(""),
              raw.path("data").path("name").asText(""),
              raw.path("data").path("entity_name").asText(""),
              raw.path("name").asText(""),
              query.toUpperCase()
          );
          all = List.of(
              new CompanySearchResult(
                  name,
                  query.toUpperCase(),
                  "PAN",
                  company.path("registered_address").path("full_address").asText(""),
                  company.path("registered_address").path("state").asText(""),
                  company.path("efiling_status").asText("ACTIVE"),
                  company.path("incorporation_date").asText(""),
                  "Company",
                  100.0
              )
          );
        }
      }
      List<CompanySearchResult> results = all.stream()
          .filter(r -> request.state() == null || request.state().isBlank() || (r.state() != null && r.state().toLowerCase().contains(request.state().toLowerCase())))
          .limit(25)
          .toList();
      results = normalizePnpIdentifiers(results);
      if (results.isEmpty()) {
        return ResponseEntity.ok(new CompanySearchResponse(request.company_name(), request.state(), List.of(), 0, noDataMessage(), "probe_no_data"));
      }
      return ResponseEntity.ok(new CompanySearchResponse(request.company_name(), request.state(), results, results.size(), null, null));
    } catch (Exception e) {
      return ResponseEntity.ok(new CompanySearchResponse(request.company_name(), request.state(), List.of(), 0, "Search failed. Please try again.", "probe_no_data"));
    }
  }

  @GetMapping("/states")
  public Map<String, Object> states() {
    return Map.of("states", List.of(
        "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh", "Goa",
        "Gujarat", "Haryana", "Himachal Pradesh", "Jharkhand", "Karnataka", "Kerala",
        "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya", "Mizoram", "Nagaland",
        "Odisha", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana",
        "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal"
    ));
  }

  @GetMapping("/explanations")
  public Map<String, Object> explanations() {
    return Map.of(
        "executive_summary", Map.of("explanation", "Risk, credit and overview summary."),
        "company_profile", Map.of("explanation", "Identity, legal profile and contact details."),
        "financials", Map.of("explanation", "Revenue, profitability, balance sheet and trend details."),
        "ratios", Map.of("explanation", "Liquidity, leverage and efficiency ratios."),
        "compliance", Map.of("explanation", "MCA, GST and EPFO compliance indicators."),
        "legal", Map.of("explanation", "Court matters, defaults and insolvency flags."),
        "management", Map.of("explanation", "Directors and related directorship network.")
    );
  }

  @GetMapping("/risk-grades")
  public Map<String, Object> riskGrades() {
    return Map.of("grades", List.of("Rx1", "Rx2", "Rx3", "Rx4", "Rx5"));
  }

  @PostMapping("/sso/exchange-launch-token")
  public ResponseEntity<SsoSessionResponse> exchangeLaunchToken(
      @Valid @RequestBody LaunchTokenExchangeRequest payload
  ) {
    String userEmail;
    String tenantId;
    String expiresAt;
    List<String> roles;

    if (masterHubIntrospectionEnabled) {
      Map<String, Object> introspected = introspectLaunchToken(payload.launch_token());
      userEmail = asString(introspected.get("user_email"));
      tenantId = asString(introspected.get("tenant_id"));
      expiresAt = asString(introspected.get("expires_at"));
      roles = asStringList(introspected.get("roles"));
    } else {
      Map<String, Object> claims = decodeAndValidateLaunchToken(payload.launch_token());
      userEmail = asString(claims.get("sub"));
      tenantId = asString(claims.get("tenant_id"));
      Number expValue = asNumber(claims.get("exp"));
      String targetSolution = asString(claims.get("target_solution"));
      roles = asStringList(claims.get("roles"));

      if (userEmail == null || tenantId == null || expValue == null) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Launch token missing required claims");
      }
      if (targetSolution != null && !targetSolution.isBlank() && !masterHubSolutionId.equals(targetSolution)) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Launch token is not scoped for this solution");
      }
      expiresAt = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(expValue.longValue()));
    }

    if (userEmail == null || tenantId == null || expiresAt == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Launch token validation missing required claims");
    }

    Set<String> allowedTenants = parseAllowedTenants(masterHubAllowedTenants);
    if (!allowedTenants.isEmpty() && !allowedTenants.contains(tenantId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant is not allowed for this solution");
    }
    return ResponseEntity.ok(new SsoSessionResponse(
        "master_solution_hub",
        userEmail,
        tenantId,
        roles,
        expiresAt
    ));
  }

  private Map<String, Object> introspectLaunchToken(String launchToken) {
    try {
      HttpClient client = HttpClient.newBuilder().build();
      String body = objectMapper.writeValueAsString(Map.of(
          "launch_token", launchToken,
          "solution_id", masterHubSolutionId
      ));
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(masterHubIntrospectionUrl))
          .header("Content-Type", "application/json")
          .header("X-Introspection-Key", masterHubIntrospectionKey)
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .build();

      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        String detail = "Launch token introspection failed";
        try {
          Map<String, Object> parsed = objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
          String parsedDetail = asString(parsed.get("detail"));
          if (parsedDetail != null) detail = parsedDetail;
        } catch (Exception ignored) {
        }
        throw new ResponseStatusException(HttpStatus.valueOf(response.statusCode()), detail);
      }
      return objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Master hub introspection unavailable");
    }
  }

  @GetMapping("/company/{identifier}")
  public ResponseEntity<?> getCompanyReport(@PathVariable String identifier,
                                            @RequestParam(name = "company_name", required = false) String companyName) {
    try {
      String id = normalizeIdentifier(identifier.trim());
      Map<String, Object> cached = loadCached(id);
      if (cached != null) return ResponseEntity.ok(cached);

      JsonNode raw = probe42Client.getComprehensiveByIdentifier(id);
      if (raw == null) return ResponseEntity.status(404).body(Map.of("detail", noDataMessage()));

      Map<String, Object> report = reportMapper.fromProbe(raw, id, companyName);
      storeCached(id, companyName, report);
      return ResponseEntity.ok(report);
    } catch (Exception e) {
      return ResponseEntity.status(500).body(Map.of("detail", "Unable to fetch report right now."));
    }
  }

  @PostMapping("/generate-report")
  public Map<String, Object> generateReport(@RequestBody Map<String, Object> body) {
    String companyName = String.valueOf(body.getOrDefault("company_name", "")).trim();
    String requestId = UUID.randomUUID().toString();
    taskStatus.put(requestId, Map.of("status", "processing", "progress", 10, "message", "Started"));

    try {
      List<CompanySearchResult> results = probe42Client.searchByNamePrefix(companyName, 25);
      results = normalizePnpIdentifiers(results);
      if (results.isEmpty()) {
        taskStatus.put(requestId, Map.of("status", "failed", "progress", 100, "message", noDataMessage()));
      } else {
        boolean created = false;
        for (CompanySearchResult candidate : results) {
          String id = normalizeIdentifier(candidate.cin());
          JsonNode raw = probe42Client.getComprehensiveByIdentifier(id);
          if (raw == null) continue;

          Map<String, Object> report = reportMapper.fromProbe(raw, id, candidate.name());
          memoryReports.put(requestId, report);
          storeCached(id, candidate.name(), report);
          taskStatus.put(requestId, Map.of("status", "completed", "progress", 100, "message", "Completed"));
          created = true;
          break;
        }
        if (!created) {
          taskStatus.put(requestId, Map.of("status", "failed", "progress", 100, "message", noDataMessage()));
        }
      }
    } catch (Exception e) {
      taskStatus.put(requestId, Map.of("status", "failed", "progress", 100, "message", "Failed to generate report"));
    }
    return Map.of("request_id", requestId, "status", "processing", "message", "Report generation started");
  }

  @GetMapping("/report-status/{requestId}")
  public Map<String, Object> reportStatus(@PathVariable String requestId) {
    return taskStatus.getOrDefault(requestId, Map.of("status", "not_found", "progress", 0, "message", "Request not found"));
  }

  @GetMapping("/report/{requestId}")
  public ResponseEntity<?> reportByRequestId(@PathVariable String requestId) {
    Map<String, Object> report = memoryReports.get(requestId);
    if (report == null) return ResponseEntity.status(404).body(Map.of("detail", "Report not found"));
    return ResponseEntity.ok(report);
  }

  @PostMapping("/generate-pdf/{identifier}")
  public ResponseEntity<?> generatePdf(@PathVariable String identifier,
                                       @RequestParam(name = "company_name", required = false) String companyName) {
    try {
      String normalizedIdentifier = normalizeIdentifier(identifier);
      Map<String, Object> report = loadCached(normalizedIdentifier);
      if (report == null) {
        JsonNode raw = probe42Client.getComprehensiveByIdentifier(normalizedIdentifier);
        if (raw == null) return ResponseEntity.status(404).body(Map.of("detail", noDataMessage()));
        report = reportMapper.fromProbe(raw, normalizedIdentifier, companyName);
        storeCached(normalizedIdentifier, companyName, report);
      }
      byte[] pdf = pdfService.generate(report);
      String filename = (String.valueOf(report.getOrDefault("name", "Company")).replaceAll("[^A-Za-z0-9]+", "_")
          + "_Due_Diligence_Report_" + DateTimeFormatter.ofPattern("yyyyMMdd").format(OffsetDateTime.now()) + ".pdf");
      return ResponseEntity.ok()
          .contentType(MediaType.APPLICATION_PDF)
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
          .header("Access-Control-Expose-Headers", "Content-Disposition")
          .body(pdf);
    } catch (Exception e) {
      return ResponseEntity.status(500).body(Map.of("detail", "PDF generation failed"));
    }
  }

  // ========================================
  // COMPREHENSIVE DATA ENDPOINTS
  // ========================================

  /**
   * Fetch comprehensive data from Probe42 and store in database
   */
  @PostMapping("/comprehensive/fetch/{cin}")
  public ResponseEntity<?> fetchComprehensive(@PathVariable String cin) {
    try {
      Map<String, Object> result = comprehensiveDataService.fetchAndStore(cin);
      
      if (result.containsKey("error")) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
      }
      
      return ResponseEntity.ok(result);
      
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
              .body(Map.of("error", "Failed to fetch comprehensive data: " + e.getMessage()));
    }
  }

  /**
   * Get comprehensive data from database
   */
  @GetMapping("/comprehensive/{cin}")
  public ResponseEntity<?> getComprehensive(@PathVariable String cin) {
    try {
      Map<String, Object> result = comprehensiveDataService.getData(cin);
      
      if (result.containsKey("error")) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
      }
      
      return ResponseEntity.ok(result);
      
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
              .body(Map.of("error", "Failed to retrieve comprehensive data: " + e.getMessage()));
    }
  }

  /**
   * Refresh comprehensive data - fetch fresh from API
   */
  @PostMapping("/comprehensive/refresh/{cin}")
  public ResponseEntity<?> refreshComprehensive(@PathVariable String cin) {
    try {
      Map<String, Object> result = comprehensiveDataService.refreshData(cin);
      
      if (result.containsKey("error")) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
      }
      
      return ResponseEntity.ok(result);
      
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
              .body(Map.of("error", "Failed to refresh comprehensive data: " + e.getMessage()));
    }
  }

  /**
   * Check if comprehensive data exists for a CIN
   */
  @GetMapping("/comprehensive/exists/{cin}")
  public ResponseEntity<?> checkComprehensiveExists(@PathVariable String cin) {
    try {
      boolean exists = comprehensiveDataService.exists(cin);
      return ResponseEntity.ok(Map.of("exists", exists, "cin", cin));
    } catch (Exception e) {
      return ResponseEntity.ok(Map.of("exists", false, "cin", cin, "error", e.getMessage()));
    }
  }

  // ========================================
  // PRIVATE HELPER METHODS
  // ========================================

  private Map<String, Object> loadCached(String identifier) {
    try {
      return cacheRepository.findById(identifier)
          .map(e -> {
            try {
              return objectMapper.readValue(e.getPayloadJson(), new TypeReference<Map<String, Object>>() {});
            } catch (Exception ex) {
              return null;
            }
          })
          .orElse(null);
    } catch (Exception e) {
      return null;
    }
  }

  private void storeCached(String identifier, String companyName, Map<String, Object> report) {
    try {
      ReportCacheEntity entity = new ReportCacheEntity();
      entity.setIdentifier(identifier);
      entity.setCompanyName(companyName);
      entity.setPayloadJson(objectMapper.writeValueAsString(report));
      entity.setUpdatedAt(OffsetDateTime.now());
      cacheRepository.save(entity);
    } catch (Exception ignored) {
    }
  }

  private String noDataMessage() {
    String base = "No data available at this time. We can raise a fresh investigation for the same.";
    if (supportContactNumber != null && !supportContactNumber.isBlank()) {
      return base + " Please contact on " + supportContactNumber + ".";
    }
    return base + " Please contact support.";
  }

  private String firstNonBlank(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) return v;
    }
    return "";
  }

  private List<CompanySearchResult> normalizePnpIdentifiers(List<CompanySearchResult> input) {
    List<CompanySearchResult> out = new ArrayList<>();
    for (CompanySearchResult r : input) {
      boolean isPnp = "Partnership".equalsIgnoreCase(r.company_type())
          || "Proprietorship".equalsIgnoreCase(r.company_type());

      // Enforce PAN-only flow for Partnership/Proprietorship entities.
      if (isPnp && "PAN".equalsIgnoreCase(r.identifier_type())) {
        out.add(r);
        continue;
      }

      if ("BID".equalsIgnoreCase(r.identifier_type())) {
        try {
          String pan = probe42Client.resolvePanFromBid(r.cin());
          if (pan != null && !pan.isBlank()) {
            out.add(new CompanySearchResult(
                r.name(),
                pan,
                "PAN",
                r.address(),
                r.state(),
                r.status(),
                r.incorporation_date(),
                r.company_type(),
                r.match_score()
            ));
            continue;
          }
        } catch (Exception ignored) {
        }
      }

      // Do not keep non-PAN P&P identifiers.
      if (isPnp) continue;

      out.add(r);
    }
    return out;
  }

  private String normalizeIdentifier(String identifier) {
    if (identifier == null) return "";
    String id = identifier.trim();
    if (id.isBlank()) return id;
    // For partnership/proprietorship flows, use PAN as canonical input.
    if (id.length() > 12) {
      try {
        String pan = probe42Client.resolvePanFromBid(id);
        if (pan != null && !pan.isBlank()) return pan;
      } catch (Exception ignored) {
      }
    }
    return id;
  }

  private Map<String, Object> decodeAndValidateLaunchToken(String token) {
    try {
      String[] parts = token.split("\\.");
      if (parts.length != 3) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired launch token");
      }

      Map<String, Object> header = objectMapper.readValue(
          Base64.getUrlDecoder().decode(parts[0]),
          new TypeReference<Map<String, Object>>() {}
      );
      String alg = asString(header.get("alg"));
      if (!"HS256".equalsIgnoreCase(alg) || !"HS256".equalsIgnoreCase(masterHubJwtAlgo)) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired launch token");
      }

      String signingInput = parts[0] + "." + parts[1];
      String expectedSig = signHs256(signingInput, masterHubJwtSecret);
      if (!constantTimeEquals(parts[2], expectedSig)) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired launch token");
      }

      Map<String, Object> claims = objectMapper.readValue(
          Base64.getUrlDecoder().decode(parts[1]),
          new TypeReference<Map<String, Object>>() {}
      );
      Number exp = asNumber(claims.get("exp"));
      long nowEpochSeconds = System.currentTimeMillis() / 1000L;
      if (exp == null || exp.longValue() <= nowEpochSeconds) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired launch token");
      }
      return claims;
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired launch token");
    }
  }

  private String signHs256(String input, String secret) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] signed = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
    return Base64.getUrlEncoder().withoutPadding().encodeToString(signed);
  }

  private boolean constantTimeEquals(String left, String right) {
    if (left == null || right == null) return false;
    byte[] a = left.getBytes(StandardCharsets.UTF_8);
    byte[] b = right.getBytes(StandardCharsets.UTF_8);
    if (a.length != b.length) return false;
    int result = 0;
    for (int i = 0; i < a.length; i++) {
      result |= a[i] ^ b[i];
    }
    return result == 0;
  }

  private Number asNumber(Object value) {
    if (value instanceof Number n) return n;
    if (value instanceof String s) {
      try {
        return Long.parseLong(s);
      } catch (NumberFormatException ignored) {
      }
    }
    return null;
  }

  private String asString(Object value) {
    if (value == null) return null;
    String s = String.valueOf(value).trim();
    return s.isBlank() ? null : s;
  }

  private List<String> asStringList(Object value) {
    if (!(value instanceof List<?> raw)) return List.of();
    List<String> out = new ArrayList<>();
    for (Object item : raw) {
      String v = asString(item);
      if (v != null) out.add(v);
    }
    return out;
  }

  private Set<String> parseAllowedTenants(String csv) {
    if (csv == null || csv.isBlank()) return Set.of();
    Set<String> tenants = new HashSet<>();
    for (String raw : csv.split(",")) {
      String trimmed = raw.trim();
      if (!trimmed.isEmpty()) tenants.add(trimmed);
    }
    return tenants;
  }
}
