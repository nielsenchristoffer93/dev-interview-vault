package com.vault.jpa;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * THE FLAW (Lost Update):
 * Saknar @Version. Om två transaktioner läser saldot samtidigt
 * och uppdaterar oberoende av varandra kommer den sista skrivningen
 * skriva över den första ("Last Commit Wins") utan att upptäcka konflikten.
 */
@Entity
@Table(name = "unversioned_accounts")
public class UnversionedAccount {

    @Id
    private Long id;

    private BigDecimal balance;

    public UnversionedAccount() {}

    public UnversionedAccount(Long id, BigDecimal balance) {
        this.id = id;
        this.balance = balance;
    }

    public Long getId() { return id; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
}
