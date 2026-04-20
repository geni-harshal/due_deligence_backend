package com.entitycheck.repository;

import com.entitycheck.model.CreditReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CreditReportRepository extends JpaRepository<CreditReport, Long> {
    Optional<CreditReport> findTopByOrderIdOrderByVersionDesc(Long orderId);
    List<CreditReport> findByOrderIdOrderByVersionDesc(Long orderId);
}