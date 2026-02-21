#!/usr/bin/env pwsh
# Cross-platform PowerShell script for generating C# protobuf files
# Works on Windows, macOS, and Linux with PowerShell Core

$ErrorActionPreference = "Stop"

# Get script directory and project root
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir

$ProtoDir = Join-Path $ProjectRoot "shared" "proto"
$OutputDir = Join-Path $ProjectRoot "client" "Assets" "Scripts" "Generated" "Proto"

# Check if protoc is installed
$protocExists = Get-Command protoc -ErrorAction SilentlyContinue
if (-not $protocExists) {
    Write-Error @"
Error: protoc is not installed.

Installation instructions:
  Windows:  choco install protobuf
            OR download from https://github.com/protocolbuffers/protobuf/releases
  macOS:    brew install protobuf
  Linux:    apt install protobuf-compiler (Debian/Ubuntu)
            OR yum install protobuf-compiler (RHEL/CentOS)
"@
    exit 1
}

# Create output directory if it doesn't exist
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

Write-Host "Generating C# protobuf files..." -ForegroundColor Cyan

# Get all .proto files
$protoFiles = Get-ChildItem -Path $ProtoDir -Filter "*.proto" | Select-Object -ExpandProperty FullName

if ($protoFiles.Count -eq 0) {
    Write-Warning "No .proto files found in $ProtoDir"
    exit 0
}

# Run protoc for each proto file
foreach ($protoFile in $protoFiles) {
    $fileName = Split-Path -Leaf $protoFile
    Write-Host "  Processing $fileName..." -ForegroundColor Gray

    & protoc --proto_path="$ProtoDir" --csharp_out="$OutputDir" $protoFile

    if ($LASTEXITCODE -ne 0) {
        Write-Error "protoc command failed for $fileName"
        exit 1
    }
}

Write-Host "`nGenerated C# files in $OutputDir`:" -ForegroundColor Green
Get-ChildItem -Path $OutputDir -Filter "*.cs" | Select-Object Name, Length, LastWriteTime | Format-Table -AutoSize

Write-Host "Success! Protobuf C# files generated." -ForegroundColor Green
