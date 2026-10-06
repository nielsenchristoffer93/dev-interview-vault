/**
 * THE FIX:
 * BFF-arkitektur (Backend-for-Frontend).
 * 1. Tokens sparas ALDRIG i webbläsarens Javascript-minne eller localStorage.
 * 2. BFF returnerar en Set-Cookie header med:
 *    - HttpOnly (osynlig för document.cookie och XSS-skript)
 *    - Secure (skickas enbart över HTTPS)
 *    - SameSite=Lax (skyddar mot Cross-Site Request Forgery)
 * 3. Klient-koden anropar BFF:en med credentials: 'same-origin' / 'include'.
 */

export interface CookieConfig {
  name: string;
  value: string;
  httpOnly: boolean;
  secure: boolean;
  sameSite: 'Lax' | 'Strict' | 'None';
  path: string;
}

export class BffCookieAuth {
  // Simulering av hur BFF (Node.js/Next.js/Spring) sätter sessions-cookien mot klienten
  public static createSecureSessionCookie(sessionToken: string): CookieConfig {
    return {
      name: '__Host-session',
      value: sessionToken,
      httpOnly: true,  // FÖRBJUDER JS ÅTKOMST
      secure: true,    // Kräver HTTPS
      sameSite: 'Lax', // Skyddar mot CSRF
      path: '/',
    };
  }

  // Simulering av BFF som anropar backend mikrotjänst med Bearer Token
  public static forwardToMicroservice(sessionToken: string, upstreamApiUrl: string): HeadersInit {
    return {
      'Authorization': `Bearer ${sessionToken}`,
      'Content-Type': 'application/json',
    };
  }
}
