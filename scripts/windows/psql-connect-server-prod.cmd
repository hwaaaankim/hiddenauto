@echo off
setlocal
if "%PGHOST%"=="" set "PGHOST=127.0.0.1"
if "%PGPORT%"=="" set "PGPORT=15434"
if "%PGUSER%"=="" set "PGUSER=ax_rag_prod_user"
if "%PGDATABASE%"=="" set "PGDATABASE=ax_rag_prod"
if "%PGPASSWORD%"=="" (echo ERROR: PGPASSWORD is required.& exit /b 2)
where psql >nul 2>nul || (echo ERROR: psql.exe is not on PATH.& exit /b 3)
psql -X -h "%PGHOST%" -p "%PGPORT%" -U "%PGUSER%" -d "%PGDATABASE%"
exit /b %ERRORLEVEL%
