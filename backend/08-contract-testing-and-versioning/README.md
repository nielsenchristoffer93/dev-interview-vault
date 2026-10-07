# 📜 Backend: Contract Testing & API Versioning (Pact & Schema Evolution)

> **Intervju-fokus:** Consumer-Driven Contract Testing (Pact), Tolerant Reader Pattern, Breaking Changes i mikrotjänster, och URI vs Content Negotiation Versioning med `Sunset`-headers.

---

## 🎯 Scenario 1: Breaking API Changes & Consumer-Driven Contracts (Pact)

### Problem (Vad kraschar / varför bryr vi oss)
OrderService anropar PaymentService via REST. I OrderService förväntas svaret:
```json
{ "paymentId": "PAY-123", "status": "CONFIRMED", "amountInCents": 5000 }
```
PaymentService-teamet bestämmer sig för att städa upp sin kodbas och döper om fältet:
```json
{ "id": "PAY-123", "status": "CONFIRMED", "amount": 50.00 }
```
Båda mikrotjänsterna bygger och passerar sina egna lokala enhetstester med 100% grönt resultat.
PaymentService driftsätts till produktion. 

**Kraschen:**
Sekunden PaymentService driftsätts kraschar OrderService i produktion med:
```text
com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException: Unrecognized field "id"
eller
NullPointerException: amountInCents is null
```
Kunder kan inte checka ut, supporttelefonerna glöder och DevOps tvingas till en akut rollback mitt under pågående trafik. 
Varför upptäcktes inte detta? För att vanliga enhetstester och mocks (Mockito) testar *antaganden* om hur omvärlden ser ut, inte hur omvärlden *faktiskt* svarar.

---

### Keyword Bingo (Facit-lingo)
* **Consumer-Driven Contract Testing (CDCT):** Testmetodik där konsumenten (OrderService) definierar sina exakta förväntningar på leverantörens (PaymentService) API i ett maskinläsbart kontrakt (Pact-fil).
* **Pact Framework:** Det ledande industriverktyget för kontraktstestning; konsumenten genererar ett kontrakt vid byggtid som sedan verifieras mot leverantören i leverantörens CI-pipeline.
* **Provider Verification:** Steget i leverantörens byggpipeline där Pact-kontraktet spelas upp mot leverantörens API för att bevisa att ingen kodändring bryter någon existerande konsument innan deploy.
* **Can-I-Deploy (Pact Broker):** Ett CLI- och pipeline-steg som frågar Pact Brokern om konsument version X och leverantör version Y är kompatibla för den aktuella miljön (t.ex. Production).
* **Mock Drift (Falsk trygghet med mocks):** När en konsument använder en Mockito-mock eller WireMock-stub som avviker från hur leverantörens faktiska API fungerar i verkligheten.
* **Production Parity utan E2E-miljöer:** Fördelen att slippa instabila, långsamma och dyra distribuerade "End-to-End"-testmiljöer genom att testa kontraktsgränserna isolerat.

---

### Verklighets-svar (30–60 sek)
> "End-to-end-tester i delade miljöer är notoriskt bräckliga och långsamma, medan lokala mocks lider av 'Mock Drift' där testerna är gröna fast API:et ändrats i produktion.
> 
> Som senior förhindrar jag detta med **Consumer-Driven Contract Testing via Pact**:
> Konsumenten definierar ett strikt kontrakt som specificerar exakt de fält den behöver. Kontraktet publiceras till en central Pact Broker. I leverantörens CI-pipeline körs en automatisk *Provider Verification* mot kontraktet. Om leverantören råkar ta bort ett fält eller ändra en typ kraschar leverantörens bygge omedelbart i deras egen PR. I deployment-pipelinen använder vi `pact-broker can-i-deploy` för att matematiskt garantera att releasen är 100% bakåtkompatibel innan driftsättning sker."

---

## 🎯 Scenario 2: API Versioning – URI Path vs Content Negotiation & Deprecation

### Problem (Vad kraschar / varför bryr vi oss)
Ett team behöver introducera en ny version av ett kund-API. 
Utvecklaren väljer den enklaste metoden: URI-versionering (`/api/v1/customers` och `/api/v2/customers`).

**Problemen som uppstår över tid:**
1. **URI & Routing Bloat:** Fem år senare har kodbasen `/v1/`, `/v2/`, `/v3/` och `/v4/`. Controllers är duplicerade, databasmodeller underhålls i 4 parallella kopior och underhållet kostar miljoner.
2. **Bruten HTTP-semantik:** Samma logiska kundentitet har 3 olika URI:er (`/api/v1/customers/42` och `/api/v2/customers/42`), vilket bryter REST-principen att en URI unikt identifierar en resurs och omöjliggör intelligent CDN-caching.
3. **Plötslig avstängning:** Teamet stänger plötsligt av `v1` utan standardiserad förvarning, vilket sänker partnerintegrationer och mobila appar som inte hunnit uppdateras.

---

