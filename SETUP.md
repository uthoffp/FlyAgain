# FlyAgain Development Setup Guide

Complete setup instructions for Windows and macOS developers.

## Table of Contents
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Detailed Setup](#detailed-setup)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required for Both Platforms

1. **Git**
   - Windows: Download from [git-scm.com](https://git-scm.com/download/win) or `winget install Git.Git`
   - macOS: `xcode-select --install` or `brew install git`

2. **Docker Desktop**
   - Windows: [Download Docker Desktop](https://docs.docker.com/desktop/setup/install/windows-install/) (WSL 2 backend required)
   - macOS: [Download Docker Desktop](https://docs.docker.com/desktop/setup/install/mac-install/)
   - Verify installation: `docker --version` and `docker compose version`

3. **Unity Hub & Unity 2022.3 LTS**
   - Download [Unity Hub](https://unity.com/download)
   - Install Unity 2022.3 LTS via Unity Hub (include "Windows Build Support" on Windows, "Mac Build Support" on macOS)

### Platform-Specific Requirements

#### Windows

4. **JDK 21** (for Kotlin server development)
   ```powershell
   # Using winget (recommended)
   winget install EclipseAdoptium.Temurin.21.JDK

   # OR using Chocolatey
   choco install temurin21
   ```
   Verify: `java -version` (should show version 21.x.x)

5. **Protocol Buffers Compiler**
   ```powershell
   # Using Chocolatey (recommended)
   choco install protobuf

   # OR download manually from:
   # https://github.com/protocolbuffers/protobuf/releases
   # Extract protoc.exe to a folder in your PATH
   ```
   Verify: `protoc --version`

6. **PowerShell 7+** (optional but recommended for better cross-platform scripting)
   ```powershell
   winget install Microsoft.PowerShell
   ```

#### macOS

4. **JDK 21** (for Kotlin server development)
   ```bash
   brew install openjdk@21

   # Add to PATH if needed
   echo 'export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"' >> ~/.zshrc
   source ~/.zshrc
   ```
   Verify: `java -version` (should show version 21.x.x)

5. **Protocol Buffers Compiler**
   ```bash
   brew install protobuf
   ```
   Verify: `protoc --version`

---

## Quick Start

### 1. Clone the Repository
```bash
git clone https://github.com/yourusername/FlyAgain.git
cd FlyAgain
```

### 2. Start Infrastructure (PostgreSQL + Redis)
```bash
# Both platforms
docker compose up -d postgres redis

# Verify services are healthy
docker compose ps
```

### 3. Generate Protocol Buffer Files

**Windows (Command Prompt/PowerShell):**
```powershell
# Using batch script
scripts\generate-csharp-protos.bat

# OR using PowerShell script (cross-platform)
pwsh scripts/generate-csharp-protos.ps1
```

**macOS/Linux:**
```bash
# Using bash script
./scripts/generate-csharp-protos.sh

# OR using PowerShell script (if pwsh installed)
pwsh scripts/generate-csharp-protos.ps1
```

### 4. Build and Run Server

**Windows (PowerShell/Command Prompt):**
```powershell
cd server

# Build all services
.\gradlew.bat build

# Run a specific service
.\gradlew.bat :login-service:run
```

**macOS/Linux:**
```bash
cd server

# Build all services
./gradlew build

# Run a specific service
./gradlew :login-service:run
```

### 5. Open Unity Project
1. Open Unity Hub
2. Click "Add" and select the `client/` folder
3. Open the project with Unity 2022.3 LTS
4. Wait for import to complete (first time may take 5-10 minutes)

---

## Detailed Setup

### Setting Up the Server (All Services via Docker)

If you want to run everything in Docker containers:

**Both Platforms:**
```bash
# From repository root
docker compose up --build -d

# View logs
docker compose logs -f

# Stop services
docker compose down

# Stop and remove volumes (fresh start)
docker compose down -v
```

### Setting Up the Unity Client

See [docs/unity-setup.md](docs/unity-setup.md) for detailed Unity configuration instructions.

**Quick summary:**
1. Open Unity Hub → Add Project → select `client/` folder
2. Install Google.Protobuf package (via NuGetForUnity or manually)
3. Generate protobuf classes (see step 3 in Quick Start)
4. Verify no errors in Unity Console

### Development Workflow

#### Running Services Locally (not in Docker)

**Windows:**
```powershell
# Start infrastructure only
docker compose up -d postgres redis

# Run services locally
cd server
.\gradlew.bat :database-service:run    # Terminal 1
.\gradlew.bat :login-service:run       # Terminal 2
.\gradlew.bat :account-service:run     # Terminal 3
.\gradlew.bat :world-service:run       # Terminal 4
```

**macOS:**
```bash
# Start infrastructure only
docker compose up -d postgres redis

# Run services locally
cd server
./gradlew :database-service:run    # Terminal 1
./gradlew :login-service:run       # Terminal 2
./gradlew :account-service:run     # Terminal 3
./gradlew :world-service:run       # Terminal 4
```

#### Updating Protobuf Definitions

When you modify `.proto` files in `shared/proto/`:

1. Regenerate C# files:
   - **Windows:** `scripts\generate-csharp-protos.bat`
   - **macOS:** `./scripts/generate-csharp-protos.sh`
   - **Cross-platform:** `pwsh scripts/generate-csharp-protos.ps1`

2. Rebuild Kotlin services:
   ```bash
   cd server
   ./gradlew build    # macOS/Linux
   .\gradlew.bat build # Windows
   ```

3. Restart Unity or reload C# scripts

---

## Troubleshooting

### Common Issues

#### "protoc: command not found" or "protoc is not recognized"

**Windows:**
```powershell
# Install via Chocolatey
choco install protobuf

# OR download from https://github.com/protocolbuffers/protobuf/releases
# Extract and add to PATH
```

**macOS:**
```bash
brew install protobuf
```

#### "java: command not found" or Java version mismatch

**Windows:**
```powershell
# Check current version
java -version

# Install JDK 21
winget install EclipseAdoptium.Temurin.21.JDK

# Restart terminal
```

**macOS:**
```bash
# Check current version
java -version

# Install JDK 21
brew install openjdk@21

# Add to PATH
echo 'export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

#### Docker "connection refused" or "service not healthy"

```bash
# Check service status
docker compose ps

# Restart services
docker compose restart postgres redis

# View service logs
docker compose logs postgres
docker compose logs redis

# Nuclear option - fresh start
docker compose down -v
docker compose up -d
```

#### Gradle builds fail on Windows

```powershell
# Ensure you're using gradlew.bat, not gradlew
cd server
.\gradlew.bat clean build

# If still failing, check Java version
java -version  # Should be 21.x.x
```

#### Unity "Assembly Definition" errors

1. Close Unity
2. Delete `client/Library` folder
3. Delete `client/Temp` folder
4. Reopen project in Unity

#### Line ending issues (Windows ↔ macOS collaboration)

```bash
# Configure git to handle line endings automatically
git config --global core.autocrlf true    # Windows
git config --global core.autocrlf input   # macOS/Linux

# This project includes .gitattributes to handle this automatically
```

#### Port already in use

**Windows:**
```powershell
# Find process using port 7777
netstat -ano | findstr :7777

# Kill process (replace PID)
taskkill /PID <PID> /F
```

**macOS:**
```bash
# Find process using port 7777
lsof -i :7777

# Kill process
kill -9 <PID>
```

---

## Verifying Your Setup

Run these commands to verify everything is installed correctly:

**Windows (PowerShell):**
```powershell
# Check versions
git --version
docker --version
docker compose version
java -version
protoc --version

# Check Docker services
docker compose ps

# Check Gradle
cd server
.\gradlew.bat --version
```

**macOS:**
```bash
# Check versions
git --version
docker --version
docker compose version
java -version
protoc --version

# Check Docker services
docker compose ps

# Check Gradle
cd server
./gradlew --version
```

All commands should execute without errors and show the correct versions.

---

## Additional Resources

- [Server README](server/README.md) - Detailed server documentation
- [Unity Setup Guide](docs/unity-setup.md) - Unity-specific setup
- [Architecture Documentation](docs/ARCHITECTURE.md) - Technical architecture details
- [Implementation Phases](docs/IMPLEMENTATION_PHASES.md) - Development roadmap
- [Game Design Document](docs/GDD.md) - Game design overview

---

## Getting Help

If you encounter issues not covered here:

1. Check the [GitHub Issues](https://github.com/yourusername/FlyAgain/issues)
2. Review service logs: `docker compose logs -f <service-name>`
3. Verify all prerequisites are installed correctly
4. Try the "nuclear option": `docker compose down -v && docker compose up --build -d`

For platform-specific issues, please include your OS version and the output of the verification commands above when reporting.
