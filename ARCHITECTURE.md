# FlyAgain - Technische Architektur

## Uebersicht

```
┌──────────────────────────────────────┐
│         Client: Unity (C#)           │
│  - 3D Rendering (URP)                │
│  - Input, UI, Audio                  │
│  - Client-Side Prediction            │
└──────────────┬───────────────────────┘
               │ TCP (TLS 1.3) + UDP
┌──────────────┴───────────────────────┐
│      Game Server: Kotlin (Netty)     │
│  - Authoritative Game Logic          │
│  - Anti-Cheat Validierung            │
│  - Zone/Channel Management           │
│  - Entity Management + AI            │
│  - Chat, Combat, Inventory           │
└──────────────┬───────────────────────┘
               │
┌──────────────┴───────────────────────┐
│           Persistenz                 │
│  - PostgreSQL (Spielerdaten)         │
│  - Redis (Sessions, Cache)           │
└──────────────────────────────────────┘
```

**Ziel-Last:** max. 10.000 Accounts gleichzeitig online, max. 5.000 CCU

---

## 1. Netzwerk-Protokoll (Client <-> Server)

### 1.1 Dual-Stack: TCP + UDP

| Transport | Verwendung | Grund |
|-----------|-----------|-------|
| **TCP** (TLS 1.3) | Login, Chat, Inventar, Handel, Equip, Zone-Wechsel, Skill-Use, Entity Spawn/Despawn | Zuverlaessige Zustellung noetig |
| **UDP** | Bewegung, Position-Updates, Heartbeat | Geschwindigkeit > Zuverlaessigkeit |

### 1.2 Serialisierung: Protocol Buffers (Protobuf)

- Generiert Kotlin- **und** C#-Code aus einer `.proto`-Datei
- Kompaktes Binaerformat (kleiner als JSON, schneller als XML)
- Versionierbar (neue Felder ohne Breaking Changes)
- Single-Source-of-Truth: `server/src/main/proto/flyagain.proto`

### 1.3 Paket-Struktur

**TCP-Paket:**
```
┌─────────────┬──────────┬─────────────────┐
│ Length (4B) │ Opcode   │ Protobuf        │
│ uint32      │ (2B)     │ Payload (N)     │
└─────────────┴──────────┴─────────────────┘
```

**UDP-Paket:**
```
┌───────────────┬──────────────┬──────────┬─────────────────┬──────────────┐
│ SessionToken  │ Sequence     │ Opcode   │ Protobuf        │ HMAC-SHA256  │
│ (8B)          │ (4B) uint32  │ (2B)     │ Payload (N)     │ (32B)        │
└───────────────┴──────────────┴──────────┴─────────────────┴──────────────┘
```

- `SessionToken`: Identifikation (keine TCP-Verbindung vorhanden)
- `Sequence`: Duplikat-Erkennung und Ordering
- `HMAC-SHA256`: Signiert `[SessionToken|Sequence|Opcode|Payload]` mit einem
  Session-Secret, das waehrend des TCP-Logins ausgehandelt wird. Verhindert
  Paket-Faelschung durch Netzwerk-Sniffer (Token allein reicht nicht)

### 1.4 Opcode-Tabelle (MVP)

