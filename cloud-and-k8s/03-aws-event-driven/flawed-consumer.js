/**
 * THE FLAW:
 * Konsumenten hanterar inte giftiga meddelanden (Poison Pills).
 * Vid JSON.parse error kraschar funktionen -> Meddelandet raderas inte (DeleteMessage körs ej).
 * Efter visibility timeout kommer SQS leverera samma meddelande igen, i all evighet!
 */
export async function flawedConsumerHandler(message) {
  console.log(`[Processing] Message ID: ${message.MessageId}`);

  // Om meddelandet är ett malformat Poison Pill kraschar JSON.parse
  const body = JSON.parse(message.Body);

  if (!body.orderId) {
    throw new Error("Invalid payload: Missing orderId!");
  }

  console.log(`[Success] Processed order: ${body.orderId}`);
  // Returnerar ok så att SQS DeleteMessage kan köras
  return true;
}
