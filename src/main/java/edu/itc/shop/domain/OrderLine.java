package edu.itc.shop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_line")
public class OrderLine {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_line_seq")
	@SequenceGenerator(name = "order_line_seq", sequenceName = "order_line_seq", allocationSize = 50)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private Order order;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(nullable = false)
	private int quantity;

	@Embedded
	private Money unitPrice;

	protected OrderLine() { }

	public OrderLine(Order order, Product product, int quantity, Money unitPrice) {
		this.order = order;
		this.product = product;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
	}

	public Long getId() { return id; }
	public Order getOrder() { return order; }
	void setOrder(Order order) { this.order = order; }
	public Product getProduct() { return product; }
	public int getQuantity() { return quantity; }
	public Money getUnitPrice() { return unitPrice; }
}
