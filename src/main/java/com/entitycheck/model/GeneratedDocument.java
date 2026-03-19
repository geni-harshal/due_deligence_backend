package com.entitycheck.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Entity
@Table(name = "generated_documents")
@Data
public class GeneratedDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private String documentType; // "due_diligence_report", "lien_report", etc.

    @Column(nullable = false)
    private String status; // "generating", "ready", "failed"

    @Column(columnDefinition = "TEXT")
    private String pdfBase64; // base64-encoded PDF content

    private String fileName;

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
