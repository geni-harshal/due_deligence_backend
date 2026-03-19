package com.entitycheck.repository;

import com.entitycheck.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // Client: find all orders for a specific client company
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.clientCompany LEFT JOIN FETCH o.product WHERE o.clientCompany.id = :companyId ORDER BY o.createdAt DESC")
    List<Order> findByClientCompanyIdWithDetails(@Param("companyId") Long companyId);

    // Operations: find all orders with their associations
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.clientCompany LEFT JOIN FETCH o.product ORDER BY o.createdAt DESC")
    List<Order> findAllWithDetails();

    // Single order with associations
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.clientCompany LEFT JOIN FETCH o.product WHERE o.id = :id")
    Optional<Order> findByIdWithDetails(@Param("id") Long id);

    // Count by status for stats
    long countByClientCompanyId(Long clientCompanyId);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.clientCompany.id = :companyId AND o.status = :status")
    long countByClientCompanyIdAndStatus(@Param("companyId") Long companyId, @Param("status") com.entitycheck.model.OrderStatus status);
}