| Bereich | Opcode | Richtung | Transport | Beschreibung |
|---------|--------|----------|-----------|-------------|
| **Auth** | | | | |
| | `0x0001` | C->S | TCP | LoginRequest (username, password) |
| | `0x0002` | S->C | TCP | LoginResponse (jwt, characterList) |
| | `0x0003` | C->S | TCP | CharacterSelect (characterId) |
| | `0x0004` | S->C | TCP | EnterWorld (position, nearbyEntities) |
| | `0x0005` | C->S | TCP | CharacterCreate (name, class) |
| | `0x0006` | C->S | TCP | RegisterRequest (username, email, password) |
| | `0x0007` | S->C | TCP | RegisterResponse (success/error) |
| **Bewegung** | | | | |
| | `0x0101` | C->S | UDP | MovementInput (position, direction, velocity, isFlying, tickNr) |
| | `0x0102` | S->C | UDP | EntityPositionUpdate (entityId, position, direction, velocity, isFlying) |
| | `0x0103` | S->C | UDP | PositionCorrection (correctedPosition, serverTick) |
| **Kampf** | | | | |
| | `0x0201` | C->S | TCP | SelectTarget (entityId) |
| | `0x0202` | C->S | TCP | UseSkill (skillId) |
| | `0x0203` | S->C | TCP | DamageEvent (attackerId, targetId, damage, isCrit) |
| | `0x0204` | S->C | TCP | EntityDeath (entityId, killerId) |
| | `0x0205` | S->C | TCP | XpGain (amount, newTotalXp, levelUp?) |
| | `0x0206` | C->S | TCP | ToggleAutoAttack (on/off) |
| **Entity** | | | | |
| | `0x0301` | S->C | TCP | EntitySpawn (entityId, type, name, position, stats, appearance) |
| | `0x0302` | S->C | TCP | EntityDespawn (entityId) |
| | `0x0303` | S->C | TCP | EntityStatsUpdate (entityId, hp, mp, maxHp, maxMp) |
| **Inventar** | | | | |
| | `0x0401` | C->S | TCP | MoveItem (fromSlot, toSlot) |
| | `0x0402` | S->C | TCP | InventoryUpdate (slot, itemId, amount, enhancement) |
| | `0x0403` | C->S | TCP | EquipItem (inventorySlot, equipSlot) |
| | `0x0404` | C->S | TCP | UnequipItem (equipSlot) |
| | `0x0405` | C->S | TCP | NpcBuy (npcId, itemDefId, amount) |
| | `0x0406` | C->S | TCP | NpcSell (inventorySlot, amount) |
| | `0x0407` | S->C | TCP | GoldUpdate (newGoldAmount) |
| **Chat** | | | | |
| | `0x0501` | C->S | TCP | ChatMessage (channel, text) |
| | `0x0502` | S->C | TCP | ChatBroadcast (channel, senderName, text) |
| **System** | | | | |
| | `0x0601` | C<>S | TCP | Heartbeat (clientTime) |
| | `0x0602` | S->C | TCP | ServerMessage (type, text) |
| | `0x0603` | S->C | TCP | ErrorResponse (opcode, errorCode, message) |
| **Zone** | | | | |
| | `0x0701` | S->C | TCP | ZoneData (mapId, entities[], spawns[]) |
| | `0x0702` | C->S | TCP | ChannelSwitch (channelId) |
| | `0x0703` | S->C | TCP | ChannelList (channels[]{id, playerCount}) |

### 1.5 Tick-Rate und Client-Side Prediction

- **Server-Tick:** 20 Hz (50ms pro Tick)
- Client sendet `MovementInput` mit lokaler Tick-Nummer
- Server validiert Position (Geschwindigkeit, Kollision, Terrain)
- Bei Abweichung: `PositionCorrection` an Client
- **Eigene Bewegung:** Client-Side Prediction (sofortige Reaktion)
- **Andere Entities:** Interpolation mit 100ms Buffer (2 Server-Ticks)

### 1.6 Bandbreiten-Schaetzung

| Metrik | Wert |
|--------|------|
| Upstream pro Spieler | ~5 KB/s |
| Downstream pro Spieler | ~15 KB/s |
| Entities in Sichtweite | 30-50 (durch SpatialGrid begrenzt) |
| Position-Updates/Sekunde | 20 (= Tick-Rate) |
| **5.000 CCU gesamt** | **~75-100 MB/s** (Server-Downstream) |

Mit Interest Management (SpatialGrid) und Delta-Compression (Post-MVP) deutlich reduzierbar.

---

## 2. Server-Struktur

### 2.1 Architektur: Modularer Monolith

Ein Kotlin-Prozess mit klar getrennten Modulen. Begruendung:
- Solo-Entwickler: Ein Deployment, einfaches Debugging
- Kein Netzwerk-Overhead zwischen Services
- Spaetere Aufteilung moeglich ohne Architektur-Umbau

```
server/
├── network/          # Netty TCP/UDP, PacketRouter, SessionManager
├── auth/             # Login, Registrierung, JWT-Generierung
├── world/            # ZoneManager, ZoneChannel, SpatialGrid
├── entity/           # PlayerEntity, MonsterEntity, NPCEntity
├── combat/           # CombatEngine, SkillSystem, DamageCalc
├── inventory/        # InventoryManager, EquipmentManager, LootSystem
├── ai/               # MonsterAI (StateMachine)
├── chat/             # ChatManager (Say, Shout, Whisper)
├── persistence/      # DatabaseManager, CacheManager, WriteBackScheduler
└── gameloop/         # GameLoop (20 Hz Tick-Orchestrierung)
```

### 2.2 Zonen- und Channel-System

**Zonen** sind logische Weltsegmente (Aerheim, Gruene Ebene, Dunkler Wald).

**Channels** verteilen Spieler innerhalb einer Zone:
- Max. **1.000 Spieler pro Channel**
- Bei Ueberschreitung: Neuer Channel oeffnet automatisch
- Spieler koennen Channel manuell wechseln (Opcode `0x0702`)
- Channel-Wechsel = kurzer Ladebildschirm

