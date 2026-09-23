@echo off
rem Registers the contextswitcher:// URL protocol for the current user, no admin needed (#47, MADR 0016).
rem Usage: register-url-handler.cmd [path\to\ContextSwitcher.exe]
rem Default: ContextSwitcher\ContextSwitcher.exe next to this script (the packageApp zip layout);
rem   in a source checkout, falls back to the locally built app image (gradlew :app:jpackageImage,
rem   or gradlew :app:registerUrlHandler to build and register in one step).
rem Re-run after moving the app image - the registry stores an absolute path.
setlocal
set "EXE=%~1"
if not "%~1"=="" goto :check
set "EXE=%~dp0ContextSwitcher\ContextSwitcher.exe"
if not exist "%EXE%" set "EXE=%~dp0..\app\build\jpackage\image\ContextSwitcher\ContextSwitcher.exe"
:check
if not exist "%EXE%" (
  echo ContextSwitcher.exe not found: "%EXE%"
  echo Build it first ^(gradlew :app:jpackageImage^) or pass its path as argument.
  exit /b 1
)
reg add "HKCU\Software\Classes\contextswitcher" /ve /d "URL:ContextSwitcher deep link" /f >nul
reg add "HKCU\Software\Classes\contextswitcher" /v "URL Protocol" /d "" /f >nul
reg add "HKCU\Software\Classes\contextswitcher\shell\open\command" /ve /d "\"%EXE%\" \"%%1\"" /f >nul
echo contextswitcher:// links now open %EXE%
