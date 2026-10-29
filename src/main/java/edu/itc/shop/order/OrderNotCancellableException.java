package edu.itc.shop.order;

import edu.itc.shop.common.ConflictException;

public class OrderNotCancellableException extends ConflictException {

    public OrderNotCancellableException(long id, OrderStatus status) {
        super("Order " + id + " is " + status + "; only NEW orders can be cancelled");
    }
}
