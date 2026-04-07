package com.entitycheck.repository;

import com.entitycheck.model.RawComprehensiveData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RawComprehensiveDataRepository extends JpaRepository<RawComprehensiveData, Long> {

    Optional<RawComprehensiveData> findTopByOrder_IdOrderByVersionDesc(Long orderId);

    List<RawComprehensiveData> findByOrder_IdOrderByVersionDesc(Long orderId);

    boolean existsByOrder_IdAndVersion(Long orderId, Integer version);
}