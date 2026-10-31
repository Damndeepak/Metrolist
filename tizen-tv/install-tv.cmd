@echo off
setlocal
cd /d "%~dp0"
set "TV_SDK=C:\tizen-studio"
set /p "TV_IP=Enter TV IP address: "
set /p "TV_PROFILE=Enter certificate profile name: "
if not exist "%TV_SDK%\tools\ide\bin\tizen.bat" (
 echo Tizen Web CLI missing. Install Main SDK - Tizen SDK tools - Web CLI.
 pause
 exit /b 1
)
"%TV_SDK%\tools\sdb.exe" connect %TV_IP%:26101
if errorlevel 1 goto failed
call "%TV_SDK%\tools\ide\bin\tizen.bat" build-web -- "%~dp0app"
if errorlevel 1 goto failed
call "%TV_SDK%\tools\ide\bin\tizen.bat" package -t wgt -s "%TV_PROFILE%" -- "%~dp0app\.buildResult"
if errorlevel 1 goto failed
set "TV_PACKAGE="
for %%F in ("%~dp0app\.buildResult\*.wgt") do set "TV_PACKAGE=%%~nxF"
if not defined TV_PACKAGE goto failed
call "%TV_SDK%\tools\ide\bin\tizen.bat" install -s %TV_IP%:26101 -n "%TV_PACKAGE%" -- "%~dp0app\.buildResult"
if errorlevel 1 goto failed
call "%TV_SDK%\tools\ide\bin\tizen.bat" run -s %TV_IP%:26101 -p DeepListTV.Player
if errorlevel 1 goto failed
echo Deeplist TV installed and launched.
pause
exit /b 0
:failed
echo Setup stopped. Send a screenshot of the error above. Do not share certificate passwords.
pause
exit /b 1
