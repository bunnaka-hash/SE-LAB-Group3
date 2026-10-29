package edu.itc.shop.order;

import edu.itc.shop.common.NotFoundException;

public class OrderNotFoundException extends NotFoundException {

    public OrderNotFoundException(long id) {
        super("Order not found: " + id);
    }
}
