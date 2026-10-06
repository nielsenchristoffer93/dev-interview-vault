# ⚡ 02-rendering-performance: Re-renders, useCallback & Virtualisering

## 1. Problem: Vad kraschar eller varför bryr vi oss?
I stora frontend-applikationer leder dålig render-prestanda till:
1. **Input Lag & UI Jank:** När användaren skriver i ett sökfält laggar gränssnittet märkbart (<15 FPS). Detta beror på att föräldrakomponentens tillstånd (`searchQuery`) triggar full omrendering av ett helt underträd med hundratals barnkomponenter.
2. **Bruten Referential Equality:** Att skicka inline arrow-funktioner (`onClick={() => handleClick(id)}`) eller inline objekt-props (`style={{ margin: 10 }}`) skapar nya minnesadresser vid varje render. Därmed fungerar inte `React.memo` och komponenten renderas alltid om.
3. **DOM Node Thrashing:** Att rendera 10 000 listelement samtidigt skapar 50 000+ DOM-noder. Webbläsarens layout-engine och garbage collector kvävs.

---

## 2. Keyword Bingo (Facit-lingo)
* **Referential Equality (Referenslikhet):** Javascript jämför objekt och funktioner med referens (`===` eller `Object.is`). Två funktioner med samma kod är inte lika om de allokerats i olika render-cykler.
* **Reconciliation (Avstämning):** Processen där Reacts fiber-motor jämför Virtual DOM-träd och avgör vad som faktiskt behöver målas i den riktiga webbläsarens DOM.
* **`React.memo`:** Higher-Order Component (HOC) som memoiserar en komponents render-resultat och skippar re-render om props är referensmässigt identiska.
* **`useCallback` & `useMemo`:**
  * `useCallback`: Cachar en funktionsdefinition mellan renderingar.
  * `useMemo`: Cachar resultatet av en beräkningstopp (t.ex. filtrering/sortering av tusentals objekt).
* **Virtualisering / Windowing:** Teknik (t.ex. `@tanstack/react-virtual` eller `react-window`) som enbart monterar de element i DOM:en som för tillfället ryms i användarens viewport (scrollfönster), oavsett om listan har 100 000 rader.

---

## 3. Verklighets-svar (30–60 sek)
> *"Prestandaproblem i React beror oftast på onödiga re-renders eller för många noder i DOM:en. Om en föräldrakomponent renderas om, renderas alla dess barn som standard också om. För att förhindra detta kapslar vi tunga barnkomponenter i `React.memo` och ser till att inte bryta referenslikheten med inline callbacks – vi stabiliserar funktioner med `useCallback` och tunga beräkningar med `useMemo`. Men om vi har tusentals rader hjälper inte memoisering ensamt; då måste vi virtualisera listan med fönsterhantering så att webbläsaren enbart renderar de 20 rader som faktiskt syns i viewporten."*

---

## 4. Felsökningsguide (Kommandon & Verktyg)

### A. React Profiler & Highlight Updates
1. Installera **React Developer Tools**.
2. Klicka på kugghjulet (Settings) -> Bocka i **"Highlight updates when components render"**.
3. Om hela skärmen blinkar grönt/gult när du skriver en enda bokstav i ett input-fält har du en kaskad av onödiga omrenderingar!

### B. Chrome Performance Profiling
1. Öppna DevTools -> **Performance**.
2. Spela in 5 sekunder interaktion.
3. Kika efter långa **"Long Tasks" (> 50 ms)** färgade i rött, samt Main Thread som domineras av `commitRoot` och `renderRoot`.

---

## 5. Koden & Verifiering

* **The Flaw:** [FlawedRenderList.tsx](file:///frontend/02-rendering-performance/FlawedRenderList.tsx) – Inline funktioner bryter `React.memo` och renderar 5 000 noder direkt.
* **The Fix:** [OptimizedVirtualList.tsx](file:///frontend/02-rendering-performance/OptimizedVirtualList.tsx) – Använder `React.memo`, `useCallback` och virtuellt fönster.
* **Verification Test:** [RenderPerformance.test.ts](file:///frontend/02-rendering-performance/RenderPerformance.test.ts) – Verifierar att stabila referenser förhindrar re-render-anrop.
