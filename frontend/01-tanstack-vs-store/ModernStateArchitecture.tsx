import React from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { create } from 'zustand';

/**
 * THE FIX:
 * Strikt separation:
 * 1. TanStack Query hanterar Server State (caching, deduplicering, invalidering).
 * 2. Zustand hanterar UI State (modaler, paneler, temporära filter).
 */

export interface User {
  id: string;
  name: string;
  email: string;
}

// 1. RENT CLIENT UI STATE (Zustand)
interface UIState {
  isSidebarOpen: boolean;
  selectedUserId: string | null;
  toggleSidebar: () => void;
  selectUser: (id: string | null) => void;
}

export const useUIStore = create<UIState>((set) => ({
  isSidebarOpen: false,
  selectedUserId: null,
  toggleSidebar: () => set((state) => ({ isSidebarOpen: !state.isSidebarOpen })),
  selectUser: (id) => set({ selectedUserId: id }),
}));

// 2. SERVER STATE (TanStack Query Custom Hooks)
export const USER_QUERY_KEY = ['users'] as const;

export function useUsersQuery(fetchUsersApi: () => Promise<User[]>) {
  return useQuery({
    queryKey: USER_QUERY_KEY,
    queryFn: fetchUsersApi,
    staleTime: 1000 * 60 * 5, // Data anses färsk i 5 minuter (inga onödiga bakgrundsanrop)
    gcTime: 1000 * 60 * 15,    // Behålls i cachen i 15 minuter efter att sista komponenten avmonterats
  });
}

export function useCreateUserMutation(createUserApi: (user: Omit<User, 'id'>) => Promise<User>) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: createUserApi,
    onSuccess: () => {
      // Automatisk cache-invalidering triggar sömlös bakgrundsuppdatering!
      queryClient.invalidateQueries({ queryKey: USER_QUERY_KEY });
    },
  });
}
