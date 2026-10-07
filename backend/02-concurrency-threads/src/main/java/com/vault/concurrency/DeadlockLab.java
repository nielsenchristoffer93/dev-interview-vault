package com.vault.concurrency;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * TOPIC 1: Deadlocks & Lock Ordering
 *
 * THE FLAW:
 * Om Tråd 1 överför från Konto A till Konto B låser den A och sedan B.
 * Om Tråd 2 samtidigt överför från Konto B till Konto A låser den B och sedan A.
 * Detta bryter mot Coffmans villkor (Circular Wait) och orsakar DEADLOCK!
 *
 * THE FIX:
 * 1. Global Lock Ordering: Lås alltid i samma ordning (t.ex. lägst ID först).
 * 2. Timed tryLock: Använd ReentrantLock.tryLock(timeout) för att ge upp om låset inte kan tas.
 */
public class DeadlockLab {

    public static class Account {
        private final long id;
        private int balance;
        public final ReentrantLock lock = new ReentrantLock();

        public Account(long id, int balance) {
            this.id = id;
            this.balance = balance;
        }

        public long getId() {
            return id;
        }

        public int getBalance() {
            return balance;
        }

        public void withdraw(int amount) {
            this.balance -= amount;
        }

        public void deposit(int amount) {
            this.balance += amount;
        }
    }

    // THE FLAW: Låser i den ordning parametrarna skickas in -> Deadlock risk!
    public void transferUnsafe(Account from, Account to, int amount) {
        synchronized (from) {
            simulateSlowNetwork();
            synchronized (to) {
                from.withdraw(amount);
                to.deposit(amount);
            }
        }
    }

    // THE FIX A: Lock Ordering (Sortera efter ID)
    public void transferOrdered(Account from, Account to, int amount) {
        Account firstLock = from.getId() < to.getId() ? from : to;
        Account secondLock = from.getId() < to.getId() ? to : from;

        synchronized (firstLock) {
            synchronized (secondLock) {
                from.withdraw(amount);
                to.deposit(amount);
            }
        }
    }

    // THE FIX B: ReentrantLock med tryLock och timeout (Förhindrar evig blockering)
    public boolean transferWithTryLock(Account from, Account to, int amount, long timeoutMs) throws InterruptedException {
        long stopTime = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < stopTime) {
            if (from.lock.tryLock(50, TimeUnit.MILLISECONDS)) {
                try {
                    if (to.lock.tryLock(50, TimeUnit.MILLISECONDS)) {
                        try {
                            from.withdraw(amount);
                            to.deposit(amount);
                            return true; // Överföring lyckades
                        } finally {
                            to.lock.unlock();
                        }
                    }
                } finally {
                    from.lock.unlock();
                }
            }
            // Vänta en liten slumpmässig stund innan nytt försök (Jitter för att undvika livelock)
            Thread.sleep((long) (Math.random() * 20));
        }
        return false; // Timeade ut utan att fastna i deadlock
    }

    private void simulateSlowNetwork() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException ignored) {
        }
    }
}
