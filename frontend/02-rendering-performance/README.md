# ⚡ 02-rendering-performance: Re-renders, useCallback & Virtualisering

Denna modul täcker 3 fundamentala render- och prestandautmaningar i moderna React-applikationer. Varje scenario presenterar problemet (vad som kraschar och varför vi bryr oss), dess specifika **Keyword Bingo** (facit-lingo), samt ett **Verklighets-svar (30–60 sek)** skräddarsytt för tekniska intervjuer.

---

## 1. Koncept & Scenarier

### Scenario 1: Onödiga re-renders & Input Lag (Parent-to-Child Cascades)

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
När en användare skriver i ett sökfält uppdateras föräldrakomponentens tillstånd (`searchQuery`) vid varje tangenttryckning. Som standard renderar React om alla barnkomponenter i hela underträdet när en förälder renderas om, oavsett om barnens props ändrats eller inte.

Om underträdet innehåller tunga grafer, tabeller eller hundratals element kraschar bildfrekvensen till < 15 FPS. Användaren upplever påtaglig fördröjning mellan knapptryckning och skärmuppdatering (**Input Lag** / **UI Jank**).

#### 2. Keyword Bingo (Facit-lingo)
* **Input Lag & UI Jank:** När webbläsarens huvudtråd blockeras av React-rendering så att gränssnittet tappar bildrutor (framerate drops under 60 FPS).
* **Parent-to-Child Re-render Cascade:** Reacts standardbeteende att rekursivt rendera om alla barn när en förälders state muteras.
* **Component Colocation / State Pushing:** Att flytta tillståndet så långt ner i komponentträdet som möjligt så att inte hela sidan drabbas.
* **Children as Props / Lift Content Up:** Mönstret att skicka statiska barn via `children`, vilket gör att React inte renderar om dem när förälderns lokala state uppdateras.

#### 3. Verklighets-svar (30–60 sek)
> *"Input lag uppstår när frekventa tillståndsförändringar, såsom tangenttryckningar i ett inputfält, triggar en hel kaskad av omrenderingar genom hela komponentträdet. Det första och effektivaste steget är inte att strössla med memoisering, utan att isolera tillståndet – antingen genom att pusha ner statet till en dedikerad sökfältskomponent, eller lyfta ut det tunga innehållet och skicka det via `children`-prop. På så sätt renderas bara den lilla input-komponenten om medan det tunga trädet förblir orört."*

