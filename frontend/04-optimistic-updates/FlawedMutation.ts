/**
 * THE FLAW:
 * Uppdaterar UI optimistiskt utan att spara en snapshot eller hantera rollback.
 * Om servern svarar med 500 eller nätverket dör visar UI felaktigt
 * att handlingen lyckades, vilket leder till "Ghost State".
 */

export interface Todo {
  id: string;
  title: string;
  completed: boolean;
}

export class FlawedMutationRunner {
  private cache: Todo[] = [];

  constructor(initialData: Todo[]) {
    this.cache = [...initialData];
  }

  public async toggleTodo(todoId: string, apiCall: () => Promise<void>): Promise<void> {
    // Uppdaterar direkt utan rollback-skyddsnät
    this.cache = this.cache.map((t) =>
      t.id === todoId ? { ...t, completed: !t.completed } : t
    );

    try {
      await apiCall();
    } catch (error) {
      // FLAW: Felet fångas, men cachen återställs INTE!
      console.error('API call failed, UI is now out of sync!');
    }
  }

  public getData(): Todo[] {
    return this.cache;
  }
}
