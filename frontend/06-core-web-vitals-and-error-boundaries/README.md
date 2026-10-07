# ⚡ Frontend: Core Web Vitals (LCP, INP, CLS) & Error Boundaries

> **Intervju-fokus:** White Screen of Death, React Error Boundaries (`getDerivedStateFromError` & `componentDidCatch`), Cumulative Layout Shift (CLS), Interaction to Next Paint (INP) vs FID, och Largest Contentful Paint (LCP) optimering.

---

## 🎯 Scenario 1: The White Screen of Death (Saknade Error Boundaries)

### Problem (Vad kraschar / varför bryr vi oss)
En användare är i kassan på en stor e-handelsplattform. 
En rekommendations-widget längst ner på sidan kraschar med:
```text
Uncaught TypeError: Cannot read properties of undefined (reading 'price')
```
Eftersom felet inte fångas av någon **React Error Boundary** avmonterar React hela komponentträdet upp till root!
Hela skärmen blir helt vit ("White Screen of Death"). Användaren kan varken slutföra sitt köp, se sin varukorg eller navigera vidare. 
E-handeln förlorar tusentals kronor per minut på grund av en trasig widget som inte ens behövdes för checkout!

---

### Keyword Bingo (Facit-lingo)
* **Error Boundary:** En React-klasskomponent som fångar JavaScript-fel var som helst i dess underliggande komponentträd, loggar felet och visar ett fallback-UI istället för att krascha hela appen.
* **`getDerivedStateFromError(error)`:** Statisk livscykelmetod i Error Boundary som används för att rendera ett fallback-UI vid fel i underordnade komponenter.
* **`componentDidCatch(error, errorInfo)`:** Livscykelmetod i Error Boundary för biverkningar (side-effects), typiskt för att skicka stacktrace och kontext till telemetritjänster som Sentry eller Datadog.
* **White Screen of Death (WSoD):** Beteendet i React 16+ där ohanterade fel under rendering gör att hela komponentträdet avmonteras och lämnar användaren med en tom vit sida.
* **Granular / Isolated Error Boundaries:** Designmönstret att kapsla in perifera moduler (t.ex. sidopaneler, kommentarer, annonser) i egna individuella boundaries så att kärnflödet (kassa, inloggning) fortsätter fungera.
* **Graceful Degradation:** Förmågan hos ett system att fortsätta leverera sina primära funktioner även när perifera delsystem slutar fungera.

---

### Verklighets-svar (30–60 sek)
> "I modern React är ohanterade renderingsfel katastrofala eftersom React automatiskt avmonterar hela root-trädet vid ohanterade exceptions, vilket ger kunden en vit skärm.
> 
> Som senior sätter jag aldrig enbart en global Error Boundary i appens root. Jag implementerar **granulära Error Boundaries** runt varje oberoende domänkomponent – exempelvis runt rekommendationer, sökfält och sidofält:
> 1. Vi implementerar en återanvändbar `ErrorBoundary` med `getDerivedStateFromError` för att visa ett snyggt fallback-UI (eller tyst dölja icke-kritiska widgets).
> 2. `componentDidCatch` skickar felkontext och komponent-stack till Sentry med session-taggar.
> 3. Vi tillhandahåller en 'Försök igen'-knapp med state-reset så användaren kan återhämta sig utan att ladda om sidan."

---

## 🎯 Scenario 2: CLS-mardrömmen – Content Jumping & Felklick

### Problem (Vad kraschar / varför bryr vi oss)
En användare på mobil ska trycka på knappen **"Avbryt prenumeration"**. 
Exakt 20 millisekunder innan fingret nuddar skärmen laddas en annonsbanderoll ovanför knappen in asynkront utan fördefinierade mått.
Hela sidan förskjuts nedåt med 150 pixlar!
Fingret träffar istället knappen **"Bekräfta 12 månaders bindningstid (999 kr)"**.
Kunden blir rasande, Google sänker sajtens SEO-ranking dramatiskt på grund av underkänd **Cumulative Layout Shift (CLS > 0.25)**, och konverteringen rasar.

---

### Keyword Bingo (Facit-lingo)
* **CLS (Cumulative Layout Shift):** Core Web Vital-metrik som mäter den totala summan av alla oväntade layoutförskjutningar som inträffar under en sidas hela livscykel. Mål: under 0.1.
* **Aspect-Ratio & Dimension Reservation:** Att alltid reservera plats i förväg för asynkront innehåll via CSS `aspect-ratio` eller explicita `width`/`height`-attribut på `<img>` och banners.
* **Skeleton Screen / Placeholder:** En visuell platshållare med identiska dimensioner som slutinnehållet för att förhindra reflow när data anländer.
* **FOUT & FOIT (Web Fonts Layout Shift):** Flash of Unstyled Text / Flash of Invisible Text som orsakar layoutskift när typsnitt laddas; motverkas med `font-display: optional` eller `swap` med metric overrides (`size-adjust`).
* **Dynamic DOM Injection:** Att infoga nya element ovanför befintligt innehåll; bör istället infogas under eller i en container med fast reserverad höjd.

---

