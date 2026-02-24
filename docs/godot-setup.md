# FlyAgain – Godot Client Setup Guide

Dieses Dokument beschreibt, wie der Godot-Client eingerichtet und lokal betrieben wird.
Alle Schritte sind für **Windows** und **macOS** dokumentiert.

---

## Voraussetzungen

### Godot 4 installieren

**Windows:**
```powershell
# Option 1: winget (empfohlen)
winget install GodotEngine.GodotEngine

# Option 2: manuell
# https://godotengine.org/download/windows/
# ZIP entpacken, godot.exe irgendwo ablegen (z.B. C:\Tools\Godot\)
# Optional: zum PATH hinzufügen für CLI-Verwendung
```

**macOS:**
```bash
# Option 1: Homebrew (empfohlen)
brew install --cask godot

# Option 2: manuell
# https://godotengine.org/download/macos/
# .dmg öffnen, Godot.app nach /Applications/ ziehen
```

> **Wichtig:** Die **Standard-Version** (GDScript) herunterladen, **nicht** die .NET/Mono-Version.
> Versionsprüfung: Godot Editor öffnen → Titelleiste zeigt "Godot Engine v4.x.x"

---

## Projekt öffnen

### Über den Godot Editor (empfohlen)

1. Godot Editor starten
2. Im **Project Manager** auf **"Import"** klicken
3. Den Ordner `client/` auswählen (oder direkt `client/project.godot` angeben)
4. **"Import & Edit"** klicken
5. Das Projekt öffnet sich mit dem LoginScreen als Hauptszene

### Über die Kommandozeile

**Windows:**
```powershell
# Godot muss im PATH sein (bei winget-Installation automatisch)
godot --path client/ --editor
```

**macOS:**
```bash
# Bei Homebrew-Installation
godot --path client/ --editor

# Bei manueller Installation
/Applications/Godot.app/Contents/MacOS/Godot --path client/ --editor
```

---

## Projektstruktur

```
client/
├── project.godot          # Godot-Projektkonfiguration (Hauptszene, Autoloads, Export)
├── autoloads/             # Globale Singletons (werden automatisch geladen)
│   ├── NetworkManager.gd  # TCP + UDP Verbindungsverwaltung
│   └── GameState.gd       # Globaler Spielzustand (eingeloggter Spieler etc.)
├── scenes/
│   └── ui/
│       └── login/
│           ├── LoginScreen.tscn   # Login-Szene
│           └── LoginScreen.gd     # Login-Logik
├── scripts/
│   ├── network/
│   │   └── PacketProtocol.gd     # Opcode-Konstanten + Paket-Serialisierung
│   └── proto/
│       ├── ProtoEncoder.gd        # Manuelle Protobuf-Codierung (GDScript)
│       └── ProtoDecoder.gd        # Manuelle Protobuf-Decodierung (GDScript)
└── themes/
    ├── Colors.gd                  # Farbpalette
    └── ThemeFactory.gd            # UI-Theme-Fabrik
```

---

## Autoloads konfigurieren

Autoloads sind in `client/project.godot` bereits eingetragen. Zur Überprüfung:

1. Godot Editor → **Project → Project Settings → Autoloads**
2. Folgende Einträge müssen vorhanden sein:

| Name           | Pfad                          |
|----------------|-------------------------------|
| NetworkManager | `res://autoloads/NetworkManager.gd` |
| GameState      | `res://autoloads/GameState.gd`      |

Falls ein Eintrag fehlt: **"+"**-Button → Pfad eintragen → Namen vergeben → **"Add"**.

---

## Spiel starten (im Editor)

1. Godot Editor öffnen, Projekt geladen
2. Server starten (siehe [SETUP.md](../SETUP.md)):
   ```bash
   docker compose up -d
   cd server && ./gradlew :login-service:run   # macOS
   cd server && .\gradlew.bat :login-service:run  # Windows
   ```
3. Im Godot Editor **F5** drücken (oder **"Play"**-Button oben rechts)
4. Der LoginScreen erscheint – Verbindung zum Server auf `localhost:7777`

---

## Exportieren (Build erstellen)

### Export-Templates herunterladen (einmalig)

1. Godot Editor → **Editor → Manage Export Templates**
2. **"Download and Install"** für die aktuelle Godot-Version klicken
3. Warten bis der Download abgeschlossen ist

> Export-Templates sind **nicht** im Repository enthalten (zu groß). Sie werden pro Developer lokal gespeichert.

### Windows-Build erstellen

