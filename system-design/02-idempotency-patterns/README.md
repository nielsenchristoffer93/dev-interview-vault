# 💳 idempotency-patterns: Idempotency Keys & Distributed Locks

Denna modul täcker 3 fundamentala feltolerans- och transaktionsmönster i distribuerade och finansiella system. Varje scenario presenterar problemet (vad som kraschar och varför vi bryr oss), dess specifika **Keyword Bingo** (facit-lingo), samt ett **Verklighets-svar (30–60 sek)** skräddarsytt för tekniska intervjuer.

---

## 1. Koncept & Scenarier

### Scenario 1: Nätverkstimeouts & Double-Charging i betalningsflöden

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
I distribuerade system och vid hantering av finansiella transaktioner är nätverket fundamentalt opålitligt.

Ett klassiskt katastrofscenario:
1. En användare klickar på "Betala 500 kr".
2. Backend tar emot anropet och drar pengarna från användarens bankkonto via betalväxeln (Stripe/Adyen).
3. På vägen tillbaka från servern till användarens webbläsare klipper mobilnätet i 2 sekunder. Webbläsaren får en nätverkstimeout.
4. Klienten gör en automatisk retry, eller så klickar den stressade användaren på "Betala" en gång till.
5. **Katastrof:** Backend tar emot anrop 2 som en helt ny transaktion och drar ytterligare 500 kr (**The Double-Charging Problem**).

#### 2. Keyword Bingo (Facit-lingo)
* **Idempotens:** Egenskapen att en operation ger exakt samma systemtillstånd oavsett om den exekveras en gång eller tio gånger ($f(f(x)) = f(x)$).
* **Double-Charging Problem:** När nätverkstimeouts lurar klienten att göra retries som leder till duplicerade finansiella dragningar.
* **Idempotency Key:** En unik UUID som klienten genererar och skickar med i HTTP-headern `Idempotency-Key: <uuid>`.
* **At-Least-Once Delivery:** I nätverk och meddelandeköer levereras anrop minst en gång; mottagaren måste själv skydda sig mot dubbletter.

#### 3. Verklighets-svar (30–60 sek)
> *"För icke-idempotenta mutationer, som betalningar, kräver vi alltid en `Idempotency-Key` (UUID) i request-headern. Om ett nätverksfel inträffar och klienten gör retry vet vi tack vare nyckeln att det rör sig om samma transaktion. Vi kontrollerar nyckeln atomärt innan vi anropar betalväxeln. Om transaktionen redan har genomförts returnerar vi direkt det tidigare sparade svaret utan att debitera kontot igen. Det eliminerar helt risken för double-charging oavsett hur många retries som sker."*

