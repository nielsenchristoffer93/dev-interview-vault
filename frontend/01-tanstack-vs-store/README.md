# 🔄 01-tanstack-vs-store: Server Cache vs UI State (Zustand/Pinia)

Denna modul täcker 4 fundamentala tillstånds- och caching-utmaningar i moderna SPA-arkitekturer (React/Vue). Varje scenario presenterar problemet (vad som kraschar och varför vi bryr oss), dess specifika **Keyword Bingo** (facit-lingo), samt ett **Verklighets-svar (30–60 sek)** skräddarsytt för tekniska intervjuer.

---

## 1. Koncept & Scenarier

### Scenario 1: Server State vs Client UI State (Stale Data & Boilerplate i Global Store)

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
I många äldre eller feldesignade React/Vue-kodbaser behandlas API-data från backend som om det vore globalt klienttillstånd. Utvecklare dumpar rå API-data i Redux, Zustand eller Pinia.

Detta leder till massiv boilerplate (`isLoading`, `data`, `isError`) och **Stale Data**: när en användare byter flik eller en kollega uppdaterar en resurs i databasen visar applikationen inaktuell information tills användaren tvingar en manuell sidomladdning (F5).

#### 2. Keyword Bingo (Facit-lingo)
* **Server State vs Client (UI) State:**
  * *Server State:* Asynkront tillstånd som ägs av backend, delas av flera användare och är en tidsbunden ögonblicksbild (cache).
  * *Client (UI) State:* Synkront tillstånd lokalt i webbläsaren (t.ex. modal öppen/stängd, mörkt tema, aktiv drag-and-drop).
* **Separation of Concerns:** Arkitekturregeln att aldrig blanda servercache med UI-tillstånd.
* **Stale Data:** Inaktuell information som visas i klienten på grund av saknad polling eller bakgrundsrefetching.
* **Boilerplate Reduction:** Hur TanStack Query eliminerar manuella reducer-actions och statusflaggor.

#### 3. Verklighets-svar (30–60 sek)
> *"Det vanligaste arkitekturfelet i frontend är att behandla backend-data som ett globalt tillstånd i Redux eller Zustand. API-data är inte klientens tillstånd – det är en asynkron cache av serverns tillstånd. Sparar man det i ett globalt store måste man skriva massor av boilerplate för laddningstillstånd, felhantering och manuell synk. Vi separerar detta helt: TanStack Query hanterar all serverdata med automatisk bakgrundsuppdatering och livscykelhantering, medan Zustand eller Pinia behålls uteslutande för rent synkront UI-tillstånd som modaler, filter och temaval."*

