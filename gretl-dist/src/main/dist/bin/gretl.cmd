@echo off
setlocal
set "BASE_DIR=%~dp0.."
if defined JAVA_HOME (
  set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_BIN=java"
)
"%JAVA_BIN%" %GRETL_JAVA_OPTS% -cp "%BASE_DIR%\lib\runtime\*;%BASE_DIR%\lib\tasks\*;%BASE_DIR%\lib\extensions\*" ch.so.agi.gretl.cli.Main %*
exit /b %ERRORLEVEL%
