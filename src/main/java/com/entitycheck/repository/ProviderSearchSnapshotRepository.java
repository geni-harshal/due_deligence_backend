// ============================================================
// FILE: src/main/java/com/entitycheck/repository/ProviderSearchSnapshotRepository.java
// ============================================================
package com.entitycheck.repository;

import com.entitycheck.model.ProviderSearchSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProviderSearchSnapshotRepository extends JpaRepository<ProviderSearchSnapshot, Long> {
    Optional<ProviderSearchSnapshot> findByOrderId(Long orderId);
}
