package com.entitycheck.config;

import com.entitycheck.model.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

@Component
public class OrderStatusConstraintSync implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(OrderStatusConstraintSync.class);

    private final JdbcTemplate jdbcTemplate;

    public OrderStatusConstraintSync(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String allowedStatuses = Arrays.stream(OrderStatus.values())
                    .map(s -> "'" + s.name() + "'")
                    .collect(Collectors.joining(", "));

            jdbcTemplate.execute("ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check");
            jdbcTemplate.execute(
                    "ALTER TABLE orders ADD CONSTRAINT orders_status_check CHECK (status IN (" + allowedStatuses + "))");

            log.info("orders_status_check synced with {} statuses", OrderStatus.values().length);
        } catch (Exception ex) {
            log.warn("Could not sync orders_status_check: {}", ex.getMessage());
        }
    }
}
