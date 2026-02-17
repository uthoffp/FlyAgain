# Unity Client Setup

Manual setup instructions for the FlyAgain Unity client.

## Prerequisites

- Unity Hub installed
- Unity 2022.3 LTS (install via Unity Hub)
- `protoc` installed (`brew install protobuf`)

## 1. Create Unity Project

1. Open Unity Hub
2. Click "New Project"
3. Select **3D (URP)** template
4. Set project path to the `client/` directory in the monorepo root
5. Click "Create project"

## 2. Create Folder Structure

Inside `Assets/`, create the following directories:

```
Scripts/
  Network/
  UI/
  Game/
  Entity/
  Generated/
    Proto/
```

## 3. Install Google.Protobuf

Option A — NuGetForUnity:
1. In Unity, go to Window > Package Manager
2. Add package from git URL: `https://github.com/GlitchEnzo/NuGetForUnity.git?path=/src/NuGetForUnity`
3. Once installed, go to NuGet > Manage NuGet Packages
4. Search for `Google.Protobuf` and install it

Option B — Manual DLL:
1. Download `Google.Protobuf` NuGet package from nuget.org
2. Extract the `.nupkg` (rename to `.zip`)
3. Copy `lib/netstandard2.0/Google.Protobuf.dll` to `Assets/Plugins/`

## 4. Generate C# Protobuf Classes

From the monorepo root, run:

```bash
./scripts/generate-csharp-protos.sh
```

This generates C# classes from `shared/proto/*.proto` into `client/Assets/Scripts/Generated/Proto/`.

## 5. Configure Build Targets

1. Go to File > Build Settings
2. Add target platforms: Windows, macOS, Linux
3. Set default platform to your development OS

## 6. Verify

- No compile errors in Unity console
- Generated proto classes are accessible (e.g., `FlyAgain.Proto.LoginRequest`)
