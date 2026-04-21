package com.entitycheck.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Entity
@Table(name = "audit_logs")
@Data
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String action;
    private String entityType;
    private Long entityId;
    private String performedBy;
    private OffsetDateTime performedAt;

    @PrePersist
    protected void onCreate() {
        performedAt = OffsetDateTime.now();
    }
}