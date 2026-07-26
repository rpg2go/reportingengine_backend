package com.reporting.service;

import com.reporting.domain.Report;
import com.reporting.domain.ReportPk;
import com.reporting.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VersioningService Unit Tests")
class VersioningServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private VersioningService service;

    private Report draftReport;
    private Report inReviewReport;
    private Report publishedReport;

    @BeforeEach
    void setUp() {
        service = new VersioningService(reportRepository, jdbcTemplate);

        draftReport = Report.builder()
                .reportId("RPT-001")
                .version(1)
                .reportName("Test Report")
                .status("draft")
                .deleted(false)
                .build();

        inReviewReport = Report.builder()
                .reportId("RPT-001")
                .version(1)
                .reportName("Test Report")
                .status("in_review")
                .deleted(false)
                .build();

        publishedReport = Report.builder()
                .reportId("RPT-001")
                .version(1)
                .reportName("Test Report")
                .status("published")
                .deleted(false)
                .build();
    }

    @Nested
    @DisplayName("Submit for Review Transition Tests")
    class SubmitForReviewTests {

        @Test
        @DisplayName("Should successfully transition status from draft to in_review")
        void submitDraftToInReview() {
            when(reportRepository.findById(new ReportPk("RPT-001", 1))).thenReturn(Optional.of(draftReport));
            when(reportRepository.save(any(Report.class))).thenAnswer(i -> i.getArgument(0));

            Report updated = service.submitForReview("RPT-001", 1);

            assertThat(updated.getStatus()).isEqualTo("in_review");
            verify(reportRepository).save(draftReport);
        }

        @Test
        @DisplayName("Should throw IllegalStateException when submitting non-draft report")
        void submitNonDraftThrows() {
            when(reportRepository.findById(new ReportPk("RPT-001", 1))).thenReturn(Optional.of(publishedReport));

            assertThatThrownBy(() -> service.submitForReview("RPT-001", 1))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot be submitted for review");
        }
    }

    @Nested
    @DisplayName("Reject to Draft Transition Tests")
    class RejectToDraftTests {

        @Test
        @DisplayName("Should successfully transition status from in_review back to draft")
        void rejectInReviewToDraft() {
            when(reportRepository.findById(new ReportPk("RPT-001", 1))).thenReturn(Optional.of(inReviewReport));
            when(reportRepository.save(any(Report.class))).thenAnswer(i -> i.getArgument(0));

            Report updated = service.rejectToDraft("RPT-001", 1);

            assertThat(updated.getStatus()).isEqualTo("draft");
            verify(reportRepository).save(inReviewReport);
        }

        @Test
        @DisplayName("Should throw IllegalStateException when rejecting report that is not in_review")
        void rejectNonInReviewThrows() {
            when(reportRepository.findById(new ReportPk("RPT-001", 1))).thenReturn(Optional.of(draftReport));

            assertThatThrownBy(() -> service.rejectToDraft("RPT-001", 1))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot be rejected to draft");
        }
    }

    @Nested
    @DisplayName("Publish and Fork Lifecycle Tests")
    class PublishAndForkTests {

        @Test
        @DisplayName("Should publish report version successfully")
        void publishReport() {
            when(reportRepository.findById(new ReportPk("RPT-001", 1))).thenReturn(Optional.of(draftReport));
            when(reportRepository.save(any(Report.class))).thenAnswer(i -> i.getArgument(0));

            Report published = service.publish("RPT-001", 1);

            assertThat(published.getStatus()).isEqualTo("published");
        }

        @Test
        @DisplayName("Should throw IllegalStateException when publishing already published version")
        void publishAlreadyPublishedThrows() {
            when(reportRepository.findById(new ReportPk("RPT-001", 1))).thenReturn(Optional.of(publishedReport));

            assertThatThrownBy(() -> service.publish("RPT-001", 1))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Report version is already PUBLISHED");
        }

        @Test
        @DisplayName("Should fork published report and clone child records via JDBC")
        void forkPublishedReport() {
            when(reportRepository.findById(new ReportPk("RPT-001", 1))).thenReturn(Optional.of(publishedReport));
            when(reportRepository.findByReportIdAndDeletedFalseOrderByVersionDesc("RPT-001"))
                    .thenReturn(List.of(publishedReport));
            when(reportRepository.saveAndFlush(any(Report.class))).thenAnswer(i -> i.getArgument(0));

            Report newDraft = service.fork("RPT-001", 1);

            assertThat(newDraft.getReportId()).isEqualTo("RPT-001");
            assertThat(newDraft.getVersion()).isEqualTo(2);
            assertThat(newDraft.getStatus()).isEqualTo("draft");

            // Verify 5 JDBC updates for child table cloning
            verify(jdbcTemplate, times(5)).update(anyString(), eq(2), eq("RPT-001"), eq(1));
        }
    }
}
