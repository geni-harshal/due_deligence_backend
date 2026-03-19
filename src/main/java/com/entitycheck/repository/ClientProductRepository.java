package com.entitycheck.repository;

import com.entitycheck.model.ClientProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ClientProductRepository extends JpaRepository<ClientProduct, Long> {

    @Query("SELECT cp FROM ClientProduct cp LEFT JOIN FETCH cp.clientCompany LEFT JOIN FETCH cp.product WHERE cp.clientCompany.id = :companyId")
    List<ClientProduct> findByClientCompanyId(@Param("companyId") Long companyId);
}