### Verklighets-svar (30–60 sek)
> "CLS är en direkt mätare på användarfrustration och straffas hårt av Googles rankingalgoritm om poängen överstiger 0.1. Den vanligaste orsaken är sena bilder, dynamiska annonser eller fonter som puttar ner befintligt innehåll medan användaren interagerar.
> 
> Som senior förhindrar jag layout shifts genom tre strikta regler:
> 1. **Reservera alltid plats:** Alla bilder, videor och annonsplatser har fördefinierade CSS `aspect-ratio` eller fasta skeleton-containers så att layouten är stabil innan tillgångarna anländer.
> 2. **Font-optimering:** Vi använder `font-display: optional` eller justerade font-metrics för att eliminera FOUT-skift.
> 3. **DOM-injektion:** Nya notiser eller dynamiska element läggs antingen i fasta toasts ovanpå sidan (fixed/absolute) eller i reserverade containers, aldrig genom att trycka ner befintligt innehåll."

---

## 🎯 Scenario 3: INP (Interaction to Next Paint) vs Main Thread Freezing

### Problem (Vad kraschar / varför bryr vi oss)
En användare klickar på ett filter i en stor tabell med 5 000 rader. 
Klicket känns helt dött – ingenting händer på skärmen under 700 ms. Användaren klickar tre gånger till i frustration.
När sidan väl vaknar körs alla fyra filteromräkningar i rad, UI:t fryser i ytterligare två sekunder!
I Google Search Console lyser sidan rött med **"Poor INP (> 500 ms)"**.

**Varför?**
Klick-hanteraren körde en tung synkron JavaScript-loop direkt på **Main Thread** utan att ge kontrollen tillbaka till webbläsaren för att rita ut nästa bildruta (paint).

---

### Keyword Bingo (Facit-lingo)
* **INP (Interaction to Next Paint):** Core Web Vital-metrik (som ersatte FID i mars 2024) som mäter sidans övergripande responsivitet genom att klocka den längsta latensen mellan en användarinteraktion och nästa visuella uppdatering. Mål: under 200 ms.
* **FID (First Input Delay):** Den äldre metriken som enbart mätte fördröjningen till första anropet, men missade hur länge UI:t faktiskt var fryst under renderingen.
* **Long Tasks (Main Thread Blocking):** JavaScript-exekveringar som tar längre än 50 ms och blockerar webbläsarens händelsehantering och rendering.
* **`startTransition` & `useDeferredValue`:** React 18 Concurrent-funktioner för att markera icke-brådskande tillståndsuppdateringar så att brådskande interaktioner (klick, tangenttryck) prioriteras direkt.
* **Time Slicing / Yielding:** Tekniken att dela upp tunga beräkningar med `scheduler.yield()` eller `setTimeout`/`requestAnimationFrame` så att webbläsaren hinner rendera mellan delstegen.
* **Web Workers:** Att flytta tunga beräkningar (filtrering av 50 000 objekt, PDF-generering) helt utanför Main Thread.

---

### Verklighets-svar (30–60 sek)
> "Sedan Google ersatte FID med **INP** räcker det inte att webbläsaren *påbörjar* hanteringen snabbt – användaren måste se ett visuellt resultat (Next Paint) inom 200 ms från interaktionen. Långa synkrona beräkningar på Main Thread blockerar webbläsarens Event Loop.
> 
> Vår lösningsarkitektur:
> 1. Vi skiljer på brådskande feedback och tunga uppdateringar. Klicket sätter omedelbart ett visuellt tryck-/laddningstillstånd synkront.
> 2. Själva den tunga filtreringen kapslas in i **React 18 `startTransition`** så att React kan avbryta renderingen om användaren klickar igen.
> 3. För gigantiska dataset flyttar vi beräkningen till en **Web Worker** eller virtualiserar vyn så att DOM-storleken förblir minimal."

---

## 🛠️ Koden & Verifiering

* **The Flaw:** [FlawedWebVitals.tsx](file:///frontend/06-core-web-vitals-and-error-boundaries/FlawedWebVitals.tsx)
  * Renderingsfel som kraschar hela appen utan Error Boundary.
  * Dynamiska element utan reserverad plats som orsakar mätbara layout shifts.
  * Synkron blockerande loop som fryst main thread under interaktion.
* **The Fix:** [ResilientErrorBoundary.tsx](file:///frontend/06-core-web-vitals-and-error-boundaries/ResilientErrorBoundary.tsx)
  * Återanvändbar `ErrorBoundary` med `getDerivedStateFromError`, fallback-UI, Sentry-telemetri och `reset`-återställning.
  * `StableAspectContainer` med reserverad CSS `aspect-ratio` och layout shift-skydd.
  * Icke-blockerande transition / time-sliced bearbetning.
* **Testerna:** [WebVitalsAndErrorBoundary.test.ts](file:///frontend/06-core-web-vitals-and-error-boundaries/WebVitalsAndErrorBoundary.test.ts)
  * Verifierar Error Boundary felhantering, telemetriloggar, CLS-reservationsberäkning och icke-blockerande state-övergångar.

---

## 🧪 Så här kör du testerna

```bash
cd frontend
npm test
```
eller för att enbart köra denna modul:
```bash
npx vitest run 06-core-web-vitals-and-error-boundaries
```
