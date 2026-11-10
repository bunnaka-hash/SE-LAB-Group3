package edu.itc.shop.web.dto;

import java.time.Instant;
import edu.itc.shop.domain.OrderStatus;

/** Closed interface projection: Hibernate selects only these columns. */
public interface OrderSummary {
    Long getId();
    OrderStatus getStatus();
    Instant getPlacedAt();
    CustomerName getCustomer();

    interface CustomerName {
        String getName();
    }
}
