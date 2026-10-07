import { describe, it, expect } from 'vitest';
import { ZodError } from 'zod';
import {
  FlawedUserManager,
  handlePaymentStatusUnsafe,
  transferFundsUnsafe,
} from './FlawedTypeAssertions';
import {
  StrictUserManager,
  handlePaymentStateSafe,
  parseAccountId,
  parseUserId,
  transferFundsSafe,
} from './StrictTypeSafety';

describe('05-typescript-type-safety: Runtime Safety & Type Boundaries', () => {
  describe('Scenario 1: Type Erasure & The "as"-lie vs Zod Schema Validation', () => {
    it('The Flaw: "as UserProfile" döljer körtidsfel när server-payload saknar obligatoriska fält', () => {
      // Backend returnerar en payload där 'roles' saknas helt eller är null
      const malformedPayload = {
        id: 'user_1',
        email: 'dev@vault.io',
      };

      // Kompilatorn tillät 'as UserProfile', men vid körtid kraschar appen!
      expect(() => {
        FlawedUserManager.hasAdminRoleUnsafe(malformedPayload);
      }).toThrow(TypeError);
    });

    it('The Fix: Zod failar fast i systemgränsen med informativa valideringsfel', () => {
      const invalidEmailPayload = {
        id: 'user_1',
        email: 'inte-en-epostadress',
        roles: ['USER'],
      };

      // Bevis: Zod fångar ogiltig data direkt innan den sprids till affärslogiken
      expect(() => {
        StrictUserManager.parseUserSafe(invalidEmailPayload);
      }).toThrow(ZodError);
    });

    it('The Fix: Zod defaults och transformationer säkerställer garanterat säkra datastrukturer', () => {
      const payloadWithoutRoles = {
        id: 'user_2',
        email: 'alice@vault.io',
      };

      // Zod fyller i default: roles = []
      const user = StrictUserManager.parseUserSafe(payloadWithoutRoles);
      expect(user.roles).toEqual([]);
      expect(StrictUserManager.hasAdminRoleSafe(payloadWithoutRoles)).toBe(false);
    });
  });

  describe('Scenario 2: Discriminated Unions & Exhaustiveness Checking (never)', () => {
    it('The Flaw: Glömt status-case i switch resulterar i tyst fallback utan varning', () => {
      // En 3DS-händelse som glömts bort faller till default
      const result = handlePaymentStatusUnsafe('REQUIRES_3DS_ACTION');
      expect(result).toBe('Okänd status');
    });

    it('The Fix: Discriminated Union hanterar alla states och garanterar typade metadata per status', () => {
      const actionResult = handlePaymentStateSafe({
        status: 'REQUIRES_3DS_ACTION',
        redirectUrl: 'https://bankid.com/verify/123',
      });

      expect(actionResult).toContain('Omdirigerar till BankID/3DS');
      expect(actionResult).toContain('https://bankid.com/verify/123');
    });
  });

  describe('Scenario 3: Branded Types & Nominal Typing', () => {
    it('The Flaw: Primitiva strängar förhindrar inte att användar-ID och konto-ID blandas ihop', () => {
      const userId = 'usr_999';
      const accountId = 'acc_111';

      // Utvecklaren råkade skicka in userId som från-konto
      const transfer = transferFundsUnsafe(userId, accountId, 100);
      expect(transfer.from).toBe(userId); // Godkänns utan kompilerings- eller körtidsfel!
    });

    it('The Fix: Branded types och smarta konstruktorer säkerställer domänintegritet', () => {
      const validAccount1 = parseAccountId('acc_1001');
      const validAccount2 = parseAccountId('acc_2002');
      const validUser = parseUserId('usr_5005');

      expect(() => parseAccountId('invalid_format_123')).toThrow('Ogiltigt konto-ID');

      const transfer = transferFundsSafe(validAccount1, validAccount2, 250);
      expect(transfer.from).toBe(validAccount1);
      expect(transfer.to).toBe(validAccount2);

      // Notera: TypeScript tillåter INTE att `validUser` skickas till `transferFundsSafe`
      // @ts-expect-error - Verifierar att TypeScript avvisar UserId där AccountId krävs
      const _typeCheckFailure = () => transferFundsSafe(validUser, validAccount2, 100);
      expect(_typeCheckFailure).toBeDefined();
    });
  });
});
