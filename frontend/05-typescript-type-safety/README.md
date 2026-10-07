# 🛡️ 05-typescript-type-safety

> **Fokus:** Hantera gapet mellan kompileringstid och körtid i TypeScript. Avslöja typkastnings-lögnen (`as SomeType`), säkra systemgränser med runtime schema validation (Zod), tvinga fram uttömmande hantering med `never`, och eliminera buggar med Branded Types.

---

## 🧭 Översikt

TypeScript är ett fantastiskt verktyg för utvecklarupplevelse och refaktorisering, men det lider av ett fundamentalt faktum som skiljer juniora utvecklare från seniora: **Type Erasure (typradering)**. Vid körning i webbläsaren eller i Node.js existerar inte ett enda interface, type alias eller generisk parameter. Om din kod litar blint på `as MyType` vid gränsen mot nätverket eller externa beroenden är din typsäkerhet en illusion.

---

## 🎯 Scenario 1: Runtime Type Erasure & The "as"-Assertion Lie (Zod & Schema Boundaries)

### Problem (Vad kraschar / varför bryr vi oss)
En utvecklare skriver `const user = (await res.json()) as UserProfile;` och antar att datan har rätt form. När backend rullar ut en schemaändring, ändrar ett fält till `null`, eller skickar ett oväntat felmeddelande kraschar frontend i produktion med:
```text
Uncaught TypeError: Cannot read properties of undefined (reading 'includes')
```
Sentry svämmar över av fel, kunden möts av en blank skärm ("White Screen of Death"), och TypeScript-kompilatorn varnade inte med ett enda ord eftersom `as` beordrade kompilatorn att hålla tyst.

### Keyword Bingo (Facit-lingo)
* **Type Erasure (Typradering):** Processen där alla typer, interfaces och typalias raderas helt vid kompilering till JavaScript; noll typkontroll sker i körtid.
* **Type Assertion (`as`):** Kompilatordirektiv som tvingar TypeScript att lita på utvecklarens ord utan att generera någon runtime-validering ("en lögn för kompilatorn").
* **Parse, Don't Validate:** Principen att transformera osäker inkommande data till en starkt typad modell via en parser (Zod), istället för manuella booleska kontroller.
* **Zod Schema (`z.object`):** Schema-deklaration som validerar rå JSON i runtime och automatiskt infererar TypeScript-typer (`z.infer<typeof Schema>`).
* **`unknown` over `any`:** Regeln att typa extern data som `unknown` vilket tvingar fram typkontroll/parsing, till skillnad från `any` som stänger av typsystemet.
* **Boundary Validation:** Att placera strikt schemavalidering i systemets ytterkanter (HTTP-anrop, WebSockets, localStorage) innan data når affärslogik.
* **Fail-Fast:** Att avbryta direkt i nätverkslagret vid avvikande data, snarare än att låta trasig data sprida sig och orsaka dolda krascher i UI:t senare.
* **Contract Drift:** När backendens API-schema ändras utan att frontend-kontraktet har synkroniserats.

### Verklighets-svar (30–60 sek)
> "I TypeScript försvinner alla typer vid kompilering genom 'Type Erasure'. En typkastning med `as` genererar noll rader JavaScript vid körtid – det är en ren lögn för kompilatorn. Om backend ändrar sitt svarsformat eller skickar `null` leder det direkt till ohanterade `TypeError` i produktion. 
> 
> Som senior sätter jag alltid en strikt valideringsgräns vid nätverkslagret enligt principen 'Parse, don't validate'. All inkommande data typas som `unknown` och körs genom ett körtidsschema, exempelvis med Zod. Om datan avviker failar vi direkt vid nätverksgränsen med informativa fel innan trasig data sprider sig in i affärslogik och React-komponenter."

### Felsökningsguide (CLI & NPM)
Hitta farliga typkastningar i kodbasen:
```bash
# Sök efter farliga 'as'-kastningar i API-anrop
grep -rn "as [A-Z]" src/api/

# Sök efter 'any' som läcker in
grep -rn ": any" src/
```

Kör verifieringstester:
```bash
cd frontend
npm test
```

### Körguide
* **The Flaw:** Granska `FlawedTypeAssertions.ts` – se hur `parseUserUnsafe()` tillåter trasig data och kraschar i `hasAdminRoleUnsafe()`.
* **The Fix:** Granska `StrictTypeSafety.ts` – se hur `UserProfileSchema.parse()` validerar och sätter defaultvärden.
* **Testverifiering:** Kör `npm test` och se hur `TypeSafety.test.ts` bevisar att kraschen fångas säkert.

### Senior Intervju-djupdykning
* **Varför är `unknown` bättre än `any`?** `any` stänger av typsystemet helt och tillåter all operationer utan kontroll. `unknown` tvingar utvecklaren att göra en typkontroll (type narrowing eller schema parsing) innan värdet kan användas.
* **Vad är 'Parse, don't validate'?** Istället för att köra booleska kontroller (`if (!user.roles)`) och behålla den osäkra datan, transformerar en parser (som Zod) den råa osäkra datan till en garanterat giltig domäntyp.

---

## 🎯 Scenario 2: Discriminated Unions & Exhaustiveness Checking (`never`)

### Problem (Vad kraschar / varför bryr vi oss)
En applikation hanterar betalningar eller ordertillstånd via en union av states. När en kollega lägger till ett nytt tillstånd (t.ex. `REQUIRES_3DS_ACTION`) missas detta i en switch-sats i UI-lagret. Switchen har en `default`-gren som returnerar en generisk sträng eller `null`. Kunden klickar på betala, men ingenting händer – applikationen fastnar i ett tyst "dödläge" utan felmeddelande i loggarna.

