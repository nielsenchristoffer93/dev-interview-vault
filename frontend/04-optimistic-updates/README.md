# 🚀 04-optimistic-updates: TanStack Query Mutation & Rollback

Denna modul täcker 3 fundamentala UX- och mutationsmönster för blixtsnabba gränssnitt i moderna webbapplikationer. Varje scenario presenterar problemet (vad som kraschar och varför vi bryr oss), dess specifika **Keyword Bingo** (facit-lingo), samt ett **Verklighets-svar (30–60 sek)** skräddarsytt för tekniska intervjuer.

---

## 1. Koncept & Scenarier

### Scenario 1: "Spinner UX" & Upplevd Latens (Laddningsindikatorer vs Omedelbar feedback)

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
I traditionella webbapplikationer väntar gränssnittet på ett `200 OK` från servern innan något uppdateras på skärmen. När en användare klickar på "Like", bockar av en todo eller sparar en statusändring visas en blockerande laddnings-spinner i 500ms till 2 sekunder.

Detta resulterar i en seg, styltig upplevelse där användaren upplever systemet som långsamt trots att backend svarar inom normala SLA:er.

#### 2. Keyword Bingo (Facit-lingo)
* **Perceived Latency (Upplevd latens):** Hur snabbt användaren uppfattar att applikationen svarar (ofta viktigare för användarupplevelsen än faktisk nätverkslatens).
* **Optimistic UI (Optimistiska uppdateringar):** Designmönster där gränssnittet antar att anropet kommer att lyckas och omedelbart uppdaterar cachen på 16ms (inom nästa bildruta).
* **Instant Feedback Loop:** Att eliminera laddningsindikatorer för högfrekventa användarinteraktioner.
* **Micro-interactions:** Snabba åtgärder som likes, toggles och avbockningar som kräver noll fördröjning.

#### 3. Verklighets-svar (30–60 sek)
> *"Användare förväntar sig att gränssnittet reagerar omedelbart. Att låsa UI:t med spinners i 1 sekund för enkla åtgärder som att gilla ett inlägg eller bocka av en uppgift skapar en onödigt trög användarupplevelse. Med optimistiska uppdateringar uppdaterar vi klientens lokala cache på bråkdelen av en millisekund innan nätverksanropet ens skickats. Användaren upplever systemet som blixtsnabbt, medan synkroniseringen mot servern sker i bakgrunden."*

