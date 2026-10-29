package edu.itc.shop.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Order arithmetic, kept free of Spring so it can be unit tested on its own. */
public final class OrderPricing {

    private OrderPricing() {
    }

    /** Sum of unit price x quantity, not rounded. */
    public static BigDecimal net(List<OrderLine> lines) {
        return lines.stream()
                .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.qty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** net x (1 + taxRate), rounded HALF_UP to cents once, on the total. */
    public static BigDecimal total(List<OrderLine> lines, BigDecimal taxRate) {
        return net(lines)
                .multiply(BigDecimal.ONE.add(taxRate))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
