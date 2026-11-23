package edu.itc.shop.api.dto;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.itc.shop.domain.Line;
import edu.itc.shop.domain.Order;

public record OrderDto(
        String id,
        List<Line> lines,
        BigDecimal total,
        String status,
        Map<String, String> _links) {

    public static OrderDto from(Order order) {
        return new OrderDto(
                order.id(),
                order.lines(),
                order.total(),
                order.status().name(),
                Map.of());
    }

    public static OrderDto withLinks(Order order, String self,
                                     String pay, String cancel,
                                     Map<String, String> productLinks) {
        Map<String, String> links = new LinkedHashMap<>();
        links.put("self", self);
        if (order.status().name().equals("NEW")) {
            links.put("pay", pay);
            links.put("cancel", cancel);
        }
        for (Line line : order.lines()) {
            links.putIfAbsent("product:" + line.getProductId(),
                    productLinks.get("product:" + line.getProductId()));
        }
        return new OrderDto(order.id(), order.lines(), order.total(),
                order.status().name(), Map.copyOf(links));
    }
}
