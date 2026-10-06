import { QueryClient } from '@tanstack/react-query';

export interface Todo {
  id: string;
  title: string;
  completed: boolean;
}

export const TODOS_KEY = ['todos'] as const;

/**
 * THE FIX:
 * Komplett och robust optimistisk mutation:
 * 1. onMutate: Avbryt queries + Snapshot av föregående data + Optimistisk uppdatering.
 * 2. onError: Rulla tillbaka till sparad snapshot.
 * 3. onSettled: Invalidera alltid för att synka med sanningen på servern.
 */
export async function executeOptimisticToggle(
  queryClient: QueryClient,
  todoId: string,
  mutationFn: () => Promise<void>
) {
  // 1. Avbryt eventuella utgående refetches så de inte skriver över vår optimistiska uppdatering
  await queryClient.cancelQueries({ queryKey: TODOS_KEY });

  // 2. Snapshot av föregående värde
  const previousTodos = queryClient.getQueryData<Todo[]>(TODOS_KEY);

  // 3. Optimistisk uppdatering i cachen
  if (previousTodos) {
    queryClient.setQueryData<Todo[]>(TODOS_KEY, (old = []) =>
      old.map((todo) =>
        todo.id === todoId ? { ...todo, completed: !todo.completed } : todo
      )
    );
  }

  try {
    // 4. Utför själva nätverksanropet
    await mutationFn();
  } catch (err) {
    // 5. ROLLBACK vid fel: Återställ från sparad snapshot!
    if (previousTodos) {
      queryClient.setQueryData(TODOS_KEY, previousTodos);
    }
    throw err;
  } finally {
    // 6. Sätt alltid sista ordet via invalidering
    queryClient.invalidateQueries({ queryKey: TODOS_KEY });
  }
}
