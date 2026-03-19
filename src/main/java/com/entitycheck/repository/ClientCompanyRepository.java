package com.entitycheck.repository;

import com.entitycheck.model.ClientCompany;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ClientCompanyRepository extends JpaRepository<ClientCompany, Long> {
    Optional<ClientCompany> findByName(String name);
}