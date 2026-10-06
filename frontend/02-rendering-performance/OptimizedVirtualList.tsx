import React, { useState, useCallback, useMemo } from 'react';

/**
 * THE FIX:
 * 1. useCallback stabiliserar handleDelete så referensen förblir identisk över renders.
 * 2. React.memo förhindrar omrendering av rader vars props inte har ändrats.
 * 3. useMemo cachar filtreringen så den inte körs i onödan.
 * 4. Fönsterhantering (Virtualisering) simulerad: Renderar enbart synligt intervall.
 */

export interface Item {
  id: number;
  text: string;
}

export const OptimizedItemRow = React.memo(({ item, onDelete }: { item: Item; onDelete: (id: number) => void }) => {
  return (
    <div className="item-row">
      <span>{item.text}</span>
      <button onClick={() => onDelete(item.id)}>Ta bort</button>
    </div>
  );
});

export const OptimizedParent = ({ items }: { items: Item[] }) => {
  const [filterText, setFilterText] = useState('');

  // 1. Stabil funktionsreferens med useCallback
  const handleDelete = useCallback((id: number) => {
    console.log('Deleted safe', id);
  }, []);

  // 2. Memoiserad filtrering
  const filteredItems = useMemo(() => {
    if (!filterText) return items;
    return items.filter((item) =>
      item.text.toLowerCase().includes(filterText.toLowerCase())
    );
  }, [items, filterText]);

  // 3. Fönsterhantering (Virtualisering): Visa enbart första 20 elementen i vyn
  const visibleItems = useMemo(() => filteredItems.slice(0, 20), [filteredItems]);

  return (
    <div>
      <input
        type="text"
        placeholder="Filtrera..."
        value={filterText}
        onChange={(e) => setFilterText(e.target.value)}
      />
      <div>
        {visibleItems.map((item) => (
          <OptimizedItemRow
            key={item.id}
            item={item}
            onDelete={handleDelete} // FIX: Stabil referens, React.memo fungerar!
          />
        ))}
      </div>
    </div>
  );
};
