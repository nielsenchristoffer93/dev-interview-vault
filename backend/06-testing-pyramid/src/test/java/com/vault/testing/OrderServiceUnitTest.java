package com.vault.testing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * PYRAMID BASE (Unit Test):
 * - Starttid: ~5 millisekunder.
 * - Ingen Spring-kontext, ingen I/O, inga nätverksanrop.
 * - Testar alla branchar av ren affärslogik.
 */
@ExtendWith(MockitoExtension.class)
public class OrderServiceUnitTest {

    @Mock
    private OrderService.OrderRepository repository;

    @Mock
    private OrderService.PaymentGateway paymentGateway;

    @InjectMocks
    private OrderService orderService;

    @Test
    @DisplayName("Unit: Lyckad orderplacering när betalning godkänns")
    void shouldPlaceOrderSuccessfully() {
        when(repository.existsById("ORD-1")).thenReturn(false);
        when(paymentGateway.charge("ORD-1", new BigDecimal("99.00"))).thenReturn(true);

        boolean result = orderService.placeOrder("ORD-1", new BigDecimal("99.00"));

        assertThat(result).isTrue();
        verify(repository).save("ORD-1", new BigDecimal("99.00"));
    }

    @Test
    @DisplayName("Unit: Kastar exception vid ogiltigt belopp utan att kontakta externa tjänster")
    void shouldRejectInvalidAmount() {
        assertThatThrownBy(() -> orderService.placeOrder("ORD-2", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(paymentGateway);
        verifyNoInteractions(repository);
    }
}
