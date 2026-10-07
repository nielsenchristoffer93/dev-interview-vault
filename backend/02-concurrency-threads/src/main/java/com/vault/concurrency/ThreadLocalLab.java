package com.vault.concurrency;

/**
 * TOPIC 3: ThreadLocal & Minnesläckor i Trådpooler
 *
 * THE FLAW:
 * I Spring Boot / Tomcat återanvänds trådar från en trådpool för att hantera
 * tusentals efterföljande HTTP-requests.
 * Om man sätter data i en ThreadLocal (t.ex. TenantId, UserId, SecurityContext, MDC)
 * och GLÖMMER att anropa .remove(), inträffar två allvarliga fel:
 * 1. Informationsläcka / Säkerhetshål: Nästa användare som tilldelas samma worker-tråd
 *    ärver föregående användares identitet eller behörighet!
 * 2. Minnesläcka (PermGen/OldGen OOM): Objekt som refereras av ThreadLocal-tabellen i tråden
 *    kan aldrig Garbage Collectas så länge tråden lever.
 *
 * THE FIX:
 * Alltid rensa i ett finally-block:
 *    try {
 *        context.set(user);
 *        process();
 *    } finally {
 *        context.remove(); // KRITISKT FÖR ATT UNDVIKA TRÅD-FÖRORENING!
 *    }
 * Alternativt använda AutoCloseable try-with-resources.
 */
public class ThreadLocalLab {

    public static class UserContextHolder {
        private static final ThreadLocal<String> currentUser = new ThreadLocal<>();

        public static void set(String user) {
            currentUser.set(user);
        }

        public static String get() {
            return currentUser.get();
        }

        public static void remove() {
            currentUser.remove();
        }

        // AutoCloseable mönster för snygg try-with-resources
        public static AutoCloseable withUser(String user) {
            set(user);
            return UserContextHolder::remove;
        }
    }

    // THE FLAW: Sätter kontext men rensar inte -> Förorenar nästa request på samma tråd!
    public void processUnsafe(String user) {
        UserContextHolder.set(user);
        // Utför arbete... och glömmer currentUser.remove()!
    }

    // THE FIX: Garanterad rensning via try-with-resources eller finally
    public void processSafe(String user, Runnable task) {
        try (AutoCloseable ignored = UserContextHolder.withUser(user)) {
            task.run();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
