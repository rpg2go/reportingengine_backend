package com.reporting.it.service;

import com.reporting.it.BaseIT;
import com.reporting.domain.Report;
import com.reporting.dto.ColumnDefDto;
import com.reporting.dto.Enums;
import com.reporting.dto.ReportConfigDto;
import com.reporting.dto.ReportRowDto;
import com.reporting.dto.MeasureDefinitionDTO;
import com.reporting.repository.ReportRepository;
import com.reporting.service.ReportConfigService;
import com.reporting.service.ReportCloneService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReportCloneService Integration Tests")
@Transactional
public class ReportCloneServiceIT extends BaseIT {

    @Autowired
    private ReportConfigService configService;

    @Autowired
    private ReportCloneService cloneService;

    @Autowired
    private ReportRepository reportRepository;

    @Test
    @DisplayName("Should successfully clone a complex report from database and verify isolation")
    public void testCloneReportIntegration() {
        // Arrange: Setup source report
        String sourceId = "RPT_TO_CLONE";
        List<ColumnDefDto> columns = List.of(
                new ColumnDefDto("C1", "Net Value", Enums.ColType.WTD, 0, null, null, 1)
        );
        List<ReportRowDto> rows = List.of(
                new ReportRowDto("R1", sourceId, "Total Revenue", Enums.RowType.data,
                        new MeasureDefinitionDTO("raw", null, null, null, "SUM(amount)"), null, "normal", 0, 1,
                        Set.of("C1"), null)
        );
        ReportConfigDto sourceConfig = new ReportConfigDto(
                sourceId, "Source Template Report", columns, rows, LocalDate.of(2026, 5, 26), 1,
                Enums.ReportStatus.draft, "weekly", null, null, false, null, null
        );
        sourceConfig.setSourceTable("analytics.fact_sales");
        configService.saveToDb(sourceConfig);

        // Act: Clone the report
        String clonedName = "Cloned Template Copy";
        Report clonedReport = cloneService.cloneReport(sourceId, clonedName);

        // Assert parent metadata
        assertThat(clonedReport).isNotNull();
        assertThat(clonedReport.getReportId()).isEqualTo("CLONED_TEMPLATE_COPY");
        assertThat(clonedReport.getReportName()).isEqualTo(clonedName);
        assertThat(clonedReport.getVersion()).isEqualTo(1);
        assertThat(clonedReport.getStatus()).isEqualTo("draft");

        // Assert isolation using configService
        ReportConfigDto loadedClone = configService.loadFromDb("CLONED_TEMPLATE_COPY", LocalDate.of(2026, 5, 26));
        assertThat(loadedClone).isNotNull();
        assertThat(loadedClone.getReportName()).isEqualTo(clonedName);
        assertThat(loadedClone.getColumns()).hasSize(1);
        assertThat(loadedClone.getColumns().get(0).colId()).isEqualTo("C1");
        assertThat(loadedClone.getRows()).hasSize(1);
        assertThat(loadedClone.getRows().get(0).rowId()).isEqualTo("R1");

        // Mutate original source configuration and ensure clone remains untouched
        List<ColumnDefDto> mutatedColumns = List.of(
                new ColumnDefDto("C1", "Net Value", Enums.ColType.WTD, 0, null, null, 1),
                new ColumnDefDto("C2", "Added Column", Enums.ColType.CALC, 0, null, "C1 * 2", 2)
        );
        ReportConfigDto mutatedSource = new ReportConfigDto(
                sourceId, "Source Template Report", mutatedColumns, rows, LocalDate.of(2026, 5, 26), 1,
                Enums.ReportStatus.draft, "weekly", null, null, false, null, null
        );
        mutatedSource.setSourceTable("analytics.fact_sales");
        configService.saveToDb(mutatedSource);

        // Verify clone is unmodified
        ReportConfigDto loadedCloneAfterMutation = configService.loadFromDb("CLONED_TEMPLATE_COPY", LocalDate.of(2026, 5, 26));
        assertThat(loadedCloneAfterMutation.getColumns()).hasSize(1);
    }
}
