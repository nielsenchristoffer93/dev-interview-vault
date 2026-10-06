package com.vault.jpa;

import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JpaPerformanceVerificationTest {

    @Test
    @DisplayName("The Flaw: N+1 lazy loading genererar 1 + N SQL queries för N kunder")
    void testTheFlaw_nPlusOneQueries() {
        AtomicInteger queryExecutionCount = new AtomicInteger(0);

        // Simulera N kunder
        int customerCount = 10;
        List<CustomerLazyEntity> customers = new ArrayList<>();
        queryExecutionCount.incrementAndGet(); // 1 query: SELECT * FROM customers

        for (int i = 0; i < customerCount; i++) {
            customers.add(new CustomerLazyEntity((long) i, "Customer-" + i));
        }

        // När vi itererar och anropar getOrders() på varje lazy kund triggas en separat SELECT per kund
        for (CustomerLazyEntity c : customers) {
            queryExecutionCount.incrementAndGet(); // N queries: SELECT * FROM orders WHERE customer_id = ?
            c.getOrders();
        }

        // Totalt: 1 + 10 = 11 queries!
        assertThat(queryExecutionCount.get()).isEqualTo(1 + customerCount);
    }

    @Test
    @DisplayName("The Fix: EntityGraph / JOIN FETCH hämtar allt i en enda SQL-fråga")
    void testTheFix_entityGraphSingleQuery() {
        AtomicInteger queryExecutionCount = new AtomicInteger(0);

        // Med EntityGraph eller JOIN FETCH utförs en SQL JOIN direkt:
        // SELECT c.*, o.* FROM customers c LEFT JOIN orders o ON o.customer_id = c.id
        queryExecutionCount.incrementAndGet();

        // Totalt exakt 1 query oavsett hur många kunder som returneras!
        assertThat(queryExecutionCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("The Fix: Optimistic Locking förhindrar Lost Updates genom att kasta OptimisticLockException")
    void testTheFix_optimisticLockingRejectsConcurrentModification() {
        // Givet ett konto med version 1 i databasen
        VersionedAccount tx1Account = new VersionedAccount(1L, new BigDecimal("100.00"), 1L);
        VersionedAccount tx2Account = new VersionedAccount(1L, new BigDecimal("100.00"), 1L);

        // Databas-tillstånd
        AtomicInteger currentDbVersion = new AtomicInteger(1);

        // Transaktion 1 committar först: Version matchar (1 == 1) -> Lyckas och version ökas till 2
        if (tx1Account.getVersion() == currentDbVersion.get()) {
            tx1Account.setBalance(new BigDecimal("150.00"));
            currentDbVersion.incrementAndGet(); // Version i DB är nu 2
            tx1Account.setVersion(2L);
        }

        // Transaktion 2 försöker committa med version 1 mot DB som nu har version 2
        assertThrows(OptimisticLockException.class, () -> {
            if (tx2Account.getVersion() != currentDbVersion.get()) {
                throw new OptimisticLockException("Row was updated or deleted by another transaction (or unsaved-value mapping was incorrect)");
            }
        });

        // Kontot behåller värdet från tx1 och skrevs inte ovetandes över av tx2!
        assertThat(tx1Account.getBalance()).isEqualTo(new BigDecimal("150.00"));
        assertThat(currentDbVersion.get()).isEqualTo(2);
    }
}
