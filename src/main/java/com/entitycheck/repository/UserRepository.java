package com.entitycheck.repository;

import com.entitycheck.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // Simple method for authentication – no fetch needed
    Optional<User> findByEmail(String email);

    // Method that eagerly fetches clientCompany – used in controllers to avoid LazyInitializationException
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.clientCompany WHERE u.email = :email")
    Optional<User> findByEmailWithCompany(@Param("email") String email);
}