package com.inventory.report.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_name", nullable = false)
    private String reportName;

    @Column(name = "data_json", nullable = false, columnDefinition = "JSON")
    private String dataJson;

    @Column(name = "generated_at", updatable = false)
    private Instant generatedAt;

    @PrePersist
    protected void onCreate() {
        generatedAt = Instant.now();
    }
}
