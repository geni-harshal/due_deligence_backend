package com.entitycheck.model;

public enum OrderStatus {
    ORDER_PLACED,
    PENDING_DATA_FETCH,
    DATA_FETCHED,
    IN_PROGRESS,           // analyst enrichment
    MODEL_EXECUTED,        // decision models run (optional)
    REPORT_GENERATING,     // NEW: calling Python model
    REPORT_GENERATED,      // NEW: report JSON stored
    PDF_GENERATING,        // NEW: generating PDF
    PDF_GENERATED,         // NEW: PDF file ready
    COMPLETED,             // published to client
    CANCELLED
}