package com.vault.idempotency;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * THE FLAW:
 * Naiv betalningsendpoint.
 * Varje gång ett POST-anrop når metoden anropas PaymentGateway.
 * Om nätverket tappar svaret och klienten gör en retry debiteras kunden dubbelt!
 */
public class FlawedPaymentEndpoint {

    public interface BankClient {
        void debit(String customerId, BigDecimal amount);
    }

    private final BankClient bankClient;
    private final AtomicInteger totalDebitsPerformed = new AtomicInteger(0);

    public FlawedPaymentEndpoint(BankClient bankClient) {
        this.bankClient = bankClient;
    }

    public String handlePayment(String customerId, BigDecimal amount) {
        // FLAW: Ingen kontroll av tidigare transaktion eller Idempotency-Key
        bankClient.debit(customerId, amount);
        totalDebitsPerformed.incrementAndGet();
        return "SUCCESS: Debiterat " + amount;
    }

    public int getTotalDebitsPerformed() {
        return totalDebitsPerformed.get();
    }
}
