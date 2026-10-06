package com.vault.jpa;

import jakarta.persistence.*;

@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private CustomerLazyEntity customer;

    public OrderEntity() {}

    public OrderEntity(Long id, String description) {
        this.id = id;
        this.description = description;
    }

    public Long getId() { return id; }
    public String getDescription() { return description; }
}
