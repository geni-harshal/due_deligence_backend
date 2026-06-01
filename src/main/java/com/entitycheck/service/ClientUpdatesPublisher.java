package com.entitycheck.service;

import com.entitycheck.model.Order;
import com.entitycheck.ws.UpdatesWebSocketHandler;
import org.springframework.stereotype.Service;

@Service
public class ClientUpdatesPublisher {

    private final UpdatesWebSocketHandler updatesWebSocketHandler;

    public ClientUpdatesPublisher(UpdatesWebSocketHandler updatesWebSocketHandler) {
        this.updatesWebSocketHandler = updatesWebSocketHandler;
    }

    public void publishOrderUpdate(Order order) {
        Long orderId = order != null ? order.getId() : null;
        String status = order != null && order.getStatus() != null ? order.getStatus().name().toLowerCase() : "unknown";
        String payload = String.format("{\"type\":\"order_update\",\"orderId\":%s,\"status\":\"%s\"}", orderId, status);
        updatesWebSocketHandler.broadcast(payload);
    }

    public void publishStatsUpdate() {
        updatesWebSocketHandler.broadcast("{\"type\":\"stats_update\"}");
    }
}
