# 🔒 04-terraform-state-locks: S3 Remote Backend & DynamoDB State Locking

Denna modul täcker 4 fundamentala samarbets- och tillståndshanteringsutmaningar i Infrastructure-as-Code (Terraform/OpenTofu). Varje scenario presenterar problemet (vad som kraschar och varför vi bryr oss), dess specifika **Keyword Bingo** (facit-lingo), samt ett **Verklighets-svar (30–60 sek)** skräddarsytt för tekniska intervjuer.

---

## 1. Koncept & Scenarier

### Scenario 1: Samtidiga `apply`-körningar & State File Corruption

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
I ett team eller en automatiserad CI/CD-pipeline (GitHub Actions / GitLab CI) händer förr eller senare följande: två pull requests mergas nästan samtidigt, eller två utvecklare kör `terraform apply` parallellt.

Utan ett distribuerat tillståndslås läser båda processerna samma ursprungliga `terraform.tfstate`. Båda beräknar sina ändringar oberoende av varandra och börjar modifiera molnresurser samtidigt. Den process som sparar sist **skriver över och korrumperar tillståndsfilen** (**State File Corruption**). Resurser som skapades av den första körningen blir "föräldralösa" (orphaned), säkerhetsregler raderas av misstag och drift uppstår som kräver timtals av riskfylld manuell JSON-kirurgi (`terraform import` / `terraform state rm`).

#### 2. Keyword Bingo (Facit-lingo)
* **State File Corruption:** När motstridiga samtidiga skrivningar förstör eller raderar resursreferenser i `terraform.tfstate`.
* **Orphaned Cloud Resources:** Molnresurser som existerar i AWS/Azure men som inte längre finns i Terraform state, vilket gör dem ohanterbara och skapar onödiga kostnader.
* **Race Condition in IaC:** När utfallet av infrastrukturbygget beror på vilken process som hann skriva sist.
* **Remote Backend:** Att flytta `terraform.tfstate` från den lokala hårddisken till en delad central molnlagring (Amazon S3).

#### 3. Verklighets-svar (30–60 sek)
> *"Att köra Terraform i ett team utan distribuerat tillståndslås leder förr eller senare till korrumperad state-fil. Om två utvecklare eller CI-jobb kör `apply` samtidigt skriver den sista över den förstas arbete, vilket skapar föräldralösa resurser och trasig infrastruktur. Därför konfigurerar vi alltid en remote S3 backend kombinerat med en DynamoDB-tabell för state locking. När ett jobb startar tar Terraform ett exklusivt lås i DynamoDB. Alla andra samtidiga körningar nekas omedelbart tills den pågående ändringen är helt slutförd."*

