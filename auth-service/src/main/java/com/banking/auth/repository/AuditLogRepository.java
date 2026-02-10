package com.banking.auth.repository;

import com.banking.auth.entity.AuditLog;
import com.banking.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    Page<AuditLog> findByEventTypeOrderByCreatedAtDesc(String eventType, Pageable pageable);

    @Query("SELECT al FROM AuditLog al WHERE al.createdAt BETWEEN :startDate AND :endDate ORDER BY al.createdAt DESC")
    List<AuditLog> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT al FROM AuditLog al WHERE al.user = :user AND al.eventType = :eventType ORDER BY al.createdAt DESC")
    List<AuditLog> findByUserAndEventType(
            @Param("user") User user,
            @Param("eventType") String eventType
    );

    @Query("SELECT COUNT(al) FROM AuditLog al WHERE al.user = :user AND al.eventType = :eventType AND al.success = false AND al.createdAt > :since")
    long countFailedAttemptsSince(
            @Param("user") User user,
            @Param("eventType") String eventType,
            @Param("since") LocalDateTime since
    );
}
