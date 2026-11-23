package edu.itc.shop.api.params;

import edu.itc.shop.domain.Money;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;

public class ProductFilter {
    @QueryParam("q")
    public String q;
    @QueryParam("minPrice")
    public Money minPrice; // "5 EUR"
    @QueryParam("maxPrice")
    public Money maxPrice;
    @QueryParam("inStock")
    @DefaultValue("false")
    public boolean inStock;

    @QueryParam("sort")
    @DefaultValue("name,asc")
    @Pattern(regexp = "(name|price|stock),(asc|desc)", message = "use field,asc or field,desc with field one of name, price, stock")
    public String sort;

    @AssertTrue(message = "minPrice must be in EUR")
    public boolean isMinPriceEur() {
        return minPrice == null || "EUR".equals(minPrice.currency());
    }

    @AssertTrue(message = "maxPrice must be in EUR")
    public boolean isMaxPriceEur() {
        return maxPrice == null || "EUR".equals(maxPrice.currency());
    }

    public String sortField() {
        return sort.split(",")[0];
    }

    public boolean descending() {
        return sort.endsWith(",desc");
    }
}