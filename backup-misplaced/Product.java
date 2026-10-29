package edu;
import java.math.BigDecimal;

public record Product(
    Long id, 
    String name,
    BigDecimal price,
    int stock
) {
    
}
