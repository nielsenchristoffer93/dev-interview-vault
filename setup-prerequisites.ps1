<#
.SYNOPSIS
    dev-interview-vault: Windows Prerequisites & Lab Infrastructure Setup
.DESCRIPTION
    Kontrollerar och installerar Minikube, kubectl samt startar Docker Compose-tjänster i Windows.
#>

$ErrorActionPreference = "Continue"

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "🛠️  DEV-INTERVIEW-VAULT: Setup av Förutsättningar (Windows)" -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan

# 1. Kontrollera kubectl
Write-Host "`n[1/4] Kontrollerar kubectl..." -ForegroundColor Yellow
if (Get-Command kubectl -ErrorAction SilentlyContinue) {
    $kVer = (kubectl version --client -o yaml 2>$null | Select-String "gitVersion")
    Write-Host "✅ kubectl är installerat: $kVer" -ForegroundColor Green
} else {
    Write-Host "⚠️ kubectl saknas. Installerar via winget..." -ForegroundColor Yellow
    winget install -e --id Kubernetes.kubectl --accept-package-agreements --accept-source-agreements
}

# 2. Kontrollera minikube
Write-Host "`n[2/4] Kontrollerar minikube..." -ForegroundColor Yellow
if (Get-Command minikube -ErrorAction SilentlyContinue) {
    Write-Host "✅ minikube är installerat: $(minikube version --short)" -ForegroundColor Green
} else {
    Write-Host "⚠️ minikube saknas. Installerar via winget..." -ForegroundColor Yellow
    winget install -e --id Kubernetes.minikube --accept-package-agreements --accept-source-agreements
}

# 3. Kontrollera Docker
Write-Host "`n[3/4] Kontrollerar Docker..." -ForegroundColor Yellow
if (Get-Command docker -ErrorAction SilentlyContinue) {
    Write-Host "✅ Docker är tillgängligt i Windows!" -ForegroundColor Green
    
    Write-Host "`n[4/4] Startar Docker Compose lab-infrastruktur..." -ForegroundColor Yellow
    docker compose -f compose/docker-compose.base.yml up -d
    docker compose -f compose/docker-compose.monitoring.yml up -d
    Write-Host "✅ Alla Docker-containrar startade!" -ForegroundColor Green
} else {
    Write-Host "ℹ️ Docker är installerat i WSL (Ubuntu) på denna maskin." -ForegroundColor Cyan
    Write-Host "   Kör './setup-prerequisites.sh' via WSL för att starta containrar inifrån Ubuntu:" -ForegroundColor Gray
    Write-Host "   wsl ./setup-prerequisites.sh" -ForegroundColor Gray
}

Write-Host "`n======================================================================" -ForegroundColor Cyan
Write-Host "🎉 KLART! Kör Minikube med: minikube start" -ForegroundColor Green
Write-Host "======================================================================`n" -ForegroundColor Cyan
