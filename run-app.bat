@echo off
REM V.E.C.T.O.R Desktop App Launcher

cd /d %~dp0

echo Starting V.E.C.T.O.R Desktop App...
java -jar target\vector-1.0.0.jar --app
pause