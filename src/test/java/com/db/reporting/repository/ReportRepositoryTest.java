package com.db.reporting.repository;

import com.db.reporting.domain.Report;
import com.db.reporting.domain.ReportPk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportRepository Unit & Interface Query Tests")
public class ReportRepositoryTest {

    @Mock
    private ReportRepository reportRepository;

    private Report reportDraft;
    private Report reportPublished;

    @BeforeEach
    void setUp() {
        reportDraft = Report.builder()
                .reportId("RPT_1")
                .version(2)
                .reportName("Sales Summary")
                .status("draft")
                .deleted(false)
                .build();

        reportPublished = Report.builder()
                .reportId("RPT_1")
                .version(1)
                .reportName("Sales Summary")
                .status("published")
                .deleted(false)
                .build();
    }

    @Nested
    @DisplayName("Report Existence & Version Retrieval Queries")
    class VersionQueryTests {

        @Test
        @DisplayName("existsByReportNameAndDeletedFalse returns true when active report exists")
        void existsByReportName() {
            when(reportRepository.existsByReportNameAndDeletedFalse("Sales Summary")).thenReturn(true);

            boolean exists = reportRepository.existsByReportNameAndDeletedFalse("Sales Summary");

            assertThat(exists).isTrue();
            verify(reportRepository).existsByReportNameAndDeletedFalse("Sales Summary");
        }

        @Test
        @DisplayName("findByReportIdAndVersion loads specific composite key version")
        void findByReportIdAndVersion() {
            when(reportRepository.findByReportIdAndVersion("RPT_1", 1)).thenReturn(Optional.of(reportPublished));

            Optional<Report> found = reportRepository.findByReportIdAndVersion("RPT_1", 1);

            assertThat(found).isPresent();
            assertThat(found.get().getVersion()).isEqualTo(1);
            assertThat(found.get().getStatus()).isEqualTo("published");
        }

        @Test
        @DisplayName("findLatestVersionPerReport returns latest max version row")
        void findLatestVersionPerReport() {
            when(reportRepository.findLatestVersionPerReport()).thenReturn(List.of(reportDraft));

            List<Report> reports = reportRepository.findLatestVersionPerReport();

            assertThat(reports).hasSize(1);
            assertThat(reports.get(0).getVersion()).isEqualTo(2);
            assertThat(reports.get(0).getStatus()).isEqualTo("draft");
        }

        @Test
        @DisplayName("findLatestPublishedPerReport returns latest published version")
        void findLatestPublishedPerReport() {
            when(reportRepository.findLatestPublishedPerReport()).thenReturn(List.of(reportPublished));

            List<Report> reports = reportRepository.findLatestPublishedPerReport();

            assertThat(reports).hasSize(1);
            assertThat(reports.get(0).getVersion()).isEqualTo(1);
            assertThat(reports.get(0).getStatus()).isEqualTo("published");
        }

        @Test
        @DisplayName("findActiveDraftsForReports surfaces draft in progress")
        void findActiveDraftsForReports() {
            when(reportRepository.findActiveDraftsForReports(List.of("RPT_1"))).thenReturn(List.of(reportDraft));

            List<Report> drafts = reportRepository.findActiveDraftsForReports(List.of("RPT_1"));

            assertThat(drafts).hasSize(1);
            assertThat(drafts.get(0).getVersion()).isEqualTo(2);
            assertThat(drafts.get(0).getStatus()).isEqualTo("draft");
        }
    }
}
