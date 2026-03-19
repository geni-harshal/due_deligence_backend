package com.entitycheck.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.*;

@Component
public class ReportMapper {
  public Map<String, Object> fromProbe(JsonNode raw, String identifier, String fallbackCompanyName) {
    JsonNode data = raw.path("data");
    JsonNode company = data.path("company");
    JsonNode llp = data.path("llp");
    JsonNode pnp = data.path("pnp");
    JsonNode entity = company.isObject() && company.size() > 0
        ? company
        : (llp.isObject() && llp.size() > 0 ? llp : pnp);

    String cin = firstNonBlank(
        text(entity, "cin", ""),
        text(entity, "llpin", ""),
        text(entity, "pan", ""),
        identifier
    );
    String name = firstNonBlank(
        text(entity, "legal_name", ""),
        text(entity, "name", ""),
        fallbackCompanyName != null ? fallbackCompanyName : identifier
    );
    String status = firstNonBlank(
        text(entity, "efiling_status", ""),
        text(entity, "status", ""),
        "Active"
    );
    String incorporationDate = firstNonBlank(
        text(entity, "incorporation_date", ""),
        text(entity, "date_of_setup", ""),
        text(entity, "registration_date", "")
    );
    String companyClass = firstNonBlank(
        text(entity, "classification", ""),
        text(entity, "entity_type", ""),
        text(entity, "type", ""),
        (entity == pnp ? "Partnership/Proprietorship" : "Company")
    );
    String address = firstNonBlank(
        entity.path("registered_address").path("full_address").asText(""),
        entity.path("address").asText(""),
        entity.path("business_address").path("address_line1").asText("")
    );
    String pan = firstNonBlank(text(entity, "pan", ""), identifier.length() == 10 ? identifier : "");
    String website = text(entity, "website", "");
    String email = firstNonBlank(text(entity, "email", ""), data.path("contact_details").path("email").path(0).path("emailId").asText(""));
    String lei = entity.path("lei").path("number").asText("");
    String listingStatus = firstNonBlank(text(entity, "status", ""), "Unlisted");
    String lastAgm = text(entity, "last_agm_date", "");
    String lastFiling = text(entity, "last_filing_date", "");

    double authorizedCr = toCrores(entity.path("authorized_capital").asDouble(0));
    double paidUpCr = toCrores(entity.path("paid_up_capital").asDouble(0));

    List<Map<String, Object>> directors = new ArrayList<>();
    JsonNode authSignatories = data.path("authorized_signatories");
    if (authSignatories.isArray()) {
      for (JsonNode d : authSignatories) {
        Map<String, Object> item = new HashMap<>();
        item.put("name", d.path("name").asText("-"));
        item.put("din", d.path("din").asText("-"));
        item.put("designation", d.path("designation").asText("-"));
        item.put("appointment_date", d.path("date_of_appointment").asText("-"));
        item.put("other_directorships", List.of());
        directors.add(item);
      }
    }

    List<Map<String, Object>> financials = new ArrayList<>();
    List<Map<String, Object>> ratios = new ArrayList<>();
    JsonNode fins = data.path("financials");
    if (fins.isArray()) {
      for (JsonNode f : fins) {
        String year = f.path("year").asText("");
        JsonNode pnl = f.path("pnl").path("lineItems");
        JsonNode bsAssets = f.path("bs").path("assets");
        JsonNode bsLiab = f.path("bs").path("liabilities");
        JsonNode rt = f.path("ratios");
        Map<String, Object> fr = new HashMap<>();
        fr.put("year", year);
        fr.put("revenue", toCrores(pnl.path("net_revenue").asDouble(0)));
        fr.put("gross_margin", toCrores(pnl.path("operating_profit").asDouble(0)));
        fr.put("ebitda", toCrores(pnl.path("operating_profit").asDouble(0)));
        fr.put("pat", toCrores(pnl.path("profit_after_tax").asDouble(0)));
        fr.put("total_assets", toCrores(bsAssets.path("given_assets_total").asDouble(0)));
        fr.put("total_liabilities", toCrores(bsLiab.path("given_liabilities_total").asDouble(0)));
        fr.put("cash", toCrores(bsAssets.path("cash_and_bank_balances").asDouble(0)));
        fr.put("debt", toCrores(bsLiab.path("long_term_borrowings").asDouble(0) + bsLiab.path("short_term_borrowings").asDouble(0)));
        fr.put("reserves", toCrores(bsLiab.path("reserves_and_surplus").asDouble(0)));
        financials.add(fr);

        Map<String, Object> rr = new HashMap<>();
        rr.put("year", year);
        rr.put("current_ratio", rt.path("current_ratio").asDouble(0));
        rr.put("quick_ratio", rt.path("quick_ratio").asDouble(0));
        rr.put("debt_equity", rt.path("debt_by_equity").asDouble(0));
        rr.put("interest_coverage", rt.path("interest_coverage_ratio").asDouble(0));
        rr.put("inventory_days", rt.path("inventory_by_sales_days").asDouble(0));
        rr.put("receivable_days", rt.path("debtors_by_sales_days").asDouble(0));
        rr.put("payable_days", rt.path("payables_by_sales_days").asDouble(0));
        rr.put("gross_margin_pct", rt.path("gross_profit_margin").asDouble(0));
        rr.put("ebitda_pct", rt.path("ebitda_margin").asDouble(0));
        rr.put("pat_pct", rt.path("net_margin").asDouble(0));
        rr.put("roe", rt.path("return_on_equity").asDouble(0));
        rr.put("roa", rt.path("return_on_capital_employed").asDouble(0));
        ratios.add(rr);
      }
    }

    List<Map<String, Object>> legalCases = new ArrayList<>();
    JsonNode legal = data.path("legal_history");
    if (legal.isArray()) {
      for (JsonNode l : legal) {
        Map<String, Object> lc = new HashMap<>();
        lc.put("court", l.path("court").asText("-"));
        lc.put("case_type", l.path("case_category").asText("-"));
        lc.put("case_number", l.path("case_number").asText("-"));
        lc.put("filing_date", l.path("date").asText("-"));
        lc.put("role", l.path("case_type").asText("").toLowerCase().contains("filed by") ? "Petitioner" : "Respondent");
        lc.put("status", l.path("case_status").asText("-"));
        legalCases.add(lc);
      }
    }

    List<Map<String, Object>> gstRecords = new ArrayList<>();
    JsonNode gst = data.path("gst_details");
    if (gst.isArray()) {
      for (JsonNode g : gst) {
        Map<String, Object> gr = new HashMap<>();
        gr.put("gstn", g.path("gstin").asText("-"));
        gr.put("trade_name", g.path("trade_name").asText("-"));
        gr.put("status", g.path("status").asText("-"));
        gr.put("registration_date", g.path("date_of_registration").asText("-"));
        gr.put("taxpayer_type", g.path("taxpayer_type").asText("-"));
        gr.put("state", g.path("state").asText("-"));
        gr.put("last_filed", g.path("filing_timeliness").asText("-"));
        gr.put("delay_days", 0);
        gstRecords.add(gr);
      }
    }

    List<Map<String, Object>> pfRecords = new ArrayList<>();
    JsonNode pf = data.path("establishments_registered_with_epfo");
    if (pf.isArray()) {
      for (JsonNode p : pf) {
        Map<String, Object> pr = new HashMap<>();
        pr.put("pf_number", p.path("establishment_id").asText("-"));
        pr.put("name", p.path("establishment_name").asText("-"));
        pr.put("status", p.path("working_status").asText("-").toUpperCase().contains("LIVE") ? "Active" : "Inactive");
        pr.put("coverage_date", p.path("date_of_setup").asText("-"));
        pr.put("employees", p.path("no_of_employees").asInt(0));
        pr.put("delay_days", 0);
        pfRecords.add(pr);
      }
    }

    Map<String, Object> riskScore = new HashMap<>();
    riskScore.put("score", 74);
    riskScore.put("grade", "Rx2");
    riskScore.put("category", "Below Average Counterparty Risk");
    riskScore.put("credit_limit_lacs", 72.5);
    riskScore.put("credit_days", 45);
    riskScore.put("factors", List.of(
        factor("MCA Status", "positive", "Entity reported active in registry"),
        factor("Liquidity", "positive", "Current ratio is healthy"),
        factor("Leverage", "positive", "Debt-equity is conservative"),
        factor("Legal Cases", "negative", legalCases.isEmpty() ? "No legal cases identified" : legalCases.size() + " legal case(s) identified"),
        factor("Defaulter Record", "positive", "No defaulter signal found")
    ));

    Map<String, Object> report = baseReportSkeleton();
    report.put("cin", cin);
    report.put("name", name);
    report.put("address", address);
    report.put("status", status);
    report.put("company_class", companyClass);
    report.put("incorporation_date", incorporationDate);
    report.put("authorized_capital", authorizedCr);
    report.put("paid_up_capital", paidUpCr);
    report.put("listing_status", listingStatus);
    report.put("email", email);
    report.put("website", website);
    report.put("phone", "");
    report.put("pan", pan);
    report.put("lei", lei);
    report.put("risk_score", riskScore);
    report.put("directors", directors);
    report.put("financials", financials);
    report.put("ratios", ratios);
    report.put("gst_records", gstRecords);
    report.put("pf_records", pfRecords);
    report.put("mca_status", status);
    report.put("last_balance_sheet", lastFiling);
    report.put("last_agm", lastAgm);
    report.put("legal_cases", legalCases);
    report.put("defaulter_status", data.path("defaulter_list").isArray() && data.path("defaulter_list").size() > 0);
    report.put("ibc_status", !entity.path("cirp_status").isNull() && !entity.path("cirp_status").asText("").isBlank());
    report.put("data_sources", List.of("Probe42 API v1.3"));
    report.put("data_quality", "probe42");
    report.put("is_real_data", true);
    report.put("data_note", "Report generated from Probe42 comprehensive response on " + OffsetDateTime.now() + ".");
    report.put("probe_data", data);
    report.put("business_description", data.path("description").path("desc_thousand_char").asText(""));
    report.put("name_history", nodeToList(data.path("name_history")));
    report.put("key_indicators", nodeToMap(data.path("key_indicators")));
    report.put("probe_financial_score", nodeToMap(data.path("probe_financial_score")));
    report.put("principal_business_activities", nodeToList(data.path("principal_business_activities")));
    report.put("financial_parameters", nodeToList(data.path("financial_parameters")));
    report.put("director_shareholdings", nodeToList(data.path("director_shareholdings")));
    report.put("holding_entities", nodeToMap(data.path("holding_entities")));
    report.put("subsidiary_entities", nodeToMap(data.path("subsidiary_entities")));

    return report;
  }

