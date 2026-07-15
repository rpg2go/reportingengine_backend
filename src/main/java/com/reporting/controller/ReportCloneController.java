package com.reporting.controller;

import com.reporting.domain.Report;
import com.reporting.service.ReportCloneService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportCloneController {

    private final ReportCloneService reportCloneService;

    /**
     * Endpoint to clone an existing report configuration template.
     *
     * @param id      the source report identifier to copy from
     * @param newName the user-specified title for the cloned report
     * @return 201 Created on success along with the new report metadata entity
     */
    @PostMapping("/{id}/clone")
    public ResponseEntity<Report> cloneReport(
            @PathVariable("id") String id,
            @RequestParam("newName") String newName) {
        log.info("REST request to clone report '{}' with new name '{}'", id, newName);
        Report clonedReport = reportCloneService.cloneReport(id, newName);
        return ResponseEntity.status(HttpStatus.CREATED).body(clonedReport);
    }
}
