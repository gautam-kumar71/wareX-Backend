package com.inventory.report.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportDataTest {

    @Test
    void onCreate_setsGeneratedAt() {
        ReportData reportData = ReportData.builder()
                .reportName("DASHBOARD")
                .dataJson("{}")
                .build();

        reportData.onCreate();

        assertThat(reportData.getGeneratedAt()).isNotNull();
    }
}
