package com.entitycheck.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.io.ByteArrayOutputStream;
import java.awt.Color;

@Service
public class GeniReportPdfGenerator {

    // Colors from the frontend CSS
    private static final Color COLOR_PRIMARY = new Color(0, 33, 71); // #002147
    private static final Color COLOR_SECONDARY = new Color(0, 106, 106); // #006a6a
    private static final Color COLOR_ACCENT = new Color(186, 26, 26); // #ba1a1a
    private static final Color COLOR_TEXT_DARK = new Color(26, 28, 32); // #1a1c20
    private static final Color COLOR_TEXT_MUTED = new Color(115, 119, 127); // #73777f
    private static final Color COLOR_BORDER = new Color(232, 234, 240); // #e8eaf0
    private static final Color COLOR_BG_LIGHT = new Color(248, 249, 252); // #f8f9fc

    // Fonts
    private static final Font FONT_TITLE = new Font(Font.HELVETICA, 20, Font.BOLD, COLOR_PRIMARY);
    private static final Font FONT_HEADING = new Font(Font.HELVETICA, 12, Font.BOLD, COLOR_PRIMARY);
    private static final Font FONT_SUBHEADING = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(67, 71, 78));
    private static final Font FONT_BODY = new Font(Font.HELVETICA, 9, Font.NORMAL, COLOR_TEXT_DARK);
    private static final Font FONT_SMALL = new Font(Font.HELVETICA, 8, Font.NORMAL, COLOR_TEXT_MUTED);
    private static final Font FONT_LABEL = new Font(Font.HELVETICA, 8, Font.BOLD, COLOR_TEXT_MUTED);
    private static final Font FONT_VALUE = new Font(Font.HELVETICA, 10, Font.BOLD, COLOR_PRIMARY);
    private static final Font FONT_KPI_VALUE = new Font(Font.HELVETICA, 16, Font.BOLD, COLOR_PRIMARY);

    public byte[] generatePdf(Map<String, Object> reportData, String orderNumber, String companyName) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        PdfWriter writer = PdfWriter.getInstance(document, baos);

        // Add page event for header/footer
        writer.setPageEvent(new PdfPageEventHelper() {
            @Override
            public void onEndPage(PdfWriter writer, Document document) {
                try {
                    PdfPTable footer = new PdfPTable(2);
                    footer.setTotalWidth(
                            document.getPageSize().getWidth() - document.leftMargin() - document.rightMargin());
                    footer.getDefaultCell().setBorder(Rectangle.NO_BORDER);
                    footer.getDefaultCell().setHorizontalAlignment(Element.ALIGN_LEFT);
                    footer.addCell(new Phrase("Geni Intelligence — Confidential", FONT_SMALL));
                    footer.getDefaultCell().setHorizontalAlignment(Element.ALIGN_RIGHT);
                    footer.addCell(new Phrase("Page " + writer.getPageNumber(), FONT_SMALL));
                    footer.writeSelectedRows(0, -1, document.leftMargin(), document.bottomMargin() - 10,
                            writer.getDirectContent());
                } catch (DocumentException ignored) {
                }
            }
        });

        document.open();

        @SuppressWarnings("unchecked")
        Map<String, Object> report = (Map<String, Object>) reportData.get("report");
        if (report == null) {
            report = reportData; // fallback
        }

        // ---- Cover Page ----
        addCoverPage(document, report, orderNumber, companyName);
        document.newPage();

        // ---- Table of Contents (simplified) ----
        // (You can add a TOC page if desired; here we skip to main sections)

        // ---- Section 03: Entity Overview ----
        addEntityOverview(document, report);
        document.newPage();

        // ---- Section 04: Operational Profile ----
        addOperationalProfile(document, report);
        document.newPage();

        // ---- Section 05: Scale & Structure ----
        addScaleStructure(document, report);
        document.newPage();

        // ---- Section 06: Executive Summary ----
        addExecutiveSummary(document, report);
        document.newPage();

        // ---- Section 07: Risk Composition & Heatmap ----
        addScoreBreakdown(document, report);
        document.newPage();

        // ---- Section 08: Management ----
        addManagement(document, report);
        document.newPage();

        // ---- Section 09: Ownership Structure ----
        addOwnership(document, report);
        document.newPage();

        // ---- Section 10: Legal Exposure & Credit Ratings ----
        addLegalExposure(document, report);
        document.newPage();

        // ---- Section 11: Compliance & Behavioral Risk ----
        addCompliance(document, report);
        document.newPage();

        // ---- Financial Statements (P&L, BS, CF) ----
        addFinancialStatements(document, report);
        // No new page here; the method handles multiple pages internally

        // ---- Financial Ratios ----
        addFinancialRatios(document, report);
        document.newPage();

        // ---- Performance Trends & Working Capital ----
        addPerformanceAndWC(document, report);
        // This method creates two pages, so it manages page breaks

        // ---- Liquidity Analysis ----
        addLiquidityAnalysis(document, report);
        document.newPage();

        // ---- Risk Interpretation ----
        addRiskAnalysis(document, report);
        document.newPage();

        // ---- Credit Recommendation ----
        addCreditRecommendation(document, report);
        document.newPage();

        // ---- Risk Factors ----
        addRiskFactors(document, report);
        document.newPage();

        // ---- Strengths ----
        addStrengths(document, report);
        document.newPage();

        // ---- Future Outlook ----
        addFutureOutlook(document, report);
        document.newPage();

        // ---- Methodology ----
        addMethodology(document, report);
        document.newPage();

        // ---- Disclaimer ----
        addDisclaimer(document, report);
        document.newPage();

        // ---- Final Conclusion ----
        addFinalConclusion(document, report);

        document.close();
        return baos.toByteArray();
    }

    // ------------------------------------------------------------
    // Helper methods for styling
    // ------------------------------------------------------------
    private void addSectionHeader(Document document, String sectionNum, String title, String reportId)
            throws DocumentException {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[] { 3, 1 });
        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.addElement(new Paragraph(sectionNum, new Font(Font.HELVETICA, 6.5f, Font.BOLD, COLOR_SECONDARY)));
        left.addElement(new Paragraph(title, FONT_TITLE));
        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        right.setVerticalAlignment(Element.ALIGN_BOTTOM);
        right.addElement(new Paragraph("Report ID: " + (reportId != null ? reportId : "—"), FONT_SMALL));
        header.addCell(left);
        header.addCell(right);
        document.add(header);
        // Underline
        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100);
        PdfPCell lineCell = new PdfPCell();
        lineCell.setBorder(Rectangle.BOTTOM);
        lineCell.setBorderColor(new Color(212, 227, 255));
        lineCell.setBorderWidth(1.5f);
        line.addCell(lineCell);
        document.add(line);
        document.add(new Paragraph(" ")); // spacing
    }

    private void addKpiGrid(Document document, List<Map<String, String>> items) throws DocumentException {
        PdfPTable table = new PdfPTable(items.size());
        table.setWidthPercentage(100);
        table.setSpacingBefore(10);
        table.setSpacingAfter(10);
        for (Map<String, String> item : items) {
            PdfPCell cell = new PdfPCell();
            cell.setBackgroundColor(COLOR_BG_LIGHT);
            cell.setBorderColor(COLOR_BORDER);
            cell.setPadding(8);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.addElement(
                    new Paragraph(item.get("label"), new Font(Font.HELVETICA, 6.5f, Font.BOLD, COLOR_TEXT_MUTED)));
            cell.addElement(new Paragraph(item.get("value"), new Font(Font.HELVETICA, 13, Font.BOLD, COLOR_PRIMARY)));
            table.addCell(cell);
        }
        document.add(table);
    }

    private void addTwoColumnKV(Document document, Map<String, String> pairs) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(5);
        for (Map.Entry<String, String> e : pairs.entrySet()) {
            PdfPCell label = new PdfPCell(new Phrase(e.getKey(), FONT_LABEL));
            label.setBorder(Rectangle.NO_BORDER);
            label.setPaddingBottom(5);
            PdfPCell value = new PdfPCell(new Phrase(e.getValue() != null ? e.getValue() : "—", FONT_BODY));
            value.setBorder(Rectangle.NO_BORDER);
            value.setPaddingBottom(5);
            table.addCell(label);
            table.addCell(value);
        }
        document.add(table);
    }

    private void addTable(Document document, List<String> headers, List<List<String>> rows) throws DocumentException {
        PdfPTable table = new PdfPTable(headers.size());
        table.setWidthPercentage(100);
        table.setSpacingBefore(5);
        table.setSpacingAfter(10);
        // Header
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, FONT_SUBHEADING));
            cell.setBackgroundColor(new Color(241, 243, 247));
            cell.setBorderColor(COLOR_BORDER);
            cell.setPadding(5);
            table.addCell(cell);
        }
        // Rows
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            Color bg = (i % 2 == 0) ? Color.WHITE : COLOR_BG_LIGHT;
            for (String cellText : row) {
                PdfPCell cell = new PdfPCell(new Phrase(cellText != null ? cellText : "—", FONT_BODY));
                cell.setBackgroundColor(bg);
                cell.setBorderColor(COLOR_BORDER);
                cell.setPadding(4);
                table.addCell(cell);
            }
        }
        document.add(table);
    }

    private String fmt(Object v) {
        return v != null && !v.toString().isBlank() ? v.toString() : "—";
    }

    private String fmtCr(Object amount) {
        if (amount == null)
            return "—";
        try {
            double val = Double.parseDouble(amount.toString());
            double cr = val / 1_00_00_000;
            return String.format("INR %.2f Cr", cr);
        } catch (NumberFormatException e) {
            return amount.toString();
        }
    }

    private String safeGetMulti(String... keysAndMaps) {
        // keysAndMaps format: map1, key1, map2, key2, ..., fallback
        for (int i = 0; i < keysAndMaps.length - 1; i += 2) {
            if (i + 1 >= keysAndMaps.length)
                break;
            Object mapObj = keysAndMaps[i];
            String key = keysAndMaps[i + 1];
            if (mapObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) mapObj;
                Object val = map.get(key);
                if (val != null && !val.toString().isBlank())
                    return val.toString();
            }
        }
        // last argument is fallback
        if (keysAndMaps.length % 2 == 1) {
            return keysAndMaps[keysAndMaps.length - 1];
        }
        return "—";
    }

    private String safeGet(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val != null && !val.toString().isBlank())
                return val.toString();
        }
        return "—";
    }

    // ------------------------------------------------------------
    // Section Implementations
    // ------------------------------------------------------------
    private void addCoverPage(Document document, Map<String, Object> report, String orderNumber, String companyName)
            throws DocumentException {
        Map<String, Object> meta = getMeta(report);
        Map<String, Object> eo = getMap(report, "entity_overview");

        String reportId = safeGet(meta, "report_id");
        String reportType = safeGet(meta, "report_type", "CREDIT DECISION INTELLIGENCE REPORT");
        String entityName = safeGet(eo, "legal_name");
        if ("—".equals(entityName))
            entityName = safeGet(meta, "entity_name");
        if ("—".equals(entityName))
            entityName = companyName;

        String cin = safeGet(eo, "cin");
        if ("—".equals(cin))
            cin = safeGet(meta, "entity_cin");
        String reportDate = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy"));

        // Background gradient is simulated with a colored rectangle
        // (Not easy in OpenPDF, we'll use a dark blue background for the whole page)
        document.add(new Paragraph(" ")); // spacing

        // Top bar
        PdfPTable top = new PdfPTable(2);
        top.setWidthPercentage(100);
        top.setWidths(new float[] { 1, 1 });
        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.addElement(new Phrase("GENI", new Font(Font.HELVETICA, 18, Font.BOLD, Color.WHITE)));
        left.addElement(new Phrase("INTELLIGENCE", new Font(Font.HELVETICA, 7, Font.BOLD, new Color(147, 242, 242))));
        PdfPCell right = new PdfPCell(
                new Phrase("STRICTLY CONFIDENTIAL", new Font(Font.HELVETICA, 7, Font.BOLD, Color.WHITE)));
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        right.setBackgroundColor(COLOR_ACCENT);
        right.setPadding(4);
        top.addCell(left);
        top.addCell(right);
        document.add(top);

        for (int i = 0; i < 8; i++)
            document.add(new Paragraph(" "));

        Paragraph typePara = new Paragraph("▸ " + reportType,
                new Font(Font.HELVETICA, 7.5f, Font.BOLD, new Color(147, 242, 242)));
        typePara.setSpacingBefore(20);
        document.add(typePara);

        Paragraph title1 = new Paragraph("Business Intelligence", new Font(Font.HELVETICA, 36, Font.BOLD, Color.WHITE));
        title1.setLeading(36);
        document.add(title1);
        Paragraph title2 = new Paragraph("& Risk Assessment", new Font(Font.HELVETICA, 36, Font.BOLD, Color.WHITE));
        title2.setLeading(36);
        document.add(title2);
        Paragraph title3 = new Paragraph("Report", new Font(Font.HELVETICA, 36, Font.BOLD, new Color(147, 242, 242)));
        title3.setLeading(36);
        document.add(title3);

        document.add(new Paragraph(" "));

        PdfPTable info = new PdfPTable(3);
        info.setWidthPercentage(100);
        info.setSpacingBefore(20);
        info.getDefaultCell().setBorder(Rectangle.NO_BORDER);
        info.getDefaultCell().setBorderColorTop(new Color(255, 255, 255, 50));
        info.getDefaultCell().setBorderWidthTop(1);
        info.getDefaultCell().setPaddingTop(10);
        info.addCell(createInfoCell("Subject Entity", entityName, Color.WHITE));
        info.addCell(createInfoCell("CIN / Identifier", cin, Color.WHITE));
        info.addCell(createInfoCell("Report Date", reportDate, Color.WHITE));
        document.add(info);

        document.add(new Paragraph(" "));
        PdfPTable footer = new PdfPTable(2);
        footer.setWidthPercentage(100);
        footer.getDefaultCell().setBorder(Rectangle.NO_BORDER);
        footer.getDefaultCell().setHorizontalAlignment(Element.ALIGN_LEFT);
        footer.addCell(new Phrase("© " + OffsetDateTime.now().getYear() + " Geni Intelligence",
                new Font(Font.HELVETICA, 6, Font.NORMAL, new Color(255, 255, 255, 100))));
        footer.getDefaultCell().setHorizontalAlignment(Element.ALIGN_RIGHT);
        footer.addCell(new Phrase("Report ID: " + reportId + "  |  Page 01",
                new Font(Font.HELVETICA, 6, Font.NORMAL, new Color(255, 255, 255, 100))));
        document.add(footer);
    }

    private PdfPCell createInfoCell(String label, String value, Color textColor) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.addElement(new Phrase(label, new Font(Font.HELVETICA, 6.5f, Font.BOLD, new Color(255, 255, 255, 128))));
        cell.addElement(new Phrase(value, new Font(Font.HELVETICA, 11, Font.BOLD, textColor)));
        return cell;
    }

    private void addEntityOverview(Document document, Map<String, Object> report) throws DocumentException {
        Map<String, Object> eo = getMap(report, "entity_overview");
        Map<String, Object> meta = getMeta(report);
        String reportId = safeGet(meta, "report_id");

        addSectionHeader(document, "Section 03", "Entity Overview & Statutory Profile", reportId);

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(10);
        addKeyValueRow(table, "Legal Name", safeGet(eo, "legal_name"));
        addKeyValueRow(table, "CIN", safeGet(eo, "cin"));
        addKeyValueRow(table, "PAN", safeGet(eo, "pan"));
        addKeyValueRow(table, "Status", safeGet(eo, "status"));
        addKeyValueRow(table, "Company Type", safeGet(eo, "company_type"));
        addKeyValueRow(table, "Incorporation Date", safeGet(eo, "incorporation_date"));
        addKeyValueRow(table, "Age", safeGet(eo, "age"));
        addKeyValueRow(table, "Industry", safeGet(eo, "industry"));
        addKeyValueRow(table, "Industry Segment", safeGet(eo, "industry_segment"));
        addKeyValueRow(table, "Authorised Capital", safeGet(eo, "authorised_capital_fmt"));
        addKeyValueRow(table, "Paid-up Capital", safeGet(eo, "paid_up_capital_fmt"));
        addKeyValueRow(table, "ROC", safeGet(eo, "roc"));
        addKeyValueRow(table, "Last AGM Date", safeGet(eo, "last_agm_date"));
        addKeyValueRow(table, "Last ROC Filing", safeGet(eo, "last_roc_filing"));
        addKeyValueRow(table, "CIRP Status", safeGet(eo, "cirp_status"));

        Map<String, Object> lei = getMap(eo, "lei");
        if (!lei.isEmpty()) {
            addKeyValueRow(table, "LEI Number", safeGet(lei, "number"));
            addKeyValueRow(table, "LEI Status", safeGet(lei, "status"));
            addKeyValueRow(table, "LEI Registration Date", safeGet(lei, "registration_date"));
        }

        Map<String, Object> addr = getMap(eo, "registered_address");
        String fullAddr = safeGet(addr, "full_address");
        if (!"—".equals(fullAddr)) {
            addKeyValueRow(table, "Registered Address", fullAddr);
        }

        document.add(table);

        // AI Summary
        String sysSummary = safeGet(eo, "sys_identification_summary");
        if (!"—".equals(sysSummary)) {
            document.add(createCalloutBox(sysSummary, COLOR_PRIMARY, new Color(147, 242, 242)));
        }
    }

    private void addOperationalProfile(Document document, Map<String, Object> report) throws DocumentException {
        Map<String, Object> op = getMap(report, "operational_profile");
        Map<String, Object> eo = getMap(report, "entity_overview");
        Map<String, Object> meta = getMeta(report);
        String reportId = safeGet(meta, "report_id");

        addSectionHeader(document, "Section 04", "Operational Profile", reportId);

        String summary = safeGet(op, "summary");
        if (!"???".equals(summary)) {
            document.add(createCalloutBox(summary, new Color(241, 246, 255), COLOR_PRIMARY));
        }

        Map<String, Object> contact = getMap(op, "contact");
        document.add(new Paragraph("Contact Information", FONT_HEADING));
        PdfPTable contactTable = new PdfPTable(2);
        contactTable.setWidthPercentage(100);
        addKeyValueRow(contactTable, "Email", fallback(safeGet(contact, "email"), safeGet(eo, "email")));
        addKeyValueRow(contactTable, "Phone", fallback(safeGet(contact, "phone"), safeGet(eo, "phone")));
        addKeyValueRow(contactTable, "Website", safeGet(eo, "website"));
        addKeyValueRow(contactTable, "Industry", safeGet(eo, "industry"));
        document.add(contactTable);
        document.add(Chunk.NEWLINE);

        Map<String, Object> pba = getMap(op, "principal_business_activity");
        document.add(new Paragraph("Principal Business Activity", FONT_HEADING));
        PdfPTable pbaTable = new PdfPTable(2);
        pbaTable.setWidthPercentage(100);
        addKeyValueRow(pbaTable, "NIC Code", safeGet(pba, "code"));
        addKeyValueRow(pbaTable, "Description", safeGet(pba, "description"));
        addKeyValueRow(pbaTable, "Turnover %", safeGet(pba, "turnover_percentage") + "%");
        document.add(pbaTable);

        Map<String, Object> gstReg = getMap(op, "gst_registrations");
        List<Map<String, Object>> gstList = getList(gstReg, "list");
        if (!gstList.isEmpty()) {
            document.add(new Paragraph("GST Registrations (" + gstList.size() + ")", FONT_HEADING));
            List<String> headers = List.of("GSTIN", "State", "Status", "Registration Date");
            List<List<String>> rows = gstList.stream().map(g -> List.of(
                    fmt(g.get("gstin")), fmt(g.get("state")), fmt(g.get("status")), fmt(g.get("reg_date"))))
                    .collect(Collectors.toList());
            addTable(document, headers, rows);
        }

        document.add(new Paragraph("EPFO Establishments: " + safeGet(op, "epfo_establishments", "0"), FONT_BODY));
    }

    private void addScaleStructure(Document document, Map<String, Object> report) throws DocumentException {
        Map<String, Object> ss = getMap(report, "scale_structure");
        Map<String, Object> meta = getMeta(report);
        addSectionHeader(document, "Section 05", "Scale & Structure", safeGet(meta, "report_id"));

        Map<String, Object> bs = getMap(ss, "business_scale");
        addKpiGrid(document, List.of(
                Map.of("label", "Revenue Range", "value", fmt(bs.get("revenue_range"))),
                Map.of("label", "Profit Range", "value", fmt(bs.get("profit_range"))),
                Map.of("label", "Employee Count", "value", fmt(bs.get("employee_count"))),
                Map.of("label", "Size Classification", "value", fmt(bs.get("size_classification")))));

        // Open Charges
        List<Map<String, Object>> charges = getList(ss, "open_charges");
        if (!charges.isEmpty()) {
            document.add(new Paragraph("Open Charges (" + charges.size() + ")", FONT_HEADING));
            List<String> headers = List.of("Charge ID", "Holder", "Amount", "Type", "Date");
            List<List<String>> rows = charges.stream().map(c -> List.of(
                    fmt(c.get("charge_id")), fmt(c.get("holder")), fmt(c.get("amount_fmt")), fmt(c.get("type")),
                    fmt(c.get("date")))).collect(Collectors.toList());
            addTable(document, headers, rows);
        }
    }

    private void addExecutiveSummary(Document document, Map<String, Object> report) throws DocumentException {
        Map<String, Object> ed = getMap(report, "executive_dashboard");
        Map<String, Object> meta = getMeta(report);
        addSectionHeader(document, "Section 06", "Executive Risk Dashboard", safeGet(meta, "report_id"));

        Map<String, Object> snap = getMap(ed, "entity_snapshot");
        document.add(new Paragraph("Subject Entity: " + fallback(safeGet(snap, "name"), safeGet(meta, "entity_name")), FONT_BODY));
        document.add(new Paragraph("CIN: " + fallback(safeGet(snap, "cin"), safeGet(meta, "entity_cin")) +
                " | Industry: " + safeGet(snap, "industry"), FONT_SMALL));

        String score = fmt(ed.get("risk_score"));
        String grade = fmt(ed.get("risk_grade"));
        String limit = fmt(ed.get("credit_limit_fmt"));
        String days = fmt(ed.get("credit_days"));
        String action = fmt(ed.get("recommended_action"));
        String pd = fmt(ed.get("probability_of_default"));

        PdfPTable kpi = new PdfPTable(3);
        kpi.setWidthPercentage(100);
        addKpiCell(kpi, "Composite Risk Score", score + "/100", "Grade " + grade);
        addKpiCell(kpi, "Recommended Credit Limit", limit, null);
        addKpiCell(kpi, "Credit Period", days + " Days", null);
        addKpiCell(kpi, "Probability of Default", pd + "%", null);
        addKpiCell(kpi, "Recommended Action", action, null);
        addKpiCell(kpi, "Score Band", fmt(ed.get("score_band")), fmt(ed.get("risk_label")));
        document.add(kpi);

        List<String> keyRiskFlags = getStringList(ed, "key_risk_flags");
        if (!keyRiskFlags.isEmpty()) {
            document.add(new Paragraph("Key Risk Flags", FONT_HEADING));
            for (String flag : keyRiskFlags) {
                document.add(new Paragraph("- " + flag, FONT_BODY));
            }
        }

        Map<String, Object> comps = getMap(ed, "score_components");
        if (!comps.isEmpty()) {
            document.add(new Paragraph("Score Components", FONT_HEADING));
            PdfPTable compTable = new PdfPTable(2);
            compTable.setWidthPercentage(100);
            for (Map.Entry<String, Object> e : comps.entrySet()) {
                addKeyValueRow(compTable, e.getKey(), fmt(e.getValue()));
            }
            document.add(compTable);
        }

        String sysSum = safeGet(ed, "sys_executive_summary");
        if (!"???".equals(sysSum)) {
            document.add(createCalloutBox(sysSum, COLOR_PRIMARY, new Color(147, 242, 242)));
        }
    }

    private void addScoreBreakdown(Document document, Map<String, Object> report) throws DocumentException {
        Map<String, Object> ed = getMap(report, "executive_dashboard");
        Map<String, Object> fc = getMap(report, "final_conclusion");
        Map<String, Object> ra = getMap(report, "risk_analysis");
        Map<String, Object> meta = getMeta(report);
        addSectionHeader(document, "Section 07", "Risk Composition & Heatmap", safeGet(meta, "report_id"));

        Map<String, Object> wf = getMap(ed, "score_waterfall");
        if (!wf.isEmpty()) {
            document.add(new Paragraph("Score Waterfall", FONT_HEADING));
            PdfPTable wfTable = new PdfPTable(2);
            wfTable.setWidthPercentage(100);
            for (Map.Entry<String, Object> e : wf.entrySet()) {
                addKeyValueRow(wfTable, toTitle(e.getKey()), fmt(e.getValue()));
            }
            document.add(wfTable);
            document.add(Chunk.NEWLINE);
        }

        Map<String, Object> heat = getMap(fc, "risk_heatmap");
        if (!heat.isEmpty()) {
            document.add(new Paragraph("Risk Heatmap", FONT_HEADING));
            PdfPTable heatTable = new PdfPTable(2);
            heatTable.setWidthPercentage(100);
            for (Map.Entry<String, Object> e : heat.entrySet()) {
                addKeyValueRow(heatTable, toTitle(e.getKey()), fmt(e.getValue()));
            }
            document.add(heatTable);
        }

        List<Map<String, Object>> riskFactors = getList(ra, "risk_factors");
        if (!riskFactors.isEmpty()) {
            document.add(new Paragraph("Risk Drivers", FONT_HEADING));
            List<String> headers = List.of("Factor", "Impact", "Direction", "Detail");
            List<List<String>> rows = riskFactors.stream().map(r -> List.of(
                    fmt(r.get("factor")), fmt(r.get("impact")), fmt(r.get("direction")), fmt(r.get("detail"))))
                    .collect(Collectors.toList());
            addTable(document, headers, rows);
        }
    }

    private void addManagement(Document document, Map<String, Object> report) throws DocumentException {
        Map<String, Object> mp = getMap(report, "management_profile");
        Map<String, Object> meta = getMeta(report);
        addSectionHeader(document, "Section 08", "Management & Governance", safeGet(meta, "report_id"));

        document.add(new Paragraph(
                "Total Directors: " + fmt(mp.get("total_directors")) + " | Active: " + fmt(mp.get("active_directors")),
                FONT_BODY));

        List<Map<String, Object>> directors = getList(mp, "directors");
        if (!directors.isEmpty()) {
            List<String> headers = List.of("Name", "Designation", "DIN", "Appointment Date", "Status");
            List<List<String>> rows = directors.stream().map(d -> List.of(
                    fmt(d.get("name")), fmt(d.get("designation")), fmt(d.get("din")), fmt(d.get("date_of_appointment")),
                    Boolean.TRUE.equals(d.get("is_active")) ? "Active" : "Inactive")).collect(Collectors.toList());
            addTable(document, headers, rows);
        }
    }

    private void addOwnership(Document document, Map<String, Object> report) throws DocumentException {
        Map<String, Object> os = getMap(report, "ownership_structure");
        Map<String, Object> meta = getMeta(report);
        addSectionHeader(document, "Section 09", "Ownership Structure", safeGet(meta, "report_id"));

        document.add(new Paragraph("Promoter Holding: " + fmt(os.get("promoter_holding_pct")) + "%", FONT_BODY));
        document.add(new Paragraph("Public Holding: " + fmt(os.get("public_holding_pct")) + "%", FONT_BODY));
        document.add(new Paragraph("As of: " + fmt(os.get("shareholding_as_of")), FONT_SMALL));

        List<Map<String, Object>> categories = getList(os, "shareholder_categories");
        if (!categories.isEmpty()) {
            List<String> headers = List.of("Category", "Holding %");
            List<List<String>> rows = categories.stream().map(c -> List.of(
                    fmt(c.get("category")), fmt(c.get("holding_pct"))))
                    .collect(Collectors.toList());
            addTable(document, headers, rows);
        }

        List<Map<String, Object>> top = getList(os, "top_shareholders");
        if (!top.isEmpty()) {
            document.add(new Paragraph("Top Shareholders", FONT_HEADING));
            List<String> headers = List.of("Shareholder", "Holding %", "Type");
            List<List<String>> rows = top.stream().map(t -> List.of(
                    fmt(t.get("name")), fmt(t.get("holding_pct")), fmt(t.get("type"))))
                    .collect(Collectors.toList());
            addTable(document, headers, rows);
        }
    }

    private void addLegalExposure(Document document, Map<String, Object> report) throws DocumentException {
        Map<String, Object> le = getMap(report, "legal_exposure");
        Map<String, Object> meta = getMeta(report);
        addSectionHeader(document, "Section 10", "Legal Exposure & Credit Ratings", safeGet(meta, "report_id"));

        // Charges
        List<Map<String, Object>> charges = getList(le, "charges");
        if (!charges.isEmpty()) {
            document.add(new Paragraph("Open Charges (" + charges.size() + ")", FONT_HEADING));
            List<String> headers = List.of("Charge ID", "Holder", "Amount", "Status", "Date");
            List<List<String>> rows = charges.stream().map(c -> List.of(
                    fmt(c.get("charge_id")), fmt(c.get("holder")), fmt(c.get("amount_fmt")), fmt(c.get("status")),
                    fmt(c.get("date_created")))).collect(Collectors.toList());
            addTable(document, headers, rows);
        }

        // Credit Ratings
        List<Map<String, Object>> ratings = getList(le, "full_credit_ratings");
        if (!ratings.isEmpty()) {
            document.add(new Paragraph("External Credit Ratings", FONT_HEADING));
            List<String> headers = List.of("Agency", "Rating", "Outlook", "Amount");
            List<List<String>> rows = ratings.stream().map(r -> {
                Map<String, Object> detail = getList(r, "rating_details").stream().findFirst().orElse(Map.of());
                return List.of(
                        fmt(r.get("agency")),
                        fmt(r.get("rating")),
                        fmt(detail.get("outlook")),
                        fmt(r.get("amount_fmt")));
            }).collect(Collectors.toList());
            addTable(document, headers, rows);
        }
    }

    private void addCompliance(Document document, Map<String, Object> report) throws DocumentException {
        Map<String, Object> cb = getMap(report, "compliance_behavior");
        Map<String, Object> meta = getMeta(report);
        addSectionHeader(document, "Section 11", "Compliance & Behavioral Risk", safeGet(meta, "report_id"));

        Map<String, Object> lights = getMap(cb, "traffic_lights");
        if (!lights.isEmpty()) {
            document.add(new Paragraph("Traffic Lights", FONT_HEADING));
            for (Map.Entry<String, Object> e : lights.entrySet()) {
                document.add(new Paragraph(e.getKey() + ": " + e.getValue(), FONT_BODY));
            }
        }
        String sysSum = safeGet(cb, "sys_compliance_summary");
        if (!"—".equals(sysSum)) {
            document.add(createCalloutBox(sysSum, COLOR_PRIMARY, new Color(147, 242, 242)));
        }
    }

    private void addFinancialStatements(Document document, Map<String, Object> report) throws DocumentException {
        Map<String, Object> fs = getMap(report, "financial_statements");
        Map<String, Object> meta = getMeta(report);
        String reportId = safeGet(meta, "report_id");

        // P&L
        addSectionHeader(document, "Section 12", "Financial Statements — Profit & Loss", reportId);
        Map<String, Object> pl = getMap(fs, "profit_loss");
        List<String> years = getStringList(fs, "years");
        if (!years.isEmpty()) {
            PdfPTable table = new PdfPTable(years.size() + 1);
            table.setWidthPercentage(100);
            table.addCell(new Phrase("Particulars", FONT_SUBHEADING));
            for (String y : years)
                table.addCell(new Phrase(y, FONT_SUBHEADING));
            addPLRow(table, "Net Revenue", pl, "net_revenue", years);
            addPLRow(table, "EBITDA", pl, "ebitda", years);
            addPLRow(table, "PAT", pl, "profit_after_tax", years);
            document.add(table);
        }
        document.newPage();

        // Balance Sheet
        addSectionHeader(document, "Section 13", "Balance Sheet", reportId);
        Map<String, Object> bs = getMap(fs, "balance_sheet");
        Map<String, Object> assets = getMap(bs, "assets");
        Map<String, Object> liab = getMap(bs, "liabilities");
        if (!years.isEmpty()) {
            PdfPTable table = new PdfPTable(years.size() + 1);
            table.setWidthPercentage(100);
            table.addCell(new Phrase("Particulars", FONT_SUBHEADING));
            for (String y : years)
                table.addCell(new Phrase(y, FONT_SUBHEADING));
            addPLRow(table, "Total Assets", assets, "total_assets", years);
            addPLRow(table, "Total Equity", liab, "total_equity", years);
            addPLRow(table, "Total Debt", liab, "total_debt", years);
            document.add(table);
        }
        document.newPage();

        // Cash Flow
        addSectionHeader(document, "Section 14", "Cash Flow", reportId);
        Map<String, Object> cf = getMap(fs, "cash_flow");
        if (!years.isEmpty()) {
            PdfPTable table = new PdfPTable(years.size() + 1);
            table.setWidthPercentage(100);
            table.addCell(new Phrase("Particulars", FONT_SUBHEADING));
            for (String y : years)
                table.addCell(new Phrase(y, FONT_SUBHEADING));
            addPLRow(table, "Operating CF", cf, "operating", years);
            addPLRow(table, "Investing CF", cf, "investing", years);
            addPLRow(table, "Financing CF", cf, "financing", years);
            addPLRow(table, "Net Change", cf, "net_change", years);
            document.add(table);
        }
    }

    private void addFinancialRatios(Document document, Map<String, Object> report) throws DocumentException {
        Map<String, Object> fr = getMap(report, "financial_ratios");
        Map<String, Object> meta = getMeta(report);
        addSectionHeader(document, "Section 15", "Financial Ratios", safeGet(meta, "report_id"));

        List<String> years = getStringList(fr, "years");
        Map<String, Object> prof = getMap(fr, "profitability");
        Map<String, Object> lev = getMap(fr, "leverage");
        Map<String, Object> liq = getMap(fr, "liquidity");

        if (!years.isEmpty()) {
            PdfPTable table = new PdfPTable(years.size() + 1);
            table.setWidthPercentage(100);
            table.addCell(new Phrase("Ratio", FONT_SUBHEADING));
            for (String y : years)
                table.addCell(new Phrase(y, FONT_SUBHEADING));
            addPLRow(table, "Net Margin %", prof, "net_margin", years);
            addPLRow(table, "EBITDA Margin %", prof, "ebitda_margin", years);
            addPLRow(table, "ROE %", prof, "return_on_equity", years);
            addPLRow(table, "Debt/Equity", lev, "debt_by_equity", years);
            addPLRow(table, "Current Ratio", liq, "current_ratio", years);
            document.add(table);
        }
    }

    private void addPerformanceAndWC(Document document, Map<String, Object> report) throws DocumentException {
        Map<String, Object> pt = getMap(report, "performance_trends");
        Map<String, Object> wc = getMap(report, "working_capital_analysis");
        Map<String, Object> meta = getMeta(report);
        String reportId = safeGet(meta, "report_id");

        addSectionHeader(document, "Section 16", "Performance Trends", reportId);
        List<String> years = getStringList(pt, "years");
        document.add(new Paragraph("Latest Revenue: " + safeGet(getMap(pt, "latest"), "revenue_fmt"), FONT_BODY));
        document.add(new Paragraph("Latest PAT: " + safeGet(getMap(pt, "latest"), "pat_fmt"), FONT_BODY));
        document.newPage();

        addSectionHeader(document, "Section 17", "Working Capital Analysis", reportId);
        document.add(new Paragraph("Net Working Capital: " + safeGet(getMap(wc, "latest"), "net_working_capital_fmt"),
                FONT_BODY));
        document.add(new Paragraph(
                "Cash Conversion Cycle: " + safeGet(getMap(wc, "latest"), "cash_conversion_cycle") + " days",
                FONT_BODY));
    }

    private void addLiquidityAnalysis(Document document, Map<String, Object> report) throws DocumentException {
        Map<String, Object> la = getMap(report, "liquidity_analysis");
        Map<String, Object> meta = getMeta(report);
        addSectionHeader(document, "Section 18", "Liquidity Analysis", safeGet(meta, "report_id"));

        Map<String, Object> latest = getMap(la, "latest");
        document.add(new Paragraph("Current Ratio: " + safeGet(latest, "current_ratio_fmt"), FONT_BODY));
        document.add(new Paragraph("Quick Ratio: " + safeGet(latest, "quick_ratio_fmt"), FONT_BODY));
        document.add(new Paragraph("Operating Cash Flow: " + safeGet(latest, "ocf_fmt"), FONT_BODY));
    }

    private void addRiskAnalysis(Document document, Map<String, Object> report) throws DocumentException {
        Map<String, Object> ra = getMap(report, "risk_analysis");
        Map<String, Object> meta = getMeta(report);
        addSectionHeader(document, "Section 19", "Risk Interpretation", safeGet(meta, "report_id"));

        document.add(new Paragraph(
                "Risk Score: " + fmt(ra.get("risk_score")) + "/100 (Grade " + fmt(ra.get("risk_grade")) + ")",
                FONT_BODY));
        document.add(
                new Paragraph("Probability of Default: " + fmt(ra.get("probability_of_default")) + "%", FONT_BODY));
        String summary = safeGet(ra, "summary");
        if (!"—".equals(summary)) {
            document.add(new Paragraph(summary, FONT_BODY));
        }
    }

    private void addCreditRecommendation(Document document, Map<String, Object> report) throws DocumentException {
        Map<String, Object> cr = getMap(report, "credit_recommendation");
        Map<String, Object> meta = getMeta(report);
        addSectionHeader(document, "Section 20", "Credit Recommendation", safeGet(meta, "report_id"));

        document.add(new Paragraph("Recommended Limit: " + fmt(cr.get("recommended_limit_fmt")), FONT_VALUE));
        document.add(new Paragraph("Effective Limit: " + fmt(cr.get("effective_limit_fmt")), FONT_BODY));
        document.add(new Paragraph("Credit Period: " + fmt(cr.get("credit_days")) + " Days", FONT_BODY));
        document.add(new Paragraph("Recommended Action: " + fmt(cr.get("recommended_action")), FONT_BODY));

        Map<String, Object> breakdown = getMap(cr, "limit_breakdown");
        if (!breakdown.isEmpty()) {
            document.add(new Paragraph("Limit Breakdown", FONT_HEADING));
            PdfPTable b = new PdfPTable(2);
            b.setWidthPercentage(100);
            for (Map.Entry<String, Object> e : breakdown.entrySet()) {
                addKeyValueRow(b, toTitle(e.getKey()), fmt(e.getValue()));
            }
            document.add(b);
        }

        List<String> conditions = getStringList(cr, "conditions");
        if (!conditions.isEmpty()) {
            document.add(new Paragraph("Recommended Conditions", FONT_HEADING));
            for (String c : conditions) {
                document.add(new Paragraph("- " + c, FONT_BODY));
            }
        }

        String sysSum = safeGet(cr, "sys_credit_summary");
        if (!"???".equals(sysSum)) {
            document.add(createCalloutBox(sysSum, COLOR_PRIMARY, new Color(147, 242, 242)));
        }
    }

    private void addRiskFactors(Document document, Map<String, Object> report) throws DocumentException {
        Map<String, Object> rf = getMap(report, "risk_factors");
        Map<String, Object> meta = getMeta(report);
        addSectionHeader(document, "Section 21", "Risk Factors", safeGet(meta, "report_id"));

        List<String> items = getStringList(rf, "items");
        for (String item : items) {
            document.add(new Paragraph("• " + item, FONT_BODY));
        }
    }

    private void addStrengths(Document document, Map<String, Object> report) throws DocumentException {
        Map<String, Object> st = getMap(report, "strengths");
        Map<String, Object> meta = getMeta(report);
        addSectionHeader(document, "Section 22", "Strengths", safeGet(meta, "report_id"));

        List<String> items = getStringList(st, "items");
        for (String item : items) {
            document.add(new Paragraph("✓ " + item, new Font(Font.HELVETICA, 9, Font.NORMAL, COLOR_SECONDARY)));
        }
    }

    private void addFutureOutlook(Document document, Map<String, Object> report) throws DocumentException {
        Map<String, Object> fo = getMap(report, "future_outlook");
        Map<String, Object> meta = getMeta(report);
        addSectionHeader(document, "Section 23", "Future Outlook", safeGet(meta, "report_id"));

        document.add(new Paragraph("Outlook Rating: " + fmt(fo.get("outlook_rating")), FONT_HEADING));
        document.add(new Paragraph(fmt(fo.get("summary")), FONT_BODY));

        List<Map<String, Object>> indicators = getList(fo, "indicators");
        if (!indicators.isEmpty()) {
            List<String> headers = List.of("Indicator", "Status", "Note");
            List<List<String>> rows = indicators.stream().map(i -> List.of(
                    fmt(i.get("label")), fmt(i.get("status")), fmt(i.get("note"))))
                    .collect(Collectors.toList());
            addTable(document, headers, rows);
        }

        List<String> watch = getStringList(fo, "watch_points");
        if (!watch.isEmpty()) {
            document.add(new Paragraph("Watch Points", FONT_HEADING));
            for (String w : watch) {
                document.add(new Paragraph("- " + w, FONT_BODY));
            }
        }
    }

    private void addMethodology(Document document, Map<String, Object> report) throws DocumentException {
        Map<String, Object> m = getMap(report, "methodology");
        Map<String, Object> meta = getMeta(report);
        addSectionHeader(document, "Section 24", "Methodology", safeGet(meta, "report_id"));

        document.add(new Paragraph("Scoring Engine: " + fmt(m.get("scoring_engine")), FONT_BODY));
        List<Map<String, Object>> scale = getList(m, "grade_scale");
        if (!scale.isEmpty()) {
            List<String> headers = List.of("Grade", "Score Range", "Risk Level");
            List<List<String>> rows = scale.stream().map(g -> List.of(
                    fmt(g.get("grade")), fmt(g.get("range")), fmt(g.get("risk")))).collect(Collectors.toList());
            addTable(document, headers, rows);
        }
    }

    private void addDisclaimer(Document document, Map<String, Object> report) throws DocumentException {
        Map<String, Object> d = getMap(report, "disclaimer");
        Map<String, Object> meta = getMeta(report);
        addSectionHeader(document, "Section 25", "Disclaimer", safeGet(meta, "report_id"));

        document.add(new Paragraph(fmt(d.get("text")), FONT_SMALL));
    }

    private void addFinalConclusion(Document document, Map<String, Object> report) throws DocumentException {
        Map<String, Object> fc = getMap(report, "final_conclusion");
        Map<String, Object> meta = getMeta(report);
        addSectionHeader(document, "Section 26", "Final Conclusion", safeGet(meta, "report_id"));

        document.add(new Paragraph("Final Verdict:", FONT_HEADING));
        document.add(new Paragraph(fmt(fc.get("final_verdict")), FONT_BODY));
        document.add(Chunk.NEWLINE);
        document.add(new Paragraph(
                "Risk Score: " + fmt(fc.get("risk_score")) + "/100 (Grade " + fmt(fc.get("risk_grade")) + ")",
                FONT_BODY));
        document.add(
                new Paragraph("Decision Confidence: " + fmt(fc.get("decision_confidence_score")) + "%", FONT_BODY));
    }

    // ------------------------------------------------------------
    // Utility methods
    // ------------------------------------------------------------
    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> source, String key) {
        Object val = source.get(key);
        return (val instanceof Map) ? (Map<String, Object>) val : Map.of();
    }

    private Map<String, Object> getMeta(Map<String, Object> report) {
        Map<String, Object> meta = getMap(report, "meta");
        if (!meta.isEmpty()) {
            return meta;
        }
        return getMap(report, "_meta");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getList(Map<String, Object> source, String key) {
        Object val = source.get(key);
        return (val instanceof List) ? (List<Map<String, Object>>) val : List.of();
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<String, Object> source, String key) {
        Object val = source.get(key);
        return (val instanceof List) ? ((List<?>) val).stream().map(Object::toString).collect(Collectors.toList())
                : List.of();
    }

    private void addKeyValueRow(PdfPTable table, String key, String value) {
        PdfPCell keyCell = new PdfPCell(new Phrase(key, FONT_LABEL));
        keyCell.setBorder(Rectangle.NO_BORDER);
        keyCell.setPaddingBottom(5);
        table.addCell(keyCell);
        PdfPCell valCell = new PdfPCell(new Phrase(value, FONT_BODY));
        valCell.setBorder(Rectangle.NO_BORDER);
        valCell.setPaddingBottom(5);
        table.addCell(valCell);
    }

    private void addKpiCell(PdfPTable table, String label, String value, String sub) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(COLOR_BG_LIGHT);
        cell.setBorderColor(COLOR_BORDER);
        cell.setPadding(10);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.addElement(new Phrase(label, FONT_SMALL));
        cell.addElement(new Phrase(value, FONT_KPI_VALUE));
        if (sub != null) {
            cell.addElement(new Phrase(sub, FONT_SMALL));
        }
        table.addCell(cell);
    }

    private void addPLRow(PdfPTable table, String label, Map<String, Object> data, String key, List<String> years) {
        table.addCell(new Phrase(label, FONT_BODY));
        for (String y : years) {
            Object val = data.get(y);
            String display = "—";
            if (val != null) {
                try {
                    double d = Double.parseDouble(val.toString());
                    double cr = d / 1_00_00_000;
                    display = String.format("INR %.2f Cr", cr);
                } catch (NumberFormatException e) {
                    display = val.toString();
                }
            }
            table.addCell(new Phrase(display, FONT_BODY));
        }
    }

    private PdfPTable createCalloutBox(String text, Color bgColor, Color textColor) {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell(new Phrase(text, new Font(Font.HELVETICA, 7.5f, Font.NORMAL, textColor)));
        cell.setBackgroundColor(bgColor);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(8);
        table.addCell(cell);
        table.setSpacingBefore(10);
        table.setSpacingAfter(10);
        return table;
    }

    private String extractFirstEmail(Map<String, Object> contact) {
        List<Map<String, Object>> emails = getList(contact, "all_emails");
        if (!emails.isEmpty()) {
            return fmt(emails.get(0).get("emailId"));
        }
        return "—";
    }

    private String extractFirstPhone(Map<String, Object> contact) {
        List<Map<String, Object>> phones = getList(contact, "all_phones");
        if (!phones.isEmpty()) {
            return fmt(phones.get(0).get("phoneNumber"));
        }
        return "—";
    }
    private String fallback(String preferred, String backup) {
        if (preferred != null && !preferred.isBlank() && !"???".equals(preferred)) {
            return preferred;
        }
        return backup;
    }

    private String toTitle(String raw) {
        if (raw == null || raw.isBlank()) {
            return "???";
        }
        String[] parts = raw.replace('_', ' ').split("\s+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                out.append(part.substring(1));
            }
        }
        return out.toString();
    }

}
