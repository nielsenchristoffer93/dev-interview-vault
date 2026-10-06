import React, { useState } from 'react';

/**
 * THE FLAW:
 * 1. Skickar en anonym inline-funktion `onClick={() => handleDelete(item.id)}`.
 *    Vid varje bokstav som skrivs i input skapas en helt ny funktionsinstans.
 * 2. Skickar inline objekt `style={{ padding: '8px' }}` vilket alltid har ny referens.
 * 3. Renderar 5 000 element direkt i DOM:en utan fönsterhantering/virtualisering.
 */

export interface Item {
  id: number;
  text: string;
}

export const FlawedItemRow = React.memo(({ item, onDelete }: { item: Item; onDelete: (id: number) => void }) => {
  // Trots React.memo tvingas denna komponent att re-rendera varje gång
  // eftersom onDelete-propen skapas på nytt i föräldern!
  return (
    <div style={{ display: 'flex', gap: '8px', padding: '4px' }}>
      <span>{item.text}</span>
      <button onClick={() => onDelete(item.id)}>Ta bort</button>
    </div>
  );
});

export const FlawedParent = ({ items }: { items: Item[] }) => {
  const [filterText, setFilterText] = useState('');

  const handleDelete = (id: number) => {
    console.log('Delete item', id);
  };

  return (
    <div>
      <input
        type="text"
        placeholder="Filtrera..."
        value={filterText}
        onChange={(e) => setFilterText(e.target.value)}
      />
      {/* Alla 5000 barn renderas om för varje tangenttryckning! */}
      {items.map((item) => (
        <FlawedItemRow
          key={item.id}
          item={item}
          onDelete={() => handleDelete(item.id)} // FLAW: Ny referens vid varje render!
        />
      ))}
    </div>
  );
};
