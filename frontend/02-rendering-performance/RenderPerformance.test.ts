import { describe, it, expect } from 'vitest';

describe('02-rendering-performance: Referential Equality & Memoization', () => {
  it('The Flaw: Nyskapade inline funktioner har olika referens i Object.is och bryter React.memo', () => {
    const createInlineHandler = (id: number) => () => console.log(id);

    const fn1 = createInlineHandler(1);
    const fn2 = createInlineHandler(1);

    // Bevis: Olika referenser! React.memo ser detta som prop-ändring och tvingar omrendering
    expect(fn1 === fn2).toBe(false);
    expect(Object.is(fn1, fn2)).toBe(false);
  });

  it('The Fix: Memoiserad referens är identisk och tillåter React.memo att skippa rendering', () => {
    const memoizedHandler = (id: number) => console.log(id);

    const ref1 = memoizedHandler;
    const ref2 = memoizedHandler;

    // Bevis: Samma minnesadress! React.memo skippar omrendering
    expect(ref1 === ref2).toBe(true);
    expect(Object.is(ref1, ref2)).toBe(true);
  });
});
