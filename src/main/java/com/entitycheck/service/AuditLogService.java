package com.entitycheck.service;

import com.entitycheck.model.AuditLog;
import com.entitycheck.model.Order;
import com.entitycheck.model.OrderStatus;
import com.entitycheck.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AuditLogService {
    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void logEvent(Order order, String action, String message, String actor, Map<String, Object> metadata) {
        save(order, action, null, null, message, actor, metadata);
    }

    public void logStatusChange(
            Order order,
            OrderStatus previousStatus,
            OrderStatus newStatus,
            String action,
            String message,
            String actor,
            Map<String, Object> metadata) {
        save(order, action, previousStatus, newStatus, message, actor, metadata);
    }

    private void save(
            Order order,
            String action,
            OrderStatus previousStatus,
            OrderStatus newStatus,
            String message,
            String actor,
            Map<String, Object> metadata) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setOrder(order);
            auditLog.setAction(action);
            auditLog.setPreviousStatus(previousStatus != null ? previousStatus.name() : null);
            auditLog.setNewStatus(newStatus != null ? newStatus.name() : null);
            auditLog.setMessage(message);
            auditLog.setActor(actor);
            if (metadata != null && !metadata.isEmpty()) {
                auditLog.setMetadata(objectMapper.writeValueAsString(metadata));
            }
            auditLogRepository.save(auditLog);
        } catch (Exception ex) {
            log.warn("Audit log write failed for order {} action {}: {}", order != null ? order.getId() : null, action,
                    ex.getMessage());
        }
    }
}
