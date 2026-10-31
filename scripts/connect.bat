@echo off
rem Connect to the tablet over wireless ADB (Android 13).
rem   scripts\connect.bat          reuse debug.properties, then prompt
rem   scripts\connect.bat --pair   force a fresh pairing
rem The port and pairing code change after every reboot or toggle; on failure this asks for
rem new values and rewrites debug.properties.
setlocal EnableDelayedExpansion

set "ROOT=%~dp0.."
set "PROPS=%ROOT%\debug.properties"
if not exist "%PROPS%" copy "%ROOT%\debug.properties.example" "%PROPS%" >nul

for /f "usebackq tokens=1,* delims==" %%A in ("%PROPS%") do set "%%A=%%B"

set "ADB=adb"
if exist "%ROOT%\local.properties" (
  for /f "usebackq tokens=1,* delims==" %%A in ("%ROOT%\local.properties") do if "%%A"=="sdk.dir" set "SDK=%%B"
)
if defined SDK (
  set "SDK=!SDK:\\=\!"
  set "SDK=!SDK:\:=:!"
  if exist "!SDK!\platform-tools\adb.exe" set "ADB=!SDK!\platform-tools\adb.exe"
)

if /i "%~1"=="--pair" goto pair

if not "%ADB_PORT:~0,1%"=="[" (
  call :try %ADB_PORT%
  if !errorlevel!==0 goto ok
)
echo Could not connect with the saved port. It changes after every reboot or Wireless-debugging toggle.

:pair
echo.
echo On the tablet: Settings ^> Developer options ^> Wireless debugging
echo   - "Pair device with pairing code" shows the PAIRING port and 6-digit code
echo   - The main screen shows "IP address ^& Port" (that is the ADB port)
set /p "PAIRING_PORT=Pairing port: "
set /p "PAIRING_CODE=Pairing code: "
"%ADB%" pair %DEVICE_IP%:%PAIRING_PORT% %PAIRING_CODE%
set /p "ADB_PORT=ADB port: "
call :try %ADB_PORT%
if !errorlevel!==0 goto ok
echo Still not connected. Same Wi-Fi? Current port?
exit /b 1

:ok
(
  echo DEVICE_IP=%DEVICE_IP%
  echo PAIRING_PORT=%PAIRING_PORT%
  echo PAIRING_CODE=%PAIRING_CODE%
  echo ADB_PORT=%ADB_PORT%
) > "%PROPS%"
echo Connected to %DEVICE_IP%:%ADB_PORT%
echo Next: gradlew installAndLaunch
exit /b 0

:try
"%ADB%" connect %DEVICE_IP%:%1
for /f %%S in ('"%ADB%" -s %DEVICE_IP%:%1 get-state 2^>nul') do if "%%S"=="device" exit /b 0
exit /b 1
