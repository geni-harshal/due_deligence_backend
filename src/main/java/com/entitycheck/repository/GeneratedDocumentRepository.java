package com.entitycheck.repository;

import com.entitycheck.model.GeneratedDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, Long> {
    List<GeneratedDocument> findByOrderId(Long orderId);
    Optional<GeneratedDocument> findByOrderIdAndDocumentType(Long orderId, String documentType);
}
