package edu.itc.shop.domain;
import jakarta.validation.constraints.Min;

public class Line {
    private long productId;
    private @Min(1) int quantity;

    public Line() {
    }

    public Line(long productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public long getProductId() {
        return productId;
    }

    public void setProductId(long productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}

