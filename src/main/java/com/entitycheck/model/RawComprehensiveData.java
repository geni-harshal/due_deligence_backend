package com.entitycheck.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "raw_comprehensive_data_versions")
@Data
public class RawComprehensiveData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false, length = 50)
    private String provider = "PROBE42";

    @Column(nullable = false, length = 21)
    private String cin;

    @Column(length = 500)
    private String companyName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String rawJson;

    @Column(nullable = false)
    private OffsetDateTime fetchedAt;

    @Column(length = 255)
    private String fetchedBy;

    @PrePersist
    protected void onCreate() {
        if (fetchedAt == null) {
            fetchedAt = OffsetDateTime.now();
        }
    }
}
