package edu.itc.shop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "product")
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq")
	@SequenceGenerator(name = "product_seq", sequenceName = "product_seq", allocationSize = 50)
	private Long id;

	@Column(nullable = false, unique = true, length = 20)
	private String sku;

	@Column(nullable = false, length = 120)
	private String name;

	@Embedded
	private Money price;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Category category;

	@Column(nullable = false)
	private int stock;

	protected Product() { }

	public Product(String sku, String name, Money price, Category category, int stock) {
		this.sku = sku;
		this.name = name;
		this.price = price;
		this.category = category;
		this.stock = stock;
	}

	public Long getId() { return id; }
	public String getSku() { return sku; }
	public String getName() { return name; }
	public Money getPrice() { return price; }
	public Category getCategory() { return category; }
	public int getStock() { return stock; }
}