**Zone-Wechsel:**
- Ladebildschirm (einfacher als Seamless, MVP-tauglich)
- Server speichert Character-State, entfernt aus alter Zone, fuegt in neue ein
- Client erhaelt `ZoneData` (0x0701) mit allen relevanten Entities

### 2.3 Interest Management: SpatialGrid

```
┌─────┬─────┬─────┬─────┐
│     │     │  M  │     │   M = Monster
├─────┼─────┼─────┼─────┤   P = Spieler
│     │  P  │  P  │     │
├─────┼─────┼─────┼─────┤   Spieler P erhaelt nur Updates
│     │  M  │ [P] │  M  │   aus seiner Zelle + 8 Nachbarzellen
├─────┼─────┼─────┼─────┤   (= 9 Zellen insgesamt)
│     │     │     │     │
└─────┴─────┴─────┴─────┘
Zellengroesse: 50x50 Einheiten
```

- Welt aufgeteilt in Grid-Zellen
- Jede Zelle haelt eine Liste ihrer Entities
- Broadcasts gehen nur an Spieler in relevanten Zellen
- Reduziert Netzwerk-Last von O(n^2) auf O(n * k) wobei k = lokale Entities

### 2.4 Game-Loop (20 Hz)

```kotlin
// Vereinfachte Darstellung
class GameLoop {
    private val TICK_RATE = 20          // Hz
    private val TICK_DURATION = 50L     // ms

    fun run() {
        while (running) {
            val tickStart = System.nanoTime()

            // 1. Input-Queue abarbeiten
            processIncomingPackets()

            // 2. Bewegung validieren + anwenden
            updateMovement(deltaTime)

            // 3. Kampf-Logik
            updateCombat(deltaTime)

            // 4. Monster-AI
            updateMonsterAI(deltaTime)

            // 5. Tote Entities verarbeiten
            processDeaths()

            // 6. State-Aenderungen broadcast
            broadcastStateChanges()

            // 7. Auf naechsten Tick warten
            sleepUntilNextTick(tickStart)
        }
    }
}
```

**Wichtig:** Der Game-Loop laeuft auf einem **dedizierten Thread**. Kein Locking noetig, da der gesamte Game-State single-threaded veraendert wird. I/O-Operationen (DB, Redis) laufen asynchron ueber Kotlin Coroutines mit `Dispatchers.IO`.

### 2.5 Monster-AI: State Machine

```
        Spieler in                    In Angriffs-
        Aggro-Range                   Range
┌──────┐         ┌───────┐         ┌────────┐
│ IDLE │────────►│ AGGRO │────────►│ ATTACK │
└──┬───┘         └───────┘         └───┬────┘
   ▲                                    │
   │  Spawn erreicht    Ziel tot /      │
┌──┴─────┐              zu weit weg     │
│ RETURN │◄─────────────────────────────┘
└────────┘
```

- IDLE: Monster steht am Spawn-Punkt, wartet
- AGGRO: Bewegt sich auf naechsten Spieler zu
- ATTACK: Fuehrt Auto-Attacks aus (attack_speed_ms Timer)
- RETURN: Laeuft zurueck zum Spawn, HP regeneriert

### 2.6 Concurrency-Modell

| Komponente | Thread/Dispatcher | Grund |
|-----------|-------------------|-------|
| Game-Loop | Dedizierter Thread | Single-threaded Game-State, kein Locking |
| Netty TCP/UDP | Netty EventLoop | Non-blocking I/O |
| Datenbank | `Dispatchers.IO` | Blocking I/O asynchron ausfuehren |
| Redis | `Dispatchers.IO` | Blocking I/O asynchron ausfuehren |
| Write-Back Timer | Coroutine + IO | Periodisches Speichern |

### 2.7 CCU-Verteilung (5.000 Spieler)

```
Aerheim (Stadt):         ~1.000 Spieler ->  1 Channel
Gruene Ebene (Lv 1-15): ~2.000 Spieler ->  2 Channels a 1.000
Dunkler Wald (Lv 15-30):~1.500 Spieler ->  2 Channels a 750
Dungeons (Instanzen):      ~500 Spieler -> 100 Instanzen a 5
──────────────────────────────────────────────────────────────
Gesamt:                   5.000 Spieler     5 Channels + 100 Instanzen
```

### 2.8 Skalierungspfad

1. **MVP:** Ein Server-Prozess, Vertical Scaling (mehr RAM/CPU)
2. **Phase 2:** Login/Auth als separater Service abtrennen
3. **Phase 3:** Chat-Server abtrennen, Zone-Server aufteilen (jede Zone eigener Prozess)
4. **Falls noetig:** Mehrere physische Server mit Load Balancer

