# FlyAgain - Implementierungsphasen

> Detaillierter Umsetzungsplan basierend auf GDD, ROADMAP und ARCHITECTURE.
> Jeder Schritt hat klare Abhaengigkeiten, Ergebnisse und Akzeptanzkriterien.

---

## Phase 1: Minimal MVP

**Ziel:** Ein spielbarer Multiplayer-Prototyp. Ein Krieger kann sich in einer Zone bewegen,
Monster toeten, XP sammeln, leveln und andere Spieler sehen.

---

### 1.1 Projektsetup und Build-Pipeline

**Server (Kotlin):**
- [ ] Gradle-Projekt erstellen (`server/`)
- [ ] Dependencies: Netty, Protobuf, PostgreSQL-Driver (HikariCP), Jedis/Lettuce (Redis), jBCrypt, java-jwt
- [ ] Projektstruktur nach ARCHITECTURE.md Modul-Layout anlegen
- [ ] Docker-Compose fuer PostgreSQL + Redis (lokale Entwicklung)
- [ ] Flyway oder Liquibase fuer DB-Migrationen einrichten
- [ ] Erste Migration: `accounts` Tabelle erstellen

**Client (Unity):**
- [ ] Unity-Projekt erstellen (`client/`) mit URP Template
- [ ] Ordnerstruktur: `Scripts/Network/`, `Scripts/UI/`, `Scripts/Game/`, `Scripts/Entity/`
- [ ] NuGet/Unity Protobuf-Package einbinden (Google.Protobuf)
- [ ] Build-Targets: Windows, macOS, Linux konfigurieren

**Shared:**
- [ ] `.proto`-Datei erstellen (`shared/proto/flyagain.proto`) mit Auth-Opcodes
- [ ] Protobuf-Codegen fuer Kotlin und C# einrichten
- [ ] Git-Repository initialisieren, `.gitignore` fuer Unity + Kotlin + IDE-Dateien

**Akzeptanzkriterien:**
- `./gradlew build` kompiliert fehlerfrei
- Unity-Projekt oeffnet sich, leere Scene mit URP-Rendering
- `docker-compose up` startet PostgreSQL + Redis
- Protobuf-Codegen generiert Kotlin- und C#-Klassen

---

### 1.2 Netzwerk-Grundgeruest

> Abhaengigkeit: 1.1

**Server:**
- [ ] Netty TCP-Server (TLS-faehig, Port 7777)
- [ ] Netty UDP-Server (Port 7778)
- [ ] `PacketRouter`: Opcode -> Handler-Mapping
- [ ] `SessionManager`: Session-Erstellung, Lookup, Invalidierung
- [ ] Paketgroessen-Limits (TCP 64KB, UDP 512B)
- [ ] Connection-Limits pro IP (max 5 TCP)
- [ ] UDP Flood Protection (In-Memory IP-Counter, max 100/s)
- [ ] Heartbeat-System (Opcode `0x0601`, 15s Timeout)
- [ ] Protobuf De-/Serialisierung mit try-catch + Malformed-Packet-Counter

**Client:**
- [ ] `NetworkManager`: TCP + UDP Verbindung zum Server
- [ ] `PacketSender`: Serialisierung + Senden (TCP/UDP je nach Opcode)
- [ ] `PacketReceiver`: Empfangen + Deserialisierung + Event-Dispatch
- [ ] Heartbeat senden (alle 5 Sekunden)
- [ ] Reconnect-Logik (3 Versuche, dann Disconnect-Screen)

**Akzeptanzkriterien:**
- Client verbindet sich per TCP zum Server
- Heartbeat laeuft stabil ueber 10 Minuten
- Server trennt Verbindung nach 15s ohne Heartbeat
- Uebergrosse Pakete werden korrekt abgewiesen

---

### 1.3 Datenbank und Auth-System

> Abhaengigkeit: 1.2

**Server:**
- [ ] DB-Migrationen: Alle Tabellen aus ARCHITECTURE.md Abschnitt 3.2 erstellen
  - `accounts`, `characters`, `item_definitions`, `inventory`, `equipment`
  - `skill_definitions`, `character_skills`
  - `monster_definitions`, `monster_spawns`, `loot_table`
  - Alle CHECK-Constraints inkludiert
