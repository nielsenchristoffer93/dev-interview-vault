# 🔄 01-tanstack-vs-store: Server Cache vs UI State (Zustand/Pinia)

## 1. Problem: Vad kraschar eller varför bryr vi oss?
I många React/Vue-kodbaser behandlas extern data från backend som om det vore globalt applikationstillstånd. Utvecklare dumpar rå API-data i Redux, Zustand eller Pinia och tvingas manuellt skriva boilerplate för `isLoading`, `data`, `isError` samt synkronisering:
* **Stale Data (Inaktuell data):** Om användaren byter flik eller en kollega uppdaterar en resurs i databasen visar applikationen inaktuell data tills användaren tvingar en manuell sidomladdning (F5).
* **Race Conditions:** Om två komponenter triggar samma API-anrop skickas duplicerade nätverksrequests (**Over-fetching**), eller så skriver en långsam respons över en snabbare ny respons.
* **Komplex Cache Invalidation:** Att hålla lokalt store synkat med backend vid mutationer (POST/PUT/DELETE) kräver manuell mutation av djupt kapslade objekt i state-trädet.

---

## 2. Keyword Bingo (Facit-lingo)
* **Server State vs Client (UI) State:**
  * **Server State:** Tillhör backend, asynkront, delas av flera användare, är i grunden en *ögonblicksbild (cache)* av ett externt tillstånd.
  * **Client State:** Rent synkront, lokalt i webbläsaren (t.ex. modal öppen/stängd, valt mörkt tema, pågående formulärsutkast).
* **Stale-While-Revalidate (SWR):** Mönstret att omedelbart visa cachad data från minnet medan en bakgrundsförfrågan skickas för att hämta senaste versionen och uppdatera UI sömlöst.
* **Window Focus Refetching:** Automatisk uppdatering av serverdata när användaren återvänder till webbläsarfliken.
* **Request Deduplication:** Om 5 olika komponenter monterar `useQuery(['user'])` samtidigt, skickar TanStack Query endast **1** nätverksanrop och delar löftet.
* **Separation of Concerns:** Zustand/Pinia hanterar *enbart* UI-tillstånd; TanStack Query hanterar *all* asynkron serverdata.

---

## 3. Verklighets-svar (30–60 sek)
> *"Det största arkitekturfelet i frontend är att blanda ihop Server State och Client UI State i ett globalt store som Redux eller Zustand. API-data är inte lokalt tillstånd – det är en asynkron cache av backend-data. Lägger man det i ett globalt store måste man manuellt hantera laddningstillstånd, felhantering, deduplicering och cacheinvalidering. Vi separerar detta helt: TanStack Query tar hand om all serverdata med automatisk cache, stale-while-revalidate och request-deduplicering. Zustand eller Pinia behålls uteslutande för rent synkront UI-tillstånd, som modaler, filter och temaval."*

---

## 4. Felsökningsguide (Kommandon & Verktyg)

### A. Inspektera TanStack Query Cache
Använd `@tanstack/react-query-devtools`:
```tsx
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
// Ger ett grafiskt fönster för att inspektera query keys, status (fresh, stale, fetching, inactive) och data
<ReactQueryDevtools initialIsOpen={false} />
```

### B. Identifiera duplicerade nätverksanrop i DevTools
1. Öppna Chrome DevTools -> **Network**-fliken.
2. Filtrera på `Fetch/XHR`.
3. Navigera mellan två vyer: Om du ser identiska GET-anrop skickas back-to-back saknas request deduplicering eller lämplig `staleTime`.

---

## 5. Koden & Verifiering

* **The Flaw:** [FlawedGlobalStore.ts](file:///frontend/01-tanstack-vs-store/FlawedGlobalStore.ts) – Lagrar serverdata i ett globalt store med manuell synkning och risk för stale data.
* **The Fix:** [ModernStateArchitecture.tsx](file:///frontend/01-tanstack-vs-store/ModernStateArchitecture.tsx) – Ren separation mellan TanStack Query (server state) och Zustand (UI state).
* **Verification Test:** [StateSeparation.test.ts](file:///frontend/01-tanstack-vs-store/StateSeparation.test.ts) – Bevisar att query keys deduplicerar nätverksanrop och håller UI-state rent.