**Labb & Kod:** [flawed-backend.tf](file:///cloud-and-k8s/04-terraform-state-locks/flawed-backend.tf) vs [secure-backend.tf](file:///cloud-and-k8s/04-terraform-state-locks/secure-backend.tf)

---

### Scenario 2: DynamoDB Distributed Locking (`LockID`) & Atomärt skydd

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
Hur förhindrar man rent tekniskt att två distribuerade servrar manipulerar samma S3-fil samtidigt? S3 i sig har atomära skrivningar på objektnivå, men saknar inbyggt stöd för ömsesidig uteslutning (Mutex) under en långvarig process som en 10-minuters `terraform apply`.

#### 2. Keyword Bingo (Facit-lingo)
* **DynamoDB State Locking:** Mekanismen där Terraform använder en DynamoDB-tabell för att registrera pågående operationer.
* **`LockID` Primärnyckel:** Det obligatoriska strängfältet i DynamoDB-tabellen som Terraform förväntar sig för att identifiera tillståndsfilen.
* **`Error acquiring the state lock`:** Felmeddelandet som kastas direkt för att skydda infrastrukturen när ett lås redan hålls av en annan process.
* **Lock Metadata:** DynamoDB-posten som sparar vem som låst (användare, hostname, process-ID, tidsstämpel och körnings-ID).

#### 3. Verklighets-svar (30–60 sek)
> *"Terraform använder DynamoDB för att uppnå distribuerad låsning över S3. Vi skapar en DynamoDB-tabell med primärnyckeln `LockID` av typen String. När Terraform inleder en plan eller apply görs en atomär skrivning med metadata om vem som kör kommandot. Om en annan instans försöker köra samtidigt ser den låset och avbryter omedelbart med felet 'Error acquiring the state lock' och visar vem som håller låset. När kommandot slutförs raderas posten automatiskt och nästa process kan fortsätta."*

**Labb & Kod:** [secure-backend.tf](file:///cloud-and-k8s/04-terraform-state-locks/secure-backend.tf) & [test-state-lock.sh](file:///cloud-and-k8s/04-terraform-state-locks/test-state-lock.sh)

---

### Scenario 3: S3 Backend Säkerhet (KMS Server-Side Encryption & Versioning)

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
I `terraform.tfstate` sparas hela infrastrukturens definition i klartext, inklusive databaslösenord, privata TLS-nycklar och hemligheter som genererats under körningen.

Om S3-hinken saknar kryptering exponeras hemligheterna, och om hinken saknar versionshantering kan en oavsiktlig manuell radering eller felkonfigurering utplåna all kännedom om företagets molninfrastruktur.

#### 2. Keyword Bingo (Facit-lingo)
* **S3 Bucket Versioning:** Skyddsnät som sparar tidigare versioner av state-filen vid varje skrivning, vilket möjliggör omedelbar återställning vid olyckor.
* **Server-Side Encryption (SSE-KMS):** Kryptering i vila med AWS KMS för att skydda känsliga hemligheter i state-filen.
* **Public Access Block:** Strikt policy som förhindrar att S3-hinken någonsin kan göras publik av misstag.
* **Least Privilege IAM Policy:** Att enbart ge CI/CD-pipelinen och behöriga roller skrivrättigheter till state-hinken.

#### 3. Verklighets-svar (30–60 sek)
> *"Terraforms tillståndsfil innehåller ofta känsliga uppgifter som databaslösenord och certifikat i klartext. Därför ställer vi tre hårda krav på vår S3 backend-hink: För det första slår vi på strikt Server-Side Encryption med AWS KMS. För det andra aktiverar vi S3 Versioning så att vi när som helst kan återställa en tidigare version om state-filen skulle skadas. För det tredje blockerar vi all publik åtkomst och begränsar åtkomsten strikt via IAM-roller så att enbart vår säkra CI/CD-pipeline har rätt att läsa och skriva till hinken."*

**Labb & Kod:** [secure-backend.tf](file:///cloud-and-k8s/04-terraform-state-locks/secure-backend.tf)

---

### Scenario 4: "The Stale Lock Dilemma" & Hantering av `terraform force-unlock`

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
En CI/CD runner kraschar hårt mitt under en `terraform apply` (t.ex. på grund av OOM eller nätverksavbrott på runnern). Processen termineras utan att Terraforms cleanup-kod hinner anropas.

Låset ligger kvar i DynamoDB (**Stale Lock / Orphaned Lock**). Alla efterföljande byggen i teamet är blockerade och misslyckas med felmeddelandet att låset redan är taget.

#### 2. Keyword Bingo (Facit-lingo)
* **Stale Lock / Orphaned Lock:** Ett lås som blivit kvarlämnat eftersom den ägande processen kraschade utan att släppa låset.
* **`terraform force-unlock <LOCK-ID>`:** Nödkommandot för att manuellt radera låset i DynamoDB.
* **Lock Verification:** Den kritiska säkerhetsrutinen att alltid verifiera att ingen annan process faktiskt kör innan man bryter ett lås.
* **Blast Radius:** Risken att bryta ett lås för en process som faktiskt fortfarande modifierar resurser i bakgrunden.

#### 3. Verklighets-svar (30–60 sek)
> *"Ett Stale Lock uppstår när en CI/CD-runner kraschar hårt mitt i en apply utan att hinna städa upp i DynamoDB. Alla efterföljande körningar blockeras. Innan vi gör något kontrollerar vi alltid körnings-ID:t i felmeddelandet och säkerställer att processen verkligen är död och inte bara långsam. När vi bekräftat att ingen process kör använder vi kommandot `terraform force-unlock <LOCK-ID>` för att manuellt släppa låset, varpå pipelinen omedelbart kan återupptas."*

**Labb & Kod:** Avsnitt 2B nedan

---

## 2. Felsökningsguide (Kommandon & Verktyg)

### A. Inspektera aktivt lås i DynamoDB (LocalStack / AWS)
```bash
# Se vem som håller låset
aws --endpoint-url=http://localhost:4566 dynamodb get-item \
  --table-name terraform-locks \
  --key '{"LockID": {"S": "vault-tf-bucket/production/terraform.tfstate-md5"}}'
```

### B. Manuellt bryta ett föräldralöst lås (Force Unlock)
Om CI/CD-jobbet avbröts hårt och låset fastnat:
```bash
terraform force-unlock <LOCK-ID>
```

---

## 3. Koden & Verifiering

* **The Flaw:** [flawed-backend.tf](file:///cloud-and-k8s/04-terraform-state-locks/flawed-backend.tf) – S3-backend utan DynamoDB-lås; tillåter samtidiga körningar.
* **The Fix:** [secure-backend.tf](file:///cloud-and-k8s/04-terraform-state-locks/secure-backend.tf) – S3 med kryptering, versionshantering och DynamoDB `LockID`.
* **Verification Script:** [test-state-lock.sh](file:///cloud-and-k8s/04-terraform-state-locks/test-state-lock.sh) – Simulerar två samtidiga låsförfrågningar och demonstrerar konfliktavvisning.

---

## 4. Så här kör du testerna

```bash
cd cloud-and-k8s/04-terraform-state-locks
./test-state-lock.sh
```
