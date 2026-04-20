package com.entitycheck.probe;

import com.entitycheck.api.dto.CompanySearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class Probe42Client {
  private static final Pattern SPACE_SPLIT = Pattern.compile("\\s+");
  private static final Pattern NON_ALNUM = Pattern.compile("[^A-Z0-9 ]");
  private static final Pattern PAN_PATTERN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");
  private static final Pattern LLPIN_PATTERN = Pattern.compile("^[A-Z]{3}-[0-9]{4}$");
  private static final Pattern CIN_PATTERN = Pattern.compile("^[A-Z0-9]{21}$");
  private static final Set<String> LEGAL_STOPWORDS = new HashSet<>(Arrays.asList(
      "PRIVATE", "LIMITED", "PVT", "LTD", "LLP", "OPC", "CO", "THE"
  ));
  private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
  private final ObjectMapper mapper = new ObjectMapper();
  private static final Logger log = LoggerFactory.getLogger(Probe42Client.class);

  @Value("${probe42.base-url:https://api.probe42.in}")
  private String baseUrl;
  @Value("${probe42.api-key:}")
  private String apiKey;
  @Value("${probe42.api-version:1.3}")
  private String apiVersion;

  public List<CompanySearchResult> searchByNamePrefix(String name, int limit) throws IOException, InterruptedException {
    String trimmed = name == null ? "" : name.trim();
    if (trimmed.isBlank()) return List.of();

    int fetchLimit = Math.max(limit, 200);
    LinkedHashSet<String> searchKeys = new LinkedHashSet<>();
    searchKeys.add(trimmed);
    searchKeys.add("THE " + trimmed);
    searchKeys.add("M/S " + trimmed);

    String[] tokens = SPACE_SPLIT.split(trimmed);
    if (tokens.length > 0) searchKeys.add(tokens[0]);
    if (tokens.length > 1) {
      searchKeys.add(tokens[0] + " " + tokens[1]);
      searchKeys.add(tokens[1]);
    }
    for (String token : tokens) {
      if (token.length() >= 3) searchKeys.add(token);
    }

    Map<String, CompanySearchResult> merged = new LinkedHashMap<>();
    for (String key : searchKeys.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank()).toList()) {
      List<CompanySearchResult> found = searchInternal(key, fetchLimit);
      for (CompanySearchResult r : found) {
        merged.putIfAbsent((r.identifier_type() + ":" + r.cin()).toUpperCase(), r);
      }
      if (merged.size() >= fetchLimit) break;
    }

    if (merged.isEmpty()) return List.of();
    List<String> queryTokens = tokenizeForMatch(trimmed);
    String normalizedQuery = normalizeForMatch(trimmed);
    Map<String, Double> scores = new HashMap<>();
    for (CompanySearchResult r : merged.values()) {
      scores.put(keyOf(r), score(r.name(), normalizedQuery, queryTokens));
    }
    List<CompanySearchResult> sorted = merged.values().stream()
        .sorted(Comparator.comparingDouble((CompanySearchResult r) -> scores.getOrDefault(keyOf(r), -1000.0)).reversed())
        .collect(Collectors.toList());

    double max = sorted.stream().mapToDouble(r -> scores.getOrDefault(keyOf(r), 0.0)).max().orElse(0.0);
    double min = sorted.stream().mapToDouble(r -> scores.getOrDefault(keyOf(r), 0.0)).min().orElse(0.0);

    return sorted.stream()
        .map(r -> new CompanySearchResult(
            r.name(),
            r.cin(),
            r.identifier_type(),
            r.address(),
            r.state(),
            r.status(),
            r.incorporation_date(),
            r.company_type(),
            normalizeScore(scores.getOrDefault(keyOf(r), 0.0), max, min)
        ))
        .limit(limit)
        .collect(Collectors.toList());
  }

  private double score(String candidateName, String normalizedQuery, List<String> queryTokens) {
    if (candidateName == null || candidateName.isBlank()) return -1000;
    String normalizedCandidate = normalizeForMatch(candidateName);
    List<String> candidateTokens = tokenizeForMatch(candidateName);
    if (normalizedCandidate.isBlank()) return -1000;

    double score = 0;
    if (normalizedCandidate.equals(normalizedQuery)) score += 1000;
    if (!normalizedQuery.isBlank() && normalizedCandidate.startsWith(normalizedQuery)) score += 450;
    if (!normalizedQuery.isBlank() && normalizedCandidate.contains(normalizedQuery)) score += 280;

    Set<String> querySet = new HashSet<>(queryTokens);
    Set<String> candidateSet = new HashSet<>(candidateTokens);
    querySet.removeIf(String::isBlank);
    candidateSet.removeIf(String::isBlank);

    int common = 0;
    for (String t : querySet) {
      if (candidateSet.contains(t)) common++;
    }
    if (!querySet.isEmpty()) {
      score += 320.0 * ((double) common / querySet.size());
    }
    if (!candidateSet.isEmpty()) {
      score += 120.0 * ((double) common / candidateSet.size());
    }

    int ordered = orderedTokenHits(candidateTokens, queryTokens);
    score += ordered * 35.0;

    if (common == 0) score -= 220;
    score -= Math.min(120, Math.abs(normalizedCandidate.length() - normalizedQuery.length()) * 1.2);
    return score;
  }

  private int orderedTokenHits(List<String> candidateTokens, List<String> queryTokens) {
    if (candidateTokens.isEmpty() || queryTokens.isEmpty()) return 0;
    int i = 0;
    int hits = 0;
    for (String q : queryTokens) {
      while (i < candidateTokens.size() && !candidateTokens.get(i).equals(q)) i++;
      if (i < candidateTokens.size()) {
        hits++;
        i++;
      } else {
        break;
      }
    }
    return hits;
  }

  private String normalizeForMatch(String input) {
    if (input == null) return "";
    String upper = NON_ALNUM.matcher(input.toUpperCase()).replaceAll(" ");
    return SPACE_SPLIT.splitAsStream(upper)
        .filter(s -> !s.isBlank())
        .collect(Collectors.joining(" "))
        .trim();
  }

  private List<String> tokenizeForMatch(String input) {
    String normalized = normalizeForMatch(input);
    if (normalized.isBlank()) return List.of();
    return SPACE_SPLIT.splitAsStream(normalized)
        .filter(s -> !s.isBlank())
        .filter(s -> !LEGAL_STOPWORDS.contains(s))
        .collect(Collectors.toList());
  }

  private String keyOf(CompanySearchResult r) {
    return (r.identifier_type() + ":" + r.cin()).toUpperCase();
  }

  private double normalizeScore(double value, double max, double min) {
    if (max <= min) return 100.0;
    double normalized = ((value - min) / (max - min)) * 100.0;
    if (normalized < 0) return 0.0;
    if (normalized > 100) return 100.0;
    return normalized;
  }

  private List<CompanySearchResult> searchInternal(String name, int limit) throws IOException, InterruptedException {
    if (name == null || name.trim().isBlank()) return List.of();
    String filters = mapper.writeValueAsString(
    mapper.createObjectNode()
        .put("nameStartsWith", name.trim())
    );
    String encodedFilters = URLEncoder.encode(filters, StandardCharsets.UTF_8);
    String url = "%s/probe_pro_sandbox/entities?limit=%d&filters=%s".formatted(baseUrl, limit, encodedFilters);

     log.info("Probe42 URL: {}", url);

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(30))
        .header("x-api-key", apiKey)
        .header("Accept", "application/json")
        .header("x-api-version", apiVersion)
        .GET()
        .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      log.debug("Probe42 response status: {}", response.statusCode());

    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      return List.of();
    }
    JsonNode root = mapper.readTree(response.body());
    JsonNode entities = root.path("data").path("entities");

    List<CompanySearchResult> out = new ArrayList<>();
    appendResults(out, entities.path("companies"), "Company", "CIN");
    appendResults(out, entities.path("llps"), "LLP", "LLPIN");
    appendResults(out, entities.path("partnerships"), "Partnership", "PAN");
    appendResults(out, entities.path("proprietorships"), "Proprietorship", "PAN");
    return out.size() > limit ? out.subList(0, limit) : out;
  }

