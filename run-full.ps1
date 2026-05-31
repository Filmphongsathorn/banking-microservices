Write-Host "=========================================================" -ForegroundColor Cyan
Write-Host " 🚀 Starting Enterprise Banking System (FULL MODE)" -ForegroundColor Cyan
Write-Host "=========================================================" -ForegroundColor Cyan
Write-Host "WARNING: This will launch all Microservices + ELK + Prometheus + Grafana" -ForegroundColor Yellow
Write-Host "This requires at least 24GB of RAM. If your PC freezes, please force restart." -ForegroundColor Red
Write-Host ""

Write-Host "[1/3] Starting Backend Services with Observability tools..." -ForegroundColor Green
docker-compose --profile heavy-ops up -d

Write-Host "[2/3] Waiting for Backend to initialize (30 seconds)..." -ForegroundColor Green
Start-Sleep -Seconds 30

Write-Host "[3/3] Starting Frontend Web App..." -ForegroundColor Green
Set-Location frontend
npm run dev