- [ ] Redis-Anbindung: Session-CRUD, Rate-Limiting-Counter
- [ ] `RegisterHandler` (Opcode `0x0006`):
  - Input-Validierung (Username 3-16 Zeichen, E-Mail-Format, Passwort min 8)
  - bcrypt-Hash (Cost 12)
  - Duplikat-Check (Username + E-Mail)
  - Rate-Limit: 3 pro Stunde pro IP
- [ ] `LoginHandler` (Opcode `0x0001`):
  - Ban-Check (`is_banned`, `ban_until`)
  - bcrypt-Verify
  - Multi-Login-Check (Reverse-Lookup `session:account:{id}`)
  - JWT generieren + Session-Token + HMAC-Secret generieren
  - Session in Redis speichern
  - Rate-Limit: 5 pro Minute pro IP
- [ ] `CharacterCreateHandler` (Opcode `0x0005`):
  - Name-Validierung (3-16 Zeichen, `[a-zA-Z0-9-]`, Blacklist)
  - Max 3 Charaktere pro Account
  - Basis-Stats je nach Klasse setzen (MVP: nur Krieger)
- [ ] `CharacterSelectHandler` (Opcode `0x0003`):
  - Ownership-Validierung (`account_id == session.accountId`)
  - Character laden, in Redis cachen
  - Zur Zone hinzufuegen (naechster Schritt)
- [ ] Session-Lifecycle: Disconnect -> Force-Flush -> Redis-Cleanup

**Client:**
- [ ] Login-Screen: Username + Passwort Eingabefelder, Login-Button
- [ ] Registrierungs-Screen: Username + E-Mail + Passwort + Bestaetigung
- [ ] Character-Select-Screen: Liste der Charaktere, Erstellen-Button
- [ ] Character-Create-Screen: Name eingeben (Klasse fix Krieger im MVP)
- [ ] Error-Handling: Fehlermeldungen vom Server anzeigen (ErrorResponse `0x0603`)

**Akzeptanzkriterien:**
- Account erstellen, einloggen, Character erstellen, Character auswaehlen
- Doppelter Username/E-Mail wird abgelehnt
- Falsches Passwort wird abgelehnt
- Rate-Limiting greift nach 5 fehlgeschlagenen Login-Versuchen
- Multi-Login kickt die alte Session

---

### 1.4 Welt, Bewegung und Zone-System

> Abhaengigkeit: 1.3

**Server:**
- [ ] `ZoneManager`: Verwaltet alle Zonen, laedt Zonen-Konfiguration
- [ ] `ZoneChannel`: Spieler-Liste, Entity-Liste, SpatialGrid
- [ ] `SpatialGrid`: 50x50 Grid-Zellen, Entity-Tracking, Nachbar-Abfragen
- [ ] Zone-Konfiguration: Aerheim (Stadt) + Gruene Ebene (Grinding-Zone)
- [ ] `EnterWorldHandler` (Opcode `0x0004`):
  - Character in Zone/Channel einfuegen
  - EntitySpawn (`0x0301`) an alle Spieler in Sichtweite senden
  - ZoneData (`0x0701`) an den neuen Spieler senden
- [ ] `MovementHandler` (UDP Opcode `0x0101`):
  - HMAC verifizieren
  - Sequence-Check (Duplikate/alte Pakete verwerfen)
  - Position validieren (Speed-Check, Terrain-Collision)
  - Bei Abweichung: PositionCorrection (`0x0103`)
  - Bei OK: EntityPositionUpdate (`0x0102`) an SpatialGrid-Nachbarn broadcasten
- [ ] `EntityManager`: Spawn/Despawn Tracking, Entity-ID-Vergabe
- [ ] Game-Loop (20 Hz): Bewegungs-Updates verarbeiten, Broadcasts senden
- [ ] Flugmechanik: `isFlying`-Flag, andere Geschwindigkeit, Y-Achsen-Bewegung
- [ ] Zone-Wechsel: Character aus Zone A entfernen, in Zone B einfuegen, Ladescreen-Trigger

