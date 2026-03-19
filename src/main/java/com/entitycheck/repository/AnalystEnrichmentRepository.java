package com.entitycheck.repository;

import com.entitycheck.model.AnalystEnrichment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AnalystEnrichmentRepository extends JpaRepository<AnalystEnrichment, Long> {
    Optional<AnalystEnrichment> findByOrderId(Long orderId);
}
