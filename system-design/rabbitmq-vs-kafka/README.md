# 📬 rabbitmq-vs-kafka: Smart Broker vs Distributed Commit Log

> **Fokus:** En av de absolut viktigaste systemdesign- och arkitekturfrågorna: **RabbitMQ (AMQP) vs Apache Kafka**. När väljer man vilken, varför krossar *Competing Consumers* händelseordningen, hur leder saknad *Prefetch Count* till tråd- och konsumentsvält, och hur isoleras *Poison Pills* med Dead-Letter Exchanges (DLX) och Dead-Letter Topics (DLT).

---

## 🧭 Översikt & Arkitekturfilosofi

| Dimension | **RabbitMQ (AMQP)** | **Apache Kafka** |
| :--- | :--- | :--- |
| **Filosofi** | **"Smart Broker, Dumb Consumer"** (Brokern sköter routing, filtrering och status). | **"Dumb Broker, Smart Consumer"** (Brokern är en append-only log; konsumenten styr sin offset). |
| **Lagringsmodell** | **Transient Queue:** Meddelanden raderas direkt när de kvitteras (`ACK`). Brokern mår bäst med *tomma köer*. | **Durable Commit Log:** Meddelanden sparas på disk i dagar/år. Kan spelas upp igen (**Replayability**). |
| **Konsumtion** | **Competing Consumers:** Flera workers plockar jobb från samma kö. Ingen garanterad ordning mellan workers. | **Partitioned Consumer Groups:** Exakt 1 worker per partition i samma grupp. Strikt ordning per partition. |
| **Push vs Pull** | **Push-baserad:** Brokern skickar data till klienten (kräver `basic.qos(prefetch)` för backpressure). | **Pull-baserad:** Konsumenten frågar efter batcher med `poll(records)` (inbyggt skydd mot överbelastning). |
| **Bästa use-case** | Task queues, komplex meddelanderouting, bakgrundsjobb, transaktionella RPC-mönster. | Event sourcing, högt genomflöde (100k+ msg/s), data streams, händelsereplay. |

---

## 🎯 Scenario 1: Competing Consumers vs Partition Ordering (FIFO per Entitet)

### Problem (Vad kraschar / varför bryr vi oss)
En e-handelsplattform skickar två händelser för samma order (`ORD-99`):
1. `ORDER_CREATED` (12:00:00.000)
2. `ORDER_CANCELLED` (12:00:00.050)

Systemet använder RabbitMQ med 4 workers på samma kö (`orders-queue`) för att hantera hög last. 
Worker 1 plockar `ORDER_CREATED` och påbörjar ett långsamt externt anrop (80 ms).
Worker 2 plockar `ORDER_CANCELLED` och blir klar på 10 ms.

**Resultat:** Avbokningen processas *före* skapandet! När Worker 2 kör hittas inte ordern i databasen och avbokningen slängs. En sekund senare skapar Worker 1 ordern. Kunden debiteras och varan skickas trots att kunden avbröt! Utvecklaren antog felaktigt: *"RabbitMQ är FIFO så ordningen bevaras automatiskt"* (FIFO gäller enbart egress från kön, inte mellan parallella workers).

### Keyword Bingo (Facit-lingo)
* **Competing Consumers:** Designmönster där flera samtidiga arbetstrådar plockar jobb från samma delade kö för horisontell skalbarhet.
* **Out-of-Order Execution:** När beroende händelser för samma entitet (t.ex. Create och Cancel) slutförs i omvänd ordning på grund av parallella konsumenter med varierande processtid.
* **Partition Key Hashing:** Mekanismen i Kafka där en nyckel (t.ex. `orderId`) hashas så att alla relaterade meddelanden deterministiskt styrs till exakt samma partition.
* **Consumer Group Assignment:** Kafkas garanti att en specifik partition inom ett topic enbart konsumeras av exakt en tråd per konsumentgrupp i taget.
* **Strict FIFO per Entity:** Arkitekturmålet att garantera strikt sekventiell ordning för varje enskild entitet, utan att offra parallellitet mellan olika entiteter.
* **Consistent Hash Exchange:** RabbitMQ-plugin/exchange som shardas över flera interna köer baserat på routing-nyckelns hash, vilket efterliknar Kafkas partitionering.
* **Single Active Consumer (SAC):** RabbitMQ-funktion som tvingar endast en worker i taget att läsa från en kö för att säkra ordning, men som offrar all parallellism.
* **Head-of-Line Blocking per Partition:** Kafkas kompromiss: om bearbetningen av ett meddelande fastnar blockeras alla andra händelser i samma partition.

