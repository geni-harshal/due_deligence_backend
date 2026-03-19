package com.entitycheck.service;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Mock Decision Engine that produces credit ratings, risk scores,
 * eligibility recommendations, and key risk drivers based on company data.
 *
 * Frontend expects:
 * {
 *   creditRating: { rating: "B+", rationale: "..." },
 *   eligibility: { recommendedAmountMin: 5000000, recommendedAmountMax: 25000000, currency: "INR" },
 *   riskScore: { score: 42, band: "Medium" },
 *   keyRiskDrivers: ["reason1", "reason2", ...]
 * }
 */
@Service
public class DecisionEngineService {

    public Map<String, Object> execute(Map<String, Object> reportData) {
        Map<String, Object> result = new LinkedHashMap<>();

        // Extract key inputs from report
        Map<String, Object> financials = asMap(reportData.get("financialSummary"));
        Map<String, Object> legal = asMap(reportData.get("legalCompliance"));
        List<Map<String, Object>> charges = asList(reportData.get("charges"));
        List<Map<String, Object>> directors = asList(reportData.get("directors"));

        // Compute a base score (0-100, lower = less risky)
        int baseScore = 30;
        List<String> riskDrivers = new ArrayList<>();

        // ── Financial analysis ──
        double deRatio = parseDouble(String.valueOf(financials.getOrDefault("debtEquityRatio", "0")));
        if (deRatio > 2.0) {
            baseScore += 15;
            riskDrivers.add("Elevated debt-to-equity ratio (" + String.format("%.2f", deRatio) + "x)");
        } else if (deRatio > 1.0) {
            baseScore += 8;
            riskDrivers.add("Moderate leverage (D/E: " + String.format("%.2f", deRatio) + "x)");
        }

        double currentRatio = parseDouble(String.valueOf(financials.getOrDefault("currentRatio", "1.5")));
        if (currentRatio < 1.0) {
            baseScore += 12;
            riskDrivers.add("Current ratio below 1.0 indicates liquidity stress");
        }

        String profitMargin = String.valueOf(financials.getOrDefault("profitMargin", "0"));
        double margin = parseDouble(profitMargin.replace("%", ""));
        if (margin < 0) {
            baseScore += 18;
            riskDrivers.add("Negative profit margin - company is loss-making");
        } else if (margin < 5) {
            baseScore += 8;
            riskDrivers.add("Thin profit margin (" + String.format("%.1f", margin) + "%)");
        }

        // ── Legal / Compliance analysis ──
        int pendingLitigation = (int) legal.getOrDefault("pendingLitigation", 0);
        if (pendingLitigation > 5) {
            baseScore += 15;
            riskDrivers.add("High number of pending litigation cases (" + pendingLitigation + ")");
        } else if (pendingLitigation > 0) {
            baseScore += 5;
            riskDrivers.add(pendingLitigation + " pending litigation case(s)");
        }

        boolean wilfulDefaulter = Boolean.TRUE.equals(legal.get("wilfulDefaulter"));
        if (wilfulDefaulter) {
            baseScore += 30;
            riskDrivers.add("CRITICAL: Company flagged as wilful defaulter");
        }

        List<String> adverse = asList(legal.get("adverseFindings")).stream()
                .map(m -> m instanceof String ? (String) m : String.valueOf(m))
                .filter(s -> !s.isBlank())
                .toList();
        if (!adverse.isEmpty()) {
            baseScore += adverse.size() * 5;
            riskDrivers.add(adverse.size() + " adverse finding(s) on record");
        }

        // ── Charges analysis ──
        long openCharges = charges.stream()
                .filter(c -> "Open".equalsIgnoreCase(String.valueOf(c.get("status"))))
                .count();
        if (openCharges > 3) {
            baseScore += 10;
            riskDrivers.add(openCharges + " open charges on assets");
        } else if (openCharges > 0) {
            baseScore += 4;
        }

        // ── Management analysis ──
        long activeDirectors = directors.stream()
                .filter(d -> Boolean.TRUE.equals(d.get("isActive")))
                .count();
        if (activeDirectors < 2) {
            baseScore += 8;
            riskDrivers.add("Limited active management (only " + activeDirectors + " active director(s))");
        }

        // Cap the score
        int riskScore = Math.min(100, Math.max(0, baseScore));

        // ── Determine risk band ──
        String riskBand;
        if (riskScore <= 20) riskBand = "Low";
        else if (riskScore <= 35) riskBand = "Low-Medium";
        else if (riskScore <= 50) riskBand = "Medium";
        else if (riskScore <= 65) riskBand = "Medium-High";
        else if (riskScore <= 80) riskBand = "High";
        else riskBand = "Very High";

        // ── Determine credit rating ──
        String rating;
        String rationale;
        if (riskScore <= 15) {
            rating = "A+";
            rationale = "Excellent financial health, strong management, minimal legal exposure.";
        } else if (riskScore <= 25) {
            rating = "A";
            rationale = "Strong financial position with minor areas of attention.";
        } else if (riskScore <= 35) {
            rating = "A-";
            rationale = "Good overall profile with some moderate risk factors.";
        } else if (riskScore <= 45) {
            rating = "B+";
            rationale = "Adequate financial health but notable risk indicators present.";
        } else if (riskScore <= 55) {
            rating = "B";
            rationale = "Moderate risk profile. Multiple risk drivers identified requiring attention.";
        } else if (riskScore <= 70) {
            rating = "B-";
            rationale = "Below-average risk profile with significant concerns in financial or legal areas.";
        } else if (riskScore <= 85) {
            rating = "C";
            rationale = "High-risk profile. Significant financial distress or legal exposure detected.";
        } else {
            rating = "D";
            rationale = "Critical risk. Major red flags across multiple dimensions.";
        }

        // ── Determine eligibility ──
        long baseAmount = 50_000_000L; // 5 Cr
        double multiplier = Math.max(0.1, (100 - riskScore) / 100.0);
        long recommendedMax = (long) (baseAmount * multiplier);
        long recommendedMin = (long) (recommendedMax * 0.3);
        // Round to nearest lakh
        recommendedMax = (recommendedMax / 100_000) * 100_000;
        recommendedMin = (recommendedMin / 100_000) * 100_000;

        if (riskDrivers.isEmpty()) {
            riskDrivers.add("No significant risk drivers identified");
        }

        // ── Build output ──
        result.put("creditRating", Map.of("rating", rating, "rationale", rationale));
        result.put("eligibility", Map.of(
                "recommendedAmountMin", recommendedMin,
                "recommendedAmountMax", recommendedMax,
                "currency", "INR"
        ));
        result.put("riskScore", Map.of("score", riskScore, "band", riskBand));
        result.put("keyRiskDrivers", riskDrivers);

        return result;
    }

    // ── Helpers ──

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object obj) {
        if (obj instanceof Map) return (Map<String, Object>) obj;
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> asList(Object obj) {
        if (obj instanceof List) return (List<T>) obj;
        return List.of();
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) return 0.0;
        try {
            return Double.parseDouble(value.replaceAll("[^0-9.\\-]", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
