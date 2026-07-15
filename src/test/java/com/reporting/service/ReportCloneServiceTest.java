package com.reporting.service;

import com.reporting.domain.*;
import com.reporting.dto.Enums;
import com.reporting.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportCloneService Unit Tests")
@SuppressWarnings({"null", "unchecked"})
public class ReportCloneServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private ColumnDefRepository columnDefRepository;
    @Mock private ReportRowRepository reportRowRepository;
    @Mock private RowMetricRepository rowMetricRepository;
    @Mock private RowFormulaRepository rowFormulaRepository;
    @Mock private RowColumnMapRepository rowColumnMapRepository;

    @InjectMocks
    private ReportCloneService reportCloneService;

    @Test
    @DisplayName("Should throw IllegalArgumentException if name is blank")
    public void cloneReport_blankName_shouldThrowException() {
        assertThatThrownBy(() -> reportCloneService.cloneReport("SRC_1", "   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("New report name cannot be empty");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException if new name already exists")
    public void cloneReport_duplicateName_shouldThrowException() {
        when(reportRepository.existsByReportNameAndDeletedFalse("Existing Report")).thenReturn(true);

        assertThatThrownBy(() -> reportCloneService.cloneReport("SRC_1", "Existing Report"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Should clone report and its children successfully")
    public void cloneReport_valid_shouldDeepCloneAllStructures() {
        String sourceId = "SRC_1";
        String newName = "New Cloned Report";
        String expectedId = "NEW_CLONED_REPORT";

        // Setup mock source report
        Report sourceReport = Report.builder()
            .reportId(sourceId)
            .version(2)
            .reportName("Original Report")
            .status("published")
            .granularity("branch_id")
            .quickFilters("[]")
            .generalFilters("[]")
            .build();

        when(reportRepository.existsByReportNameAndDeletedFalse(newName)).thenReturn(false);
        // Mock that the generated ID does not exist in db
        when(reportRepository.findById(new ReportPk(expectedId, 1))).thenReturn(Optional.empty());
        when(reportRepository.findFirstByReportIdAndDeletedFalseOrderByVersionDesc(sourceId)).thenReturn(Optional.of(sourceReport));

        // Mock saved report return
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Setup child lists
        ColumnDef col = ColumnDef.builder().colId("C1").label("Sales").colType("MTD").displayOrder(1).build();
        ReportRow row = ReportRow.builder().rowId("R1").label("Section Header").rowType("section").displayOrder(1).build();
        RowMetric metric = RowMetric.builder().rowId("R1").sqlExpr("SUM(amount)").build();
        RowFormula formula = RowFormula.builder().rowId("R2").formulaExpr("R1 * 0.1").build();
        RowColumnMap map = RowColumnMap.builder().rowId("R1").colId("C1").isEnabled(true).build();

        when(columnDefRepository.findByReportReportIdAndReportVersionOrderByDisplayOrderAsc(sourceId, 2)).thenReturn(List.of(col));
        when(reportRowRepository.findByReportIdAndVersionOrderByDisplayOrderAsc(sourceId, 2)).thenReturn(List.of(row));
        when(rowMetricRepository.findByReportIdAndVersion(sourceId, 2)).thenReturn(List.of(metric));
        when(rowFormulaRepository.findByReportIdAndVersion(sourceId, 2)).thenReturn(List.of(formula));
        when(rowColumnMapRepository.findByReportIdAndVersion(sourceId, 2)).thenReturn(List.of(map));

        // Call clone
        Report cloned = reportCloneService.cloneReport(sourceId, newName);

        // Assertions
        assertThat(cloned).isNotNull();
        assertThat(cloned.getReportId()).isEqualTo(expectedId);
        assertThat(cloned.getReportName()).isEqualTo(newName);
        assertThat(cloned.getStatus()).isEqualTo("draft");
        assertThat(cloned.getVersion()).isEqualTo(1);
        assertThat(cloned.getGranularity()).isEqualTo("branch_id");

        // Verify child saves occurred with correct parameters
        verify(columnDefRepository, times(1)).saveAll(argThat(list -> {
            List<ColumnDef> cols = (List<ColumnDef>) list;
            return cols.size() == 1 && cols.get(0).getReport().getReportId().equals(expectedId) && cols.get(0).getColId().equals("C1");
        }));

        verify(reportRowRepository, times(1)).saveAll(argThat(list -> {
            List<ReportRow> rows = (List<ReportRow>) list;
            return rows.size() == 1 && rows.get(0).getReportId().equals(expectedId) && rows.get(0).getVersion() == 1 && rows.get(0).getRowId().equals("R1");
        }));

        verify(rowMetricRepository, times(1)).saveAll(argThat(list -> {
            List<RowMetric> metrics = (List<RowMetric>) list;
            return metrics.size() == 1 && metrics.get(0).getReportId().equals(expectedId) && metrics.get(0).getVersion() == 1;
        }));

        verify(rowFormulaRepository, times(1)).saveAll(argThat(list -> {
            List<RowFormula> formulas = (List<RowFormula>) list;
            return formulas.size() == 1 && formulas.get(0).getReportId().equals(expectedId) && formulas.get(0).getVersion() == 1;
        }));

        verify(rowColumnMapRepository, times(1)).saveAll(argThat(list -> {
            List<RowColumnMap> maps = (List<RowColumnMap>) list;
            return maps.size() == 1 && maps.get(0).getReportId().equals(expectedId) && maps.get(0).getVersion() == 1;
        }));
    }
}