### Keyword Bingo (Facit-lingo)
* **URI Path Versioning (`/v1/...`):** Den vanligaste och mest synliga versioneringsmetoden; enkel att konfigurera men skapar duplicerade routings och URI-proliferation.
* **Content Negotiation / Header Versioning (`Accept`-header):** Versionering via MIME-typer (t.ex. `Accept: application/vnd.company.v2+json`); bevarar en stabil URI för samma resurs och stödjer finmaskig representation.
* **Custom Request Header (`X-API-Version`):** Versionering via en anpassad HTTP-header; bevarar URI:n och är enklare att skicka än komplexa MIME-typer.
* **RFC 8594 `Sunset` Header:** Den officiella HTTP-svarsheadern som anger det exakta datumet och klockslaget då en API-endpoint upphör att fungera (t.ex. `Sunset: Wed, 11 Nov 2026 00:00:00 GMT`).
* **RFC 7234 `Warning: 299`:** HTTP-header för att signalera deprecation och instruera klienter att uppgradera utan att kasta 4xx-fel.
* **Breaking Changes vs Non-breaking Changes:**
  * *Non-breaking:* Lägga till ett valfritt fält, lägga till en ny endpoint.
  * *Breaking:* Ta bort ett fält, döpa om ett fält, ändra datatyp, ändra valideringsregler till striktare.

---

### Verklighets-svar (30–60 sek)
> "Att versionera i URI-slingan (`/v1/`, `/v2/`) är lätt att komma igång med men skapar snabbt kodduplicering och bryter REST-principen om unika resursidentifierare.
> 
> Som senior föredrar jag en tvådelad strategi:
> För interna mikrotjänster och partners tillämpar vi antingen **Content Negotiation** (`Accept: application/vnd.api.v2+json`) eller anpassade headers så att resursens URI förblir stabil. När en äldre version fasas ut stänger vi aldrig bara av den; vi implementerar IETF-standarden med HTTP-headern **`Sunset`** (RFC 8594) och `Deprecation: true`. Detta ger konsumenters automatiserade loggövervakning och API-klienter ett formellt, maskinläsbart datum för migrationen."

---

## 🎯 Scenario 3: Tolerant Reader Pattern & Schema Evolution

### Problem (Vad kraschar / varför bryr vi oss)
En mobilklient eller intern mikrotjänst läser användardata med standardinställningar i Jackson ObjectMapper:
```java
ObjectMapper mapper = new ObjectMapper(); // FAIL-ON-UNKNOWN-PROPERTIES är TRUE som standard i äldre mönster!
```
När backend lägger till ett nytt harmlöst fält (`"preferredLanguage": "sv-SE"`) kraschar alla konsumenter direkt med `UnrecognizedPropertyException`.
Konsumenten kraschade alltså på ett fält som den inte ens brydde sig om!

---

### Keyword Bingo (Facit-lingo)
* **Tolerant Reader Pattern:** Martin Fowlers designprincip för distribuerade system: en konsument ska enbart läsa och validera de specifika fält den faktiskt behöver och tyst ignorera alla okända fält som skickas med.
* **Postel's Law (Robustness Principle):** *"Be conservative in what you do, be liberal in what you accept from others."*
* **Jackson `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES`:** Jacksons konfigurationsflagga som måste sättas till `false` (eller `@JsonIgnoreProperties(ignoreUnknown = true)`) för att förhindra krascher vid schematillägg.
* **Additive-Only Schema Changes:** Leverantörsregeln att aldrig ta bort eller mutera befintliga fält, utan enbart introducera nya valfria (nullable) fält.
* **Forward Compatibility:** Förmågan hos äldre kod att bearbeta data som genererats av en nyare version utan att krascha.

---

### Verklighets-svar (30–60 sek)
> "Mikrotjänster och frontend-klienter får aldrig krascha för att en backend har lagt till ett nytt fält. Detta förhindras med **Tolerant Reader Pattern** och Postels lag.
> 
> Konsumenter konfigureras alltid med `FAIL_ON_UNKNOWN_PROPERTIES = false` (eller `@JsonIgnoreProperties(ignoreUnknown = true)`). På leverantörssidan tillämpar vi **Additive-Only Schema Evolution**: fält raderas aldrig och byter aldrig typ. Om ett fält ska fasas ut markeras det som deprecated men fortsätter returneras parallellt med det nya fältet tills alla kända konsumenter migrerats enligt deras Pact-kontrakt."

---

## 🛠️ Koden & Verifiering

* **The Flaw:** [FlawedContractProvider.java](file:///backend/08-contract-testing-and-versioning/FlawedContractProvider.java)
  * Ändrar fältnamn från `amountInCents` till `amount`, vilket kraschar intet ont anande konsumenter.
  * Saknar `Sunset`-header vid deprecation.
* **The Fix:** [ResilientContractProvider.java](file:///backend/08-contract-testing-and-versioning/ResilientContractProvider.java)
  * Upprätthåller kontrakt genom att returnera både `amountInCents` och nya fältet (additive schema).
  * Skickar RFC 8594 `Sunset: Wed, 11 Nov 2026 00:00:00 GMT` och `Deprecation: true`.
  * Konsumenten använder Tolerant Reader (`FAIL_ON_UNKNOWN_PROPERTIES = false`).
* **Verification Test:** [ContractVerificationTest.java](file:///backend/08-contract-testing-and-versioning/ContractVerificationTest.java)
  * Bevisar att konsument kraschar mot den felaktiga leverantören (contract break).
  * Bevisar att tolerant reader hanterar nya fält utan fel.
  * Bevisar att Sunset och Deprecation headers genereras korrekt.

---

## 🧪 Så här kör du testerna

```bash
cd backend/08-contract-testing-and-versioning
mvn test
```
