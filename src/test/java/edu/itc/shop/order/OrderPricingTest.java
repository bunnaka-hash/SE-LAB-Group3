package edu.itc.shop.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Plain unit test: no Spring, no database. */
class OrderPricingTest {

    private static OrderLine line(int qty, String unitPrice) {
        return new OrderLine(1L, qty, new BigDecimal(unitPrice));
    }

    @Test
    void netIsSumOfUnitPriceTimesQuantity() {
        var lines = List.of(line(2, "49.90"), line(1, "10.00"));

        assertThat(OrderPricing.net(lines)).isEqualByComparingTo("109.80");
    }

    @Test
    void totalAddsTaxAndRoundsHalfUpToCents() {
        // 2 x 49.50 + 1 x 9.95 = 108.95; x 1.10 = 119.845 -> 119.85
        var lines = List.of(line(2, "49.50"), line(1, "9.95"));

        BigDecimal total = OrderPricing.total(lines, new BigDecimal("0.10"));

        assertThat(total).isEqualTo(new BigDecimal("119.85"));
        assertThat(total.scale()).isEqualTo(2);
    }

    @Test
    void roundingHappensOnceOnTheTotalNotPerLine() {
        // per line: 0.35 x 1.10 = 0.385 -> 0.39, three times = 1.17
        // once:     1.05 x 1.10 = 1.155 -> 1.16
        var lines = List.of(line(1, "0.35"), line(1, "0.35"), line(1, "0.35"));

        assertThat(OrderPricing.total(lines, new BigDecimal("0.10"))).isEqualTo(new BigDecimal("1.16"));
    }

    @Test
    void zeroTaxKeepsNetPrice() {
        var lines = List.of(line(3, "7.50"));

        assertThat(OrderPricing.total(lines, BigDecimal.ZERO)).isEqualTo(new BigDecimal("22.50"));
    }
}