### Keyword Bingo (Facit-lingo)
* **Discriminated Union (Tagged Union):** En sammanslagning av typer som delar ett gemensamt literalfält (t.ex. `status: 'PENDING' | 'SUCCESS'`) för säker typavsmalning.
* **Narrowing (Typavsmalning):** Kompilatorns förmåga att härleda en mer specifik typ inuti ett kodblock efter kontroll av diskriminatorn.
* **Exhaustiveness Check:** Att vid byggtid garantera att alla tänkbara grenar i en union hanteras.
* **Bottom Type (`never`):** TypeScripts bottentyp som representerar värden som inte kan inträffa; när alla varianter hanterats har variabeln typen `never`.
* **`assertNever(state: never)`:** Hjälpfunktion i `default`-grenen som ger kompileringsfel om ett nytt, ohanterat union-state når dit.
* **State Machine Integrity:** Att förhindra "döda lägen" i gränssnittet genom att garantera att varje möjligt domäntillstånd har en definierad vy.
* **Compile-Time Safety:** Att flytta upptäckten av saknade fall från manuella produktionstester till kompilatorn i CI/CD.

### Verklighets-svar (30–60 sek)
> "När vi modellerar tillstånd i komplexa flöden som checkout eller auth använder vi Discriminated Unions med en gemensam diskriminator, till exempel `status`. Felet många gör är att använda en öppen `default`-gren i switch-satser som tyst sväljer nya tillstånd när domänen växer.
> 
> För att garantera att alla tillstånd hanteras implementerar jag en uttömmande kontroll (exhaustiveness check) med `assertNever(state)`. Eftersom TypeScript använder `never` som bottentyp när alla unionens medlemmar har smalnats av (narrowed), ger kompilatorn ett omedelbart byggfel om ett nytt state läggs till utan att hanteras i switchen. Vi flyttar därmed buggen från kunden i produktion till kompilatorn i CI/CD."

### Senior Intervju-djupdykning
```typescript
export function assertNever(value: never): never {
  throw new Error(`Ohanterat union-tillstånd: ${JSON.stringify(value)}`);
}
```
Om unionen har medlemmarna `A | B | C` och switchen bara hanterar `A` och `B`, kommer `state` i `default` att ha typen `C`. Eftersom `C` inte kan tilldelas till `never` kastar TypeScript ett kompileringsfel:
```text
Argument of type 'C' is not assignable to parameter of type 'never'.
```

---

## 🎯 Scenario 3: Branded Types & Nominal Typing för Kritiska ID:n

### Problem (Vad kraschar / varför bryr vi oss)
TypeScript har ett **strukturellt typsystem (duck typing)**, inte ett nominellt. Det innebär att två typer med samma form betraktas som identiska. En överföringsfunktion tar `transferFunds(fromAccountId: string, toAccountId: string, amount: number)`. En utvecklare råkar kasta om argumenten eller skickar in ett `userId` som `accountId`. TypeScript godkänner koden utan varning eftersom alla ID:n är `string`. Pengar dras från fel konto i produktion.

### Keyword Bingo (Facit-lingo)
* **Structural Typing (Duck Typing):** Typsystem där två typer betraktas som identiska om de delar samma form/egenskaper (alla `string` är utbytbara).
* **Nominal Typing:** Typsystem där typer särskiljs baserat på namn och deklaration, oavsett underliggande datatyp.
* **Branded Types (Flavoring):** Tekniken att korsa en primitiv typ med ett fantomfält/symbol (`string & { readonly __brand: unique symbol }`) för att emulera nominell typning.
* **Type-level Branding:** Att göra identifierare unika enbart i typsystemet utan att skapa körtidsobjekt.
* **Zero Runtime Overhead:** Ingen CPU- eller minneskostnad i produktion eftersom branding-symbolen raderas vid kompilering.
* **Domain Primitives:** Att ge domänbegrepp (`AccountId`, `UserId`, `OrderId`) distinkta typer istället för generiska råsträngar.
* **Smart Constructor:** Fabriksfunktion (`parseAccountId()`) som validerar formatet och utgör den enda tillåtna källan för att skapa den brandade typen.

### Verklighets-svar (30–60 sek)
> "Eftersom TypeScript är strukturellt typat kan man råka skicka ett `UserId` till en funktion som förväntar sig ett `AccountId` så länge båda är vanliga `string`. Det skapar katastrofala buggar som passerar kompilatorn helt obemärkt.
> 
> För att lösa detta använder jag 'Branded Types' (även kallat Nominal Typing eller Flavoring). Genom att korsa en primitiv typ med ett unikt objekt med en symbol (`type AccountId = string & { readonly __brand: unique symbol }`) gör vi ID:t unikt på typnivå utan någon som helst körtidskostnad. ID:t kan enbart skapas via en smart konstruktor/validerare, vilket gör det omöjligt att blanda ihop domän-ID:n."

---

## 📋 Snabbreferens för Intervjun

| Koncept | The Flaw (Varför det smäller) | The Fix (Senior Lösning) |
| :--- | :--- | :--- |
| **API Boundary** | `as Type` ignorerar ändringar i körtid | `unknown` + Zod schema validation (`.parse()`) |
| **State Machine** | Tyst `default` sväljer nya enum/status-värden | `default: return assertNever(state)` |
| **Domän-ID:n** | Primitiva `string` kan förväxlas och kasta om parametrar | Branded Types (`AccountId`, `UserId`) med smart constructor |
