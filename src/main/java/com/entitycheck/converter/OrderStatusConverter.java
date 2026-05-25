package com.entitycheck.converter;

import com.entitycheck.model.OrderStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converter to handle migration from old CREDIT_REPORT_* enum values to new REPORT_* values.
 * This allows the application to read old database values and convert them to new enum values.
 */
@Converter(autoApply = true)
public class OrderStatusConverter implements AttributeConverter<OrderStatus, String> {

    @Override
    public String convertToDatabaseColumn(OrderStatus attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name();
    }

    @Override
    public OrderStatus convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }

        // Handle legacy database values
        String normalizedValue = dbData;
        
        // Map old CREDIT_REPORT_* values to new REPORT_* values
        if (dbData.equals("CREDIT_REPORT_GENERATION_IN_PROGRESS")) {
            normalizedValue = "REPORT_GENERATION_IN_PROGRESS";
        } else if (dbData.equals("CREDIT_REPORT_GENERATED")) {
            normalizedValue = "REPORT_GENERATED";
        } else if (dbData.equals("CREDIT_REPORT_GENERATION_FAILED")) {
            normalizedValue = "REPORT_GENERATION_FAILED";
        }

        try {
            return OrderStatus.valueOf(normalizedValue);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown OrderStatus value: " + dbData, e);
        }
    }
}
