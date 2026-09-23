@echo off
rem Runs ContextSwitcher until it is really quit: exit code 55 means "Restart to
rem update" was clicked, so pull, rebuild and start it again. Any other code ends
rem the loop.
cd /d "%~dp0.."
rem Tells the app a restart request will be acted on (AppUpdate.RUN_LOOP_ENV).
set CONTEXTSWITCHER_RUN_LOOP=1
:loop
call git pull --no-rebase || exit /b 1
call gradlew.bat :app:jpackageImage || exit /b 1
call jbang scripts\WhatsNew.java || exit /b 1
app\build\jpackage\image\ContextSwitcher\ContextSwitcher.exe
if %errorlevel% equ 55 goto loop
exit /b %errorlevel%
