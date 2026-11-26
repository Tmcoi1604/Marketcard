@echo off
REM Bien dich phan mem may tram MarketCard (can JDK 11+)
cd /d %~dp0
if not exist out mkdir out
javac -encoding UTF-8 -d out -cp "lib/*" src\marketcard\app\*.java
if errorlevel 1 (echo Bien dich LOI & exit /b 1)
echo Bien dich xong. Chay: run.bat