### 2.9 Dungeon-Instanzen (Post-MVP)

- Eigene `DungeonInstance`-Objekte mit separatem Game-State
- Max. 5 Spieler pro Instanz (Party-Groesse)
- Eigener Tick-Loop (kann niedrigere Rate haben wenn alle Spieler im selben Raum)
- Automatische Aufraeumung nach Completion oder Timeout (30 Minuten)

---

## 3. Datenbank-Schema

### 3.1 Prinzip

| System | Verwendung | Persistenz |
|--------|-----------|-----------|
| **PostgreSQL** | Accounts, Charaktere, Inventar, Spieldefinitionen | Permanent |
| **Redis** | Sessions, Character-Cache, Zone-State, Rate-Limits | Temporaer |
| **RAM** | Aktiver Game-State (Positionen, HP, Cooldowns) | Nur zur Laufzeit |

### 3.2 PostgreSQL-Schema

#### Accounts

```sql
CREATE TABLE accounts (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(32) UNIQUE NOT NULL,
    email           VARCHAR(255) UNIQUE NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login      TIMESTAMPTZ,
    is_banned       BOOLEAN NOT NULL DEFAULT FALSE,
    ban_reason      TEXT,
    ban_until       TIMESTAMPTZ
);
```

#### Charaktere

```sql
CREATE TABLE characters (
    id              BIGSERIAL PRIMARY KEY,
    account_id      BIGINT NOT NULL REFERENCES accounts(id),
    name            VARCHAR(32) UNIQUE NOT NULL,
    class           SMALLINT NOT NULL,
    level           SMALLINT NOT NULL DEFAULT 1,
    xp              BIGINT NOT NULL DEFAULT 0,
    hp              INT NOT NULL,
    mp              INT NOT NULL,
    max_hp          INT NOT NULL,
    max_mp          INT NOT NULL,
    str             SMALLINT NOT NULL,
    sta             SMALLINT NOT NULL,
    dex             SMALLINT NOT NULL,
    int_stat        SMALLINT NOT NULL,
    stat_points     SMALLINT NOT NULL DEFAULT 0,
    map_id          SMALLINT NOT NULL DEFAULT 1,
    pos_x           REAL NOT NULL DEFAULT 0,
    pos_y           REAL NOT NULL DEFAULT 0,
    pos_z           REAL NOT NULL DEFAULT 0,
    gold            BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    play_time       BIGINT NOT NULL DEFAULT 0,
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_class CHECK (class BETWEEN 0 AND 3),
    CONSTRAINT chk_level CHECK (level BETWEEN 1 AND 200),
    CONSTRAINT chk_xp CHECK (xp >= 0),
    CONSTRAINT chk_gold CHECK (gold >= 0),
    CONSTRAINT chk_stats CHECK (str >= 0 AND sta >= 0 AND dex >= 0 AND int_stat >= 0),
    CONSTRAINT chk_stat_points CHECK (stat_points >= 0),
    CONSTRAINT chk_hp CHECK (hp >= 0 AND hp <= max_hp AND max_hp > 0),
    CONSTRAINT chk_mp CHECK (mp >= 0 AND mp <= max_mp AND max_mp > 0)
);

CREATE INDEX idx_characters_account ON characters(account_id);
```

- Max. 3 Charaktere pro Account (Application-Level Constraint)
- `class`: 0=Krieger, 1=Magier, 2=Assassine, 3=Kleriker
- `is_deleted`: Soft-Delete statt hartem Loeschen
- CHECK-Constraints als letzte Verteidigungslinie gegen korrupte Daten

#### Item-Definitionen (Statische Spieldaten)

```sql
CREATE TABLE item_definitions (
    id              SERIAL PRIMARY KEY,
    name            VARCHAR(64) NOT NULL,
    type            SMALLINT NOT NULL,
    subtype         SMALLINT NOT NULL DEFAULT 0,
    level_req       SMALLINT NOT NULL DEFAULT 1,
    class_req       SMALLINT,
    rarity          SMALLINT NOT NULL DEFAULT 0,
    base_attack     SMALLINT NOT NULL DEFAULT 0,
    base_defense    SMALLINT NOT NULL DEFAULT 0,
    base_hp         SMALLINT NOT NULL DEFAULT 0,
    base_mp         SMALLINT NOT NULL DEFAULT 0,
    buy_price       INT NOT NULL DEFAULT 0,
    sell_price      INT NOT NULL DEFAULT 0,
    stackable       BOOLEAN NOT NULL DEFAULT FALSE,
    max_stack       SMALLINT NOT NULL DEFAULT 1,
    description     TEXT
);
```