**Client:**
- [ ] Third-Person-Kamera: Freie Rotation, Zoom
- [ ] Spieler-Bewegung: WASD + Maus, Click-to-Move
- [ ] Client-Side Prediction: Lokale Bewegung sofort anwenden
- [ ] Server-Reconciliation: PositionCorrection verarbeiten, Snap-Back
- [ ] Entity-Interpolation: Andere Spieler smooth bewegen (100ms Buffer)
- [ ] Flugmechanik: Leertaste zum Abheben/Landen, Steigen/Sinken
- [ ] Terrain: Einfache Heightmap (Gruene Ebene = flaches Grasland)
- [ ] Aerheim: Einfache Stadt-Geometrie (Platzhalter-Assets)
- [ ] Remote-Spieler: EntitySpawn empfangen -> Charakter-Modell instanziieren
- [ ] Zone-Wechsel: Ladescreen bei ZoneData-Empfang

**Akzeptanzkriterien:**
- Spieler spawnt in Aerheim nach CharacterSelect
- WASD-Bewegung funktioniert mit Client-Side Prediction
- Andere Spieler sind sichtbar und bewegen sich smooth
- Flug funktioniert (Abheben, frei bewegen, Landen)
- Speed-Hack wird vom Server erkannt und korrigiert
- Zone-Wechsel von Aerheim zur Gruenen Ebene funktioniert

---

### 1.5 Kampfsystem und Monster-AI

> Abhaengigkeit: 1.4

**Server:**
- [ ] `CombatEngine`:
  - Schadensformel: `damage = attackerAtk - defenderDef + random(-2, +2)`
  - Kritischer Treffer: `if (random < critChance) damage *= 1.5`
  - Auto-Attack Timer (Waffen-Speed, z.B. 2000ms)
- [ ] `SkillSystem`:
  - Skill-Definitionen in DB laden (Seed-Daten fuer 3-4 Krieger-Skills)
  - `UseSkillHandler` (Opcode `0x0202`):
    - Pruefe: Skill existiert, Spieler hat Skill, genug MP, Cooldown abgelaufen
    - Pruefe: Target in Range, Target existiert, selbe Zone
    - Schaden berechnen, HP abziehen
    - DamageEvent (`0x0203`) an alle in Sichtweite broadcasten
- [ ] `AutoAttackHandler` (Opcode `0x0206`):
  - Toggle Auto-Attack an/aus
  - Server fuehrt Auto-Attacks im Game-Loop aus (Timer-basiert)
- [ ] `MonsterEntity`:
  - Monster-Definitionen aus DB laden
  - Spawning: Laut `monster_spawns` Tabelle instanziieren
  - Respawn-Timer nach Tod
- [ ] `MonsterAI` (State Machine):
  - IDLE: Warte am Spawn
  - AGGRO: Spieler in `aggro_range` -> auf Spieler zubewegen
  - ATTACK: In `attack_range` -> Auto-Attack ausfuehren
  - RETURN: Ziel tot/zu weit -> zurueck zum Spawn, HP regenerieren
- [ ] `DeathHandler`:
  - Monster stirbt: XpGain (`0x0205`) an Killer, Loot berechnen
  - Spieler stirbt: Respawn in Stadt (Aerheim), kein Item-Verlust im MVP
- [ ] `LootSystem`:
  - Drop-Chance aus `loot_table` wuerfeln
  - Loot-Ownership: 30 Sekunden fuer Killer, danach frei
  - Loot als Entity spawnen (EntitySpawn an Spieler in Sichtweite)
- [ ] XP + Level-Up:
  - XP-Tabelle: Level 1-15 (z.B. Level 2 = 100 XP, Level 15 = 50.000 XP)
  - Level-Up: Stats erhoehen, HP/MP voll auffuellen, stat_points vergeben

**Client:**
- [ ] Tab-Targeting: Monster anklicken -> SelectTarget (`0x0201`)
- [ ] Target-Frame UI: Name, HP-Balken des Targets
- [ ] Auto-Attack: F-Taste oder Doppelklick auf Target
- [ ] Skill-Bar: 4 Slots (Tasten 1-4), Cooldown-Anzeige
- [ ] Damage-Numbers: Schwebe-Zahlen bei Treffern (DamageEvent empfangen)
- [ ] Tod-Screen: "Du bist gestorben" + Respawn-Button
- [ ] Monster-Modelle: Platzhalter-Assets (z.B. Low-Poly Slimes, Pilze)
- [ ] HP/MP-Balken: Eigene HP/MP in der UI
- [ ] XP-Balken: Fortschrittsanzeige, Level-Up-Effekt
- [ ] Loot-Anzeige: Drop auf dem Boden, Klick zum Aufheben

