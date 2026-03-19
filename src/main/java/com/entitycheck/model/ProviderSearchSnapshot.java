package com.entitycheck.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Entity
@Table(name = "provider_search_snapshots")
@Data
public class ProviderSearchSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String rawResultsJson; // full Probe42 response

    @Column(columnDefinition = "TEXT")
    private String transformedReportJson; // transformed report for frontend

    private OffsetDateTime fetchedAt;

    @PrePersist
    protected void onCreate() {
        fetchedAt = OffsetDateTime.now();
    }
}