- `type`: 0=Waffe, 1=Ruestung, 2=Verbrauchbar, 3=Material
- `rarity`: 0=Common, 1=Uncommon, 2=Rare, 3=Epic
- `class_req`: NULL = alle Klassen koennen es tragen

#### Inventar

```sql
CREATE TABLE inventory (
    id              BIGSERIAL PRIMARY KEY,
    character_id    BIGINT NOT NULL REFERENCES characters(id),
    slot            SMALLINT NOT NULL,
    item_id         INT NOT NULL REFERENCES item_definitions(id),
    amount          SMALLINT NOT NULL DEFAULT 1,
    enhancement     SMALLINT NOT NULL DEFAULT 0,
    UNIQUE (character_id, slot),

    CONSTRAINT chk_slot CHECK (slot BETWEEN 0 AND 99),
    CONSTRAINT chk_amount CHECK (amount >= 1),
    CONSTRAINT chk_enhancement CHECK (enhancement BETWEEN 0 AND 10)
);

CREATE INDEX idx_inventory_character ON inventory(character_id);
```

- 100 Slots (0-99), durch CHECK-Constraint auf DB-Ebene erzwungen
- `enhancement`: +0 bis +10, durch CHECK-Constraint begrenzt
- `amount >= 1`: Leere Slots werden geloescht, nicht auf 0 gesetzt

#### Equipment

```sql
CREATE TABLE equipment (
    character_id    BIGINT NOT NULL REFERENCES characters(id),
    slot_type       SMALLINT NOT NULL,
    inventory_id    BIGINT NOT NULL REFERENCES inventory(id),
    PRIMARY KEY (character_id, slot_type)
);
```

- `slot_type`: 0=Waffe, 1=Helm, 2=Brust, 3=Hose, 4=Schuhe, 5=Ring, 6=Halskette

#### Skills

```sql
CREATE TABLE skill_definitions (
    id              SERIAL PRIMARY KEY,
    name            VARCHAR(64) NOT NULL,
    class_req       SMALLINT NOT NULL,
    level_req       SMALLINT NOT NULL,
    max_level       SMALLINT NOT NULL DEFAULT 5,
    mp_cost         SMALLINT NOT NULL,
    cooldown_ms     INT NOT NULL,
    base_damage     SMALLINT NOT NULL DEFAULT 0,
    damage_per_level SMALLINT NOT NULL DEFAULT 0,
    range_units     REAL NOT NULL DEFAULT 2.0,
    description     TEXT
);

CREATE TABLE character_skills (
    character_id    BIGINT NOT NULL REFERENCES characters(id),
    skill_id        INT NOT NULL REFERENCES skill_definitions(id),
    skill_level     SMALLINT NOT NULL DEFAULT 1,
    PRIMARY KEY (character_id, skill_id)
);
```

#### Monster und Spawns

```sql
CREATE TABLE monster_definitions (
    id              SERIAL PRIMARY KEY,
    name            VARCHAR(64) NOT NULL,
    level           SMALLINT NOT NULL,
    hp              INT NOT NULL,
    attack          SMALLINT NOT NULL,
    defense         SMALLINT NOT NULL,
    xp_reward       INT NOT NULL,
    aggro_range     REAL NOT NULL DEFAULT 10.0,
    attack_range    REAL NOT NULL DEFAULT 2.0,
    attack_speed_ms INT NOT NULL DEFAULT 2000,
    move_speed      REAL NOT NULL DEFAULT 3.0
);

CREATE TABLE monster_spawns (
    id              SERIAL PRIMARY KEY,
    monster_id      INT NOT NULL REFERENCES monster_definitions(id),
    map_id          SMALLINT NOT NULL,
    pos_x           REAL NOT NULL,
    pos_y           REAL NOT NULL,
    pos_z           REAL NOT NULL,
    spawn_radius    REAL NOT NULL DEFAULT 5.0,
    spawn_count     SMALLINT NOT NULL DEFAULT 1,
    respawn_ms      INT NOT NULL DEFAULT 30000
);
```

#### Loot-Tabelle

```sql
CREATE TABLE loot_table (
    id              SERIAL PRIMARY KEY,
    monster_id      INT NOT NULL REFERENCES monster_definitions(id),
    item_id         INT NOT NULL REFERENCES item_definitions(id),
    drop_chance     REAL NOT NULL,
    min_amount      SMALLINT NOT NULL DEFAULT 1,
    max_amount      SMALLINT NOT NULL DEFAULT 1
);

CREATE INDEX idx_loot_monster ON loot_table(monster_id);
```

### 3.3 Redis-Schema