**Labb & Kod:** [FlawedPaymentEndpoint.java](file:///system-design/idempotency-patterns/FlawedPaymentEndpoint.java) vs [IdempotentPaymentHandler.java](file:///system-design/idempotency-patterns/IdempotentPaymentHandler.java)

---

### Scenario 2: Atomär Idempotency Key i Redis (`SET NX EX`) & Resultat-caching

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
Hur förhindrar man att två parallella anrop med samma Idempotency-Key (t.ex. två blixtsnabba klick på millisekunder) båda hinner läsa att transaktionen inte är gjord och kör betalningen parallellt (**Check-then-Act Race Condition**)?

Att göra en vanlig `GET` följt av `SET` i Redis eller en databas skapar ett tidsfönster där båda anropen slinker igenom.

#### 2. Keyword Bingo (Facit-lingo)
* **`SET key value NX EX <seconds>`:** Atomär Redis-instruktion som enbart sätter värdet om nyckeln inte redan existerar (`NX`) och sätter en automatisk TTL (`EX`).
* **Transient State ("PROCESSING"):** Att markera nyckeln med status PROCESSING under tiden det externa betalanropet pågår.
* **Result Payload Caching:** Att spara det slutgiltiga JSON-svaret under samma nyckel (med t.ex. 24 timmars TTL) så att framtida retries omedelbart får identiskt svar.
* **HTTP 409 Conflict:** Statuskoden som returneras om ett andra anrop anländer medan det första fortfarande bearbetas.

#### 3. Verklighets-svar (30–60 sek)
> *"För att implementera idempotens trådsäkert i en distribuerad miljö använder vi Redis med kommandot `SET key "PROCESSING" NX EX 120`. Eftersom `NX` är atomärt i Redis lyckas exakt en nod att ta låset. Om anropet redan pågår svarar vi med 409 Conflict. När betalningen har slutförts uppdaterar vi nyckeln med det färdiga resultatet och en TTL på 24 timmar. Om klienten gör en retry inom ett dygn läser vi det sparade resultatet ur Redis och returnerar det på under 5 millisekunder utan något nytt anrop till betaltjänsten."*

**Labb & Kod:** [IdempotentPaymentHandler.java](file:///system-design/idempotency-patterns/IdempotentPaymentHandler.java) & [IdempotencyVerificationTest.java](file:///system-design/idempotency-patterns/IdempotencyVerificationTest.java)

---

### Scenario 3: Distribuerade lås (Distributed Locks), Fencing Tokens & GC-pauser

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
I komplexa distribuerade system kan en servernod som håller ett distribuerat lås drabbas av en lång Stop-The-World (STW) GC-paus. Medan noden är fryst löper låsets TTL i Redis ut. En annan nod tar låset och börjar ändra i databasen.

När den första noden vaknar ur sin paus tror den att den fortfarande håller låset och skriver över den andra nodens ändringar (**Split-Brain Write**).

#### 2. Keyword Bingo (Facit-lingo)
* **Distributed Lock:** Lås som samordnas mellan oberoende servernoder (t.ex. Redisson eller etcd).
* **GC-Pause Lock Expiration:** Risken att ett lås löper ut under en lång lokal processpaus.
* **Fencing Token:** Ett monotont ökande nummer som tilldelas vid låsförvärv; databasen godkänner enbart skrivningar med högre token än den senast sedda transaktionen.
* **Redlock Algoritmen:** Martin Kleppmann vs Salvatore Sanfilippo-debatten kring säkerheten hos distribuerade lås över flera Redis-masters vid nätverkspartitionering.
* **Optimistic DB Checks:** Att alltid komplettera distribuerade minneslås med en versionskontroll (`@Version` / Fencing Token) på databasnivå.

#### 3. Verklighets-svar (30–60 sek)
> *"Ett distribuerat lås i Redis med TTL är inte tillräckligt för att garantera säkerhet vid långa GC-pauser. Om en nod fryser kan låset löpa ut och tas av en annan nod, vilket leder till att båda skriver samtidigt när den första vaknar. Det formellt korrekta skyddet är Fencing Tokens: varje gång ett lås tas returneras ett monotont ökande nummer. Databasen tillåter enbart skrivningar om tokenet är högre än den senast godkända skrivningen, vilket gör att den fördröjda nodens skrivning automatiskt nekas."*

**Labb & Kod:** [IdempotentPaymentHandler.java](file:///system-design/idempotency-patterns/IdempotentPaymentHandler.java)

---

## 2. Felsökningsguide (Kommandon & Verktyg)

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

## 3. Koden & Verifiering

* **The Flaw:** [FlawedPaymentEndpoint.java](file:///system-design/02-idempotency-patterns/FlawedPaymentEndpoint.java) – Drar pengar blint vid varje inkommande anrop; debiterar kunden dubbelt vid retries.
* **The Fix:** [IdempotentPaymentHandler.java](file:///system-design/02-idempotency-patterns/IdempotentPaymentHandler.java) – Atomär kontroll via Redis `SET NX`, sparad respons och strikt dubbleringsskydd.
* **Verification Test:** [IdempotencyVerificationTest.java](file:///system-design/02-idempotency-patterns/IdempotencyVerificationTest.java) – Bevisar att 5 parallella anrop med samma Idempotency-Key resulterar i exakt 1 debitering.

---

## 4. Så här kör du testerna

```bash
cd system-design/02-idempotency-patterns
mvn test
```
