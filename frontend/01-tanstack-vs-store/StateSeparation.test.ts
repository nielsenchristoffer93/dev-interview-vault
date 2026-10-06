import { describe, it, expect, vi } from 'vitest';
import { QueryClient } from '@tanstack/react-query';
import { USER_QUERY_KEY } from './ModernStateArchitecture';
import { createFlawedStore } from './FlawedGlobalStore';

describe('01-tanstack-vs-store: State Separation Lab', () => {
  it('The Flaw: Manuellt store kräver manuell felhantering och deduplicerar inte anrop automatiskt', async () => {
    let callCount = 0;
    const fetcher = vi.fn().mockImplementation(async () => {
      callCount++;
      return [{ id: '1', name: 'Alice', email: 'alice@example.com' }];
    });

    const store = createFlawedStore(fetcher);

    // Två samtidiga anrop
    await Promise.all([store.fetchUsers(), store.fetchUsers()]);

    // Båda anropen exekverades (ingen request deduplication)
    expect(callCount).toBe(2);
  });

  it('The Fix: TanStack Query deduplicerar samtidiga förfrågningar på samma query key', async () => {
    const queryClient = new QueryClient();
    let networkCallCount = 0;

    const fetcher = vi.fn().mockImplementation(async () => {
      networkCallCount++;
      return [{ id: '1', name: 'Alice', email: 'alice@example.com' }];
    });

    // Två komponenter efterfrågar samma data samtidigt via QueryClient
    const [res1, res2] = await Promise.all([
      queryClient.fetchQuery({ queryKey: USER_QUERY_KEY, queryFn: fetcher }),
      queryClient.fetchQuery({ queryKey: USER_QUERY_KEY, queryFn: fetcher }),
    ]);

    // Verifiering: Endast 1 faktiskt nätverksanrop gjordes!
    expect(networkCallCount).toBe(1);
    expect(res1).toEqual(res2);
  });
});