```
session:{sessionId}          -> JSON{accountId, characterId, ip, loginTime,    TTL 24h
                                     hmacSecret}
session:account:{accountId}  -> sessionId (Reverse-Lookup fuer Multi-Login)    TTL 24h
character:{charId}           -> Serialisierter Character-State                  TTL 1h
zone:{mapId}:channel:{n}     -> SET von Character-IDs                          Kein TTL
rate_limit:{ip}:{action}     -> INT Counter                                    TTL 60s
online_players               -> SET von Character-IDs                          Kein TTL
```

### 3.4 Write-Back Strategie

```
                  ┌─────────────┐
  Game-State ────►│    RAM      │  (Echtzeit, jeder Tick)
  (Positionen,    └──────┬──────┘
   HP, Cooldowns)        │
                         │ alle 60 Sekunden
                         ▼
                  ┌─────────────┐
                  │   Redis     │  (Cache, schnelle Recovery)
                  └──────┬──────┘
                         │ alle 5 Minuten + bei Logout/Zone-Wechsel
                         ▼
                  ┌─────────────┐
                  │ PostgreSQL  │  (Permanente Persistenz)
                  └─────────────┘

  Ausnahme: Inventar-Aenderungen -> sofort nach PostgreSQL
```

- Bei Server-Crash: Max. 60 Sekunden Positionsverlust (akzeptabel)
- Inventar/Gold: Kein Datenverlust (sofortige DB-Schreibung)
- **Disconnect/Logout:** Synchroner Force-Flush nach PostgreSQL bevor Session freigegeben wird.
  Verhindert Race Condition bei schnellem Re-Login (alter State ueberschreibt neuen)
- **Re-Login-Sperre:** Login blockiert solange Flush der alten Session laeuft (`session:account:{id}` Lock)

---

## 4. Authentifizierung und Sicherheit

### 4.1 Login-Flow

```
  Client                              Server                         Redis          PostgreSQL
    │                                    │                             │                │
    │─── LoginRequest ──────────────────►│                             │                │
    │    {username, password}            │──── SELECT * FROM accounts ────────────────►│
    │                                    │◄─── account row ──────────────────────────│
    │                                    │                             │                │
    │                                    │  1. is_banned pruefen       │                │
    │                                    │     (ban_until abgelaufen?) │                │
    │                                    │  2. bcrypt.verify(password, │                │
    │                                    │                    hash)    │                │
    │                                    │                             │                │
    │                                    │── GET session:account:{id} ►│                │
    │                                    │   (Multi-Login-Check)       │                │
    │                                    │   Falls existiert:          │                │
    │                                    │   -> Alte Session kicken    │                │
    │                                    │   -> Force-Flush Write-Back │                │
    │                                    │                             │                │
    │                                    │── SET session:{id} ────────►│                │
    │                                    │── SET session:account:{aId} │                │
    │                                    │── UPDATE last_login ────────────────────────►│
    │                                    │                             │                │
    │◄── LoginResponse ─────────────────│                             │                │
    │    {jwt, sessionSecret,           │                             │                │
    │     characterList}                │                             │                │
    │                                    │                             │                │
    │─── CharacterSelect ──────────────►│                             │                │
    │    {characterId}                  │  Validate: character.       │                │
    │                                    │  account_id == session.     │                │
    │                                    │  accountId                  │                │
    │                                    │── Load character ───────────────────────────►│
    │                                    │── Cache in Redis ──────────►│                │
    │                                    │                             │                │
    │◄── EnterWorld ────────────────────│                             │                │
    │    {position, entities, stats}    │                             │                │
    │                                    │                             │                │
    │═══ Ab hier: UDP mit HMAC ════════│                             │                │
```

**Sicherheitspruefungen im Login-Flow:**
1. **Ban-Check:** `is_banned` und `ban_until` pruefen. Gebannte Accounts erhalten `ErrorResponse`
2. **Multi-Login-Schutz:** Reverse-Lookup `session:account:{accountId}` in Redis prueft ob Account
   bereits eingeloggt ist. Falls ja: alte Session wird gekickt (Disconnect + Force-Flush),
   bevor die neue Session erstellt wird. Verhindert Duplikations-Exploits
3. **Character-Ownership:** `CharacterSelect` validiert `WHERE id = ? AND account_id = ?`.
   Fremde Charaktere koennen nicht ausgewaehlt werden
4. **Session-Secret:** 32-Byte Secret wird bei Login generiert und ueber TLS an den Client
   gesendet. Wird fuer HMAC-Signierung der UDP-Pakete verwendet

### 4.2 Token-System

**JWT** (nur fuer initialen Login/Character-Select ueber TCP):
```json
{
  "sub": 12345,
  "iat": 1700000000,
  "exp": 1700086400,
  "sid": "a1b2c3d4"
}
```

