# 📨 03-aws-event-driven: EventBridge -> SQS -> Dead-Letter Queue (DLQ)

Denna modul täcker 4 fundamentala utmaningar i händelsestyrda molnarkitekturer (Event-Driven Architecture) på AWS. Varje scenario presenterar problemet (vad som kraschar och varför vi bryr oss), dess specifika **Keyword Bingo** (facit-lingo), samt ett **Verklighets-svar (30–60 sek)** skräddarsytt för tekniska intervjuer.

---

## 1. Koncept & Scenarier

### Scenario 1: Poison Pills & Oändliga Krasch-loopar i SQS

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
I ett asynkront meddelandesystem anländer förr eller senare ett korrupt meddelande (t.ex. med trasigt JSON-schema eller saknade obligatoriska fält) – en så kallad **Poison Pill**.

Konsumenten försöker parsa meddelandet, kastar ett oväntat undantag och kraschar innan den hinner bekräfta och radera meddelandet från kön. När SQS `VisibilityTimeout` (t.ex. 30s) löper ut blir meddelandet synligt igen och konsumeras på nytt. Utan skyddsmekanismer fastnar konsumenten i en oändlig kraschloop. Alla efterföljande friska meddelanden blockeras bakom det trasiga meddelandet (**Head-of-Line Blocking**), CPU skenar och molnkostnaderna exploderar.

#### 2. Keyword Bingo (Facit-lingo)
* **Poison Pill:** Ett ogiltigt eller korrupt meddelande som ofrånkomligen får konsumenten att krascha vid varje bearbetningsförsök.
* **Visibility Timeout:** Tidsfönstret under vilket ett konsumerat meddelande hålls dolt för andra trådar medan det bearbetas.
* **Head-of-Line Blocking:** När ett enda trasigt meddelande hindrar hela kön och tusentals friska meddelanden från att bearbetas.
* **Crash Loop Backoff:** När konsumenttjänsten startas om kontinuerligt av orkestreraren på grund av återkommande krascher.

#### 3. Verklighets-svar (30–60 sek)
> *"I ett händelsestyrt system med SQS är det oundvikligt att korrupta meddelanden förr eller senare dyker upp – så kallade Poison Pills. Om konsumenten kraschar utan att ack:a meddelandet dyker det upp igen efter visibility timeout och skapar en oändlig kraschloop som blockerar all efterföljande trafik. För att förhindra Head-of-Line Blocking måste vi koppla en Dead-Letter Queue (DLQ) med en Redrive Policy. Då isoleras det trasiga meddelandet automatiskt efter ett bestämt antal försök, så att kön kan fortsätta flyta normalt."*

