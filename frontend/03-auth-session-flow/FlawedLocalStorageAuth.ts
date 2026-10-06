/**
 * THE FLAW:
 * Att lagra access_token i localStorage.
 * Varje tredjepartsskript (Google Analytics, npm-beroenden, XSS)
 * kan läsa ut hemligheten direkt!
 */

export class FlawedLocalStorageAuth {
  private static readonly TOKEN_KEY = 'vault_access_token';

  public static loginSuccess(jwtToken: string): void {
    localStorage.setItem(this.TOKEN_KEY, jwtToken);
  }

  public static getBearerToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  public static logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
  }
}
