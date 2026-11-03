@echo off
REM Run this in a terminal with Gradle and JDK 25 installed.
gradle clean build
if errorlevel 1 exit /b 1
echo Built: build\libs\FlexyCrates-1.0.0.jar
