package edu.itc.shop.api.params;

import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;
import edu.itc.shop.domain.Money;

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
    @Pattern(regexp = "[a-z]+,(asc|desc)", message = "use field,asc or field,desc")
    public String sort;

    public String sortField() {
        return sort.split(",")[0];
    }

    public boolean descending() {
        return sort.endsWith(",desc");
    }
}