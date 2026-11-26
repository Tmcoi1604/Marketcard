@echo off
REM Chay phan mem may tram MarketCard
cd /d %~dp0
if not exist out\marketcard\app\App.class call build.bat
java -Dfile.encoding=UTF-8 -cp "out;lib/*" marketcard.app.App