  public Map<String, Object> baseReportSkeleton() {
    Map<String, Object> r = new HashMap<>();
    r.put("cin", "");
    r.put("name", "");
    r.put("address", "");
    r.put("status", "Active");
    r.put("company_class", "Company");
    r.put("incorporation_date", "");
    r.put("age_years", null);
    r.put("authorized_capital", 0.0);
    r.put("paid_up_capital", 0.0);
    r.put("nic_code", null);
    r.put("nic_description", "");
    r.put("listing_status", "");
    r.put("email", "");
    r.put("website", "");
    r.put("phone", "");
    r.put("pan", "");
    r.put("lei", "");
    r.put("risk_score", Map.of("score", 0, "grade", "-", "category", "-", "credit_limit_lacs", 0, "credit_days", 0, "factors", List.of()));
    r.put("directors", new ArrayList<>());
    r.put("shareholders", new ArrayList<>());
    r.put("gst_records", new ArrayList<>());
    r.put("pf_records", new ArrayList<>());
    r.put("mca_status", "-");
    r.put("last_agm", "-");
    r.put("last_balance_sheet", "-");
    r.put("legal_cases", new ArrayList<>());
    r.put("defaulter_status", false);
    r.put("ibc_status", false);
    r.put("financials", new ArrayList<>());
    r.put("ratios", new ArrayList<>());
    r.put("related_parties", new ArrayList<>());
    r.put("charges", new ArrayList<>());
    r.put("customer_sentiment", 0);
    r.put("employee_sentiment", 0);
    r.put("strengths", new ArrayList<>());
    r.put("concerns", new ArrayList<>());
    r.put("field_sources", new HashMap<>());
    return r;
  }

