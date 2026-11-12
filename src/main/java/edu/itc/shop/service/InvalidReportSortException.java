package edu.itc.shop.service;

public class InvalidReportSortException extends RuntimeException {
    public InvalidReportSortException(String sort) {
        super("Unsupported report sort '" + sort + "'. Allowed: sku, name, amount, stock, tagCount");
    }
}
