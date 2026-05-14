package com.inventory.report.repository;

import com.inventory.report.entity.ReportData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReportRepository extends JpaRepository<ReportData, Long> {
    Optional<ReportData> findTopByReportNameOrderByGeneratedAtDesc(String reportName);
}
