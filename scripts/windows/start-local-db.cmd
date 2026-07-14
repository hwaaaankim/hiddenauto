@echo off
setlocal
set "ROOT=%~dp0\..\.."
set "ENVFILE=%ROOT%\.env.local"
if not exist "%ENVFILE%" (
  echo ERROR: %ENVFILE% not found.
  echo Copy .env.local.example to .env.local and set passwords first.
  exit /b 2
)
where docker >nul 2>nul || (echo ERROR: docker.exe is not on PATH.& exit /b 3)
docker compose --env-file "%ENVFILE%" -f "%ROOT%\docker-compose.local.yml" up -d
exit /b %ERRORLEVEL%
