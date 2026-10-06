# 📨 03-aws-event-driven: EventBridge -> SQS -> Dead-Letter Queue (DLQ)

## 1. Problem: Vad kraschar eller varför bryr vi oss?
I händelsestyrda system (**Event-Driven Architecture**) kan en konsument krascha på grund av ett oväntat meddelande:
* Ett meddelande med trasigt JSON-schema eller korrupt data anländer (**Poison Pill**).
* Konsumenten kastar exception och misslyckas med att ack:a (ta bort) meddelandet.
* Efter att SQS `VisibilityTimeout` löper ut blir meddelandet synligt igen och konsumeras på nytt.
* Utan en **Dead-Letter Queue (DLQ)** fastnar konsumenten i en oändlig krasch-loop. Alla efterföljande friska meddelanden blir fördröjda eller blockeras (**Head-of-Line Blocking**), CPU-användningen skenar och molnkostnaderna exploderar.

---

## 2. Keyword Bingo (Facit-lingo)
* **Amazon EventBridge:** Serverless händelsebuss (Event Bus) som filtrerar och dirigerar händelser mellan AWS-tjänster med mönstermatchning (Event Patterns).
* **Fan-out Pattern:** En enda händelse publiceras till en händelsebuss/SNS och dupliceras automatiskt till flera oberoende SQS-köer för olika domänteam.
* **At-Least-Once Delivery:** SQS garanterar att meddelandet levereras minst en gång, vilket innebär att konsumenten **måste vara idempotent** (kunna hantera dubbletter).
* **Poison Pill:** Ett ogiltigt meddelande som alltid får konsumenten att krascha oavsett hur många retries som görs.
* **Dead-Letter Queue (DLQ):** En sekundär SQS-kö dit meddelanden automatiskt flyttas efter att de misslyckats ett definierat antal gånger.
* **Redrive Policy (`maxReceiveCount`):** Regeln på huvudkön som specificerar efter hur många misslyckade leveransförsök (`maxReceiveCount: 3`) meddelandet ska dirigeras om till DLQ.
* **Visibility Timeout:** Tidsfönstret under vilket ett konsumerat meddelande är dolt för andra konsumenter medan det bearbetas.

---

## 3. Verklighets-svar (30–60 sek)
> *"I ett händelsestyrt system med EventBridge och SQS måste vi utgå från 'at-least-once delivery' och skydda oss mot 'poison pills'. Om ett korrupt meddelande kraschar konsumenten återgår det till kön efter visibility timeout och skapar en oändlig kraschloop. Lösningen är att alltid koppla en Dead-Letter Queue med en strikt Redrive Policy och `maxReceiveCount` satt till exempelvis 3 eller 5. Efter 3 misslyckade försök flyttas meddelandet till DLQ:n, en CloudWatch-alarm notifierar teamet och övriga friska meddelanden i kön kan fortsätta bearbetas utan fördröjning."*

---

## 4. Felsökningsguide (Kommandon & Verktyg i LocalStack)

Körs mot vår lokala LocalStack-container på port `4566`:

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

## 5. Koden & Verifiering

* **The Flaw:** [flawed-consumer.js](file:///cloud-and-k8s/03-aws-event-driven/flawed-consumer.js) – Konsumerar utan DLQ och fastnar i oändlig retry-loop.
* **The Fix:** [setup-event-driven-localstack.sh](file:///cloud-and-k8s/03-aws-event-driven/setup-event-driven-localstack.sh) – Skapar EventBridge-regel, SQS med DLQ och `maxReceiveCount: 3`.
* **Verification Script:** [verify-dlq.sh](file:///cloud-and-k8s/03-aws-event-driven/verify-dlq.sh) – Publicerar ett Poison Pill och bevisar att det isoleras i DLQ efter 3 försök.
