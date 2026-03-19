package com.entitycheck.service;

// ── OpenPDF imports (explicit — NO wildcards to avoid ambiguity) ──
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Generates Due Diligence Report PDF using OpenPDF.
 */
@Service
public class OrderPdfService {

    private static final Font TITLE_FONT = new Font(Font.HELVETICA, 22, Font.BOLD, new Color(30, 64, 175));
    private static final Font HEADING_FONT = new Font(Font.HELVETICA, 14, Font.BOLD, new Color(30, 41, 59));
    private static final Font SUBHEADING_FONT = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(71, 85, 105));
    private static final Font BODY_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(51, 65, 85));
    private static final Font LABEL_FONT = new Font(Font.HELVETICA, 9, Font.BOLD, new Color(100, 116, 139));
    private static final Font VALUE_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(15, 23, 42));
    private static final Font SMALL_FONT = new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(148, 163, 184));
    private static final Font BADGE_FONT = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);

    public byte[] generateDDRPdf(String orderNumber, String companyName,
                                  Map<String, Object> reportData,
                                  Map<String, Object> enrichment,
                                  Map<String, Object> decisionOutputs) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 50, 50, 60, 50);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            doc.open();

            // ── Cover Page ──
            addCoverPage(doc, orderNumber, companyName);
            doc.newPage();

            // ── Company Profile ──
            Map<String, Object> profile = asMap(reportData.get("companyProfile"));
            addSectionTitle(doc, "1. Company Profile");
            addKeyValue(doc, "Company Name", str(profile.get("name")));
            addKeyValue(doc, "CIN", str(profile.get("cin")));
            addKeyValue(doc, "Company Type", str(profile.get("companyType")));
            addKeyValue(doc, "Category", str(profile.get("category")));
            addKeyValue(doc, "Date of Incorporation", str(profile.get("dateOfIncorporation")));
            addKeyValue(doc, "Listing Status", str(profile.get("listingStatus")));
            addKeyValue(doc, "Authorized Capital", str(profile.get("authorizedCapital")));
            addKeyValue(doc, "Paid-up Capital", str(profile.get("paidUpCapital")));
            addKeyValue(doc, "Registered Office", str(profile.get("registeredOffice")));
            addKeyValue(doc, "Activity", str(profile.get("activityDescription")));
            doc.add(Chunk.NEWLINE);

            // ── Registration Details ──
            Map<String, Object> reg = asMap(reportData.get("registrationDetails"));
            addSectionTitle(doc, "2. Registration Details");
            addKeyValue(doc, "PAN", str(reg.get("pan")));
            addKeyValue(doc, "GSTIN", str(reg.get("gstin")));
            addKeyValue(doc, "TAN", str(reg.get("tan")));
            addKeyValue(doc, "IEC", str(reg.get("importExportCode")));
            addKeyValue(doc, "ROC Code", str(reg.get("rocCode")));
            addKeyValue(doc, "Last Balance Sheet", str(reg.get("lastBalanceSheetDate")));
            addKeyValue(doc, "Last Annual Return", str(reg.get("lastAnnualReturnDate")));
            doc.add(Chunk.NEWLINE);

            // ── Directors ──
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> directors = (List<Map<String, Object>>) reportData.getOrDefault("directors", List.of());
            addSectionTitle(doc, "3. Directors / Management (" + directors.size() + ")");
            if (!directors.isEmpty()) {
                PdfPTable table = new PdfPTable(4);
                table.setWidthPercentage(100);
                table.setWidths(new float[]{3f, 2f, 2f, 1.5f});
                addTableHeader(table, "Name", "Designation", "DIN", "Status");
                for (Map<String, Object> d : directors) {
                    addTableCell(table, str(d.get("name")));
                    addTableCell(table, str(d.get("designation")));
                    addTableCell(table, str(d.get("din")));
                    addTableCell(table, Boolean.TRUE.equals(d.get("isActive")) ? "Active" : "Ceased");
                }
                doc.add(table);
            }
            doc.add(Chunk.NEWLINE);
            doc.newPage();

            // ── Financial Summary ──
            Map<String, Object> fin = asMap(reportData.get("financialSummary"));
            addSectionTitle(doc, "4. Financial Summary");
            addKeyValue(doc, "Revenue / Turnover", str(fin.get("revenue")));
            addKeyValue(doc, "Net Profit / (Loss)", str(fin.get("netProfit")));
            addKeyValue(doc, "EBITDA", str(fin.get("ebitda")));
            addKeyValue(doc, "Total Assets", str(fin.get("totalAssets")));
            addKeyValue(doc, "Total Liabilities", str(fin.get("totalLiabilities")));
            addKeyValue(doc, "Net Worth", str(fin.get("netWorth")));
            addKeyValue(doc, "D/E Ratio", str(fin.get("debtEquityRatio")));
            addKeyValue(doc, "Current Ratio", str(fin.get("currentRatio")));
            addKeyValue(doc, "Profit Margin", str(fin.get("profitMargin")));
            addKeyValue(doc, "Return on Equity", str(fin.get("returnOnEquity")));
            doc.add(Chunk.NEWLINE);

            // ── Charges / Liens ──
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> charges = (List<Map<String, Object>>) reportData.getOrDefault("charges", List.of());
            addSectionTitle(doc, "5. Charges / Liens (" + charges.size() + ")");
            if (!charges.isEmpty()) {
                PdfPTable cTable = new PdfPTable(4);
                cTable.setWidthPercentage(100);
                cTable.setWidths(new float[]{3f, 2f, 1.5f, 1.5f});
                addTableHeader(cTable, "Charge Holder", "Nature", "Status", "Amount");
                for (Map<String, Object> c : charges) {
                    addTableCell(cTable, str(c.get("chargeHolder")));
                    addTableCell(cTable, str(c.get("nature")));
                    addTableCell(cTable, str(c.get("status")));
                    addTableCell(cTable, str(c.get("amount")));
                }
                doc.add(cTable);
            } else {
                doc.add(new Paragraph("No charges recorded.", BODY_FONT));
            }
            doc.add(Chunk.NEWLINE);

            // ── Legal / Compliance ──
            Map<String, Object> legal = asMap(reportData.get("legalCompliance"));
            addSectionTitle(doc, "6. Legal / Compliance");
            addKeyValue(doc, "Compliance Rating", str(legal.get("complianceRating")));
            addKeyValue(doc, "Pending Litigation", str(legal.get("pendingLitigation")));
            addKeyValue(doc, "Suit Filed Cases", str(legal.get("suitFiledCases")));
            addKeyValue(doc, "Wilful Defaulter", Boolean.TRUE.equals(legal.get("wilfulDefaulter")) ? "⚠ YES" : "No");
            doc.add(Chunk.NEWLINE);
            doc.newPage();

            // ── Analyst Investigation Summary ──
            if (enrichment != null) {
                addSectionTitle(doc, "7. Investigation Summary");
                String summary = str(enrichment.get("investigationSummary"));
                if (!summary.isBlank()) {
                    doc.add(new Paragraph(summary, BODY_FONT));
                    doc.add(Chunk.NEWLINE);
                }
                String comments = str(enrichment.get("analystComments"));
                if (!comments.isBlank()) {
                    doc.add(new Paragraph("Analyst Comments:", SUBHEADING_FONT));
                    doc.add(new Paragraph(comments, BODY_FONT));
                    doc.add(Chunk.NEWLINE);
                }
                String recommendation = str(enrichment.get("recommendationNotes"));
                if (!recommendation.isBlank()) {
                    doc.add(new Paragraph("Recommendation:", SUBHEADING_FONT));
                    doc.add(new Paragraph(recommendation, BODY_FONT));
                    doc.add(Chunk.NEWLINE);
                }
            }

            // ── Decision Engine Outputs ──
            if (decisionOutputs != null) {
                addSectionTitle(doc, "8. Analytical Outputs");

                Map<String, Object> cr = asMap(decisionOutputs.get("creditRating"));
                addKeyValue(doc, "Credit Rating", str(cr.get("rating")));
                addKeyValue(doc, "Rationale", str(cr.get("rationale")));

                Map<String, Object> risk = asMap(decisionOutputs.get("riskScore"));
                addKeyValue(doc, "Risk Score", str(risk.get("score")) + " / 100");
                addKeyValue(doc, "Risk Band", str(risk.get("band")));

                Map<String, Object> elig = asMap(decisionOutputs.get("eligibility"));
                String eligStr = formatINR(toLong(elig.get("recommendedAmountMin")))
                        + " – " + formatINR(toLong(elig.get("recommendedAmountMax")));
                addKeyValue(doc, "Recommended Eligibility", eligStr);

                @SuppressWarnings("unchecked")
                List<String> drivers = (List<String>) decisionOutputs.getOrDefault("keyRiskDrivers", List.of());
                if (!drivers.isEmpty()) {
                    doc.add(Chunk.NEWLINE);
                    doc.add(new Paragraph("Key Risk Drivers:", SUBHEADING_FONT));
                    for (String driver : drivers) {
                        doc.add(new Paragraph("  • " + driver, BODY_FONT));
                    }
                }
                doc.add(Chunk.NEWLINE);
            }

            // ── Disclaimer ──
            doc.newPage();
            addSectionTitle(doc, "Disclaimer");
            doc.add(new Paragraph(
                    "This report is prepared for informational purposes only based on data obtained from " +
                            "public and proprietary sources. The information contained herein should not be construed " +
                            "as legal, financial, or professional advice. While every effort has been made to ensure " +
                            "accuracy, no guarantee is made regarding the completeness or timeliness of the data. " +
                            "The recipient assumes full responsibility for any decisions made based on this report.",
                    BODY_FONT
            ));
            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph("Generated: " + OffsetDateTime.now().format(
                    DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")), SMALL_FONT));

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage(), e);
        }
    }

    // ── Helper methods ──

    private void addCoverPage(Document doc, String orderNumber, String companyName) throws DocumentException {
        for (int i = 0; i < 8; i++) doc.add(Chunk.NEWLINE);

        Paragraph title = new Paragraph("DUE DILIGENCE REPORT", TITLE_FONT);
        title.setAlignment(Element.ALIGN_CENTER);
        doc.add(title);

        doc.add(Chunk.NEWLINE);
        Paragraph company = new Paragraph(companyName, new Font(Font.HELVETICA, 16, Font.BOLD, new Color(15, 23, 42)));
        company.setAlignment(Element.ALIGN_CENTER);
        doc.add(company);

        doc.add(Chunk.NEWLINE);
        Paragraph order = new Paragraph("Order: " + orderNumber, BODY_FONT);
        order.setAlignment(Element.ALIGN_CENTER);
        doc.add(order);

        Paragraph date = new Paragraph("Date: " + OffsetDateTime.now().format(
                DateTimeFormatter.ofPattern("dd MMMM yyyy")), BODY_FONT);
        date.setAlignment(Element.ALIGN_CENTER);
        doc.add(date);

        for (int i = 0; i < 6; i++) doc.add(Chunk.NEWLINE);

        Paragraph confidential = new Paragraph("CONFIDENTIAL", new Font(Font.HELVETICA, 11, Font.BOLD, Color.RED));
        confidential.setAlignment(Element.ALIGN_CENTER);
        doc.add(confidential);

        Paragraph footer = new Paragraph("Powered by DiligencePro", SMALL_FONT);
        footer.setAlignment(Element.ALIGN_CENTER);
        doc.add(footer);
    }

    private void addSectionTitle(Document doc, String title) throws DocumentException {
        Paragraph p = new Paragraph(title, HEADING_FONT);
        p.setSpacingBefore(10);
        p.setSpacingAfter(8);
        doc.add(p);

        // Underline
        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setBorderWidthBottom(1.5f);
        cell.setBorderColorBottom(new Color(59, 130, 246));
        cell.setBorderWidthTop(0);
        cell.setBorderWidthLeft(0);
        cell.setBorderWidthRight(0);
        cell.setFixedHeight(2);
        line.addCell(cell);
        doc.add(line);
        doc.add(new Paragraph(" ", new Font(Font.HELVETICA, 4)));
    }

    private void addKeyValue(Document doc, String label, String value) throws DocumentException {
        Paragraph p = new Paragraph();
        p.add(new Chunk(label + ": ", LABEL_FONT));
        p.add(new Chunk(value != null && !value.isBlank() ? value : "—", VALUE_FONT));
        p.setSpacingAfter(3);
        doc.add(p);
    }

    private void addTableHeader(PdfPTable table, String... headers) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, LABEL_FONT));
            cell.setBackgroundColor(new Color(241, 245, 249));
            cell.setPadding(6);
            cell.setBorderWidth(0.5f);
            cell.setBorderColor(new Color(226, 232, 240));
            table.addCell(cell);
        }
    }

    private void addTableCell(PdfPTable table, String value) {
        PdfPCell cell = new PdfPCell(new Phrase(value != null ? value : "—", BODY_FONT));
        cell.setPadding(5);
        cell.setBorderWidth(0.5f);
        cell.setBorderColor(new Color(226, 232, 240));
        table.addCell(cell);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object obj) {
        if (obj instanceof Map) return (Map<String, Object>) obj;
        return Map.of();
    }

    private String str(Object obj) {
        return obj != null ? String.valueOf(obj) : "";
    }

    private long toLong(Object obj) {
        if (obj instanceof Number) return ((Number) obj).longValue();
        try { return Long.parseLong(String.valueOf(obj)); } catch (Exception e) { return 0; }
    }

    private String formatINR(long amount) {
        if (amount >= 10_000_000) return "₹" + String.format("%.2f", amount / 10_000_000.0) + " Cr";
        if (amount >= 100_000) return "₹" + String.format("%.2f", amount / 100_000.0) + " L";
        return "₹" + String.format("%,d", amount);
    }
}