**Labb & Kod:** [FlawedGlobalStore.ts](file:///frontend/01-tanstack-vs-store/FlawedGlobalStore.ts) vs [ModernStateArchitecture.tsx](file:///frontend/01-tanstack-vs-store/ModernStateArchitecture.tsx)

---

### Scenario 2: Request Deduplication & Race Conditions vid parallella anrop

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
När 5 olika komponenter på samma sida oberoende av varandra behöver samma data (t.ex. användarprofil eller kundkorg) och använder traditionella `useEffect`-anrop, skickas **5 duplicerade nätverksanrop** samtidigt mot backend (**Over-fetching**).

Dessutom uppstår ofta *Race Conditions* vid snabba filterbyten: om anrop A tar 500ms och anrop B tar 100ms kan svar A anlända sist och skriva över det nyare svaret B med gammal data.

#### 2. Keyword Bingo (Facit-lingo)
* **Request Deduplication:** Mekanism som delar samma pågående HTTP-löfte mellan flera komponenter så att endast 1 nätverksanrop skickas.
* **Network Race Condition:** När asynkrona svar anländer i en annan ordning än förfrågningarna skickades och korrumperar UI:t.
* **Query Key Serialization:** Hur unika nycklar (`['user', id]`) serialiseras och används som identifierare i TanStack Querys interna cache.
* **Automatic Cancellation:** Avbrytande av föråldrade nätverksanrop via `AbortController` när sökparametrar ändras.

#### 3. Verklighets-svar (30–60 sek)
> *"Om flera komponenter på samma dashboard monterar `useEffect` för att hämta samma användardata bombarderas backend med duplicerade anrop. Med TanStack Query löses detta automatiskt genom Request Deduplication: alla komponenter deklarerar samma Query Key (`['currentUser']`), vilket gör att bara ett nätverksanrop skickas medan övriga komponenter delar samma löfte. Vid filterbyten avbryts dessutom tidigare anrop via AbortController för att helt eliminera nätverkskapplöpningar."*

**Labb & Kod:** [ModernStateArchitecture.tsx](file:///frontend/01-tanstack-vs-store/ModernStateArchitecture.tsx) & [StateSeparation.test.ts](file:///frontend/01-tanstack-vs-store/StateSeparation.test.ts)

---

### Scenario 3: Stale-While-Revalidate (SWR) & Window Focus Refetching

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
När en användare navigerar mellan olika flikar i en webbapp möts de ofta av tomma skärmar eller blinkande spinners varje gång en vy öppnas. Om data har ändrats i databasen under tiden märker inte användaren detta förrän sidan laddas om manuellt.

Detta skapar en trög och frustrerande användarupplevelse där gränssnittet känns dött och opålitligt.

#### 2. Keyword Bingo (Facit-lingo)
* **Stale-While-Revalidate (SWR):** Mönstret att omedelbart visa cachad data från minnet medan en bakgrundsförfrågan i det tysta hämtar och validerar senaste versionen.
* **Window Focus Refetching (`refetchOnWindowFocus`):** Automatisk uppdatering av data så fort användaren byter tillbaka till webbläsarfliken.
* **`staleTime` vs `gcTime` (f.d. `cacheTime`):**
  * *`staleTime`:* Hur länge data betraktas som färsk innan en refetch tillåts.
  * *`gcTime`:* Hur länge inaktiv data sparas i minnet innan Garbage Collection rensar den.
* **Optimistic Offline Support:** Att kunna använda appen med cachad data vid tillfälliga nätverksavbrott.

#### 3. Verklighets-svar (30–60 sek)
> *"För att ge användaren en blixtsnabb upplevelse tillämpar vi Stale-While-Revalidate via TanStack Query. När användaren navigerar till en vy visar vi omedelbart den cachade datan utan någon laddnings-spinner, medan biblioteket i bakgrunden skickar en förfrågan för att kontrollera om datan ändrats. Med funktioner som Window Focus Refetching säkerställer vi att så fort användaren återvänder till fliken från en annan app hämtas de senaste ändringarna automatiskt, vilket håller datat färskt utan manuell inblandning."*

**Labb & Kod:** [ModernStateArchitecture.tsx](file:///frontend/01-tanstack-vs-store/ModernStateArchitecture.tsx)

---

### Scenario 4: Cache Invalidation vid mutationer (POST/PUT/DELETE)

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
När en användare skapar en ny post via formulär (t.ex. skapar en order eller raderar en post) försöker traditionell kod manuellt manipulera globala arrayer i Redux/Zustand:
`state.orders = state.orders.filter(...)`.

Denna manuella datamanipulation är extremt buggbenägen: sorteringsordningar blir felaktiga, paginering desynkas, och summeringar stämmer inte med databasens faktiska beräkningar.

#### 2. Keyword Bingo (Facit-lingo)
* **Declarative Cache Invalidation (`queryClient.invalidateQueries`):** Att markera berörda query-nycklar som föråldrade istället för att manuellt pussla i klientens arrayer.
* **Automatic Refetch on Mutation:** Att automatiskt trigga en ny hämtning när en mutation bekräftats av servern.
* **Exact vs Fuzzy Key Matching:** Möjligheten att invalidera en hel gren (t.ex. alla queries som börjar med `['orders']`).
* **Source of Truth:** Principen att backend alltid är den enda källan till sanning för affärsdata.

#### 3. Verklighets-svar (30–60 sek)
> *"Vid mutationer försöker vi aldrig manuellt modifiera och skarva i djupt kapslade arrayer i klientens state, eftersom det snabbt leder till buggar med sortering och paginering. Istället använder vi deklarativ cache-invalidering: när en `useMutation` slutförs anropar vi `queryClient.invalidateQueries({ queryKey: ['orders'] })`. TanStack Query markerar då datan som föråldrad och hämtar automatiskt den korrekta, officiella datamängden från servern i bakgrunden."*

**Labb & Kod:** [ModernStateArchitecture.tsx](file:///frontend/01-tanstack-vs-store/ModernStateArchitecture.tsx)

---

## 2. Felsökningsguide (Kommandon & Verktyg)

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

## 3. Koden & Verifiering

* **The Flaw:** [FlawedGlobalStore.ts](file:///frontend/01-tanstack-vs-store/FlawedGlobalStore.ts) – Lagrar serverdata i ett globalt store med manuell synkning och risk för stale data.
* **The Fix:** [ModernStateArchitecture.tsx](file:///frontend/01-tanstack-vs-store/ModernStateArchitecture.tsx) – Ren separation mellan TanStack Query (server state) och Zustand (UI state).
* **Verification Test:** [StateSeparation.test.ts](file:///frontend/01-tanstack-vs-store/StateSeparation.test.ts) – Bevisar att query keys deduplicerar nätverksanrop och håller UI-state rent.

---

## 4. Så här kör du testerna

Kör enhetstesterna med Vitest / Jest:
```bash
cd frontend/01-tanstack-vs-store
npm test
```
