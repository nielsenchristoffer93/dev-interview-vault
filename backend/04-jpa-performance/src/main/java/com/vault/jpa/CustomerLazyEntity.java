package com.vault.jpa;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * THE FLAW (N+1 Query):
 * Standard @OneToMany med FetchType.LAZY.
 * När getOrders() anropas för 10 kunder genereras:
 * 1 SELECT för alla kunder + 10 individuella SELECT för orders = 11 queries.
 */
@Entity
@Table(name = "customers")
public class CustomerLazyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
    private List<OrderEntity> orders = new ArrayList<>();

    public CustomerLazyEntity() {}

    public CustomerLazyEntity(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public List<OrderEntity> getOrders() { return orders; }
    public void setOrders(List<OrderEntity> orders) { this.orders = orders; }
}
