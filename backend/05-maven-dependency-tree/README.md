# 📦 05-maven-dependency-tree: Jar Hell, Exclusions & Transitive Dependencies

Denna modul täcker 4 fundamentala beroende- och byggutmaningar i enterprise Java/Maven-projekt. Varje scenario presenterar problemet (vad som kraschar och varför vi bryr oss), dess specifika **Keyword Bingo** (facit-lingo), samt ett **Verklighets-svar (30–60 sek)** skräddarsytt för tekniska intervjuer.

---

## 1. Koncept & Scenarier

### Scenario 1: Jar Hell & `NoSuchMethodError` (Nearest-Definition-Wins)

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
Projektet bygger och kompilerar helt utan fel. Men när applikationen startar eller när en viss kodväg exekveras i produktion kraschar den omedelbart med:
```text
java.lang.NoSuchMethodError: 'com.fasterxml.jackson.databind.ObjectMapper com.fasterxml.jackson.databind.ObjectMapper.findAndRegisterModules()'
```
eller
```text
java.lang.ClassNotFoundException / java.lang.NoClassDefFoundError
```

Detta beror på **Jar Hell** orsakad av motstridiga transitiva beroenden. Två olika bibliotek drar in olika versioner av samma JAR (t.ex. Jackson 2.9 vs 2.16). Mavens medlingsalgoritm (**Nearest-Definition-Wins**) väljer den version som ligger på kortast avstånd från rot-POM:en. Om den äldre versionen råkar ligga närmare vinner den, vilket klipper bort nya metoder som det nyare biblioteket förväntar sig i runtime.

#### 2. Keyword Bingo (Facit-lingo)
* **Jar Hell / Classpath Shadowing:** När två JAR-filer på klasspathen definierar samma klass (`com.example.Foo`) och ClassLoadern godtyckligt väljer den första den hittar.
* **Transitive Dependency (Transitivt beroende):** Ett bibliotek som ditt projekt är beroende av indirekt via ett direkt beroende.
* **Nearest-Definition-Wins:** Mavens deterministiska medlingsregel: Versionen på kortast träd-djup från rot-POM:en vinner oavsett om den är äldre eller nyare.
* **`NoSuchMethodError`:** Runtime-felet som bevisar att koden kompilerades mot en nyare JAR än den som faktiskt laddades i runtime.
* **`mvn dependency:tree -Dverbose`:** Kommandot för att visualisera hela beroendeträdet och se vilka versioner Maven har utelämnat eller valt.

#### 3. Verklighets-svar (30–60 sek)
> *"Jar Hell uppstår när olika bibliotek drar in inkompatibla versioner av samma transitiva beroende. Eftersom Maven löser konflikter med regeln 'Nearest Definition Wins' kan en äldre version råka vinna bara för att den ligger närmare rot-POM:en i trädet, vilket orsakar `NoSuchMethodError` i runtime. Jag felsöker detta genom att köra `mvn dependency:tree -Dverbose -Dincludes=groupId:artifactId` för att se exakt var dubbletterna kommer ifrån. Lösningen är att tvinga rätt version via `<dependencyManagement>` eller lägga till en `<exclusion>`."*

