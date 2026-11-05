package edu.itc.shop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record Address(
		@Column(name = "street") String street,
		@Column(name = "city") String city,
		@Column(name = "zip") String zip) {
}
