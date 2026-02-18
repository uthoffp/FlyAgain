# FlyAgain Server

Kotlin/Gradle Microservice-Backend bestehend aus 4 Services:

| Service | Port(s) | Aufgabe |
|---|---|---|
| database-service | gRPC 9090 | PostgreSQL-Zugriff, Flyway-Migrationen, Write-Back |
| login-service | TCP 7777 | Authentifizierung, Registrierung, JWT, Sessions |
| account-service | TCP 7779 | Character CRUD, JWT-Validierung |
| world-service | TCP 7780, UDP 7781 | Gameplay, Game-Loop, Zonen, Combat, AI |

## Voraussetzungen

- **Docker Desktop** (inkl. `docker compose`)
  - [macOS](https://docs.docker.com/desktop/setup/install/mac-install/)
  - [Windows](https://docs.docker.com/desktop/setup/install/windows-install/) (WSL 2 Backend empfohlen)
- **JDK 21** — nur nötig bei lokaler Entwicklung ohne Docker

## Quick Start (Docker)

Alle Befehle werden vom **Repository-Root** (`FlyAgain/`) ausgeführt.

### Alle Services starten

```bash
docker compose up --build -d
```

Das startet PostgreSQL, Redis und alle 4 Services in der richtigen Reihenfolge (via `depends_on`).

### Logs verfolgen

```bash
# Alle Services
docker compose logs -f

# Einzelner Service
docker compose logs -f login-service
```

### Einzelne Services starten

```bash
# Nur Infrastruktur (Postgres + Redis)
docker compose up -d postgres redis

# Einen bestimmten Service (+ dessen Abhängigkeiten)
docker compose up --build -d database-service
docker compose up --build -d login-service
docker compose up --build -d account-service
docker compose up --build -d world-service
```

### Services stoppen

```bash
# Alles stoppen (Daten bleiben in Volumes erhalten)
docker compose down

# Alles stoppen UND Volumes löschen (DB-Reset)
docker compose down -v
```

### Services neu bauen (nach Code-Änderungen)

```bash
# Alle neu bauen und starten
docker compose up --build -d

# Nur einen bestimmten Service neu bauen
docker compose up --build -d login-service
```

## Startreihenfolge

Docker Compose startet die Services automatisch in der richtigen Reihenfolge:

```
PostgreSQL + Redis  (Healthchecks)
       ↓
database-service    (gRPC :9090)
       ↓
login-service       (TCP :7777)
account-service     (TCP :7779)
world-service       (TCP :7780, UDP :7781)
```

## Lokale Entwicklung (ohne Docker für Services)

Wenn du nur PostgreSQL und Redis per Docker laufen lässt und die Services lokal startest:

```bash
# 1. Infrastruktur starten
docker compose up -d postgres redis

# 2. Services einzeln starten (aus server/)
cd server
./gradlew :database-service:run
./gradlew :login-service:run
./gradlew :account-service:run
./gradlew :world-service:run
```

Unter Windows PowerShell statt `./gradlew` → `gradlew.bat` verwenden.

## Konfiguration

Umgebungsvariablen (Defaults sind für lokale Entwicklung vorkonfiguriert):

| Variable | Default | Beschreibung |
|---|---|---|
| `FLYAGAIN_DB_URL` | `jdbc:postgresql://localhost:5432/flyagain` | PostgreSQL JDBC URL |
| `FLYAGAIN_DB_USER` | `flyagain` | DB-Benutzer |
| `FLYAGAIN_DB_PASSWORD` | `flyagain_dev` | DB-Passwort |
| `FLYAGAIN_REDIS_URL` | `redis://localhost:6379` | Redis URL |
| `FLYAGAIN_JWT_SECRET` | `dev-secret-change-in-production` | JWT-Signatur-Secret |
| `FLYAGAIN_DB_SERVICE_HOST` | `localhost` | Hostname des database-service |
| `FLYAGAIN_DB_SERVICE_PORT` | `9090` | gRPC-Port des database-service |

Im Docker-Netzwerk werden die Hostnamen automatisch auf die Service-Namen aufgelöst (z.B. `postgres`, `redis`, `database-service`). Die `docker-compose.yml` setzt diese Werte bereits korrekt.

## Nützliche Gradle-Befehle (aus `server/`)

```bash
# Alle Services bauen
./gradlew build

# Einzelnen Service bauen
./gradlew :login-service:build

# Tests ausführen
./gradlew test

# Distribution erstellen
./gradlew installDist

# Aufräumen
./gradlew clean
```

## Troubleshooting

| Problem | Lösung |
|---|---|
| `java: command not found` | JDK 21 installieren: `brew install openjdk@21` (macOS) / `winget install EclipseAdoptium.Temurin.21.JDK` (Windows) |
| Port bereits belegt | Service auf dem Port stoppen oder Port via Umgebungsvariable ändern |
| DB/Redis Connection Error | `docker compose ps` prüfen — Postgres/Redis müssen `healthy` sein |
| Docker Build schlägt fehl | `docker compose build --no-cache <service-name>` versuchen |
| Datenbank zurücksetzen | `docker compose down -v && docker compose up -d` |
