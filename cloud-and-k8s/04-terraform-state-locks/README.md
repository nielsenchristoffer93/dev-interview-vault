# 🔒 04-terraform-state-locks: S3 Remote Backend & DynamoDB State Locking

## 1. Problem: Vad kraschar eller varför bryr vi oss?
I ett team eller en automatiserad CI/CD-pipeline (GitHub Actions / GitLab CI) händer förr eller senare följande:
Två pull requests mergas nästan samtidigt, eller två utvecklare kör `terraform apply` parallellt.
* Utan ett distribuerat tillståndslås läser båda processerna samma gamla `terraform.tfstate`.
* Båda beräknar sina diffar oberoende av varandra och börjar modifiera molnresurser samtidigt.
* Den process som sparar sist **skriver över och korrumperar state-filen** (**State File Corruption**).
* Resultat: Resurser som skapades av den första körningen blir "föräldralösa" (orphaned), säkerhetsgrupper raderas av misstag eller drift uppstår som kräver timtals av riskfylld manuell JSON-kirurgi (`terraform import` / `terraform state rm`).

---

## 2. Keyword Bingo (Facit-lingo)
* **Remote State Backend:** Att lagra tillståndet centralt (i Amazon S3) istället för lokalt i `terraform.tfstate` på en enskild utvecklares dator.
* **State Locking (Tillståndslåsning):** En ömsesidig uteslutning (mutex) som förhindrar att mer än en person eller pipeline ändrar samma infrastruktur samtidigt.
* **DynamoDB Lock Table:** En DynamoDB-tabell med primärnyckeln `LockID` (String) som Terraform använder för att förvärva och släppa låset via atomära operationer.
* **`Error: Error acquiring the state lock`:** Felmeddelandet Terraform kastar när en annan körning redan har låset.
* **`terraform force-unlock <LOCK-ID>`:** Nödkommandot för att manuellt släppa ett lås om en CI/CD-runner kraschat och lämnat låset hängande.
* **S3 Versioning & SSE:** Krav på S3-hinken: Versionshantering (för att kunna rulla tillbaka vid oavsiktlig korruption) och Server-Side Encryption (KMS) eftersom tillståndet kan innehålla lösenord och certifikat i klartext.

---

## 3. Verklighets-svar (30–60 sek)
> *"Om flera utvecklare eller CI/CD-pipelines kör `terraform apply` samtidigt utan låsning riskerar vi race conditions och korrupt state, vilket leder till föräldralösa resurser och nertid. Därför konfigurerar vi alltid en remote S3 backend kombinerat med en DynamoDB-tabell för state locking. Tabellen måste ha primärnyckeln `LockID`. När Terraform startar tar den ett lås i DynamoDB. Om en annan process försöker köra samtidigt nekas den omedelbart med 'Error acquiring state lock'. Om en pipeline dör oväntat och lämnar låset kvar kan vi verifiera att ingen process kör och säkert släppa det med `terraform force-unlock`."*

---

## 4. Felsökningsguide (Kommandon & Verktyg)

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

## 5. Koden & Verifiering

* **The Flaw:** [flawed-backend.tf](file:///cloud-and-k8s/04-terraform-state-locks/flawed-backend.tf) – S3-backend utan DynamoDB-lås; tillåter samtidiga körningar.
* **The Fix:** [secure-backend.tf](file:///cloud-and-k8s/04-terraform-state-locks/secure-backend.tf) – S3 med kryptering, versionshantering och DynamoDB `LockID`.
* **Verification Script:** [test-state-lock.sh](file:///cloud-and-k8s/04-terraform-state-locks/test-state-lock.sh) – Simulerar två samtidiga låsförfrågningar och demonstrerar konfliktavvisning.
