@echo off
setlocal
if "%PGHOST%"=="" set "PGHOST=127.0.0.1"
if "%PGPORT%"=="" set "PGPORT=5433"
if "%PGUSER%"=="" set "PGUSER=ax_admin"
if "%PGDATABASE%"=="" set "PGDATABASE=ax_rag_local"
call "%~dp0apply-agent-v2-direct.cmd" local %*
exit /b %ERRORLEVEL%