**Labb & Kod:** [OptimisticTodoMutation.ts](file:///frontend/04-optimistic-updates/OptimisticTodoMutation.ts)

---

### Scenario 2: Trasig Optimism & Desynkat State (Saknad Rollback vid 500/Nätverksfel)

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
En utvecklare implementerar "optimism" genom att helt enkelt uppdatera lokalt state direkt utan någon felåterställningsmekanism.

När servern kastar `500 Internal Server Error`, `403 Forbidden` (saknad behörighet), eller när mobilnätet dör, visas ändringen fortfarande som genomförd på skärmen. När användaren senare laddar om sidan försvinner ändringen spårlöst (**Ghost Updates**). Användaren tappar förtroendet för applikationen eftersom gränssnittet visat ett falskt tillstånd.

#### 2. Keyword Bingo (Facit-lingo)
* **Ghost Updates / Silent Desync:** När gränssnittet visar en ändring som servern har nekat eller misslyckats med att spara.
* **Rollback Mechanism:** Möjligheten att omedelbart och exakt återställa tidigare tillstånd om nätverksanropet misslyckas.
* **Error Notification / Toast:** Att tydligt kommunicera till användaren att åtgärden inte kunde sparas, samtidigt som UI rullas tillbaka mjukt.
* **Transient vs Fatal Mutation Failure:** Att skilja på nätverksavbrott som kan återförsökas och affärsvalideringsfel som kräver användaråtgärd.

#### 3. Verklighets-svar (30–60 sek)
> *"Optimistiska uppdateringar utan rollback är livsfarliga – om backend kastar 500 eller nekar anropet med 403 tror användaren att uppgiften sparats, men vid nästa sidomladdning är ändringen borta. Det bryter helt mot principen om dataintegritet. För att göra optimistiska uppdateringar säkra måste vi alltid spara en snapshot av det ursprungliga tillståndet innan vi muterar cachen. Vid minsta fel rullar vi omedelbart tillbaka till den sparade snapshoten och meddelar användaren med ett tydligt felmeddelande."*

**Labb & Kod:** [FlawedMutation.ts](file:///frontend/04-optimistic-updates/FlawedMutation.ts) vs [OptimisticTodoMutation.ts](file:///frontend/04-optimistic-updates/OptimisticTodoMutation.ts)

---

### Scenario 3: Den fullständiga TanStack Mutation Lifecycle (`cancelQueries`, snapshot, `onError` rollback & `onSettled`)

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
Om två snabba mutationer körs parallellt på samma resurs, eller om en pågående bakgrundsrefetch slutförs precis medan en optimistisk mutation sker, uppstår en allvarlig *Race Condition*: den äldre bakgrundsdatan skriver över den optimistiska uppdateringen.

Dessutom riskerar cachen att glida ifrån serverns faktiska genererade värden (t.ex. server-genererade ID:n eller tidsstämplar) om den inte slutgiltigt synkas.

#### 2. Keyword Bingo (Facit-lingo)
* **`cancelQueries`:** Avbryter alla pågående utgående refetches för att förhindra att inaktuell serverdata skriver över vår optimistiska uppdatering.
* **`onMutate` Snapshot:** Sparar `previousData` och returnerar ett kontextobjekt som skickas vidare till felhanteraren.
* **`onError(err, newTodo, context)` Rollback:** Återställer cachen med `queryClient.setQueryData(key, context.previousData)`.
* **`onSettled` Invalidation:** Körs **alltid** oavsett om anropet lyckades eller misslyckades (`queryClient.invalidateQueries`) för att garantera att klienten synkar med serverns sanna tillstånd.

#### 3. Verklighets-svar (30–60 sek)
> *"Den kompletta livscykeln för en robust optimistisk mutation i TanStack Query består av fyra steg: I `onMutate` avbryter vi först eventuella pågående queries med `cancelQueries` för att undvika race conditions. Därefter sparar vi en snapshot av nuvarande data och uppdaterar cachen optimistiskt. I `onError` använder vi den sparade snapshoten för att omedelbart återställa cachen om anropet kraschar. Slutligen kör vi alltid `invalidateQueries` i `onSettled` – oavsett framgång eller fel – för att garantera att klienten slutgiltigt synkas med serverns auktoritativa sanning."*

**Labb & Kod:** [OptimisticTodoMutation.ts](file:///frontend/04-optimistic-updates/OptimisticTodoMutation.ts) & [OptimisticRollback.test.ts](file:///frontend/04-optimistic-updates/OptimisticRollback.test.ts)

---

## 2. Felsökningsguide (Kommandon & Verktyg)

### A. Simulera trasigt nätverk i DevTools
1. Öppna Chrome DevTools -> **Network**.
2. Välj **Fast 3G** eller **Offline** i throttling-dropdownen.
3. Klicka på en knapp: Verifiera att UI reagerar inom 16ms (1 frame) och att en banner eller toast visas och återställer statet om anropet timear ut.

---

## 3. Koden & Verifiering

* **The Flaw:** [FlawedMutation.ts](file:///frontend/04-optimistic-updates/FlawedMutation.ts) – Uppdaterar UI utan felhantering eller rollback vid nätverksfel.
* **The Fix:** [OptimisticTodoMutation.ts](file:///frontend/04-optimistic-updates/OptimisticTodoMutation.ts) – Komplett mönster med `cancelQueries`, snapshot, `onError` rollback och `onSettled`.
* **Verification Test:** [OptimisticRollback.test.ts](file:///frontend/04-optimistic-updates/OptimisticRollback.test.ts) – Bevisar att rollback återställer tidigare data när API kastar 500.

---

## 4. Så här kör du testerna

```bash
cd frontend/04-optimistic-updates
npm test
```
