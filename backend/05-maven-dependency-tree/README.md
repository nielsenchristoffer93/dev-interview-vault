# 📦 05-maven-dependency-tree: Jar Hell, Exclusions & Transitive Dependencies

## 1. Problem: Vad kraschar eller varför bryr vi oss?
Projektet bygger och kompilerar utan fel, men i produktion kraschar applikationen omedelbart under runtime med:
```text
java.lang.NoSuchMethodError: 'com.fasterxml.jackson.databind.ObjectMapper com.fasterxml.jackson.databind.ObjectMapper.findAndRegisterModules()'
```
eller
```text
java.lang.ClassNotFoundException / java.lang.NoClassDefFoundError
```
Detta beror på **Jar Hell** orsakad av motstridiga transitiva beroenden. Två olika bibliotek drar in olika versioner av samma JAR (t.ex. Jackson 2.9 vs 2.16). Mavens medlingsalgoritm (**Nearest-Definition-Wins**) väljer den äldre versionen för att den råkar ligga närmare i beroendeträdet, vilket klipper bort nya metoder som det andra biblioteket kräver under runtime.

---

## 2. Keyword Bingo (Facit-lingo)
* **Transitive Dependency (Transitivt beroende):** Ett bibliotek som ditt projekt är beroende av eftersom ett av dina direkta bibliotek behöver det.
* **Nearest-Definition-Wins:** Mavens konfliktlösningsregel: Den version som ligger på kortast djup i beroendeträdet från din POM vinner. Om djupet är samma vinner den som deklareras först.
* **Jar Hell / Classpath Shadowing:** När två JAR-filer på classpath innehåller samma fullt kvalificerade klassnamn (`com.example.Foo`). JVM:s ClassLoader laddar den första den hittar och ignorerar den andra.
* **`<dependencyManagement>`:** Mavens mekanism för att centralisera och tvinga versioner för transitiva beroenden utan att automatiskt lägga till dem som direkta beroenden.
* **`<exclusions>`:** Att explicit förbjuda en specifik transitiv artefakt från att dras in av ett visst bibliotek.
* **`maven-enforcer-plugin` (`<banDuplicateClasses>` / `<requireUpperBoundDeps>`):** Plugin som failar bygget direkt vid versionskrockar istället för att låta det smälla i produktion.

---

## 3. Verklighets-svar (30–60 sek)
> *"Jar Hell uppstår när olika bibliotek kräver inkompatibla versioner av samma transitiva beroende. Eftersom Maven löser konflikter med 'Nearest Definition Wins' kan en äldre version av misstag trumfa en nyare, vilket orsakar `NoSuchMethodError` i runtime. För att felsöka kör jag alltid `mvn dependency:tree -Dverbose -Dincludes=com.fasterxml.jackson*` för att se exakt vilken nod som introducerar den oönskade versionen. För att lösa det centraliserar vi versionen i `<dependencyManagement>` så att alla moduler tvingas använda samma version, eller lägger till en `<exclusion>` på det felande biblioteket."*

---

## 4. Felsökningsguide (Kommandon & Verktyg)

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

## 5. Koden & Verifiering

* **The Flaw:** [pom.flawed.xml](file:///backend/05-maven-dependency-tree/pom.flawed.xml) – Deklarerar bibliotek som drar in motstridiga versioner där äldre Jackson vinner via nearest-definition.
* **The Fix:** [pom.xml](file:///backend/05-maven-dependency-tree/pom.xml) – Använder `<dependencyManagement>` och `<exclusions>` för att låsa versionen och garantera runtime-kompatibilitet.
* **Verification Test:** [DependencyTreeVerificationTest.java](file:///backend/05-maven-dependency-tree/src/test/java/com/vault/maven/DependencyTreeVerificationTest.java) – Verifierar att den förväntade metoden finns tillgänglig i klasspathen under runtime.
