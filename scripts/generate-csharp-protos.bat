@echo off
setlocal enabledelayedexpansion

REM Get script directory and project root
set "SCRIPT_DIR=%~dp0"
set "PROJECT_ROOT=%SCRIPT_DIR%..\"

set "PROTO_DIR=%PROJECT_ROOT%shared\proto"
set "OUTPUT_DIR=%PROJECT_ROOT%client\Assets\Scripts\Generated\Proto"

REM Check if protoc is installed
where protoc >nul 2>nul
if %errorlevel% neq 0 (
    echo Error: protoc is not installed.
    echo Install via: choco install protobuf
    echo Or download from: https://github.com/protocolbuffers/protobuf/releases
    exit /b 1
)

REM Create output directory if it doesn't exist
if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"

echo Generating C# protobuf files...
protoc --proto_path="%PROTO_DIR%" --csharp_out="%OUTPUT_DIR%" "%PROTO_DIR%"\*.proto

if %errorlevel% neq 0 (
    echo Error: protoc command failed
    exit /b 1
)

echo Generated C# files in %OUTPUT_DIR%:
dir /B "%OUTPUT_DIR%"

echo.
echo Success! Protobuf C# files generated.