**Session-Token + HMAC** (fuer UDP-Pakete nach Login):
- Token: 8 Byte, kryptographisch zufaellig (`SecureRandom`, nicht `Random`)
- Secret: 32 Byte, kryptographisch zufaellig (fuer HMAC-SHA256)
- Token in Redis gespeichert mit Mapping zu Account/Character
- Secret wird nur ueber TLS an Client gesendet, nie ueber UDP
- Validierung pro UDP-Paket: Token-Lookup in Redis + HMAC verifizieren
- Ohne Secret kann ein Angreifer abgefangene Tokens nicht missbrauchen

### 4.3 Passwort-Sicherheit

- **bcrypt** mit Cost-Factor 12
- Minimum 8 Zeichen, Maximum 72 (bcrypt-Limit)
- Keine Klartext-Speicherung
- Kein MD5, SHA-1 oder SHA-256 (zu schnell fuer Passwort-Hashing)

### 4.4 Anti-Cheat: Server-Authoritative Validierungen

Der Server ist die einzige Wahrheit. Der Client ist ein "dummer" Renderer.

| Exploit | Wie es funktioniert | Server-Validierung |
|---------|-------------------|-------------------|
| **Speed-Hack** | Client meldet zu schnelle Bewegung | `distance <= maxSpeed * tickDelta * 1.2` (20% Latenz-Toleranz) |
| **Teleport-Hack** | Client meldet Position weit entfernt | Position-Delta > Schwellwert -> Reject + PositionCorrection |
| **Damage-Hack** | Client sendet ueberhoehtenn Schaden | Server berechnet **allen** Schaden. Client sendet nur `UseSkill(id)` |
| **Cooldown-Hack** | Client ignoriert Skill-Cooldowns | Server trackt Cooldowns. Requests vor Ablauf werden ignoriert |
| **Duplikations-Glitch** | Race Condition bei Item-Operationen | Atomare DB-Transaktionen, Server validiert Slot-Belegung |
| **Packet-Replay** | Alte Pakete erneut senden | Sequence-Nummer. Doppelte/alte Sequenzen verworfen |
| **Invalid Items** | Falsches Equipment anlegen | Server prueft Level-Req, Klassen-Req, Item existiert im Inventar |
| **Gold-Hack** | Client manipuliert Gold-Wert | Alle Gold-Transaktionen server-seitig. Client hat nur Lese-Zugriff |
| **NPC-Shop-Exploit** | Buy/Sell von ueberall auf der Map | Server prueft Distanz zum NPC (`distance <= 10 Einheiten`). Zu weit weg -> Reject |
| **Loot-Stealing** | Fremdes Loot aufheben | Loot gehoert dem Killer fuer 30 Sekunden (Loot-Ownership). Danach frei fuer alle |
| **Stat-Exploit** | Mehr Stat-Punkte verteilen als vorhanden | Server validiert `stat_points >= requested`. Summe aller Stats muss Formel entsprechen |
| **Target-Exploit** | Unsichtbare/entfernte Entities targeten | `SelectTarget` validiert: Entity existiert, selbe Zone/Channel, in Sichtweite (SpatialGrid) |
| **Wall-Clip** | Durch Terrain/Waende bewegen | Server prueft Kollision gegen Terrain-Heightmap und statische Collider |

### 4.5 Rate Limiting und Flood Protection

**Paket-Groessen-Limits (erste Verteidigung, vor jeder Verarbeitung):**

| Transport | Max. Paketgroesse | Aktion bei Ueberschreitung |
|-----------|------------------|---------------------------|
| TCP | 64 KB pro Nachricht | Verbindung trennen |
| UDP | 512 Byte pro Paket | Paket verwerfen (silent drop) |

**Connection-Limits (Netty-Level, vor Auth):**

| Limit | Wert | Aktion |
|-------|------|--------|
| Max. TCP-Verbindungen pro IP | 5 | Weitere Verbindungen abweisen |
| Max. TCP-Verbindungen gesamt | 10.000 | Weitere Verbindungen abweisen |
| TCP-Idle-Timeout | 30 Sekunden (vor Login), 5 Min (nach Login) | Verbindung trennen |
| UDP-Pakete pro IP pro Sekunde | 100 | Ueberschuessige Pakete verwerfen |

UDP-Flood-Protection wird **vor** dem Redis-Lookup ausgefuehrt (In-Memory IP-Counter),
damit ein Angreifer Redis nicht ueberlasten kann.

**Application-Level Rate Limiting (Redis-basiert, nach Auth):**

