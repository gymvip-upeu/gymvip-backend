package com.gymvip.controller;

import com.gymvip.dto.attendance.AttendanceDtos.*;
import com.gymvip.service.AttendanceProcessingService;
import com.gymvip.service.AttendanceTerminalService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/attendance")
public class AttendanceTerminalController {

    private final AttendanceTerminalService terminalService;
    private final AttendanceProcessingService processingService;

    public AttendanceTerminalController(
            AttendanceTerminalService terminalService,
            AttendanceProcessingService processingService
    ) {
        this.terminalService = terminalService;
        this.processingService = processingService;
    }

    @GetMapping("/terminals")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCION')")
    public List<TerminalDto> listTerminals() {
        return terminalService.listTerminals();
    }

    @PostMapping("/terminals")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public TerminalDto createTerminal(@Valid @RequestBody TerminalCreateRequest request, Authentication auth) {
        return terminalService.createTerminal(request, auth.getName());
    }

    @PostMapping("/terminals/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCION')")
    public TerminalDto activateTerminal(@PathVariable UUID id, Authentication auth) {
        return terminalService.activateTerminal(id, auth.getName());
    }

    @PostMapping("/terminals/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCION')")
    public TerminalDto deactivateTerminal(@PathVariable UUID id, Authentication auth) {
        return terminalService.deactivateTerminal(id, auth.getName());
    }

    @GetMapping("/terminals/{id}/challenge")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCION')")
    public ChallengeResponse getChallenge(@PathVariable UUID id, Authentication auth) {
        return terminalService.generateChallenge(id, auth.getName());
    }

    @GetMapping({"/feed", "/terminals/{id}/feed"})
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCION')")
    public List<Map<String, Object>> getRecentFeed(
            @PathVariable(required = false) UUID id,
            @RequestParam(defaultValue = "15") int limit
    ) {
        return processingService.getRecentFeed(limit);
    }

    @GetMapping("/agenda")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCION')")
    public AgendaPageResponse getAgenda(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String query,
            @RequestParam(required = false, defaultValue = "TODOS") String method,
            @RequestParam(required = false, defaultValue = "TODOS") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        LocalDate effectiveFrom = from != null ? from : startDate;
        LocalDate effectiveTo = to != null ? to : endDate;
        return processingService.queryAttendanceAgenda(effectiveFrom, effectiveTo, query, method, status, page, size);
    }

    @GetMapping(value = "/agenda/export", produces = "text/csv")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> exportAgenda(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String query,
            @RequestParam(required = false, defaultValue = "TODOS") String method,
            @RequestParam(required = false, defaultValue = "TODOS") String status
    ) {
        LocalDate effectiveFrom = from != null ? from : startDate;
        LocalDate effectiveTo = to != null ? to : endDate;
        String csv = processingService.exportAttendanceCsv(effectiveFrom, effectiveTo, query, method, status);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"asistencias_" + LocalDate.now() + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }
}
