import { describe, it, expect } from 'vitest';
import { QueryClient } from '@tanstack/react-query';
import { TODOS_KEY, executeOptimisticToggle, Todo } from './OptimisticTodoMutation';
import { FlawedMutationRunner } from './FlawedMutation';

describe('04-optimistic-updates: Optimistic Rollback Lab', () => {
  const initialTodos: Todo[] = [
    { id: 't1', title: 'Bygg säkra endpoints', completed: false },
    { id: 't2', title: 'Förbered arkitekturfrågor', completed: true },
  ];

  it('The Flaw: Vid backend-krasch förblir felaktigt tillstånd sparat i cachen utan rollback', async () => {
    const flawedRunner = new FlawedMutationRunner(initialTodos);

    const failingApiCall = async () => {
      throw new Error('500 Internal Server Error');
    };

    await flawedRunner.toggleTodo('t1', failingApiCall);

    // Bevis på felet: t1 är nu 'true' i UI trots att servern returnerade 500!
    const data = flawedRunner.getData();
    expect(data.find((t) => t.id === 't1')?.completed).toBe(true);
  });

  it('The Fix: executeOptimisticToggle rullar tillbaka cachen till snapshot vid API-fel', async () => {
    const queryClient = new QueryClient();
    queryClient.setQueryData<Todo[]>(TODOS_KEY, initialTodos);

    const failingApiCall = async () => {
      throw new Error('503 Service Unavailable');
    };

    // Försök utföra den optimistiska uppdateringen
    await expect(
      executeOptimisticToggle(queryClient, 't1', failingApiCall)
    ).rejects.toThrow('503 Service Unavailable');

    // Bevis på fixen: Cachen har rullats tillbaka till det ursprungliga tillståndet!
    const recoveredData = queryClient.getQueryData<Todo[]>(TODOS_KEY);
    expect(recoveredData?.find((t) => t.id === 't1')?.completed).toBe(false);
  });
});