| Aktion | Limit | Zeitfenster | Key |
|--------|-------|-------------|-----|
| Login-Versuche | 5 | pro Minute | `rate_limit:{ip}:login` |
| Registrierung | 3 | pro Stunde | `rate_limit:{ip}:register` |
| Chat-Nachrichten | 10 | pro 10 Sekunden | `rate_limit:{charId}:chat` |
| Item-Operationen | 30 | pro Minute | `rate_limit:{charId}:item` |
| Skill-Usage | Durch Cooldowns begrenzt (server-seitig) | - | - |
| Ungueltige Pakete pro Session | 50 | pro Minute | In-Memory Counter |

Bei >50 ungueltigen Paketen/Minute wird die Session als verdaechtig markiert und getrennt.

### 4.6 Verschluesselung

| Verbindung | Verschluesselung | Grund |
|-----------|-----------------|-------|
| TCP | **TLS 1.3** | Login-Daten, Chat, Inventar - alles sensibel |
| UDP (MVP) | **Keine** (Session-Token fuer Auth) | Performance-Prioritaet, keine sensiblen Daten |
| UDP (Post-MVP) | **DTLS** | Wenn Verschluesselung benoetigt wird |

### 4.7 Session-Lifecycle und Disconnect-Handling

**Session-Invalidierung:**
```
Disconnect erkannt (TCP close / Heartbeat-Timeout 15s)
  │
  ├── 1. Character-State Force-Flush nach PostgreSQL (synchron)
  ├── 2. Character aus Zone/Channel entfernen
  ├── 3. EntityDespawn an alle Spieler in Sichtweite broadcasten
  ├── 4. Redis: session:{id} loeschen
  ├── 5. Redis: session:account:{accountId} loeschen
  └── 6. Redis: Character-Cache invalidieren
```

- Heartbeat-Timeout: 15 Sekunden ohne Heartbeat -> Disconnect
- Force-Flush verhindert Datenverlust UND Race Condition bei schnellem Re-Login
- Re-Login erst moeglich nachdem Flush abgeschlossen ist (Session-Lock)

### 4.8 Paket-Deserialisierung und Fehlerbehandlung

- **Alle** Protobuf-Deserialisierungen in try-catch wrappen
- Malformed Packets: Silent Drop + Counter erhoehen
- Unbekannte Opcodes: Silent Drop + Counter erhoehen
- Bei >50 fehlerhaften Paketen/Minute: Session trennen (siehe 4.5)
- Kein Stack-Trace an Client leaken (nur generischer ErrorCode)

### 4.9 Input-Validierung

- **Chat:** Max. 200 Zeichen, HTML/Script-Tags entfernen, Null-Bytes entfernen
- **Character-Namen:** 3-16 Zeichen, nur `[a-zA-Z0-9-]`, Unique-Check, Blacklist
- **Numerische Werte:** Range-Checks (keine negativen Slot-IDs, gueltiger Skill-ID-Bereich)
- **String-Laengen:** Alle Strings mit Maximum begrenzt, ueberlaenge abschneiden

### 4.10 Post-MVP Sicherheit

- **2FA:** TOTP (Google Authenticator / Authy kompatibel)
- **IP-Logging:** `account_ip_log` Tabelle, Login von neuem Land = E-Mail-Warnung
- **Auto-Ban:** Wiederholte Cheat-Versuche (z.B. 10x Speed-Hack in 5 Min) = temporaerer Ban
- **GM-Tools:** Spieler beobachten, Items pruefen, Teleportieren, Bannen

---

## 5. MVP vs Post-MVP Abgrenzung

| Bereich | MVP (Phase 1-2) | Post-MVP (Phase 3+) |
|---------|-----------------|---------------------|
| **Netzwerk** | TCP+UDP, Protobuf, 20Hz Tick, Client-Side Prediction | Delta Compression, DTLS, zlib |
| **Server** | Modularer Monolith, 2 Zonen, Channel-System, SpatialGrid | Dungeon-Instanzen, Horizontal Scaling, Zone-Server-Splitting |
| **Datenbank** | Core-Tabellen, Redis-Cache, Write-Back | Gilden-Tabellen, Chat-Logs, Quest-Progress, IP-Logs |
| **Sicherheit** | TLS, bcrypt, JWT+HMAC-Session, Ban-Check, Multi-Login-Schutz, Character-Ownership, Anti-Cheat (Speed/Teleport/Damage/Cooldown/Dupe/Replay/Items/Gold/NPC-Proximity/Loot-Ownership/Stats/Target/Wall-Clip), Flood Protection, Rate Limiting, Paketgroessen-Limits, Session-Lifecycle, Protobuf-Fehlerbehandlung, DB CHECK-Constraints | 2FA, IP-Anomalie-Erkennung, Auto-Bans, GM-Tools, DTLS |