**Labb & Kod:** [FlawedRenderList.tsx](file:///frontend/02-rendering-performance/FlawedRenderList.tsx) vs [OptimizedVirtualList.tsx](file:///frontend/02-rendering-performance/OptimizedVirtualList.tsx)

---

### Scenario 2: Bruten Referential Equality (`useCallback`, `useMemo` & `React.memo`)

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
En utvecklare försöker optimera en tung barnkomponent genom att kapsla den i `React.memo`. Men barnkomponenten fortsätter att renderas om vid exakt varje cykel.

Orsaken är bruten **Referential Equality**: föräldern skickar en inline callback (`onClick={() => handleClick(id)}`) eller ett inline-objekt (`style={{ margin: 10 }}`). I JavaScript är två funktioner eller objekt aldrig lika (`===`) om de allokerats i olika minnesadresser under två olika render-körningar. `React.memo` ser en "ny" prop varje gång och memoiseringen blir 100% verkningslös.

#### 2. Keyword Bingo (Facit-lingo)
* **Referential Equality (Referenslikhet):** Jämförelse via `Object.is` eller `===` som utvärderar minnespekare snarare än strukturellt innehåll.
* **`React.memo`:** Higher-Order Component som memoiserar render-resultatet och skippar re-render om alla props har identisk referenslikhet.
* **`useCallback`:** React-hook som bevarar samma funktionsinstans i minnet mellan render-cykler tills dess beroenden ändras.
* **`useMemo`:** React-hook som cachar det beräknade resultatet av en tung CPU-operation (t.ex. sortering/filtrering).
* **Inline Callbacks Anti-pattern:** Att definiera anonyma arrow-funktioner direkt i JSX-props som skickas till memoiserade komponenter.

#### 3. Verklighets-svar (30–60 sek)
> *"När `React.memo` inte förhindrar omrenderingar beror det nästan alltid på bruten referenslikhet. Om du skickar inline-funktioner eller inline-objekt skapas en ny minnesadress vid varje render. För `React.memo` ser det ut som att proppen har ändrats och komponenten renderas om i alla fall. Lösningen är att stabilisera funktionsreferenser med `useCallback` och tunga beräkningsresultat med `useMemo`. Då bevaras referenserna intakta mellan körningarna och `React.memo` kan framgångsrikt avbryta onödiga renderingar."*

**Labb & Kod:** [OptimizedVirtualList.tsx](file:///frontend/02-rendering-performance/OptimizedVirtualList.tsx) & [RenderPerformance.test.ts](file:///frontend/02-rendering-performance/RenderPerformance.test.ts)

---

### Scenario 3: DOM Node Thrashing & Listvirtualisering (Windowing)

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
När en tabell eller lista med 10 000 rader ska visas försöker applikationen rendera alla rader samtidigt i DOM:en. Detta skapar över 50 000 individuella DOM-noder.

Webbläsarens layout- och reflow-motor kollapsar, minnesanvändningen exploderar och scrolling blir hackig. Inga mängder av `React.memo` eller `useCallback` kan lösa detta, eftersom själva webbläsarens DOM-träd är överbelastat.

#### 2. Keyword Bingo (Facit-lingo)
* **DOM Node Thrashing:** Extrem prestandaförlust när webbläsaren tvingas beräkna stil och layout för tiotusentals samtidiga DOM-element.
* **Virtualisering / Windowing (Fönsterhantering):** Tekniken att enbart skapa DOM-noder för de element som ryms i användarens synliga viewport plus en liten buffert.
* **Viewport Offset & Dynamic Heights:** Att beräkna scrollposition (`scrollTop`) och dynamiskt ersätta osynliga element med tom padding.
* **`@tanstack/react-virtual` / `react-window`:** De etablerade industristandard-biblioteken för virtuella listor och tabeller.
* **Recycled DOM Nodes:** Återanvändning av ett fast litet antal DOM-element (t.ex. 25–30 st) oavsett om datamängden innehåller 1 000 000 rader.

#### 3. Verklighets-svar (30–60 sek)
> *"För stora datamängder hjälper inte memoisering – webbläsaren klarar helt enkelt inte av att hålla 50 000 DOM-noder vid liv utan att layout och scroll börjar lagga. Lösningen är Virtualisering (Windowing) med bibliotek som `@tanstack/react-virtual`. Istället för att rendera hela listan monterar vi enbart de cirka 20 rader som för tillfället syns i användarens viewport, plus en liten scrollbuffert. DOM:en hålls extremt lättviktig och applikationen scrollar i silkeslena 60 FPS oavsett om listan har hundra eller en miljon rader."*

**Labb & Kod:** [OptimizedVirtualList.tsx](file:///frontend/02-rendering-performance/OptimizedVirtualList.tsx)

---

## 2. Felsökningsguide (Kommandon & Verktyg)

### A. React Profiler & Highlight Updates
1. Installera **React Developer Tools**.
2. Klicka på kugghjulet (Settings) -> Bocka i **"Highlight updates when components render"**.
3. Om hela skärmen blinkar grönt/gult när du skriver en enda bokstav i ett input-fält har du en kaskad av onödiga omrenderingar!

### B. Chrome Performance Profiling
1. Öppna DevTools -> **Performance**.
2. Spela in 5 sekunder interaktion under snabb inmatning/scroll.
3. Leta efter långa **"Long Tasks" (> 50 ms)** färgade med röda flaggor, och kontrollera om Main Thread domineras av `commitRoot` och `renderRoot`.

---

## 3. Koden & Verifiering

* **The Flaw:** [FlawedRenderList.tsx](file:///frontend/02-rendering-performance/FlawedRenderList.tsx) – Inline-funktioner bryter `React.memo` och renderar 5 000 DOM-noder omedelbart.
* **The Fix:** [OptimizedVirtualList.tsx](file:///frontend/02-rendering-performance/OptimizedVirtualList.tsx) – Använder `React.memo`, `useCallback` och listvirtualisering via TanStack Virtual.
* **Verification Test:** [RenderPerformance.test.ts](file:///frontend/02-rendering-performance/RenderPerformance.test.ts) – Verifierar att stabila funktionsreferenser förhindrar re-render-kaskader.

---

## 4. Så här kör du testerna

```bash
cd frontend/02-rendering-performance
npm test
```
