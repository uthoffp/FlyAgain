# FlyAgain - Game Design Document

## 1. Vision

**FlyAgain** ist ein originales MMORPG, das sich auf faires Gameplay ohne Pay-to-Win konzentriert. Alle spielrelevanten Items und Fortschritte müssen im Spiel erarbeitet werden.

**Kernphilosophie:** Skill und Zeitinvestment bestimmen den Fortschritt - nicht die Geldbörse.

---

## 2. Kernmechaniken

### 2.1 Flugmechanik
- Spieler können ab einem bestimmten Level fliegen (Besen, Boards, Flügel etc.)
- Fliegen ist ein zentrales Fortbewegungsmittel in der offenen Welt

### 2.2 Kampfsystem
- **Tab-Target:** Klassisches Targeting - Monster anklicken, Auto-Attack + Skills einsetzen. Das monotone, meditative Grinding-Feeling soll bewusst beibehalten werden. Zielgruppe sind Spieler, die diesen Loop schätzen.
- **Klassen-basiert:** Verschiedene Klassen mit einzigartigen Fähigkeitenbäumen
- **Skills:** Rudimentäres Skill-System mit grundlegenden Fähigkeiten pro Klasse

### 2.3 Progression / Grinding
- **Level-System:** Klassisches XP-basiertes Leveling durch Monsterkämpfe
- **Equipment-System:** Waffen und Rüstungen mit Upgrade-System (Enhancement)
- **Kein RNG-P2W:** Upgrade-Chancen sind transparent, kein Cash-Shop-Schutz gegen Failure
- **Dungeons:** Instanzierte Dungeons mit Boss-Mechaniken und Loot-Tabellen
- **Quests:** Wiederholbare Daily/Weekly-Quests (keine Story-Quests im MVP)

### 2.4 PvP-System (Post-MVP)
- **Guild vs Guild (GvG):** Organisierte Gildenkriege um Territorien/Ressourcen
- **Open-World PvP:** Optionale PvP-Zonen mit Risiko/Belohnung
- **Ranking-System:** Saisonale Ranglisten mit kosmetischen Belohnungen

---

## 3. Klassen (MVP - Initial 4 Klassen)

| Klasse     | Rolle       | Beschreibung                              |
|------------|-------------|-------------------------------------------|
| Warrior    | Tank/Melee  | Hohe HP, starke Nahkampf-Angriffe         |
| Mage       | Ranged DPS  | Elementarmagie, hoher Schaden, niedrige HP |
| Assassin   | Melee DPS   | Schnelle Angriffe, Ausweichen, Kritisch   |
| Cleric     | Healer/Buff | Heilung, Buffs, Support-Fähigkeiten       |

Jede Klasse erhält später (post-MVP) eine Spezialisierung (2nd Job).

---

## 4. Welt-Design (MVP)

### 4.1 Startgebiet
- **Stadt Aerheim:** Hub-Stadt mit NPCs (Händler, Quest-Geber)
- **Green Plains:** Level 1-15 Grinding-Zone mit schwachen Monstern
- **Dark Forest:** Level 15-30 Zone mit stärkeren Monstern

### 4.2 Monster
- 10-15 verschiedene Monstertypen für den MVP
- Jedes Monster hat eine Loot-Tabelle und XP-Werte
- 1 Dungeon-Boss als Proof-of-Concept

---

## 5. Anti-Pay-to-Win Design-Prinzipien

1. **Kein Gameplay-Vorteil durch Echtgeld:** Keine Stat-Boosts, XP-Booster oder Enhancement-Schutz im Shop
2. **Transparente Mechaniken:** Alle Drop-Raten und Upgrade-Chancen sind öffentlich einsehbar
3. **Faire Progression:** Der Fortschritt soll sich belohnend anfühlen ohne künstliche Verlangsamung
4. **Skill > Gear:** PvP-Balance berücksichtigt Spielerfähigkeit stärker als Equipment-Level
5. **Keine Lootboxen:** Keine Zufalls-Käufe - Spieler wissen immer, was sie erhalten

---

## 6. Technische Anforderungen

### 6.0 Technologie-Stack
- **Client:** Godot 4 (GDScript)
- **Server:** Kotlin (Netty) - Authoritative Game Server
- **Datenbank:** PostgreSQL + Redis
- **Erwartete Last:** max. 10.000 Spieler gesamt, max. 5.000 gleichzeitig

### 6.1 Server-Architektur (Kotlin)
- **Authoritative Server:** Server validiert alle Aktionen (Anti-Cheat)
- **Dedicated Server:** Zentraler Game-Server (kein Peer-to-Peer)
- **Datenbank:** PostgreSQL für Spielerdaten, Redis für Sessions/Cache
- **Instanzen:** Dungeons als separate Instanzen

### 6.2 Client (Godot 4 / GDScript)
- **3D-Grafik:** Third-Person-Kamera mit freier Rotation
- **UI-System:** Chat, Inventar, Skilltree, Map, Party/Guild-Management
- **Netzwerk:** TCP/UDP Kommunikation mit Kotlin-Server, Client-Side Prediction

### 6.3 Minimal MVP Scope
- 1 spielbare Klasse (Warrior)
- 1 offene Zone + 1 Stadt
- Grundbewegung (Laufen + Fliegen)
- Basis-Kampfsystem (Auto-Attack + 3-4 Skills)
- 3-5 Monstertypen
- Level-System (Level 1-15)
- Basis-UI (HP/MP-Balken, Inventar, Chat)
- Multiplayer: Andere Spieler sehen und grundlegend interagieren

---

## 7. Monetarisierung

> Noch zu definieren. Mögliche Modelle:
> - Rein kosmetischer Shop (Skins, Mounts, Emotes)
> - Buy-to-Play Modell
> - Freemium mit fairem Free-Tier
>
> **Rote Linie:** Kein Pay-to-Win, keine Lootboxen, keine Gameplay-Vorteile durch Echtgeld.