### Verklighets-svar (30–60 sek)
> "Att använda vanliga Competing Consumers i RabbitMQ för entitetstillstånd bryter händelseordningen så fort man har fler än en worker, eftersom snabbare trådar kan hinna slutföra en `CANCEL`-händelse innan en långsam tråd är klar med `CREATE`. 
> 
> I Apache Kafka löser vi detta genom att använda entitetens ID (t.ex. `orderId`) som **Partition Key**. Alla events för samma order hashas till samma partition, och eftersom Kafka garanterar att en partition enbart konsumeras av en enda tråd inom en konsumentgrupp garanteras strikt sekventiell ordning per entitet samtidigt som vi skalar horisontellt över partitionerna. I RabbitMQ kräver motsvarande mönster antingen en *Consistent Hash Exchange* som shardas över flera köer eller *Single Active Consumer*."

### Senior Intervju-djupdykning
* **Varför ger inte `Single Active Consumer` i RabbitMQ samma skalbarhet som Kafka?** SAC innebär att endast en aktiv konsument tar emot ALLA meddelanden i kön medan övriga står som standbys. Man tappar därmed all parallellism för hela kön. I Kafka bibehålls parallelliteten genom att partitioner fördelas jämnt över konsumenterna i gruppen.
* **Vad är nackdelen med Kafkas partitionering?** Om ett specifikt `orderId` hänger upp sig (t.ex. pga timeout eller retry) blockeras alla andra ordrar som hashats till samma partition (**Head-of-Line Blocking per partition**).

---

## 🎯 Scenario 2: Prefetch Count (`basic.qos`) & Consumer Starvation (RabbitMQ)

### Problem (Vad kraschar / varför bryr vi oss)
RabbitMQ är en *push-baserad* mäklare. När en worker kopplar upp sig utan att ange `basic.qos(prefetch_count)` är standardvärdet obegränsat (`0`).
När ett nattjobb skickar 10 000 tunga PDF-genereringsuppgifter pushar RabbitMQ omedelbart alla 10 000 meddelanden till den första workern som anslöt.

**Resultat:** 
* Worker 1 fyller sitt RAM-minne med 10 000 buffrade meddelanden, drabbas av GC-pauser och får OutOfMemoryError (krasch).
* Worker 2 och Worker 3 (som startade några sekunder senare) står med 0 jobb (**Consumer Starvation**).
* När Worker 1 kraschar återställer RabbitMQ alla 10 000 oackade meddelanden till kön, varpå Worker 2 sväljer alla och kraschar i en evig kedjereaktion (**Crash Loop**).

### Keyword Bingo (Facit-lingo)
* **`basic.qos` (Quality of Service):** AMQP-protokollets metod för att sätta gränser för hur många meddelanden en konsument får ta emot i förväg.
* **Prefetch Count:** Det exakta antalet oackade meddelanden som RabbitMQ tillåts pusha till klienten innan en `ACK` krävs (t.ex. 10..50).
* **Push vs Pull Broker:**
  * *Push (RabbitMQ):* Brokern skickar data så fort den anländer; riskerar att dränka konsumentens minne utan flödeskontroll.
  * *Pull (Kafka):* Konsumenten begär aktivt batcher (`poll`); naturligt skyddad mot överbelastning och minnesbrist.
* **Consumer Starvation:** När en enda girig worker buffrar alla meddelanden lokalt och lämnar övriga anslutna workers sysslolösa.
* **Unacknowledged Message Pool:** Samlingen av meddelanden som skickats till en worker men ännu inte bekräftats som slutförda.
* **Backpressure (Mottryck):** Förmågan hos ett system att signalera uppströms att bromsa takten när konsumtionen inte hinner med produktionen.
* **Fair Dispatch (Rättvis fördelning):** Att distribuera arbete jämnt mellan tillgängliga trådar i takt med att de faktiskt blir lediga.
* **`max.poll.records`:** Kafkas motsvarighet i klientkonfigurationen som begränsar antalet records som returneras per `poll()`-anrop.

### Verklighets-svar (30–60 sek)
> "Eftersom RabbitMQ pushar meddelanden måste konsumenten explicit sätta en `prefetch_count` via `basic.qos` (ofta mellan 10 och 50 beroende på processtid). Utan prefetch tömmer brokern hela kön in i den första anslutna konsumentens minnesbuffert. Det leder till konsumentsvält för övriga workers och garanterad OutOfMemoryError vid stora toppar.
> 
> I Kafka existerar inte detta problem eftersom Kafka är en **pull-baserad** logg där konsumenten aktivt efterfrågar data via `poll()`. Där styrs backpressure automatiskt genom `max.poll.records`, och brokern kan aldrig överbelasta en konsument."