**Seed-Daten (Server DB):**
```
Krieger-Skills:
  - Schlag (Lv1, 0 MP, 1.5s CD, 120% ATK Schaden, Range 2)
  - Schildstoss (Lv3, 10 MP, 5s CD, 150% ATK + Slow, Range 2)
  - Wirbelschlag (Lv5, 20 MP, 8s CD, AoE 200% ATK, Range 3)
  - Kriegsschrei (Lv8, 15 MP, 30s CD, +20% ATK Buff 30s, Self)

Monster (Gruene Ebene):
  - Schleimling (Lv1-3, passiv)
  - Waldpilz (Lv3-5, passiv)
  - Wildeber (Lv5-8, aggressiv)
  - Waldwolf (Lv8-12, aggressiv)
  - Steingolem (Lv12-15, aggressiv, Mini-Boss)
```

**Akzeptanzkriterien:**
- Monster stehen in der Zone und haben AI (Aggro, Angriff, Return)
- Tab-Targeting und Auto-Attack funktionieren
- 4 Skills mit Cooldowns und MP-Kosten
- Monster sterben, droppen Loot und XP
- Level-Up von 1 auf 15 moeglich durch Grinding
- Spieler-Tod -> Respawn in Aerheim

---

### 1.6 Inventar, Equipment und NPC-Shops

> Abhaengigkeit: 1.5

**Server:**
- [ ] `InventoryManager`:
  - MoveItem (`0x0401`): Slot-Validierung, atomare DB-Transaktion
  - Loot aufheben: Naehe-Check, Ownership-Check, freien Slot finden
  - Stack-Logik fuer stackable Items
- [ ] `EquipmentManager`:
  - EquipItem (`0x0403`): Level-Req, Klassen-Req, Typ pruefen
  - UnequipItem (`0x0404`): Freien Inventar-Slot pruefen
  - Stats neu berechnen bei Equip/Unequip
- [ ] `NpcShopHandler`:
  - NpcBuy (`0x0405`): NPC-Proximity-Check (10 Einheiten), Gold-Check
  - NpcSell (`0x0406`): Item existiert, Sell-Preis berechnen
  - GoldUpdate (`0x0407`) senden
- [ ] NPC-Definitionen: Haendler in Aerheim mit Basis-Waffen und -Ruestungen

**Client:**
- [ ] Inventar-Fenster: 10x10 Grid (100 Slots), Drag & Drop
- [ ] Equipment-Fenster: Charakter-Silhouette mit 7 Slots
- [ ] Item-Tooltip: Name, Typ, Stats, Level-Req, Rarity-Farbe
- [ ] NPC-Interaktion: NPC anklicken -> Shop-Fenster oeffnen
- [ ] Shop-Fenster: Kauf/Verkauf-Tabs, Gold-Anzeige
- [ ] Gold-Anzeige in der UI (permanent sichtbar)

**Seed-Daten:**
```
Items (Krieger, Gruene Ebene):
  - Holzschwert (Lv1, ATK+5, 10 Gold)
  - Eisenschwert (Lv5, ATK+12, 100 Gold)
  - Stahlschwert (Lv10, ATK+22, 500 Gold)
  - Lederruestung (Lv1, DEF+3, 15 Gold)
  - Kettenruestung (Lv5, DEF+8, 120 Gold)
  - Plattenruestung (Lv10, DEF+15, 600 Gold)
  - Heiltrank (Verbrauchbar, +50 HP, 5 Gold, Stackable x20)
  - Manatrank (Verbrauchbar, +30 MP, 8 Gold, Stackable x20)
```

**Akzeptanzkriterien:**
- Inventar oeffnen, Items verschieben (Drag & Drop)
- Waffe/Ruestung anziehen -> Stats aendern sich
- NPC-Shop: Kaufen (Gold abgezogen), Verkaufen (Gold erhalten)
- Loot vom Boden aufheben -> erscheint im Inventar
- NPC-Shop nur in Naehe funktionierend (Proximity-Check)

