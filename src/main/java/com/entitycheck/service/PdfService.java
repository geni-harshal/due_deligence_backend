package com.entitycheck.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PdfService {
  private static final int MAX_ARRAY_ROWS = 20;
  private static final int MAX_PRIMITIVE_LIST = 50;

  public byte[] generate(Map<String, Object> report) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      Document document = new Document(PageSize.A4, 32, 32, 28, 28);
      PdfWriter.getInstance(document, out);
      document.open();

      Font title = new Font(Font.HELVETICA, 18, Font.BOLD, new java.awt.Color(15, 23, 42));
      Font subtitle = new Font(Font.HELVETICA, 10, Font.NORMAL, new java.awt.Color(71, 85, 105));
      Font section = new Font(Font.HELVETICA, 11, Font.BOLD, java.awt.Color.WHITE);
      Font subhead = new Font(Font.HELVETICA, 10, Font.BOLD, new java.awt.Color(30, 41, 59));
      Font body = new Font(Font.HELVETICA, 9, Font.NORMAL, new java.awt.Color(30, 41, 59));
      Font muted = new Font(Font.HELVETICA, 8, Font.NORMAL, new java.awt.Color(100, 116, 139));

      addReportHeader(document, report, title, subtitle, body, muted);
      addSpacing(document, 8f);

      Object riskObj = report.get("risk_score");
      if (riskObj instanceof Map<?, ?> risk) {
        addSectionBar(document, "Risk Summary", section);
        PdfPTable riskTable = new PdfPTable(new float[]{1f, 1f, 2f});
        riskTable.setWidthPercentage(100);
        addCell(riskTable, "Score", subhead, new java.awt.Color(241, 245, 249), Element.ALIGN_LEFT);
        addCell(riskTable, "Grade", subhead, new java.awt.Color(241, 245, 249), Element.ALIGN_LEFT);
        addCell(riskTable, "Category", subhead, new java.awt.Color(241, 245, 249), Element.ALIGN_LEFT);
        addCell(riskTable, val(risk.get("score")), body, java.awt.Color.WHITE, Element.ALIGN_LEFT);
        addCell(riskTable, val(risk.get("grade")), body, java.awt.Color.WHITE, Element.ALIGN_LEFT);
        addCell(riskTable, val(risk.get("category")), body, java.awt.Color.WHITE, Element.ALIGN_LEFT);
        document.add(riskTable);
        addSpacing(document, 6f);
      }

      Object probeObj = report.get("probe_data");
      if (!(probeObj instanceof Map<?, ?> rawProbe)) {
        addSectionBar(document, "Additional Information", section);
        document.add(new Paragraph("No additional information available.", body));
        document.close();
        return out.toByteArray();
      }

      Map<String, Object> probe = castMap(rawProbe);

      addSection(document, "1. Overview", probe, List.of(
          "company", "llp", "pnp", "description", "name_history", "key_indicators", "probe_financial_score",
          "industry_segments", "principal_business_activities"
      ), section, subhead, body, muted);

      addSection(document, "2. Management", probe, List.of(
          "authorized_signatories", "director_network", "director_shareholdings", "holding_entities",
          "subsidiary_entities", "associate_entities", "joint_ventures", "owners"
      ), section, subhead, body, muted);

      addSection(document, "3. Financials", probe, List.of(
          "financials", "nbfc_financials", "financial_parameters", "related_party_transactions",
          "shareholdings", "shareholdings_summary", "shareholdings_more_than_five_percent",
          "peer_comparison", "credit_ratings", "credit_rating_rationale", "revenue"
      ), section, subhead, body, muted);

      addSection(document, "4. Compliance", probe, List.of(
          "gst_details", "establishments_registered_with_epfo", "filing_dates",
          "struckoff248_details", "msme_supplier_payment_delays", "contact_details"
      ), section, subhead, body, muted);

      addSection(document, "5. Legal", probe, List.of(
          "legal_history", "legal_cases_of_financial_disputes", "defaulter_list", "open_charges",
          "open_charges_latest_event", "charge_sequence", "bifr_history", "cdr_history", "unaccepted_rating"
      ), section, subhead, body, muted);

      Set<String> covered = new LinkedHashSet<>(List.of(
          "company", "llp", "pnp", "description", "name_history", "key_indicators", "probe_financial_score",
          "industry_segments", "principal_business_activities", "authorized_signatories", "director_network",
          "director_shareholdings", "holding_entities", "subsidiary_entities", "associate_entities",
          "joint_ventures", "owners", "financials", "nbfc_financials", "financial_parameters",
          "related_party_transactions", "shareholdings", "shareholdings_summary",
          "shareholdings_more_than_five_percent", "peer_comparison", "credit_ratings",
          "credit_rating_rationale", "revenue", "gst_details", "establishments_registered_with_epfo",
          "filing_dates", "struckoff248_details", "msme_supplier_payment_delays", "contact_details",
          "legal_history", "legal_cases_of_financial_disputes", "defaulter_list", "open_charges",
          "open_charges_latest_event", "charge_sequence", "bifr_history", "cdr_history", "unaccepted_rating"
      ));
      Map<String, Object> leftover = new LinkedHashMap<>();
      for (Map.Entry<String, Object> e : probe.entrySet()) {
        if (!covered.contains(e.getKey())) leftover.put(e.getKey(), e.getValue());
      }
      if (!leftover.isEmpty()) {
        addSection(document, "6. Additional Information", leftover, new ArrayList<>(leftover.keySet()), section, subhead, body, muted);
      }

      document.close();
      return out.toByteArray();
    } catch (DocumentException e) {
      throw new RuntimeException("PDF generation failed", e);
    }
  }

  private void addSection(
      Document document,
      String title,
      Map<String, Object> payload,
      List<String> keys,
      Font headingBar,
      Font subhead,
      Font body,
      Font muted
  ) throws DocumentException {
    addSectionBar(document, title, headingBar);
    boolean printed = false;
    for (String key : keys) {
      if (!payload.containsKey(key)) continue;
      Object value = payload.get(key);
      if (value == null) continue;
      Paragraph keyHeading = new Paragraph(toTitle(key), subhead);
      keyHeading.setSpacingBefore(4f);
      keyHeading.setSpacingAfter(2f);
      document.add(keyHeading);
      renderValue(document, value, body, muted);
      printed = true;
    }
    if (!printed) {
      document.add(new Paragraph("No records available in this section.", body));
    }
    addSpacing(document, 8f);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> castMap(Map<?, ?> input) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<?, ?> e : input.entrySet()) {
      out.put(String.valueOf(e.getKey()), e.getValue());
    }
    return out;
  }

  private void renderValue(Document document, Object value, Font body, Font muted) throws DocumentException {
    if (value == null) {
      document.add(new Paragraph("-", muted));
      return;
    }

    if (value instanceof Map<?, ?> rawMap) {
      Map<String, Object> map = castMap(rawMap);
      renderMap(document, map, body, muted);
      return;
    }

    if (value instanceof List<?> list) {
      renderList(document, list, body, muted);
      return;
    }

    document.add(new Paragraph(val(value), body));
  }

  private void renderMap(Document document, Map<String, Object> map, Font body, Font muted) throws DocumentException {
    List<Map.Entry<String, Object>> primitive = new ArrayList<>();
    List<Map.Entry<String, Object>> nested = new ArrayList<>();
    for (Map.Entry<String, Object> e : map.entrySet()) {
      if (isPrimitive(e.getValue())) primitive.add(e);
      else nested.add(e);
    }

    if (!primitive.isEmpty()) {
      PdfPTable kv = new PdfPTable(new float[]{1f, 2.2f});
      kv.setWidthPercentage(100);
      for (Map.Entry<String, Object> e : primitive) {
        addCell(kv, toTitle(e.getKey()), muted, new java.awt.Color(248, 250, 252), Element.ALIGN_LEFT);
        addCell(kv, val(e.getValue()), body, java.awt.Color.WHITE, Element.ALIGN_LEFT);
      }
      document.add(kv);
      addSpacing(document, 3f);
    }

    for (Map.Entry<String, Object> e : nested) {
      Paragraph label = new Paragraph("  " + toTitle(e.getKey()), muted);
      label.setSpacingBefore(1f);
      label.setSpacingAfter(1f);
      document.add(label);
      renderValue(document, e.getValue(), body, muted);
    }
  }

  private void renderList(Document document, List<?> list, Font body, Font muted) throws DocumentException {
    if (list.isEmpty()) {
      document.add(new Paragraph("No records.", muted));
      return;
    }

    boolean allPrimitive = list.stream().allMatch(this::isPrimitive);
    if (allPrimitive) {
      int lim = Math.min(list.size(), MAX_PRIMITIVE_LIST);
      for (int i = 0; i < lim; i++) {
        Paragraph line = new Paragraph("• " + val(list.get(i)), body);
        line.setIndentationLeft(8f);
        document.add(line);
      }
      if (list.size() > lim) {
        document.add(new Paragraph("... showing " + lim + " of " + list.size() + " items", muted));
      }
      return;
    }

    boolean allMaps = list.stream().allMatch(v -> v instanceof Map<?, ?>);
    if (allMaps) {
      List<Map<String, Object>> rows = new ArrayList<>();
      for (Object o : list) rows.add(castMap((Map<?, ?>) o));
      renderObjectRows(document, rows, body, muted);
      return;
    }

    for (int i = 0; i < Math.min(list.size(), MAX_ARRAY_ROWS); i++) {
      document.add(new Paragraph("Item " + (i + 1), muted));
      renderValue(document, list.get(i), body, muted);
    }
  }

  private void renderObjectRows(Document document, List<Map<String, Object>> rows, Font body, Font muted) throws DocumentException {
    if (rows.isEmpty()) return;
    List<String> columns = inferColumns(rows, 8);
    if (!columns.isEmpty() && columns.size() <= 6) {
      PdfPTable t = new PdfPTable(columns.size());
      t.setWidthPercentage(100);
      for (String col : columns) addCell(t, toTitle(col), muted, new java.awt.Color(241, 245, 249), Element.ALIGN_LEFT);
      int lim = Math.min(rows.size(), MAX_ARRAY_ROWS);
      for (int i = 0; i < lim; i++) {
        Map<String, Object> row = rows.get(i);
        for (String col : columns) {
          addCell(t, compactVal(row.get(col)), body, java.awt.Color.WHITE, Element.ALIGN_LEFT);
        }
      }
      document.add(t);
      if (rows.size() > lim) document.add(new Paragraph("... showing " + lim + " of " + rows.size() + " records", muted));
      return;
    }

    int lim = Math.min(rows.size(), 8);
    for (int i = 0; i < lim; i++) {
      Paragraph rowTitle = new Paragraph("Record " + (i + 1), muted);
      rowTitle.setSpacingBefore(2f);
      document.add(rowTitle);
      renderMap(document, rows.get(i), body, muted);
    }
    if (rows.size() > lim) document.add(new Paragraph("... showing " + lim + " of " + rows.size() + " records", muted));
  }

  private List<String> inferColumns(List<Map<String, Object>> rows, int cap) {
    LinkedHashSet<String> cols = new LinkedHashSet<>();
    int sample = Math.min(rows.size(), 15);
    for (int i = 0; i < sample; i++) {
      cols.addAll(rows.get(i).keySet());
      if (cols.size() >= cap) break;
    }
    return new ArrayList<>(cols).subList(0, Math.min(cols.size(), cap));
  }

  private void addReportHeader(Document document, Map<String, Object> report, Font title, Font subtitle, Font body, Font muted) throws DocumentException {
    Paragraph t = new Paragraph("Due Diligence Report", title);
    t.setSpacingAfter(3f);
    document.add(t);
    Paragraph st = new Paragraph("Generated: " + java.time.OffsetDateTime.now().toString(), subtitle);
    st.setSpacingAfter(8f);
    document.add(st);

    PdfPTable summary = new PdfPTable(new float[]{1f, 2f, 1f, 2f});
    summary.setWidthPercentage(100);
    addCell(summary, "Company", muted, new java.awt.Color(241, 245, 249), Element.ALIGN_LEFT);
    addCell(summary, val(report.get("name")), body, java.awt.Color.WHITE, Element.ALIGN_LEFT);
    addCell(summary, "Identifier", muted, new java.awt.Color(241, 245, 249), Element.ALIGN_LEFT);
    addCell(summary, val(report.get("cin")), body, java.awt.Color.WHITE, Element.ALIGN_LEFT);
    addCell(summary, "Status", muted, new java.awt.Color(241, 245, 249), Element.ALIGN_LEFT);
    addCell(summary, val(report.get("status")), body, java.awt.Color.WHITE, Element.ALIGN_LEFT);
    addCell(summary, "Class", muted, new java.awt.Color(241, 245, 249), Element.ALIGN_LEFT);
    addCell(summary, val(report.get("company_class")), body, java.awt.Color.WHITE, Element.ALIGN_LEFT);
    addCell(summary, "Incorporation Date", muted, new java.awt.Color(241, 245, 249), Element.ALIGN_LEFT);
    addCell(summary, val(report.get("incorporation_date")), body, java.awt.Color.WHITE, Element.ALIGN_LEFT);
    addCell(summary, "PAN", muted, new java.awt.Color(241, 245, 249), Element.ALIGN_LEFT);
    addCell(summary, val(report.get("pan")), body, java.awt.Color.WHITE, Element.ALIGN_LEFT);
    PdfPCell addressLabel = new PdfPCell(new Phrase("Address", muted));
    styleCell(addressLabel, new java.awt.Color(241, 245, 249), Element.ALIGN_LEFT);
    addressLabel.setColspan(1);
    summary.addCell(addressLabel);
    PdfPCell addressVal = new PdfPCell(new Phrase(val(report.get("address")), body));
    styleCell(addressVal, java.awt.Color.WHITE, Element.ALIGN_LEFT);
    addressVal.setColspan(3);
    summary.addCell(addressVal);
    document.add(summary);
  }

  private void addSectionBar(Document document, String text, Font sectionFont) throws DocumentException {
    PdfPTable bar = new PdfPTable(1);
    bar.setWidthPercentage(100);
    PdfPCell cell = new PdfPCell(new Phrase(text, sectionFont));
    cell.setBackgroundColor(new java.awt.Color(30, 64, 175));
    cell.setBorder(Rectangle.NO_BORDER);
    cell.setPaddingTop(6f);
    cell.setPaddingBottom(6f);
    cell.setPaddingLeft(8f);
    bar.addCell(cell);
    bar.setSpacingBefore(5f);
    bar.setSpacingAfter(4f);
    document.add(bar);
  }

  private void addCell(PdfPTable table, String text, Font font, java.awt.Color bg, int align) {
    PdfPCell c = new PdfPCell(new Phrase(text, font));
    styleCell(c, bg, align);
    table.addCell(c);
  }

  private void styleCell(PdfPCell c, java.awt.Color bg, int align) {
    c.setBackgroundColor(bg);
    c.setHorizontalAlignment(align);
    c.setVerticalAlignment(Element.ALIGN_TOP);
    c.setBorderColor(new java.awt.Color(226, 232, 240));
    c.setBorderWidth(0.7f);
    c.setPadding(5f);
  }

  private void addSpacing(Document document, float size) throws DocumentException {
    Paragraph s = new Paragraph(new Chunk(" "));
    s.setSpacingAfter(size);
    document.add(s);
  }

  private boolean isPrimitive(Object x) {
    return x instanceof String || x instanceof Number || x instanceof Boolean || x == null;
  }

  private String toTitle(String key) {
    if (key == null) return "";
    String[] parts = key.replace("_", " ").split("\\s+");
    StringBuilder sb = new StringBuilder();
    for (String part : parts) {
      if (part.isBlank()) continue;
      if (!sb.isEmpty()) sb.append(' ');
      sb.append(Character.toUpperCase(part.charAt(0)));
      if (part.length() > 1) sb.append(part.substring(1));
    }
    return sb.toString();
  }

  private String val(Object x) {
    if (x == null) return "-";
    if (x instanceof Number n) return formatNumber(n);
    if (x instanceof Boolean b) return b ? "Yes" : "No";
    return String.valueOf(x);
  }

  private String compactVal(Object x) {
    if (x instanceof Map<?, ?>) return "[Object]";
    if (x instanceof List<?>) return "[List]";
    String v = val(x);
    return v.length() > 80 ? v.substring(0, 80) + "..." : v;
  }

  private String formatNumber(Number n) {
    double d = n.doubleValue();
    if (Math.floor(d) == d) return String.format("%,.0f", d);
    return String.format("%,.2f", d);
  }
}
