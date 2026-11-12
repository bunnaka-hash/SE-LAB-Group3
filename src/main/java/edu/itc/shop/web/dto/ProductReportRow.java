package edu.itc.shop.web.dto;

import java.math.BigDecimal;

public record ProductReportRow(String sku, String name, BigDecimal amount, int stock, Long tagCount) { }
