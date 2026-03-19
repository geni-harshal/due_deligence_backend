package com.entitycheck.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Entity
@Table(name = "client_products")
@Data
public class ClientProduct {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "client_company_id", nullable = false)
    private ClientCompany clientCompany;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    private OffsetDateTime grantedAt;

    @PrePersist
    protected void onCreate() {
        grantedAt = OffsetDateTime.now();
    }
}