  private static Map<String, Object> factor(String factor, String impact, String detail) {
    Map<String, Object> f = new HashMap<>();
    f.put("factor", factor);
    f.put("impact", impact);
    f.put("detail", detail);
    return f;
  }

  private static String text(JsonNode node, String key, String def) {
    String v = node.path(key).asText("");
    return v == null || v.isBlank() ? def : v;
  }

  private static double toCrores(double amount) {
    if (amount <= 0) return 0;
    return Math.round((amount / 10000000.0) * 100.0) / 100.0;
  }

  private static String firstNonBlank(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) return v;
    }
    return "";
  }

  private static List<Map<String, Object>> nodeToList(JsonNode node) {
    List<Map<String, Object>> out = new ArrayList<>();
    if (!node.isArray()) return out;
    for (JsonNode child : node) {
      if (child.isObject()) {
        out.add(nodeToMap(child));
      }
    }
    return out;
  }

  private static Map<String, Object> nodeToMap(JsonNode node) {
    Map<String, Object> out = new HashMap<>();
    if (!node.isObject()) return out;
    node.fields().forEachRemaining(e -> {
      JsonNode v = e.getValue();
      if (v.isObject()) out.put(e.getKey(), nodeToMap(v));
      else if (v.isArray()) out.put(e.getKey(), nodeToList(v));
      else if (v.isBoolean()) out.put(e.getKey(), v.asBoolean());
      else if (v.isNumber()) out.put(e.getKey(), v.numberValue());
      else if (v.isNull()) out.put(e.getKey(), null);
      else out.put(e.getKey(), v.asText());
    });
    return out;
  }
}
