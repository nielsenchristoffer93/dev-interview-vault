package com.vault.testing;

import java.math.BigDecimal;

public class OrderService {

    public interface OrderRepository {
        boolean existsById(String id);
        void save(String id, BigDecimal amount);
    }

    public interface PaymentGateway {
        boolean charge(String orderId, BigDecimal amount);
    }

    private final OrderRepository repository;
    private final PaymentGateway paymentGateway;

    public OrderService(OrderRepository repository, PaymentGateway paymentGateway) {
        this.repository = repository;
        this.paymentGateway = paymentGateway;
    }

    public boolean placeOrder(String orderId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (repository.existsById(orderId)) {
            throw new IllegalStateException("Order already exists");
        }

        boolean charged = paymentGateway.charge(orderId, amount);
        if (charged) {
            repository.save(orderId, amount);
            return true;
        }
        return false;
    }
}