---

## 🎯 Scenario 3: Poison Pills, Infinite Loops & Dead-Letter Exchanges (DLX)

### Problem (Vad kraschar / varför bryr vi oss)
Ett korrupt meddelande (t.ex. felaktig JSON eller saknat fält) läggs på kön.
När workern försöker avkoda meddelandet kastar deserializern ett undantag. I felhanteraren kör utvecklaren:
```java
channel.basicNack(deliveryTag, false, true); // requeue = true!
```
Eller så kraschar processen utan att skicka en ACK/NACK.

**Resultat:** Brokern lägger omedelbart tillbaka giftpillret längst fram i kön. Nästa millisekund plockas det upp igen, kraschar igen och requeuas igen. CPU rusar till 100%, loggarna fylls med miljontals stacktraces, och framför allt: **Alla tusentals friska meddelanden bakom giftpillret blockeras helt** (**Head-of-Line Blocking**).

### Keyword Bingo (Facit-lingo)
* **Poison Pill:** Ett korrupt eller icke-parsbart meddelande som ofrånkomligen kraschar konsumenten vid varje bearbetningsförsök.
* **Infinite Redelivery Loop:** En oändlig kraschloop som uppstår när ett felaktigt meddelande avvisas med `requeue=true` och omedelbart levereras på nytt.
* **Head-of-Line Blocking:** När ett enda trasigt meddelande blockerar kön och förhindrar att alla bakomliggande giltiga meddelanden någonsin nås.
* **Dead-Letter Exchange (DLX):** En dedikerad RabbitMQ-exchange dit avvisade meddelanden dirigeras automatiskt vid `NACK(requeue=false)` eller TTL-utgång.
* **Dead-Letter Queue (DLQ):** Karantänskön som tar emot och lagrar förbrukade giftpiller för felsökning, larm och manuell omkörning.
* **Negative Acknowledgment (`NACK` / `reject`):** Klientens signal till brokern att ett meddelande inte kunde hanteras; med `requeue=false` skickas det till DLX istället för att loopa.
* **Quorum Queues & `x-delivery-count`:** Moderna Raft-baserade köer i RabbitMQ som automatiskt räknar antal leveransförsök per meddelande.
* **Dead Letter Topic (DLT):** Kafkas motsvarighet där Spring Kafkas `DeadLetterPublishingRecoverer` publicerar giftpillret till ett separat topic (t.ex. `orders.DLT`) och committar konsumentens offset.

### Verklighets-svar (30–60 sek)
> "Att göra `nack` med `requeue=true` vid felaktiga meddelanden (Poison Pills) skapar en oändlig omleveransloop som helt blockerar efterföljande meddelanden och slukar CPU och loggutrymme.
> 
> I RabbitMQ löser vi detta genom att konfigurera en **Dead-Letter Exchange (DLX)** och använda Quorum Queues med `x-delivery-count`. Om ett meddelande misslyckas fler än 3 gånger avvisar vi det med `nack(requeue=false)`. Brokern flyttar då meddelandet automatiskt till en DLQ för manuell granskning, och kön fortsätter rulla utan driftstopp. I Kafka använder vi Spring Kafkas `DeadLetterPublishingRecoverer` tillsammans med en `ErrorHandlingDeserializer` som skickar giftpillret till ett `.DLT`-topic och committar offseten."

---

## 📋 Snabbreferens för Intervjun

| Koncept | RabbitMQ (AMQP) | Apache Kafka |
| :--- | :--- | :--- |
| **Garanterad Ordning** | Kräver *Consistent Hash Exchange* eller *Single Active Consumer* | Inbyggt via **Partition Key Hashing** inom konsumentgrupp |
| **Flödeskontroll (Backpressure)** | Kräver explicit `channel.basicQos(prefetch_count)` | Inbyggt via Pull-modellen (`consumer.poll(max.poll.records)`) |
| **Felhantering (Poison Pill)** | `NACK(requeue=false)` -> Dead-Letter Exchange (DLX) | `ErrorHandlingDeserializer` -> Dead Letter Topic (DLT) |
| **Livscykel & Replay** | Raderas vid `ACK` (inget replay-stöd) | Raderas efter retention-tid (kan spelas upp igen via ny offset) |
