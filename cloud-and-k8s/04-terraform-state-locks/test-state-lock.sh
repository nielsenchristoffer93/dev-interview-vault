#!/usr/bin/env bash
set -euo pipefail

ENDPOINT="http://localhost:4566"
REGION="eu-north-1"
TABLE_NAME="terraform-locks"
LOCK_ID="vault-terraform-state-prod/production/terraform.tfstate-md5"

echo "=== Verifierar DynamoDB State Locking-mekanism i LocalStack ==="

# 1. Skapa tabellen om den inte finns
echo "1. Skapar DynamoDB-tabellen '$TABLE_NAME' med hash_key 'LockID'..."
aws --endpoint-url="$ENDPOINT" --region "$REGION" dynamodb create-table \
  --table-name "$TABLE_NAME" \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST || echo "Tabellen finns redan."

# 2. Process A tar låset (Simulering av pågående 'terraform apply')
echo "2. Process A förvärvar låset..."
aws --endpoint-url="$ENDPOINT" --region "$REGION" dynamodb put-item \
  --table-name "$TABLE_NAME" \
  --item "{\"LockID\": {\"S\": \"$LOCK_ID\"}, \"Info\": {\"S\": \"Pipeline A körs av Alice\"}}" \
  --condition-expression "attribute_not_exists(LockID)"

echo "✅ Process A har låset!"

# 3. Process B försöker förvärva samma lås samtidigt (Simulering av samtidig körning i CI)
echo "3. Process B försöker förvärva samma lås samtidigt..."
if aws --endpoint-url="$ENDPOINT" --region "$REGION" dynamodb put-item \
  --table-name "$TABLE_NAME" \
  --item "{\"LockID\": {\"S\": \"$LOCK_ID\"}, \"Info\": {\"S\": \"Pipeline B körs av Bob\"}}" \
  --condition-expression "attribute_not_exists(LockID)" 2>/dev/null; then
  echo "❌ FEL: Process B lyckades skriva över låset!"
  exit 1
else
  echo "✅ FRAMGÅNG: Process B nekades med ConditionalCheckFailedException!"
  echo "Terraform kastar: 'Error: Error acquiring the state lock'"
fi

# 4. Process A avslutar och släpper låset
echo "4. Process A släpper låset..."
aws --endpoint-url="$ENDPOINT" --region "$REGION" dynamodb delete-item \
  --table-name "$TABLE_NAME" \
  --key "{\"LockID\": {\"S\": \"$LOCK_ID\"}}"

echo "✅ Låset släppt. Systemet är fritt för nästa pipeline."