**Labb & Kod:** [flawed-consumer.js](file:///cloud-and-k8s/03-aws-event-driven/flawed-consumer.js) vs [setup-event-driven-localstack.sh](file:///cloud-and-k8s/03-aws-event-driven/setup-event-driven-localstack.sh)

---

### Scenario 2: Dead-Letter Queue (DLQ) & Redrive Policy (`maxReceiveCount`)

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
Hur hanterar vi meddelanden som misslyckas upprepade gånger utan att förlora affärskritisk kunddata? Att bara svälja fel och radera meddelandet leder till permanent dataförlust (Lost Messages).

#### 2. Keyword Bingo (Facit-lingo)
* **Dead-Letter Queue (DLQ):** En sekundär SQS-kö dit ohanterbara meddelanden automatiskt dirigeras om för analys och karantän.
* **Redrive Policy:** Egenskapen på källkön som kopplar samman den med dess DLQ.
* **`maxReceiveCount: 3`:** Tröskelvärdet som anger hur många gånger ett meddelande får levereras och misslyckas innan det omdirigeras till DLQ.
* **CloudWatch Alarm:** Larm baserat på `ApproximateNumberOfMessagesVisible > 0` i DLQ:n för att notifiera jourhavande utvecklare via Slack/PagerDuty.
* **DLQ Redrive / Reprocessing:** AWS SQS-funktion för att skicka tillbaka korrigerade meddelanden från DLQ till källkön efter att buggen i konsumentkoden har fixats.

#### 3. Verklighets-svar (30–60 sek)
> *"En Dead-Letter Queue (DLQ) fungerar som en säkerhetsventil för meddelanden som inte kan bearbetas. Vi konfigurerar en Redrive Policy på SQS-kön med `maxReceiveCount` satt till exempelvis 3. Om ett meddelande misslyckas tre gånger i rad flyttar AWS automatiskt meddelandet till DLQ:n. Vi sätter ett CloudWatch-larm på DLQ:ns meddelandeantal så att teamet larmas. När buggen åtgärdats kan vi använda AWS Redrive-funktion för att pumpa tillbaka meddelandena till huvudkön för ombearbetning utan någon dataförlust."*

**Labb & Kod:** [setup-event-driven-localstack.sh](file:///cloud-and-k8s/03-aws-event-driven/setup-event-driven-localstack.sh) & [verify-dlq.sh](file:///cloud-and-k8s/03-aws-event-driven/verify-dlq.sh)

---

### Scenario 3: Fan-out Pattern & EventBridge Content Filtering

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
I en växande mikrotjänstmiljö vill flera domäner reagera på samma affärshändelse (t.ex. `OrderPlaced`). Betaltjänsten, Lagersystemet och Marknadsavdelningen behöver alla datan.

Om producenten tvingas skicka meddelandet manuellt till tre olika endpoints uppstår hård koppling (**Point-to-Point Coupling**) och dålig feltolerans: om ett mottagarsystem är nere kraschar producentens anrop.

#### 2. Keyword Bingo (Facit-lingo)
* **Amazon EventBridge:** Serverless Event Bus som möjliggör lös koppling mellan producenter och konsumenter.
* **Fan-out Pattern:** Mönstret där en enda publicerad händelse automatiskt dupliceras till flera oberoende prenumererande köer.
* **Content-Based Filtering (Event Patterns):** Deklarativa JSON-regler i EventBridge som filtrerar händelser så att konsumenter enbart tar emot relevanta meddelanden (t.ex. enbart ordrar med `total > 1000` eller `country: SE`).
* **Decoupled Architecture:** Att producenten har noll kännedom om vem som lyssnar på händelserna.

#### 3. Verklighets-svar (30–60 sek)
> *"För att frikoppla mikrotjänster använder vi Fan-out med Amazon EventBridge. Producenten publicerar en enda händelse till en central Event Bus och behöver inte veta vilka andra tjänster som existerar. EventBridge dirigerar händelsen parallellt till respektive domänteams dedikerade SQS-kö. Med Event Patterns tillämpar vi dessutom deklarativ filtrering direkt på bussen så att tjänster enbart tar emot de specifika händelsetyper de bryr sig om, vilket sparar nätverkstrafik och konsumentresurser."*

**Labb & Kod:** [setup-event-driven-localstack.sh](file:///cloud-and-k8s/03-aws-event-driven/setup-event-driven-localstack.sh)

---

### Scenario 4: At-Least-Once Delivery & Konsument-idempotens

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
Standard SQS erbjuder **At-Least-Once Delivery** (minst-en-gång-leverans). Detta innebär att samma meddelande i sällsynta fall kan levereras två gånger, eller att en nätverksglitch gör att ett meddelande som redan bearbetats levereras på nytt.

Om konsumenten inte är **idempotent** debiteras kunden dubbelt eller skickas två fysiska paket från lagret.

#### 2. Keyword Bingo (Facit-lingo)
* **At-Least-Once Delivery:** Garantin att inget meddelande tappas bort, men med bieffekten att dubbletter kan förekomma.
* **Konsument-idempotens:** Kravet att konsumenten kan hantera samma meddelande flera gånger utan oönskade sidoeffekter.
* **Message Deduplication ID / Event ID:** Unikt ID som följer med händelsen och lagras i en idempotensdatabas (t.ex. DynamoDB/Redis) för att filtrera dubbletter.
* **FIFO SQS vs Standard SQS:**
  * *Standard SQS:* Nästan obegränsad genomströmning, men "at-least-once" och ej strikt ordning.
  * *FIFO SQS:* Exakt-en-gång och strikt ordning, men begränsad till max 300–3 000 meddelanden per sekund.

#### 3. Verklighets-svar (30–60 sek)
> *"I distribuerade system med SQS Standard måste vi alltid designa konsumenterna för 'at-least-once delivery'. Eftersom dubbletter kan inträffa måste varje meddelande bära med sig ett unikt händelse-ID. När konsumenten bearbetar meddelandet kontrollerar den ID:t mot en databas eller ett distribuerat lås i Redis. Om ID:t redan har behandlats kvitterar vi bara meddelandet och kastar det utan att utföra affärslogiken igen. Det garanterar full dataintegritet till en bråkdel av kostnaden och begränsningarna hos strikta FIFO-köer."*

**Labb & Kod:** [verify-dlq.sh](file:///cloud-and-k8s/03-aws-event-driven/verify-dlq.sh)

---

## 2. Felsökningsguide (Kommandon & Verktyg i LocalStack)

Körs mot en lokal LocalStack-container på port `4566`:

```bash
# 1. Kontrollera antal meddelanden i huvudkö och DLQ
aws --endpoint-url=http://localhost:4566 sqs get-queue-attributes \
  --queue-url http://localhost:4566/000000000000/orders-queue \
  --attribute-names ApproximateNumberOfMessages ApproximateNumberOfMessagesNotVisible

aws --endpoint-url=http://localhost:4566 sqs get-queue-attributes \
  --queue-url http://localhost:4566/000000000000/orders-dlq \
  --attribute-names ApproximateNumberOfMessages

# 2. Inspektera förgiftat meddelande i DLQ
aws --endpoint-url=http://localhost:4566 sqs receive-message \
  --queue-url http://localhost:4566/000000000000/orders-dlq
```

---

## 3. Koden & Verifiering

* **The Flaw:** [flawed-consumer.js](file:///cloud-and-k8s/03-aws-event-driven/flawed-consumer.js) – Konsumerar utan DLQ och fastnar i oändlig retry-loop.
* **The Fix:** [setup-event-driven-localstack.sh](file:///cloud-and-k8s/03-aws-event-driven/setup-event-driven-localstack.sh) – Skapar EventBridge-regel, SQS med DLQ och `maxReceiveCount: 3`.
* **Verification Script:** [verify-dlq.sh](file:///cloud-and-k8s/03-aws-event-driven/verify-dlq.sh) – Publicerar ett Poison Pill och bevisar att det isoleras i DLQ efter 3 försök.

---

## 4. Så här kör du testerna

```bash
cd cloud-and-k8s/03-aws-event-driven
./setup-event-driven-localstack.sh
./verify-dlq.sh
```