---

### 1.7 Chat-System

> Abhaengigkeit: 1.2

**Server:**
- [ ] `ChatManager`:
  - ChatMessage (`0x0501`) empfangen
  - Input-Validierung: Max 200 Zeichen, Tags strippen, Null-Bytes entfernen
  - Rate-Limit: 10 Nachrichten pro 10 Sekunden
  - Channels: `say` (SpatialGrid-Nachbarn), `shout` (ganze Zone)
  - ChatBroadcast (`0x0502`) an relevante Spieler senden

**Client:**
- [ ] Chat-Fenster: Scrollbare Nachrichtenliste, Eingabefeld
- [ ] Chat-Channels: Tab-Buttons fuer Say/Shout
- [ ] Eingabe mit Enter, `/shout` Prefix fuer Zone-Chat
- [ ] Spielername farbig anzeigen

**Akzeptanzkriterien:**
- Say-Chat: Nur Spieler in Naehe sehen die Nachricht
- Shout-Chat: Alle Spieler in der Zone sehen die Nachricht
- Rate-Limiting greift (keine Spam-Flut)
- Sonderzeichen/HTML-Tags werden korrekt gehandhabt

---

### 1.8 Basis-UI und Polish

> Abhaengigkeit: 1.4 - 1.7

**Client:**
- [ ] HUD: HP/MP-Balken, XP-Balken, Level-Anzeige, Gold
- [ ] Minimap: Einfache Draufsicht mit Spieler-Punkt und NPC-Markierungen
- [ ] ESC-Menu: Optionen (Lautstaerke, Grafik), Logout, Beenden
- [ ] Tastenbelegung: Anzeige der Default-Keys
- [ ] Performance: FPS-Counter, grundlegende Optimierung (Object Pooling fuer Entities)
- [ ] Audio: Hintergrundmusik (Platzhalter), Treffer-Sound, Level-Up-Sound

**Akzeptanzkriterien:**
- Alle UI-Elemente sichtbar und funktional
- Stabile 60 FPS mit 50 Entities in Sicht
- Logout bringt zurueck zum Character-Select

---

### Phase 1 - Zusammenfassung

```
Schritt  | Abhaengigkeit | Server                              | Client
---------|---------------|-------------------------------------|----------------------------------
1.1      | -             | Gradle, Docker, DB-Migrationen      | Unity-Projekt, Protobuf
1.2      | 1.1           | Netty TCP/UDP, PacketRouter          | NetworkManager, Heartbeat
1.3      | 1.2           | Auth, DB, Redis, Sessions            | Login/Register/CharSelect UI
1.4      | 1.3           | Zonen, SpatialGrid, Game-Loop        | Bewegung, Kamera, Flug, Entities
1.5      | 1.4           | Combat, Skills, Monster-AI, Loot     | Targeting, Skillbar, Damage-Zahlen
1.6      | 1.5           | Inventar, Equipment, NPC-Shops       | Inventar-UI, Equipment-UI, Shop
1.7      | 1.2           | ChatManager, Channels                | Chat-Fenster
1.8      | 1.4-1.7       | -                                    | HUD, Minimap, Audio, Polish
```

**Parallel moeglich:** 1.7 (Chat) kann parallel zu 1.4-1.6 entwickelt werden.

---

## Phase 2: Core Gameplay

**Ziel:** Vollstaendiges Klassen-System, Equipment-Enhancement, erster Dungeon,
Quest-System und zweite Zone. Das Spiel fuehlt sich wie ein richtiges MMORPG an.

> Abhaengigkeit: Phase 1 abgeschlossen

---

### 2.1 Weitere Klassen

- [ ] Klassen-Daten in DB: Magier, Assassine, Kleriker
- [ ] Basis-Stats pro Klasse (HP/MP-Skalierung, Primaer-Stat)
- [ ] Je 4 Skills pro neue Klasse:
  - **Magier:** Feuerball (Ranged), Eissplitter (Slow), Blitzschlag (AoE), Magieschild (Buff)
  - **Assassine:** Dolchstoss, Schattenschritt (Dash), Giftklinge (DoT), Hinterhalt (Crit-Buff)
  - **Kleriker:** Heilung (Single), Segen (Party-Buff), Heiliger Schlag, Schutzaura (AoE-Buff)
