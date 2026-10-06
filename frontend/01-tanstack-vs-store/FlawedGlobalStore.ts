/**
 * THE FLAW:
 * Att dumpa serverdata i ett globalt UI-store (Zustand/Redux).
 * Problem:
 * 1. Manuell hantering av isLoading, isError, errorMessage.
 * 2. Ingen automatisk Stale-While-Revalidate eller bakgrundsuppdatering vid fönsterfokus.
 * 3. Ingen deduplicering: Om två komponenter anropar fetchUsers() skickas två HTTP-anrop.
 * 4. Cache-invalidering blir ett mörker vid POST/PUT.
 */

export interface User {
  id: string;
  name: string;
  email: string;
}

export interface FlawedStoreState {
  // Server-state felaktigt placerat i globalt klientstore
  users: User[];
  isLoading: boolean;
  error: string | null;
  lastFetchedAt: number | null;

  // UI-state
  isSidebarOpen: boolean;

  fetchUsers: () => Promise<void>;
  toggleSidebar: () => void;
}

// Pseudokod för felaktigt tillståndsmönster
export const createFlawedStore = (fetcher: () => Promise<User[]>) => {
  let state: FlawedStoreState = {
    users: [],
    isLoading: false,
    error: null,
    lastFetchedAt: null,
    isSidebarOpen: false,
    toggleSidebar() {
      state.isSidebarOpen = !state.isSidebarOpen;
    },
    async fetchUsers() {
      state.isLoading = true;
      try {
        const data = await fetcher();
        state.users = data;
        state.lastFetchedAt = Date.now();
        state.error = null;
      } catch (err: any) {
        state.error = err.message;
      } finally {
        state.isLoading = false;
      }
    },
  };
  return state;
};
