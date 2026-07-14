@echo off
setlocal
if "%PGHOST%"=="" set "PGHOST=127.0.0.1"
if "%PGPORT%"=="" set "PGPORT=15433"
if "%PGUSER%"=="" set "PGUSER=ax_admin"
if "%PGDATABASE%"=="" set "PGDATABASE=ax_rag_dev"
call "%~dp0enqueue-semantic-direct.cmd" dev
exit /b %ERRORLEVEL%
