package edu.itc.shop.domain;

import java.math.BigDecimal;
import java.util.List;

public record Order(
        String id,
        List<Line> lines,
        BigDecimal total,
        OrderStatus status
) {
}