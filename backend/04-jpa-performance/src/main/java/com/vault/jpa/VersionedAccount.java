package com.vault.jpa;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * THE FIX (Optimistic Locking):
 * Fältet @Version tvingar Hibernate att inkludera version i WHERE-klausulen:
 * UPDATE versioned_accounts SET balance = ?, version = version + 1
 * WHERE id = ? AND version = ?
 *
 * Om en annan transaktion redan ändrat raden returnerar databasen 0 uppdaterade rader,
 * varpå Hibernate kastar OptimisticLockException. Lost Updates är därmed omöjliga!
 */
@Entity
@Table(name = "versioned_accounts")
public class VersionedAccount {

    @Id
    private Long id;

    private BigDecimal balance;

    @Version
    private Long version;

    public VersionedAccount() {}

    public VersionedAccount(Long id, BigDecimal balance, Long version) {
        this.id = id;
        this.balance = balance;
        this.version = version;
    }

    public Long getId() { return id; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
