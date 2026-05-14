package com.inventory.report.controller;

import com.inventory.report.dto.response.ApiResponse;
import com.inventory.report.dto.response.DashboardStats;
import com.inventory.report.service.DashboardStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Slf4j
public class ReportController {

    private final DashboardStatsService dashboardStatsService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardStats>> getDashboardStats() {
        try {
            DashboardStats stats = dashboardStatsService.loadDashboardStats();
            log.debug("Dashboard stats generated: warehouses={}, suppliers={}", stats.getTotalWarehouses(), stats.getActiveSuppliers());
            return ResponseEntity.ok(ApiResponse.success(stats));
        } catch (Exception e) {
            log.error("Failed to load dashboard stats", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(500, "Failed to parse dashboard data"));
        }
    }

    @GetMapping("/stock-movement-summary/pdf")
    public ResponseEntity<byte[]> downloadStockMovementSummaryPdf() {
        DashboardStats stats = dashboardStatsService.loadDashboardStats();
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        log.info("Generating stock movement summary PDF for {}", today);
        String reportText = String.join("\n",
                "WareX Stock Movement Summary",
                "Generated on: " + today,
                "",
                "Warehouse network: " + stats.getTotalWarehouses(),
                "Recorded shipments: " + stats.getShipments(),
                "Active suppliers: " + stats.getActiveSuppliers(),
                "Inventory valuation: INR " + String.format("%.2f", stats.getTotalValue()),
                "",
                "Summary:",
                "This snapshot reflects the latest inventory movements processed by the reporting service.",
                "Use it as an operational overview for current warehouse and supplier activity.");

        return buildDownloadResponse(
                buildSimplePdf(reportText),
                "stock-movement-summary-" + today + ".pdf",
                MediaType.APPLICATION_PDF
        );
    }

    @GetMapping("/supplier-performance/excel")
    public ResponseEntity<byte[]> downloadSupplierPerformanceExcel() {
        DashboardStats stats = dashboardStatsService.loadDashboardStats();
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        log.info("Generating supplier performance Excel for {}", today);
        String xml = """
                <?xml version="1.0"?>
                <Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet"
                 xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">
                  <Worksheet ss:Name="Supplier Performance">
                    <Table>
                      <Row>
                        <Cell><Data ss:Type="String">Metric</Data></Cell>
                        <Cell><Data ss:Type="String">Value</Data></Cell>
                      </Row>
                      <Row>
                        <Cell><Data ss:Type="String">Generated On</Data></Cell>
                        <Cell><Data ss:Type="String">%s</Data></Cell>
                      </Row>
                      <Row>
                        <Cell><Data ss:Type="String">Active Suppliers</Data></Cell>
                        <Cell><Data ss:Type="Number">%d</Data></Cell>
                      </Row>
                      <Row>
                        <Cell><Data ss:Type="String">Warehouse Coverage</Data></Cell>
                        <Cell><Data ss:Type="Number">%d</Data></Cell>
                      </Row>
                      <Row>
                        <Cell><Data ss:Type="String">Estimated Inventory Value</Data></Cell>
                        <Cell><Data ss:Type="Number">%.2f</Data></Cell>
                      </Row>
                      <Row>
                        <Cell><Data ss:Type="String">Operational Note</Data></Cell>
                        <Cell><Data ss:Type="String">Supplier coverage remains aligned with the current warehouse footprint.</Data></Cell>
                      </Row>
                    </Table>
                  </Worksheet>
                </Workbook>
                """.formatted(today, stats.getActiveSuppliers(), stats.getTotalWarehouses(), stats.getTotalValue());

        MediaType excelType = MediaType.parseMediaType("application/vnd.ms-excel");
        return buildDownloadResponse(
                xml.getBytes(StandardCharsets.UTF_8),
                "supplier-performance-" + today + ".xls",
                excelType
        );
    }

    @GetMapping("/financial-reconciliation/csv")
    public ResponseEntity<byte[]> downloadFinancialReconciliationCsv() {
        DashboardStats stats = dashboardStatsService.loadDashboardStats();
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        log.info("Generating financial reconciliation CSV for {}", today);
        String csv = String.join("\n",
                "metric,value",
                "generated_on," + today,
                "inventory_valuation," + String.format("%.2f", stats.getTotalValue()),
                "active_suppliers," + stats.getActiveSuppliers(),
                "shipments," + stats.getShipments(),
                "open_orders," + stats.getOpenOrders(),
                "warehouse_count," + stats.getTotalWarehouses(),
                "status,Reconciliation snapshot ready");

        MediaType csvType = MediaType.parseMediaType("text/csv");
        return buildDownloadResponse(
                csv.getBytes(StandardCharsets.UTF_8),
                "financial-reconciliation-" + today + ".csv",
                csvType
        );
    }

    private ResponseEntity<byte[]> buildDownloadResponse(byte[] content, String filename, MediaType mediaType) {
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(content);
    }

    private byte[] buildSimplePdf(String text) {
        String[] lines = text.split("\\R");
        StringBuilder body = new StringBuilder("BT\n/F1 12 Tf\n50 760 Td\n14 TL\n");
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                body.append("T*\n");
            }
            body.append("(").append(escapePdf(lines[i])).append(") Tj\n");
        }
        body.append("ET");

        String stream = body.toString();
        int streamLength = stream.getBytes(StandardCharsets.US_ASCII).length;
        String[] objects = {
                "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n",
                "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n",
                "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n",
                "4 0 obj\n<< /Length " + streamLength + " >>\nstream\n" + stream + "\nendstream\nendobj\n",
                "5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n"
        };

        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        int[] offsets = new int[objects.length + 1];
        for (int i = 0; i < objects.length; i++) {
            offsets[i + 1] = pdf.toString().getBytes(StandardCharsets.US_ASCII).length;
            pdf.append(objects[i]);
        }

        int xrefStart = pdf.toString().getBytes(StandardCharsets.US_ASCII).length;
        pdf.append("xref\n0 6\n");
        pdf.append("0000000000 65535 f \n");
        for (int i = 1; i < offsets.length; i++) {
            pdf.append(String.format("%010d 00000 n \n", offsets[i]));
        }
        pdf.append("trailer\n<< /Size 6 /Root 1 0 R >>\n");
        pdf.append("startxref\n").append(xrefStart).append("\n%%EOF");
        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private String escapePdf(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)");
    }
}
