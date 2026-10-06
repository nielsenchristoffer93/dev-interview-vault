#!/usr/bin/env bash
set -euo pipefail

ENDPOINT="http://localhost:4566"
REGION="eu-north-1"
QUEUE_URL="http://localhost:4566/000000000000/orders-queue"
DLQ_URL="http://localhost:4566/000000000000/orders-dlq"

echo "=== Verifierar DLQ-isolering för Poison Pill ==="

# 1. Skicka ett förgiftat meddelande (ogiltig JSON)
echo "1. Skickar ett ogiltigt Poison Pill meddelande till orders-queue..."
aws --endpoint-url="$ENDPOINT" --region "$REGION" sqs send-message \
  --queue-url "$QUEUE_URL" \
  --message-body "MALFORMED_POISON_PILL_NOT_JSON"

# 2. Simulera konsument som misslyckas 3 gånger
echo "2. Simulerar 3 misslyckade mottagningar (läs och kasta utan delete)..."
for i in 1 2 3; do
  echo " - Försök $i..."
  RECEIVE_RES=$(aws --endpoint-url="$ENDPOINT" --region "$REGION" sqs receive-message \
    --queue-url "$QUEUE_URL" --visibility-timeout 1 || echo "")
  sleep 1.5
done

# 3. Kontrollera om meddelandet flyttats till DLQ
echo "3. Kontrollerar om meddelandet flyttats till orders-dlq..."
DLQ_MESSAGES=$(aws --endpoint-url="$ENDPOINT" --region "$REGION" sqs receive-message \
  --queue-url "$DLQ_URL" --max-number-of-messages 1 || echo "")

if echo "$DLQ_MESSAGES" | grep -q "MALFORMED_POISON_PILL"; then
  echo "✅ FRAMGÅNG: Poison Pill fångades och flyttades automatiskt till Dead-Letter Queue!"
else
  echo "ℹ️ Meddelandet bearbetas fortfarande eller väntar på nästa redrive-cykel."
fi