public JsonNode getComprehensiveByIdentifier(String identifier) throws IOException, InterruptedException {
    String id = identifier == null ? "" : identifier.trim();
    if (id.isBlank()) return null;

    String upper = id.toUpperCase();
    List<String> urls = new ArrayList<>();
    
    if (PAN_PATTERN.matcher(upper).matches()) {
        urls.add("%s/probe_pro_sandbox/pnps/%s/comprehensive-details?identifier_type=PAN".formatted(baseUrl, upper));
    } else if (LLPIN_PATTERN.matcher(upper).matches()) {
        urls.add("%s/probe_pro_sandbox/llps/%s/comprehensive-details".formatted(baseUrl, upper));
    } else if (CIN_PATTERN.matcher(upper).matches()) {
        urls.add("%s/probe_pro_sandbox/companies/%s/comprehensive-details".formatted(baseUrl, upper));
    } else {
        urls.add("%s/probe_pro_sandbox/pnps/%s/comprehensive-details?identifier_type=BID".formatted(baseUrl, id));
        urls.add("%s/probe_pro_sandbox/pnps/%s/comprehensive-details".formatted(baseUrl, id));
        urls.add("%s/probe_pro_sandbox/companies/%s/comprehensive-details".formatted(baseUrl, id));
        urls.add("%s/probe_pro_sandbox/llps/%s/comprehensive-details".formatted(baseUrl, id));
        urls.add("%s/probe_pro_sandbox/pnps/%s/comprehensive-details?identifier_type=PAN".formatted(baseUrl, id));
    }
    
    for (String url : urls) {
        log.info("Probe42 API call: {}", url);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(45))
            .header("x-api-key", apiKey)
            .header("Accept", "application/json")
            .header("x-api-version", apiVersion)
            .GET()
            .build();
            
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        log.debug("Probe42 response status: {} for URL: {}", response.statusCode(), url);
        
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return mapper.readTree(response.body());
        } else if (response.statusCode() == 403) {
            log.error("Probe42 API returned 403 Forbidden. Check API key validity.");
            throw new RuntimeException("Probe42 API authentication failed (403). Please check your API key.");
        } else if (response.statusCode() == 401) {
            log.error("Probe42 API returned 401 Unauthorized.");
            throw new RuntimeException("Probe42 API unauthorized (401). Please check your API key.");
        } else {
            log.warn("Probe42 API returned {} for URL: {}", response.statusCode(), url);
        }
    }
    return null;
}

  public String resolvePanFromBid(String bid) throws IOException, InterruptedException {
    if (bid == null || bid.trim().isBlank()) return null;
    String id = bid.trim();
    List<String> urls = List.of(
        "%s/probe_pro_sandbox/pnps/%s/comprehensive-details?identifier_type=BID".formatted(baseUrl, id),
        "%s/probe_pro_sandbox/pnps/%s/comprehensive-details".formatted(baseUrl, id)
    );
    for (String url : urls) {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .timeout(Duration.ofSeconds(45))
          .header("x-api-key", apiKey)
          .header("Accept", "application/json")
          .header("x-api-version", apiVersion)
          .GET()
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        JsonNode root = mapper.readTree(response.body());
        String pan = root.path("data").path("pnp").path("pan").asText("");
        if (!pan.isBlank()) return pan;
      }
    }
    return null;
  }

  private void appendResults(List<CompanySearchResult> out, JsonNode arr, String companyType, String identifierType) {
    if (!arr.isArray()) return;
    for (JsonNode node : arr) {
      String name = node.path("legal_name").asText("");
      String id;
      String idType = identifierType;
      switch (identifierType) {
        case "LLPIN" -> id = node.path("llpin").asText("");
        case "PAN" -> {
          String pan = node.path("pan").asText("");
          if (pan == null || pan.isBlank()) {
            id = node.path("bid").asText("");
            idType = "BID";
          } else {
            id = pan;
          }
        }
        default -> id = node.path("cin").asText("");
      }
      if (!name.isBlank() && !id.isBlank()) {
        out.add(new CompanySearchResult(
            name,
            id,
            idType,
            "",
            "",
            node.path("status").asText("ACTIVE"),
            "",
            companyType,
            null
        ));
      }
    }
  }
}
