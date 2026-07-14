@echo off
setlocal
set "ROOT=%~dp0\..\.."
set "ENVFILE=%ROOT%\.env.local"
if not exist "%ENVFILE%" (echo ERROR: %ENVFILE% not found.& exit /b 2)
where docker >nul 2>nul || (echo ERROR: docker.exe is not on PATH.& exit /b 3)
docker compose --env-file "%ENVFILE%" -f "%ROOT%\docker-compose.local.yml" down
exit /b %ERRORLEVEL%
