package edu.itc.shop.api.dto;

import edu.itc.shop.domain.Money;

public record ProductV2(
        long id,
        String name,
        String sku,
        Money price,
        int stock,
        boolean available
) {}