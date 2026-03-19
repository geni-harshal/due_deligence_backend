package com.entitycheck.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Entity
@Table(name = "analyst_enrichments")
@Data
public class AnalystEnrichment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(columnDefinition = "TEXT")
    private String investigationSummary;

    @Column(columnDefinition = "TEXT")
    private String analystComments;

    @Column(columnDefinition = "TEXT")
    private String redFlagsJson; // JSON array of strings

    @Column(columnDefinition = "TEXT")
    private String recommendationNotes;

    @Column(columnDefinition = "TEXT")
    private String decisionOutputsJson; // JSON of credit rating, risk score, etc.

    private boolean isDraft = true;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
