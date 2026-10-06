# 🚀 04-optimistic-updates: TanStack Query Mutation & Rollback

## 1. Problem: Vad kraschar eller varför bryr vi oss?
I moderna appar förväntar sig användare omedelbar respons när de klickar på "Like", "Spara" eller bockar av en todo.
Två vanliga misstag uppstår:
1. **Långsam "Spinner UX":** Gränssnittet fryser med en laddningsindikator i 1–2 sekunder tills servern svarat med 200 OK.
2. **Trasig Desynkad Optimism (Utan Rollback):** Utvecklaren uppdaterar lokalt tillstånd direkt för att göra UI snabbt, men om servern svarar med `500 Internal Server Error` eller `403 Forbidden` finns ingen felåterställning. Användaren ser att uppgiften är markerad som klar, men vid nästa sidomladdning har ändringen försvunnit!

---

## 2. Keyword Bingo (Facit-lingo)
* **Optimistic UI (Optimistiska uppdateringar):** UI antar att anropet kommer att lyckas och uppdaterar omedelbart cachen innan servern har svarat.
* **`cancelQueries`:** Avbryter pågående utgående refetches för att förhindra att äldre data skriver över vår optimistiska uppdatering.
* **Snapshot Rollback (`context.previousData`):** Att spara undan en kopia av cachen i `onMutate` och returnera den så att `onError` kan återställa ursprungstillståndet vid fel.
* **`onSettled` Invalidation:** Att alltid invalidera queryn när mutationen är klar (oavsett om den lyckades eller misslyckades) för att garantera att klienten synkar med serverns sanna tillstånd.
* **Race Condition on Concurrent Mutations:** När två optimistiska uppdateringar körs i snabb följd mot samma resurs.

---

## 3. Verklighets-svar (30–60 sek)
> *"För att ge användaren en blixtsnabb upplevelse använder vi optimistiska uppdateringar via TanStack Query. I `onMutate` avbryter vi först eventuella pågående queries med `cancelQueries` för att undvika race conditions. Sedan sparar vi en snapshot av nuvarande cache-data och uppdaterar cachen omedelbart med det förväntade resultatet. Om serveranropet misslyckas fångar vi det i `onError` och rullar omedelbart tillbaka cachen med hjälp av vår sparade snapshot. Slutligen kör vi alltid `invalidateQueries` i `onSettled` så att klienten garanterat återfår serverns sanna tillstånd."*

---

## 4. Felsökningsguide (Kommandon & Verktyg)

### A. Simulera trasigt nätverk i DevTools
1. Öppna Chrome DevTools -> **Network**.
2. Välj **Fast 3G** eller **Offline** i throttling-dropdownen.
3. Klicka på en knapp: Verifiera att UI reagerar inom 16ms (1 frame) och att en banner eller toast visas och återställer statet om anropet timear ut.

---

## 5. Koden & Verifiering

* **The Flaw:** [FlawedMutation.ts](file:///frontend/04-optimistic-updates/FlawedMutation.ts) – Uppdaterar UI utan felhantering eller rollback vid nätverksfel.
* **The Fix:** [OptimisticTodoMutation.ts](file:///frontend/04-optimistic-updates/OptimisticTodoMutation.ts) – Komplett mönster med `cancelQueries`, snapshot, `onError` rollback och `onSettled`.
* **Verification Test:** [OptimisticRollback.test.ts](file:///frontend/04-optimistic-updates/OptimisticRollback.test.ts) – Bevisar att rollback återställer tidigare data när API kastar 500.