**Im Editor:**
1. **Project → Export**
2. Plattform **"Windows Desktop"** auswählen (ggf. zuerst **"Add..."** klicken)
3. Export-Pfad festlegen (z.B. `builds/windows/FlyAgain.exe`)
4. **"Export Project"** klicken

**Per CLI (Windows & macOS):**
```bash
# macOS - exportiert für Windows (Cross-Compile)
/Applications/Godot.app/Contents/MacOS/Godot --headless --export-release "Windows Desktop" builds/windows/FlyAgain.exe --path client/

# Windows
godot --headless --export-release "Windows Desktop" builds/windows/FlyAgain.exe --path client/
```

### macOS-Build erstellen

```bash
# Nur auf macOS möglich (Apple-Signierungsanforderungen)
/Applications/Godot.app/Contents/MacOS/Godot --headless --export-release "macOS" builds/macos/FlyAgain.dmg --path client/
```

### Linux-Build erstellen

```bash
# Auf macOS oder Linux
godot --headless --export-release "Linux/X11" builds/linux/FlyAgain.x86_64 --path client/
```

---

## Serververbindung konfigurieren

Die Server-Adresse ist in `NetworkManager.gd` als Konstante definiert:

```gdscript
# client/autoloads/NetworkManager.gd
const LOGIN_HOST = "localhost"
const LOGIN_PORT = 7777
```

Für eine andere Umgebung (z.B. Test-Server) diese Werte anpassen oder über eine
Konfigurationsdatei auslesen.

---

## Protobuf-Workflow

Der Godot-Client verwendet **keine Codegenerierung**. Stattdessen sind
`ProtoEncoder.gd` und `ProtoDecoder.gd` manuelle Implementierungen, die das
Wire-Format direkt in GDScript verarbeiten.

**Bei Änderungen an `shared/proto/flyagain.proto`:**

1. Neues Feld oder neue Nachricht in der `.proto`-Datei hinzufügen
2. `ProtoEncoder.gd` erweitern: Feldnummer + Typ gemäß Protobuf-Spezifikation kodieren
3. `ProtoDecoder.gd` erweitern: Gleiche Feldnummer lesen und dekodieren
4. `PacketProtocol.gd` aktualisieren: Neuen Opcode eintragen (falls neue Nachricht)
5. Kotlin-Stubs neu generieren:
   ```bash
   cd server && ./gradlew build   # macOS/Linux
   cd server && .\gradlew.bat build  # Windows
   ```

---

## Häufige Probleme

### "Cannot connect to server" beim Login

- Server läuft nicht → `docker compose ps` und Service-Logs prüfen
- Falscher Port → `NetworkManager.gd` prüfen (`LOGIN_PORT = 7777`)
- Firewall blockiert Verbindung → Ausnahme für Port 7777 (TCP) hinzufügen

**Windows Firewall-Ausnahme:**
```powershell
New-NetFirewallRule -DisplayName "FlyAgain Dev Server" -Direction Inbound -Protocol TCP -LocalPort 7777,7779,7780 -Action Allow
```

### Autoload-Fehler beim Start ("Identifier not declared")

1. **Project → Project Settings → Autoloads** öffnen
2. Sicherstellen, dass `NetworkManager` und `GameState` eingetragen sind
3. Godot Editor neu starten

### `.godot/`-Ordner fehlt oder ist beschädigt

Der `.godot/`-Ordner ist der Editor-Cache und wird automatisch neu erstellt:

```bash
# Cache löschen (Godot muss geschlossen sein)
rm -rf client/.godot/   # macOS/Linux
Remove-Item -Recurse -Force client/.godot\   # Windows PowerShell
```

Godot beim nächsten Start neu öffnen → Cache wird automatisch regeneriert.

### Szene öffnet sich nicht als Hauptszene

1. **Project → Project Settings → Application → Run**
2. **"Main Scene"** auf `res://scenes/ui/login/LoginScreen.tscn` setzen

---

## Weiterführende Ressourcen

- [Offizielle Godot 4 Dokumentation](https://docs.godotengine.org/en/stable/)
- [GDScript Referenz](https://docs.godotengine.org/en/stable/tutorials/scripting/gdscript/index.html)
- [Godot Networking](https://docs.godotengine.org/en/stable/tutorials/networking/index.html)
- [ARCHITECTURE.md](ARCHITECTURE.md) – Technische Architektur (Netzwerk-Protokoll, Opcode-Tabelle)
- [SETUP.md](../SETUP.md) – Server und Infrastruktur aufsetzen