**Labb & Kod:** [pom.flawed.xml](file:///backend/05-maven-dependency-tree/pom.flawed.xml) vs [pom.xml](file:///backend/05-maven-dependency-tree/pom.xml)

---

### Scenario 2: Centraliserad versionstvingning med `<dependencyManagement>` & BOM

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
I stora projekt eller multi-modul-system specificerar utvecklare ofta versionsnummer direkt i `<dependencies>`. När olika moduler anger olika versioner av samma bibliotek uppstår inkompatibiliteter och svårhanterliga byggkrockar.

Att manuellt hålla tiotals POM-filer i synk vid säkerhetsuppdateringar leder till mänskliga fel och inkonsekvens.

#### 2. Keyword Bingo (Facit-lingo)
* **`<dependencyManagement>`:** Mavens centraliserade regelbok som deklarerar och tvingar versionsnummer för artefakter utan att automatiskt lägga till dem som beroenden.
* **Bill of Materials (BOM):** En POM som enbart innehåller `<dependencyManagement>` (t.ex. `spring-boot-dependencies`), importerad med `<scope>import</scope>` och `<type>pom</type>`.
* **Transitiv determinism:** Att garantera att alla moduler i ett ekosystem delar exakt samma testade biblioteksversioner.
* **Separation of Concerns:** Att skilja på versionshantering (i förälder/management) och beroendedeklaration (i barnmoduler).

#### 3. Verklighets-svar (30–60 sek)
> *"I enterprise-arkitektur hanterar vi beroenden via `<dependencyManagement>`. Det fungerar som en centraliserad innehållsförteckning som tvingar specifika versioner för alla barnmoduler och transitiva beroenden, utan att moduler tvingas dra in artefakter de inte behöver. Vi importerar oftast färdiga BOM:ar, såsom `spring-boot-dependencies`, med `scope: import`. Då kan enskilda moduler deklarera beroenden utan versions-tagg och vi garanterar att hela systemet kör på samma testade version."*

**Labb & Kod:** [pom.xml](file:///backend/05-maven-dependency-tree/pom.xml)

---

### Scenario 3: Kirurgisk sanering med `<exclusions>`

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
Ett externt bibliotek (t.ex. en legacy SOAP-klient) drar transitivt med sig ett gammalt, sårbart bibliotek med allvarliga CVE-säkerhetsbrister (t.ex. en antik Log4j 1.x eller `commons-logging`).

Detta orsakar dels konflikter med moderna loggramverk (`SLF4J: Class path contains multiple SLF4J bindings`), dels failar säkerhetsscanningsverktyg (Snyk / Dependabot) bygget.

#### 2. Keyword Bingo (Facit-lingo)
* **`<exclusions>`:** XML-element som klipper bort ett specifikt transitivt underberoende från att dras in av en artefakt.
* **SLF4J Multiple Bindings:** Klassisk loggkonflikt när både Logback och Log4j finns på klasspathen samtidigt.
* **CVE Remediation:** Att eliminera kända säkerhetssårbarheter genom att förbjuda föråldrade bibliotek.
* **Overriding Transitive Graph:** Att kombinera en exkludering med ett modernt substitut (t.ex. `jcl-over-slf4j`).

#### 3. Verklighets-svar (30–60 sek)
> *"Om ett tredjepartsbibliotek drar in ett oönskat eller sårbart transitivt paket använder vi `<exclusions>` inuti det beroendets deklaration. Ett klassiskt exempel är att exkludera gamla `commons-logging` eller `log4j` som krockar med Spring Boots `Logback`/`SLF4J`. Genom att exkludera artefakten håller vi klasspathen ren från dubbletter, tystar SLF4J-varningar och eliminerar säkerhetsflaggor från våra sårbarhetsscanners."*

**Labb & Kod:** [pom.xml](file:///backend/05-maven-dependency-tree/pom.xml)

---

### Scenario 4: Förebyggande kvalitetsgrind med `maven-enforcer-plugin`

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
Att upptäcka versionskonflikter och Jar Hell i produktion eller under runtime-tester är oacceptabelt i professionell utveckling.

Utan automatiska kontroller i byggpipelinen kan en utvecklare råka merga en PR som introducerar en inkompatibel transitiv version utan att någon märker det förrän applikationen smäller i drift.

#### 2. Keyword Bingo (Facit-lingo)
* **`maven-enforcer-plugin`:** Officiellt Maven-plugin som tvingar fram arkitekturregler och misslyckas bygget om de bryts.
* **`<dependencyConvergence>`:** Den ultimata regeln: kräver att alla noder i hela beroendeträdet som refererar till en artefakt måste konvergera till exakt samma version.
* **`<requireUpperBoundDeps>`:** Regel som garanterar att den version som väljs alltid är minst lika ny som alla transitiva krav.
* **Fail-Fast Build:** Principen att krascha bygget lokalt och i CI inom sekunder istället för att skeppa buggar till produktion.

#### 3. Verklighets-svar (30–60 sek)
> *"För att garantera att versionskonflikter aldrig når produktion använder vi `maven-enforcer-plugin` med regeln `<dependencyConvergence>` i vår CI/CD-pipeline. Om två bibliotek kräver olika versioner av samma transitiva beroende avbryter Enforcer-pluginet bygget direkt med ett tydligt felmeddelande. Det tvingar teamet att medvetet deklarera den rätta versionen i `<dependencyManagement>` istället för att låta Mavens Nearest-Wins-algoritm chansa."*

**Labb & Kod:** [pom.xml](file:///backend/05-maven-dependency-tree/pom.xml) & Avsnitt 2C nedan

---

## 2. Felsökningsguide (Kommandon & Verktyg)

### A. Inspektera hela beroendeträdet med verbose-läge
```bash
# Hitta var dubbletter eller gamla versioner dras in
mvn dependency:tree -Dverbose

# Filtrera på specifik grupp eller artefakt
mvn dependency:tree -Dverbose -Dincludes=com.fasterxml.jackson.core:*
mvn dependency:tree -Dincludes=org.slf4j:*
```

### B. Analysera oanvända och saknade beroenden
```bash
# Identifierar dependencies deklarerade men oanvända, eller använda men ej deklarerade
mvn dependency:analyze
```

### C. Tvinga versionskontroll med Maven Enforcer Plugin
Lägg till i din `pom.xml`:
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-enforcer-plugin</artifactId>
    <version>3.4.1</version>
    <executions>
        <execution>
            <id>enforce-dependency-convergence</id>
            <goals><goal>enforce</goal></goals>
            <configuration>
                <rules>
                    <dependencyConvergence/>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

---

## 3. Koden & Verifiering

* **The Flaw:** [pom.flawed.xml](file:///backend/05-maven-dependency-tree/pom.flawed.xml) – Deklarerar bibliotek som drar in motstridiga versioner där äldre Jackson vinner via nearest-definition.
* **The Fix:** [pom.xml](file:///backend/05-maven-dependency-tree/pom.xml) – Använder `<dependencyManagement>` och `<exclusions>` för att låsa versionen och garantera runtime-kompatibilitet.
* **Verification Test:** [DependencyTreeVerificationTest.java](file:///backend/05-maven-dependency-tree/src/test/java/com/vault/maven/DependencyTreeVerificationTest.java) – Verifierar att den förväntade metoden finns tillgänglig i klasspathen under runtime.

---

## 4. Så här kör du testerna

Kör verifieringstestet med Maven:
```bash
cd backend/05-maven-dependency-tree
mvn clean test
```
Testet kontrollerar via Java Reflection att metoden `findAndRegisterModules()` existerar på `ObjectMapper` på klasspathen, vilket bevisar att fixen fungerar och att Jackson 2.16 laddas korrekt.
