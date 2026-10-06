# 💳 idempotency-patterns: Idempotency Keys & Distributed Locks

## 1. Problem: Vad kraschar eller varför bryr vi oss?
I distribuerade system och vid hantering av finansiella transaktioner är nätverket opålitligt.
Ett vanligt scenario:
* En användare klickar på "Betala 500 kr".
* Backend tar emot anropet och drar pengarna från användarens bankkonto via Stripe/Adyen.
* På vägen tillbaka från din server till användarens webbläsare tappar mobilmasten kontakten under 2 sekunder. Webbläsaren får en nätverkstimeout.
* Klienten eller nätverkslagret utför en automatisk retry, eller så klickar den stressade användaren på "Betala" en gång till.
* **Katastrof:** Backend tar emot anrop 2 som en ny transaktion och drar ytterligare 500 kr (**Double-Charging Problem**).

---

## 2. Keyword Bingo (Facit-lingo)
* **Idempotens:** Egenskapen att en operation ger exakt samma slutresultat i systemet oavsett om den körs en gång eller tio gånger ($f(f(x)) = f(x)$).
* **Idempotency Key (Idempotensnyckel):** En unik UUID som klienten genererar och skickar i HTTP-headern `Idempotency-Key: <uuid>`.
* **At-Least-Once Delivery:** I distribuerade köer och webhooks levereras meddelanden minst en gång. Konsumenter **måste** använda idempotens för att filtrera dubbletter.
* **Distributed Lock (Distribuerat lås):** Ett lås som koordineras över flera servernoder, vanligtvis i Redis via atomära `SET key value NX PX <ttl>`.
* **Redlock Algoritm:** Martin Kleppmann vs Salvatore Sanfilippo-debatten om distribuerad låsning över flera oberoende Redis-masters.
* **Fencing Token:** Ett monotont ökande nummer som returneras av låshanteraren för att förhindra att en klient vars lås löpt ut (pga GC-paus) skriver över nyare tillstånd i databasen.

---

## 3. Verklighets-svar (30–60 sek)
> *"För icke-idempotenta mutationer som betalningar kräver vi alltid en `Idempotency-Key` (UUID) i request-headern. När anropet når backend gör vi ett atomärt förvärv i Redis med `SET key "PROCESSING" NX EX 120`. Om nyckeln redan finns vet vi att det är en dubblett eller ett pågående anrop; då svarar vi antingen med 409 Conflict eller inväntar och returnerar det tidigare cachade svaret. När transaktionen lyckats sparas status och respons under samma nyckel med exempelvis 24 timmars TTL. Om klienten gör retry skickar vi direkt tillbaka 200 OK med det sparade svaret utan att debitera kontot igen."*

---

## 4. Felsökningsguide (Kommandon & Verktyg)

### A. Inspektera Idempotensnycklar i Redis CLI
```bash
# Anslut till Redis
redis-cli -p 6379

# Leta efter aktiva idempotensnycklar
KEYS "idempotency:*"

# Se TTL och status för en transaktion
TTL "idempotency:order-999-uuid"
GET "idempotency:order-999-uuid"
```

---

## 5. Koden & Verifiering

* **The Flaw:** [FlawedPaymentEndpoint.java](file:///system-design/idempotency-patterns/FlawedPaymentEndpoint.java) – Drar pengar blint vid varje inkommande anrop; debiterar kunden dubbelt vid retries.
* **The Fix:** [IdempotentPaymentHandler.java](file:///system-design/idempotency-patterns/IdempotentPaymentHandler.java) – Atomär kontroll via Redis `SET NX`, sparad respons och strikt dubbleringsskydd.
* **Verification Test:** [IdempotencyVerificationTest.java](file:///system-design/idempotency-patterns/IdempotencyVerificationTest.java) – Bevisar att 5 parallella anrop med samma Idempotency-Key resulterar i exakt 1 debitering.
