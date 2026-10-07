import { z } from 'zod';

/**
 * THE FIX:
 * 1. Runtime Schema Validation med Zod ("Parse, don't validate"):
 *    - All extern data (API, localStorage, WebSockets) behandlas som 'unknown'.
 *    - Schemat validerar och transformerar data i applikationsgränsen (Boundary).
 * 2. Exhaustiveness Checking med TypeScript 'never':
 *    - assertNever() garanterar att kompilatorn ger fel vid byggtid om en union utökas.
 * 3. Branded Types (Nominal Typing):
 *    - Förhindrar att primitiva strängar för olika domänentiteter förväxlas.
 */

// 1. RUNTIME CONTRACT VALIDATION (Zod)
export const UserProfileSchema = z.object({
  id: z.string().min(1, 'ID får inte vara tomt'),
  email: z.string().email('Ogiltig e-postadress'),
  roles: z.array(z.string()).default([]),
});

export type UserProfileValidated = z.infer<typeof UserProfileSchema>;

export class StrictUserManager {
  /**
   * Säker parsning med fail-fast vid felaktig payload från nätverket
   */
  public static parseUserSafe(rawJson: unknown): UserProfileValidated {
    return UserProfileSchema.parse(rawJson);
  }

  public static hasAdminRoleSafe(rawJson: unknown): boolean {
    const user = this.parseUserSafe(rawJson);
    return user.roles.includes('ADMIN');
  }
}

// 2. DISCRIMINATED UNIONS & EXHAUSTIVENESS CHECKING
export type StrictPaymentState =
  | { status: 'PENDING'; timeoutMs: number }
  | { status: 'SUCCESS'; transactionId: string }
  | { status: 'FAILED'; errorCode: string }
  | { status: 'REQUIRES_3DS_ACTION'; redirectUrl: string };

/**
 * Utility-funktion för kompileringstidsgaranti av uttömmande kontroll.
 * Om ett nytt state läggs till i unionen utan att hanteras i switchen,
 * kommer TypeScript att vägra kompilera koden.
 */
export function assertNever(value: never, errorMessage?: string): never {
  throw new Error(errorMessage ?? `Ohanterat tillstånd i union: ${JSON.stringify(value)}`);
}

export function handlePaymentStateSafe(state: StrictPaymentState): string {
  switch (state.status) {
    case 'PENDING':
      return `Väntar på bekräftelse (timeout: ${state.timeoutMs}ms)...`;
    case 'SUCCESS':
      return `Betalning godkänd! Ref: ${state.transactionId}`;
    case 'FAILED':
      return `Betalning misslyckades med felkod: ${state.errorCode}`;
    case 'REQUIRES_3DS_ACTION':
      return `Omdirigerar till BankID/3DS: ${state.redirectUrl}`;
    default:
      // Om någon lägger till ett 5:e state ger kompilatorn ett rött felstreck här!
      return assertNever(state);
  }
}

// 3. BRANDED TYPES (Nominal Typing i strukturella TypeScript)
declare const __brand: unique symbol;
export type Brand<T, TBrand extends string> = T & { readonly [__brand]: TBrand };

export type AccountId = Brand<string, 'AccountId'>;
export type UserId = Brand<string, 'UserId'>;

export function parseAccountId(value: string): AccountId {
  if (!value.startsWith('acc_')) {
    throw new Error(`Ogiltigt konto-ID: ${value}`);
  }
  return value as AccountId;
}

export function parseUserId(value: string): UserId {
  if (!value.startsWith('usr_')) {
    throw new Error(`Ogiltigt användar-ID: ${value}`);
  }
  return value as UserId;
}

export function transferFundsSafe(
  fromAccount: AccountId,
  toAccount: AccountId,
  amount: number
): { from: AccountId; to: AccountId; amount: number } {
  return {
    from: fromAccount,
    to: toAccount,
    amount,
  };
}
