package edu.itc.shop.domain;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity

public class Tag {
    // SEQUENCE, matching V1__init.sql (tag_seq INCREMENT BY 50); tag.id is not an identity column
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tag_seq")
    @SequenceGenerator(name = "tag_seq", sequenceName = "tag_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @ManyToMany(mappedBy = "tags")
    private Set<Product> products = new HashSet<>();

    protected Tag() {
    }

    public Tag(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Set<Product> getProducts() {
        return Set.copyOf(products);
    }

    void addProduct(Product product) {
        products.add(product);
    }

    @Override
    public boolean equals(Object o) {
        return this == o ||
                (o instanceof Tag other &&
                        id != null && id.equals(other.id));
    }

    @Override
    public int hashCode() {
        return Tag.class.hashCode();
    }

    @Override
    public String toString() {
        return "Tag[id=%d, name=%s]".formatted(id, name);
    }
}
