package com.vault.resilience;

import java.time.Duration;

/**
 * THE FLAW:
 * 1. Inga timeouts: Om den externa tjänsten hänger i 60 sekunder blockeras den anropande tråden i 60s.
 * 2. Inga retries eller kretsbrytare: Vid transienta nätverksfel smäller det direkt med 500.
 * 3. Thread Starvation: När 200 samtidiga användare anropar metoden är Tomcats trådpool slut.
 */
public class UnprotectedPaymentClient {

    public interface RemoteGateway {
        String process(String transactionId) throws Exception;
    }

    private final RemoteGateway remoteGateway;

    public UnprotectedPaymentClient(RemoteGateway remoteGateway) {
        this.remoteGateway = remoteGateway;
    }

    public String pay(String transactionId) throws Exception {
        // Blockerande anrop utan timeout, kretsbrytare eller fallback
        return remoteGateway.process(transactionId);
    }
}
