/**
 * THE FLAW:
 * 1. Type Assertion Lie ("as UserProfile"): Kompilatorn litar blint på typkastningen.
 *    När backend ändrar API-kontraktet, skickar null eller felaktiga fält, kraschar
 *    applikationen vid körtid med Uncaught TypeError.
 * 2. Non-exhaustive switch: Nya händelser/states hanteras inte och faller igenom
 *    till ett tyst felaktigt fallback-tillstånd.
 * 3. Primitiv-besatthet (Primitive Obsession): Inget skydd mot att kasta om ID-parametrar.
 */

export interface UserProfile {
  id: string;
  email: string;
  roles: string[];
}

export class FlawedUserManager {
  /**
   * FARA: Typradering (Type Erasure) gör att 'as UserProfile' försvinner helt vid körning.
   * Om rawJson saknar 'roles' (t.ex. null eller undefined) kraschar koden vid åtkomst.
   */
  public static parseUserUnsafe(rawJson: unknown): UserProfile {
    return rawJson as UserProfile; // Kompilatorn är nöjd, men körtiden är oskyddad!
  }

  public static hasAdminRoleUnsafe(rawJson: unknown): boolean {
    const user = this.parseUserUnsafe(rawJson);
    // Kraschar om roles saknas: TypeError: Cannot read properties of undefined (reading 'includes')
    return user.roles.includes('ADMIN');
  }
}

// 2. Brist på uttömmande kontroll (Non-exhaustive handling)
export type PaymentStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'REQUIRES_3DS_ACTION';

export function handlePaymentStatusUnsafe(status: PaymentStatus): string {
  switch (status) {
    case 'PENDING':
      return 'Väntar på bekräftelse...';
    case 'SUCCESS':
      return 'Betalning genomförd!';
    case 'FAILED':
      return 'Betalning misslyckades.';
    // Utvecklaren glömde 'REQUIRES_3DS_ACTION'!
    // TypeScript varnar inte här om en default fallback returneras.
    default:
      return 'Okänd status'; // UI visar ett tyst fel och användaren fastnar utan 3DS-prompt
  }
}

// 3. Omkastade primitiva ID:n
export function transferFundsUnsafe(
  fromAccountId: string,
  toAccountId: string,
  amount: number
): { from: string; to: string; amount: number } {
  return {
    from: fromAccountId,
    to: toAccountId,
    amount,
  };
}
