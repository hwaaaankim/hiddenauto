@echo off
setlocal
set "ROOT=%~dp0\..\.."
if "%PGHOST%"=="" set "PGHOST=127.0.0.1"
if "%PGPORT%"=="" set "PGPORT=5433"
if "%PGUSER%"=="" set "PGUSER=ax_admin"
if "%PGDATABASE%"=="" set "PGDATABASE=ax_rag_local"
if "%PGPASSWORD%"=="" (echo ERROR: PGPASSWORD is required.& exit /b 2)
where psql >nul 2>nul || (echo ERROR: psql.exe is not on PATH.& exit /b 3)
psql -X -v ON_ERROR_STOP=1 -h "%PGHOST%" -p "%PGPORT%" -U "%PGUSER%" -d "%PGDATABASE%" -f "%ROOT%\db\verify_agent_v2.sql"
exit /b %ERRORLEVEL%
