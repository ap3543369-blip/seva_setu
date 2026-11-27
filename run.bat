@echo off
cd /d "%~dp0"
cd frontend
call npm install --no-audit --no-fund
call npm run build
cd ..
start "EcoAI" cmd /c "cd ecoai && python -m pip install -q -r requirements.txt && python service.py"
mvn spring-boot:run
