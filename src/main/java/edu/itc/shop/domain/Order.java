package edu.itc.shop.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "orders")
public class Order {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orders_seq")
	@SequenceGenerator(name = "orders_seq", sequenceName = "orders_seq", allocationSize = 50)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "customer_id", nullable = false)
	private Customer customer;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OrderStatus status;

	@Column(name = "placed_at", nullable = false)
	private Instant placedAt;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<OrderLine> lines = new ArrayList<>();
	
	@Version
	@Column(nullable = false)
	private long version;

	protected Order() { }

	public Order(Customer customer, OrderStatus status, Instant placedAt) {
		this.customer = customer;
		this.status = status;
		this.placedAt = placedAt;
	}

	// convenience constructor 
	public Order(Customer customer) {
		this(customer, OrderStatus.NEW, Instant.now());
	}

	public OrderLine addLine(Product product, int quantity) {
		OrderLine line = new OrderLine(this, product, quantity, product.getPrice());
		lines.add(line);
		return line;
	}

	public void removeLine(OrderLine line) {
		lines.remove(line);
		line.setOrder(null);
	}

	public Money total() {
		return lines.stream()
				.map(l -> l.getUnitPrice().times(l.getQuantity()))
				.reduce(Money::plus)
				.orElseGet(() -> Money.eur("0.00"));
	}

	public List<OrderLine> getLines() {
		return Collections.unmodifiableList(lines);
	}

	public Long getId() {
		return id;
	}

	public Customer getCustomer() {
		return customer;
	}

	public OrderStatus getStatus() {
		return status;
	}

	public void setStatus(OrderStatus status) {
		this.status = status;
	}

	public Instant getPlacedAt() {
		return placedAt;
	}

	public long getVersion() {
		return version;
	}

	@Override
	public boolean equals(Object obj) {
		return this == obj || (obj instanceof Order other && id != null && id.equals(other.getId()));

	}

	@Override 
	public int hashCode() {
		return Order.class.hashCode();
	}

	@Override 
	public String toString() {
		return "Order[id=%d, status=%s, placedAt=%s]".formatted(id, status, placedAt);
	}
	

}
