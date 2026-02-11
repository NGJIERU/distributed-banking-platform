package com.banking.auth.controller;

import com.banking.auth.entity.AuditLog;
import com.banking.auth.entity.User;
import com.banking.auth.repository.AuditLogRepository;
import com.banking.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
@Tag(name = "Admin Audit", description = "Admin endpoints for querying audit logs")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {

    private final AuditLogRepository auditLogRepository;
    private final UserService userService;

    @Operation(summary = "Get all audit logs", description = "Retrieve paginated audit logs")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Audit logs retrieved"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not authorized (admin only)")
    })
    @GetMapping
    public ResponseEntity<Page<AuditLogResponse>> getAllAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        log.info("Admin fetching audit logs: page={}, size={}", page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLog> auditLogs = auditLogRepository.findAll(pageable);
        
        return ResponseEntity.ok(auditLogs.map(this::toResponse));
    }

    @Operation(summary = "Get audit logs by event type", description = "Filter audit logs by event type")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Audit logs retrieved"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not authorized (admin only)")
    })
    @GetMapping("/by-type/{eventType}")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogsByType(
            @PathVariable String eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        log.info("Admin fetching audit logs by type: {}", eventType);
        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLog> auditLogs = auditLogRepository.findByEventTypeOrderByCreatedAtDesc(eventType, pageable);
        
        return ResponseEntity.ok(auditLogs.map(this::toResponse));
    }

    @Operation(summary = "Get audit logs by user", description = "Filter audit logs by user ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Audit logs retrieved"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not authorized (admin only)"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/by-user/{userId}")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogsByUser(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        log.info("Admin fetching audit logs for user: {}", userId);
        
        User user = userService.findById(userId)
                .orElse(null);
        
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        
        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLog> auditLogs = auditLogRepository.findByUserOrderByCreatedAtDesc(user, pageable);
        
        return ResponseEntity.ok(auditLogs.map(this::toResponse));
    }

    @Operation(summary = "Get audit logs by date range", description = "Filter audit logs by date range")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Audit logs retrieved"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not authorized (admin only)")
    })
    @GetMapping("/by-date-range")
    public ResponseEntity<List<AuditLogResponse>> getAuditLogsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        log.info("Admin fetching audit logs from {} to {}", startDate, endDate);
        List<AuditLog> auditLogs = auditLogRepository.findByDateRange(startDate, endDate);
        
        return ResponseEntity.ok(auditLogs.stream().map(this::toResponse).toList());
    }

    @Operation(summary = "Get audit statistics", description = "Get summary statistics of audit events")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statistics retrieved"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not authorized (admin only)")
    })
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getAuditStatistics() {
        log.info("Admin fetching audit statistics");
        
        long totalLogs = auditLogRepository.count();
        
        return ResponseEntity.ok(Map.of(
                "totalAuditLogs", totalLogs,
                "eventTypes", AuditLog.EventType.values()
        ));
    }

    private AuditLogResponse toResponse(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getEventType(),
                auditLog.getUser() != null ? auditLog.getUser().getId() : null,
                auditLog.getUser() != null ? auditLog.getUser().getEmail() : null,
                auditLog.getSuccess(),
                auditLog.getIpAddress(),
                auditLog.getUserAgent(),
                auditLog.getEventData(),
                auditLog.getCreatedAt()
        );
    }

    public record AuditLogResponse(
            UUID id,
            String eventType,
            UUID userId,
            String userEmail,
            Boolean success,
            String ipAddress,
            String userAgent,
            Map<String, Object> eventData,
            LocalDateTime createdAt
    ) {}
}
