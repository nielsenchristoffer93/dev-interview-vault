package com.vault.jpa;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * THE FIX (EntityGraph):
 * Genom att deklarera NamedEntityGraph eller använda JPQL "JOIN FETCH c.orders"
 * instrueras Hibernate att utföra en SQL LEFT JOIN och hämta kunden och alla ordrar
 * i ett enda databasanrop (1 query istället för 1 + N).
 */
@Entity
@Table(name = "customers_opt")
@NamedEntityGraph(
    name = "Customer.orders",
    attributeNodes = @NamedAttributeNode("orders")
)
public class CustomerOptimizedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
    private List<OrderEntity> orders = new ArrayList<>();

    public CustomerOptimizedEntity() {}

    public CustomerOptimizedEntity(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public List<OrderEntity> getOrders() { return orders; }
    public void setOrders(List<OrderEntity> orders) { this.orders = orders; }
}
