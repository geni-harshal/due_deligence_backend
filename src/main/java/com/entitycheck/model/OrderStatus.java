package com.entitycheck.model;

public enum OrderStatus {
    ORDER_PLACED,
    PENDING_DATA_FETCH,
    DATA_FETCHED,
    IN_PROGRESS,
    MODEL_EXECUTED,
    PDF_GENERATED,
    COMPLETED,
    CANCELLED
}