- [ ] Character-Create: Klassenauswahl-UI mit Beschreibungen
- [ ] Klassen-spezifische Waffen und Ruestungen (Item-Definitionen erweitern)
- [ ] Klassen-spezifische Animationen (Platzhalter oder Assets)

**Akzeptanzkriterien:**
- Alle 4 Klassen spielbar mit einzigartigen Skills
- Klassen-Restriktionen bei Equipment greifen
- Jede Klasse fuehlt sich unterschiedlich an

---

### 2.2 Equipment-Enhancement

- [ ] Enhancement-NPC in Aerheim
- [ ] Enhancement-Mechanik:
  - +1 bis +3: 100% Erfolg
  - +4 bis +6: 70% Erfolg
  - +7 bis +8: 40% Erfolg
  - +9: 20% Erfolg
  - +10: 10% Erfolg
  - Bei Fehlschlag: Enhancement zurueck auf +0
- [ ] Kosten: Gold + Material-Items (neue Drops von Monstern)
- [ ] Enhancement-UI: Item auswaehlen, Chance anzeigen, Bestaetigen
- [ ] Server-Validierung: Chance server-seitig berechnen (kein Client-Trust)
- [ ] Enhancement-Effekte: Visuell auf Waffe (Glow-Effekt ab +7)

**Akzeptanzkriterien:**
- Enhancement-System vollstaendig funktional
- Chancen stimmen mit Definition ueberein (Server-seitig verifiziert)
- Transparente Darstellung der Chancen fuer den Spieler

---

### 2.3 Dungeon-System

- [ ] `DungeonInstance`-Klasse: Separater Game-State, eigener Entity-Pool
- [ ] Dungeon-Eingang in der Gruenen Ebene (NPC oder Portal)
- [ ] Party-System (MVP: max 5 Spieler):
  - Party erstellen, einladen, verlassen
  - Party-Chat
  - Geteiltes XP bei Party-Kills
- [ ] Dungeon: "Hoehle des Steingolem"
  - 3 Raeume mit Monster-Gruppen
  - Endboss: Steingolem-Koenig (erweiterte AI: Phasen, AoE-Angriff)
  - Boss-Loot: Seltene/Epische Items
- [ ] Timer: 30 Minuten Dungeon-Timeout
- [ ] Instanz-Aufraeumung nach Completion/Timeout

**Akzeptanzkriterien:**
- Party kann Dungeon betreten (eigene Instanz)
- Boss hat Mechaniken (nicht nur DPS-Check)
- Dungeon ist wiederholbar, Loot-Tabelle funktioniert
- Instanz wird nach Abschluss aufgeraeumt

---

### 2.4 Quest-System

- [ ] `QuestManager`: Quest-Annahme, Fortschritts-Tracking, Abgabe
- [ ] Quest-Typen:
  - Kill-Quest: "Toete 10 Wildeber" (Daily)
  - Sammel-Quest: "Sammle 5 Wolfsfelpe" (Daily)
  - Boss-Quest: "Besiege den Steingolem-Koenig" (Weekly)
- [ ] Quest-NPCs in Aerheim
- [ ] Belohnungen: XP + Gold + gelegentlich Items
- [ ] Daily-Reset (Server-Zeit 05:00 UTC), Weekly-Reset (Montag 05:00 UTC)
- [ ] Quest-DB-Tabellen: `quest_definitions`, `character_quest_progress`
- [ ] Quest-UI: Quest-Log, Quest-Tracker am Bildschirmrand

**Akzeptanzkriterien:**
- Quests annehmen, Fortschritt tracken, abgeben
- Daily/Weekly-Reset funktioniert korrekt
- Belohnungen werden korrekt vergeben

---

### 2.5 Zweite Zone: Dunkler Wald

- [ ] Zone-Konfiguration: Dunkler Wald (Level 15-30)
- [ ] 5-8 neue Monstertypen (Level 15-30)
- [ ] Neue Loot-Tabellen fuer hoehere Level
- [ ] Uebergang von Gruener Ebene zum Dunklen Wald
- [ ] Terrain: Dichterer Wald, dunklere Atmosphaere
- [ ] Neue Items: Level 15-30 Waffen und Ruestungen

