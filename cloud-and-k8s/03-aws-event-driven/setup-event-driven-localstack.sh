#!/usr/bin/env bash
set -euo pipefail

ENDPOINT="http://localhost:4566"
REGION="eu-north-1"

echo "=== Sätter upp EventBridge och SQS med Dead-Letter Queue (DLQ) i LocalStack ==="

# 1. Skapa Dead-Letter Queue (DLQ)
echo "1. Skapar orders-dlq..."
DLQ_URL=$(aws --endpoint-url="$ENDPOINT" --region "$REGION" sqs create-queue \
  --queue-name orders-dlq \
  --output text --query 'QueueUrl')
DLQ_ARN=$(aws --endpoint-url="$ENDPOINT" --region "$REGION" sqs get-queue-attributes \
  --queue-url "$DLQ_URL" --attribute-names QueueArn \
  --output text --query 'Attributes.QueueArn')

echo "DLQ ARN: $DLQ_ARN"

# 2. Skapa Huvudkön med Redrive Policy (maxReceiveCount = 3)
echo "2. Skapar orders-queue med redrive-policy mot DLQ..."
REDRIVE_POLICY="{\"deadLetterTargetArn\":\"$DLQ_ARN\",\"maxReceiveCount\":3}"

QUEUE_URL=$(aws --endpoint-url="$ENDPOINT" --region "$REGION" sqs create-queue \
  --queue-name orders-queue \
  --attributes "{\"RedrivePolicy\": $(echo $REDRIVE_POLICY | jq -R .), \"VisibilityTimeout\": \"2\"}" \
  --output text --query 'QueueUrl')
QUEUE_ARN=$(aws --endpoint-url="$ENDPOINT" --region "$REGION" sqs get-queue-attributes \
  --queue-url "$QUEUE_URL" --attribute-names QueueArn \
  --output text --query 'Attributes.QueueArn')

echo "Huvudkö URL: $QUEUE_URL"

# 3. Skapa EventBridge Rule för order-events och länka till SQS-kön
echo "3. Skapar EventBridge-regel och länkar target..."
aws --endpoint-url="$ENDPOINT" --region "$REGION" events put-rule \
  --name "OrderCreatedRule" \
  --event-pattern "{\"source\":[\"com.vault.orders\"],\"detail-type\":[\"OrderPlaced\"]}"

aws --endpoint-url="$ENDPOINT" --region "$REGION" events put-targets \
  --rule "OrderCreatedRule" \
  --targets "Id"="1","Arn"="$QUEUE_ARN"

echo "✅ Arkitektur uppsatt: EventBridge -> SQS (orders-queue) -> DLQ (orders-dlq)"
