package edu.itc.shop.customer;

import edu.itc.shop.common.NotFoundException;

public class CustomerNotFoundException extends NotFoundException {

    public CustomerNotFoundException(long id) {
        super("Customer not found: " + id);
    }
}