**Akzeptanzkriterien:**
- Zone erreichbar und bespielbar
- Monster-Schwierigkeit passt zum Level-Bereich
- Progression fuehlt sich natuerlich an (Gruene Ebene -> Dunkler Wald)

---

## Phase 3: PvP & Social

**Ziel:** Wettbewerbselemente und soziale Systeme. Spieler haben Gruende,
miteinander zu interagieren und gegeneinander anzutreten.

> Abhaengigkeit: Phase 2 abgeschlossen

---

### 3.1 Gildensystem

- [ ] DB-Tabellen: `guilds`, `guild_members` (Rang, Beitritts-Datum)
- [ ] Gilden-Erstellung (Name, Max-Mitglieder)
- [ ] Raenge: Meister, Offizier, Mitglied
- [ ] Gilden-Chat (neuer Chat-Channel)
- [ ] Gilden-UI: Mitgliederliste, Einladungen, Rang-Verwaltung

### 3.2 Handelssystem

- [ ] Spieler-zu-Spieler Handel: Handelsanfrage, beiderseitige Bestaetigung
- [ ] Trade-Window: Beide Seiten Items + Gold anzeigen
- [ ] Server-Validierung: Atomare Transaktion (beide Inventare gleichzeitig aendern)
- [ ] Anti-Scam: Bestaetigung nach jeder Aenderung zuruecksetzen

### 3.3 PvP-Arena

- [ ] Arena-NPC in Aerheim: 1v1 Matchmaking (Level-basiert)
- [ ] Arena-Instanz: Separater Game-State, keine Item-Drops
- [ ] Countdown -> Kampf -> Sieger/Verlierer
- [ ] Belohnungen: Arena-Punkte (kosmetischer Shop spaeter)

### 3.4 Ranking-System

- [ ] Ranglisten: Level, Arena-Siege, PvE-Boss-Kills
- [ ] DB-Tabelle: `rankings` (Saison, Typ, Spieler, Punkte)
- [ ] Saison-Reset (z.B. alle 3 Monate)
- [ ] Ranking-UI: Top 100, eigene Position

### 3.5 Open-World PvP (Optional Zone)

- [ ] PvP-Zone markieren (z.B. Teil des Dunklen Waldes)
- [ ] PvP-Flag-System: Automatisch in PvP-Zone, Warnung bei Eintritt
- [ ] Belohnungen/Risiken: Bonus-XP in PvP-Zone, aber PvP-Tod = XP-Verlust
- [ ] Anti-Grief: Level-Differenz-Schutz (kein PvP bei >10 Level Unterschied)

---

## Phase 4: Expansion

**Ziel:** Tiefere Progression, mehr Content, laengere Spielerbindung.

> Abhaengigkeit: Phase 3 abgeschlossen

---

### 4.1 2nd Job Spezialisierungen

- [ ] Pro Klasse 2 Spezialisierungen (ab Level 30):
  - Krieger -> Ritter (Tank) / Berserker (DPS)
  - Magier -> Elementarist (AoE) / Erzmagier (Single-Target)
  - Assassine -> Schattenklinge (Burst) / Giftmischer (DoT)
  - Kleriker -> Priester (Heilung) / Paladin (Hybrid Tank/Heal)
- [ ] Neue Skill-Trees pro Spezialisierung (4-6 neue Skills)
- [ ] Klassen-Quest fuer Spezialisierung

### 4.2 Weitere Zonen

- [ ] Zone 3: Wueste (Level 30-45)
- [ ] Zone 4: Vulkangebiet (Level 45-60)
- [ ] Neue Monster, Items, Loot-Tabellen pro Zone

### 4.3 Weitere Dungeons

- [ ] Dungeon 2: Waldtempel (Level 20-25, 5-Spieler)
- [ ] Dungeon 3: Wuestenruine (Level 35-40, 5-Spieler)
- [ ] Raid: Vulkanfestung (Level 55-60, 10-Spieler)

### 4.4 Crafting-System

