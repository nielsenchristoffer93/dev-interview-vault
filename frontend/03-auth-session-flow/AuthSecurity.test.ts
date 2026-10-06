import { describe, it, expect, beforeEach } from 'vitest';
import { FlawedLocalStorageAuth } from './FlawedLocalStorageAuth';
import { BffCookieAuth } from './BffCookieAuth';

describe('03-auth-session-flow: Auth Security Lab', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('The Flaw: localStorage kan enkelt exfiltreras av godtyckligt script (XSS-simulering)', () => {
    const secretJwt = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.sensitive_token';
    FlawedLocalStorageAuth.loginSuccess(secretJwt);

    // Simulera ett injicerat XSS-script i webbläsaren
    const simulatedXssAttacker = () => {
      return localStorage.getItem('vault_access_token');
    };

    const stolenToken = simulatedXssAttacker();

    // Bevis: Angriparen fick tag på JWT-tokenet direkt från minnet
    expect(stolenToken).toBe(secretJwt);
  });

  it('The Fix: HttpOnly session cookie kan inte komprometteras via Javascript', () => {
    const secretSession = 'super_secret_session_token_123';
    const cookie = BffCookieAuth.createSecureSessionCookie(secretSession);

    // Verifiera säkerhetsflaggorna på cookien
    expect(cookie.httpOnly).toBe(true);
    expect(cookie.secure).toBe(true);
    expect(cookie.sameSite).toBe('Lax');

    // Bevis: document.cookie kan aldrig läsa en HttpOnly cookie i webbläsaren
    // BFF tar hand om att injicera tokenet mot mikrotjänster
    const upstreamHeaders = BffCookieAuth.forwardToMicroservice(cookie.value, '/api/payments');
    expect(upstreamHeaders).toHaveProperty('Authorization', `Bearer ${secretSession}`);
  });
});
