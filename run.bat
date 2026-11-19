@echo off
rem Usage: run.bat          -> website at http://localhost:8080
rem        run.bat console  -> menu in the terminal
call mvn -q -B package || exit /b 1
java -jar target\shopsync-mini.jar %*