- [ ] Sammeln: Erze, Kraeuter, Haeute (aus der Welt oder von Monstern)
- [ ] Berufe: Schmied, Alchemist, Schneider
- [ ] Crafting-UI: Rezepte, Materialien, Herstellung
- [ ] Hergestellte Items: Konkurrenzfaehig mit Drops (kein P2W)

### 4.5 Flug-Erweiterungen

- [ ] Verschiedene Flug-Mounts (Besen, Boards, Fluegel) mit Speed-Unterschieden
- [ ] Flug-Rennen (Minigame, Checkpoint-basiert)
- [ ] Flug-Mount-Quests zum Freischalten

### 4.6 Events

- [ ] Event-System: Zeitgesteuerte World-Events (z.B. Boss-Invasion)
- [ ] Saisonale Events: Weihnachten, Halloween (kosmetische Belohnungen)
- [ ] Event-Shop mit Event-Waehrung

---

## Phase 5: Polish & Launch

**Ziel:** Spielreif. Stabil, balanced, cheat-sicher, getestet.

> Abhaengigkeit: Phase 4 abgeschlossen

---

### 5.1 Balancing

- [ ] Klassen-Balance: DPS-Vergleiche, Heiler-Effizienz, Tank-Survivability
- [ ] XP-Kurve glaetten (kein Dead-Zone-Feeling)
- [ ] Wirtschafts-Balance: Gold-Sinks vs Gold-Einkommen
- [ ] Dungeon/Raid-Schwierigkeit testen und anpassen
- [ ] Enhancement-Chancen evaluieren und ggf. anpassen

### 5.2 Anti-Cheat Haertung

- [ ] Alle Validierungen aus ARCHITECTURE.md 4.4 implementiert und getestet
- [ ] Automatische Ban-Systeme (10x Cheat-Versuch -> Temp-Ban)
- [ ] GM-Tools: Spieler beobachten, Inventar pruefen, Teleportieren, Bannen
- [ ] Logging: Verdaechtige Aktionen in DB loggen fuer manuelle Pruefung
- [ ] Client-Side: Basis Obfuscation (IL2CPP), Memory-Scan-Erkennung

### 5.3 Performance-Optimierung

- [ ] Server: Profiling unter Last (5.000 simulierte CCU)
- [ ] Server: Object-Pooling, SpatialGrid-Optimierung, GC-Tuning
- [ ] Client: Draw-Call-Batching, LOD, Occlusion Culling
- [ ] Client: Entity-Pooling (nicht fuer jeden Spawn neu instanziieren)
- [ ] Netzwerk: Delta-Compression fuer Position-Updates
- [ ] Datenbank: Slow-Query-Analyse, Index-Optimierung

### 5.4 2FA und Account-Sicherheit

- [ ] TOTP-basierte 2FA (Google Authenticator / Authy)
- [ ] Recovery-Codes bei 2FA-Aktivierung
- [ ] IP-Logging: `account_ip_log` Tabelle
- [ ] E-Mail-Warnung bei Login von neuem Standort
- [ ] Passwort-Reset per E-Mail

### 5.5 Closed Beta

- [ ] Beta-Einladungssystem (Keys / Whitelist)
- [ ] In-Game Bug-Report-System
- [ ] Telemetrie: Crash-Reports, Performance-Metriken
- [ ] Feedback-Kanal (Discord o.ae.)
- [ ] Bekannte Bugs fixen, Feedback einarbeiten

### 5.6 Open Beta

- [ ] Server-Infrastruktur fuer offenen Zugang vorbereiten
- [ ] Registrierung oeffnen
- [ ] Monitoring: Server-Last, DB-Performance, Error-Rates
- [ ] Hotfix-Pipeline: Schnelle Deployments bei kritischen Bugs
- [ ] Wipe-Policy kommunizieren (Open-Beta-Fortschritt = permanent oder nicht?)

### 5.7 Launch

- [ ] Finale Server-Infrastruktur (Hosting, Backups, Monitoring)
- [ ] Webseite mit Download-Links
- [ ] Launcher mit Auto-Update
- [ ] Terms of Service und Datenschutzerklaerung
- [ ] Community-Kanaele (Discord, Forum)
- [ ] Launch-Event (In-Game)

---

## Naechster Schritt

**Phase 1, Schritt 1.1: Projektsetup und Build-Pipeline** ist der erste konkrete Arbeitsschritt.
