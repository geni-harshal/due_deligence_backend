package com.entitycheck.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "orders")
@Data
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String orderNumber;

    @ManyToOne
    @JoinColumn(name = "client_company_id", nullable = false)
    private ClientCompany clientCompany;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    private String subjectName; // company name
    private String subjectType; // Company, LLP, Proprietorship

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String subjectDetails; // store as JSON (CIN, city, state, etc.)

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.ORDER_PLACED;

    @Enumerated(EnumType.STRING)
    private Priority priority = Priority.NORMAL;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String analystEnrichment; // JSON of notes, redFlags, decisionOutputs

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String providerSearchSnapshot; // raw Probe42 data

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String generatedDocuments; // array of {id, documentType, status, url}

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime completedAt;

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