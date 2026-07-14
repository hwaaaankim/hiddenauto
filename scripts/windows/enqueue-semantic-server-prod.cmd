@echo off
setlocal
if "%PGHOST%"=="" set "PGHOST=127.0.0.1"
if "%PGPORT%"=="" set "PGPORT=15434"
if "%PGUSER%"=="" set "PGUSER=ax_admin"
if "%PGDATABASE%"=="" set "PGDATABASE=ax_rag_prod"
call "%~dp0enqueue-semantic-direct.cmd" prod
exit /b %ERRORLEVEL%
