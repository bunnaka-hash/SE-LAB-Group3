
package edu.itc.shop.api.dto;

import java.math.BigDecimal;
import jakarta.xml.bind.annotation.*;
import edu.itc.shop.domain.Product;

@XmlRootElement(name = "product")
@XmlAccessorType(XmlAccessType.FIELD)
public class ProductDto {

    @XmlAttribute
    public long id;

    public String name;
    public String sku;
    public BigDecimal price;
    public int stock;

    public ProductDto() {
    }

    public static ProductDto from(Product p) {
        var d = new ProductDto();

        d.id = p.id();
        d.name = p.name();
        d.sku = p.sku();
        d.price = p.price();
        d.stock = p.stock();

        return d;
    }

    public Product toProduct(long id, int version) {
        return new Product(
            id,
            name,
            sku,
            price,
            stock,
            version
        );
    }
}
