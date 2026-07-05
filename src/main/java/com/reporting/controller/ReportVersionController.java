package com.reporting.controller;

import com.reporting.domain.Report;
import com.reporting.repository.ReportRepository;
import com.reporting.service.VersioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Thin HTTP adapter for the report version lifecycle.
 *
 * <p>All business logic — state machine enforcement, cloning, and fork-guard
 * checks — lives in {@link VersioningService}. This controller's only job is
 * to translate HTTP parameters into service calls and map results or exceptions
 * to appropriate HTTP responses.</p>
 *
 * <h2>State machine</h2>
 * <pre>
 *   draft ──submit-review──▶ in_review ──publish──▶ published ──fork──▶ draft (next version)
 *           ◀──────reject──────────────
 * </pre>
 *
 * @see VersioningService
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/reports/{id}/version")
@RequiredArgsConstructor
public class ReportVersionController {

    private final ReportRepository reportRepository;
    private final VersioningService versioningService;

    /**
     * Lists all versions of a report, ordered from newest to oldest.
     *
     * @param id the report identifier
     * @return ordered list of {@link Report} entities
     */
    @GetMapping("/list")
    public ResponseEntity<List<Report>> listVersions(@PathVariable("id") String id) {
        return ResponseEntity.ok(reportRepository.findByReportIdAndDeletedFalseOrderByVersionDesc(id));
    }

    /**
     * Transitions a report version from {@code draft} to {@code in_review}.
     *
     * @param id      the report identifier
     * @param version the version number to submit
     * @return 200 OK with a status message, or 400 if the transition is invalid
     */
    @PostMapping("/submit-review")
    public ResponseEntity<?> submitForReview(
            @PathVariable("id") String id,
            @RequestParam("version") Integer version) {
        try {
            versioningService.submitForReview(id, version);
            return ResponseEntity.ok(Map.of("message", "Report submitted for review successfully.", "status", "in_review"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Transitions a report version from {@code in_review} back to {@code draft}.
     *
     * @param id      the report identifier
     * @param version the version number to reject
     * @return 200 OK with a status message, or 400 if the transition is invalid
     */
    @PostMapping("/reject")
    public ResponseEntity<?> rejectToDraft(
            @PathVariable("id") String id,
            @RequestParam("version") Integer version) {
        try {
            versioningService.rejectToDraft(id, version);
            return ResponseEntity.ok(Map.of("message", "Report rejected back to draft successfully.", "status", "draft"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Publishes a report version and auto-creates the next draft by cloning all
     * child records.
     *
     * @param id      the report identifier
     * @param version the version number to publish
     * @return 200 OK with published and next-draft version numbers, or 400 on invalid transition
     */
    @PostMapping("/publish")
    public ResponseEntity<?> publish(
            @PathVariable("id") String id,
            @RequestParam("version") Integer version) {
        try {
            versioningService.publish(id, version);
            return ResponseEntity.ok(Map.of(
                "message", "Report published successfully.",
                "publishedVersion", version
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Manually forks a published version into a new draft version.
     *
     * @param id      the report identifier
     * @param version the published version to fork from
     * @return 200 OK with the new draft version number, or 400 if fork is not allowed
     */
    @PostMapping("/fork")
    public ResponseEntity<?> fork(
            @PathVariable("id") String id,
            @RequestParam("version") Integer version) {
        try {
            Report newDraft = versioningService.fork(id, version);
            return ResponseEntity.ok(Map.of(
                "message", "New draft version v" + newDraft.getVersion() + " created successfully.",
                "nextDraftVersion", newDraft.getVersion()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
