@echo off
setlocal EnableExtensions EnableDelayedExpansion
set "TARGET=%~1"
set "ENQUEUE=%~2"
set "ROOT=%~dp0\..\.."

if "%TARGET%"=="" goto :usage
if /I not "%TARGET%"=="local" if /I not "%TARGET%"=="dev" if /I not "%TARGET%"=="prod" goto :usage
if "%PGHOST%"=="" set "PGHOST=127.0.0.1"
if "%PGUSER%"=="" set "PGUSER=ax_admin"
if "%PGPASSWORD%"=="" (echo ERROR: PGPASSWORD for the migration/admin account is required.& exit /b 2)
if "%PGPORT%"=="" (echo ERROR: PGPORT is required.& exit /b 2)
if "%PGDATABASE%"=="" (echo ERROR: PGDATABASE is required.& exit /b 2)
where psql >nul 2>nul || (echo ERROR: psql.exe is not on PATH.& exit /b 3)
where pg_dump >nul 2>nul || (echo ERROR: pg_dump.exe is not on PATH.& exit /b 3)

if /I "%TARGET%"=="prod" (
  set /p "ANSWER=PROD DB %PGDATABASE% patch. Type APPLY-PROD exactly: "
  if not "!ANSWER!"=="APPLY-PROD" (echo Cancelled.& exit /b 4)
)

echo [0/5] CONNECTION CHECK %PGHOST%:%PGPORT%/%PGDATABASE%
psql -X -v ON_ERROR_STOP=1 -h "%PGHOST%" -p "%PGPORT%" -U "%PGUSER%" -d "%PGDATABASE%" -c "select current_database(), current_user, now();" || exit /b 9
echo [1/5] PRECHECK
psql -X -v ON_ERROR_STOP=1 -h "%PGHOST%" -p "%PGPORT%" -U "%PGUSER%" -d "%PGDATABASE%" -f "%ROOT%\db\precheck_agent_v2.sql" || exit /b 10
echo [2/5] BACKUP
call "%ROOT%\scripts\windows\backup-agent-db.cmd" || exit /b 11
echo [3/5] APPLY 022 AGENT V2
psql -X -v ON_ERROR_STOP=1 -h "%PGHOST%" -p "%PGPORT%" -U "%PGUSER%" -d "%PGDATABASE%" -f "%ROOT%\db\schema_patch_20260714_agent_v2_semantic_memory.sql" || exit /b 12
echo [4/5] VERIFY
psql -X -v ON_ERROR_STOP=1 -h "%PGHOST%" -p "%PGPORT%" -U "%PGUSER%" -d "%PGDATABASE%" -f "%ROOT%\db\verify_agent_v2.sql" || exit /b 13
if /I "%ENQUEUE%"=="--enqueue" (
  echo [5/5] ENQUEUE EXISTING DATA
  psql -X -v ON_ERROR_STOP=1 -h "%PGHOST%" -p "%PGPORT%" -U "%PGUSER%" -d "%PGDATABASE%" -f "%ROOT%\db\enqueue_semantic_rebuild_all.sql" || exit /b 14
) else (
  echo [5/5] ENQUEUE SKIPPED. Run the matching enqueue script after application configuration is ready.
)
echo %TARGET% Agent v2 DB patch completed successfully.
exit /b 0

:usage
echo Usage: apply-agent-v2-direct.cmd local^|dev^|prod [--enqueue]
exit /b 64
