package com.entitycheck.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DatabaseSchemaService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaService.class);
    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void createTableOnStartup() {
        log.info("=== Creating versioned comprehensive data table ===");

        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS raw_comprehensive_data_versions (
                    id BIGSERIAL PRIMARY KEY,
                    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
                    version INT NOT NULL,
                    provider VARCHAR(50) NOT NULL DEFAULT 'PROBE42',
                    cin VARCHAR(21) NOT NULL,
                    company_name VARCHAR(500),
                    raw_json JSONB NOT NULL,
                    transformed_json JSONB,
                    fetched_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                    fetched_by VARCHAR(255),
                    CONSTRAINT uq_raw_comprehensive_order_version UNIQUE (order_id, version)
                )
            """);

            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_raw_comprehensive_order_id ON raw_comprehensive_data_versions(order_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_raw_comprehensive_cin ON raw_comprehensive_data_versions(cin)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_raw_comprehensive_version ON raw_comprehensive_data_versions(version)");

            log.info("=== Versioned comprehensive data table ready ===");
        } catch (Exception e) {
            log.error("Error creating versioned comprehensive data table", e);
            throw new RuntimeException("Failed to create versioned comprehensive data table", e);
        }
    }
}