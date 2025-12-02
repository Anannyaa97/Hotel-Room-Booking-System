@echo off
REM compile_and_run.bat - compile Java sources and run Main using local mysql connector
cd /d "%~dp0"

REM generate response file with quoted paths to handle spaces/parentheses
del sources.txt 2>nul
for /f "delims=" %%F in ('dir /s /b src\*.java') do @echo "%%~fF" >> sources.txt

set JAR=src\lib\mysql-connector-j-9.4.0.jar
echo Compiling with JAR=%JAR%
javac -d out -cp "%JAR%" @sources.txt
if %errorlevel% neq 0 (
	echo javac failed with %errorlevel%
	pause
	exit /b %errorlevel%
)
echo Compilation successful.
echo Running Main...
java -cp out;"%JAR%" Main
pause
