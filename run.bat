@echo off
TITLE Planetary Satellite System

SET LIBS=lib\sqlite-jdbc.jar;lib\slf4j-api.jar;lib\slf4j-simple.jar

echo [1/3] Cleaning...
if exist out rmdir /S /Q out
mkdir out

echo [2/3] Compiling...
dir /s /b src\main\java\*.java > sources.txt
javac -cp "%LIBS%" -d out -sourcepath src\main\java @sources.txt
del sources.txt

echo [3/3] Starting...
xcopy /E /I /Q resources out\resources >nul 2>&1
java --enable-native-access=ALL-UNNAMED -cp "out;%LIBS%" com.planetarysystem.Main

pause

