# Engine-Vergleich für FlyAgain

## Anforderungsprofil

- 3D MMORPG mit Flugmechanik (simple Flyff-ähnliche Grafik)
- Multiplayer (Server-Client) von Anfang an
- Bevorzugte Sprache: Kotlin (Server wird in Kotlin geschrieben)
- C# nicht bevorzugt, aber akzeptabel. C++ bekannt aus Studium.
- Solo-Entwickler, viel KI-Unterstützung geplant
- Kein visueller Editor nötig (Coding bevorzugt)
- Kein 3D-Modelling-Erfahrung → einfache/fertige Assets nötig
- Performance und Sicherheit sind extrem wichtig
- Erwartete Last: max. 10.000 Spieler gesamt, max. 5.000 gleichzeitig
- Ziel: Minimal MVP, nicht 10 Jahre Entwicklung

---

## Unity (C#)

### Überblick
Marktführende Game-Engine. Riesiges Ökosystem, primär C#.
Server separat in Kotlin.

### Vorteile

**KI-Unterstützung & Ecosystem**
- Größte Wissensbasis aller Engines - jede KI kennt Unity in- und auswendig
- Millionen Tutorials, StackOverflow-Antworten, YouTube-Videos
- Riesiger Asset Store: fertige 3D-Modelle, Shader, UI-Systeme, Netzwerk-Lösungen
- Für jemanden ohne 3D-Modelling-Erfahrung Gold wert (fertige Low-Poly Assets kaufbar)

**Netzwerk für MMOs**
- **FishNet:** Modernes Open-Source Networking-Framework, speziell für MMOs geeignet
- **Mirror:** Bewährte Alternative, große Community
- Beide unterstützen Authoritative Server, Client-Side Prediction, Object Sync
- Alternativ: Eigener Kotlin-Server über TCP/UDP (empfohlen für FlyAgain)

**Grafik**
- Universal Render Pipeline (URP) für performante, skalierbare Grafik
- Shader Graph für visuelle Shader-Erstellung ohne Code
- Für Flyff-Style ideal: einfach genug, aber bei Bedarf ausbaubar
- Post-Processing, Partikel, Lighting - alles out-of-the-box

**Entwicklung**
- Schnellster Weg zum spielbaren MVP
- C# ist syntaktisch sehr nah an Kotlin (Umstieg in Tagen möglich)
- Hot Reload / Play Mode für schnelles Testen
- Visueller Editor vorhanden, aber Coding-first Workflow möglich
- Cross-Platform: Windows, macOS, Linux Builds

**Performance**
- IL2CPP kompiliert C# zu nativem Code → gute Runtime-Performance
- Für Flyff-Style Grafik + 5.000 CCU mehr als ausreichend
- Profiler und Debugging-Tools eingebaut

### Nachteile
- **C# statt Kotlin:** Client-Code muss in C# geschrieben werden
- **Lizenzkosten:** Unity Runtime Fee / Abo-Modell (aktuelle Konditionen prüfen)
- **Editor-Abhängigkeit:** Auch wenn du Code bevorzugst, manches geht nur über den Editor (Scene Setup, Prefabs, Material-Zuweisung)
- **Closed Source:** Engine-Code nicht einsehbar (kein Custom-Engine-Feeling)
- **IL2CPP Build-Zeiten:** Können bei größeren Projekten lang werden
- **Vendor Lock-in:** Abhängigkeit von Unity Technologies

### Server-Architektur
```
Client: Unity (C#) ──TCP/UDP──► Game Server: Kotlin (Ktor/Netty)
                                      │
                                      ▼
                               PostgreSQL + Redis
```
- Client nutzt Unity für Rendering, Input, UI
- Authoritative Game Server in Kotlin (deine Stärke)
- Kotlin-Server validiert alle Aktionen (Anti-Cheat, Sicherheit)
- Unity-Client ist "dumb" - zeigt nur an, was der Server sagt

---

## Unreal Engine 5 (C++)

### Überblick
High-End Game-Engine von Epic Games. C++ und Blueprints (visuelles Scripting).
Server separat in Kotlin oder als Unreal Dedicated Server.

### Vorteile

**KI-Unterstützung & Ecosystem**
- Zweitgrößte Wissensbasis nach Unity
- Viele Tutorials und Dokumentation, besonders für AAA-Projekte
- Marketplace mit Assets, Plugins und fertigen Systemen
- KI-Modelle kennen Unreal C++ und Blueprints gut

**Netzwerk für MMOs**
- **Eingebautes Replication System:** Robustes, erprobtes Netzwerk-Framework
- Dedicated Server Support direkt in der Engine
- Automatische Property-Replication und RPC-Calls
- Für mittelgroße Multiplayer erprobt (aber nicht für tausende Spieler pro Instanz)

**Grafik**
- Beste Grafik-Qualität aller Engines (Nanite, Lumen, Virtual Shadow Maps)
- Auch für einfache Grafik nutzbar - Stylized/Low-Poly möglich
- Massiv mehr Headroom nach oben falls Grafik später wichtiger wird
- Mega World System für große offene Welten

**Entwicklung**
- Blueprints ermöglichen schnelles Prototyping ohne C++
- C++ für performance-kritische Systeme
- Live Coding (Hot Reload für C++)
- Mächtige Debug- und Profiling-Tools
- Cross-Platform: Windows, macOS (eingeschränkt), Linux

**Performance**
- Nativer C++ Code → beste mögliche Performance
- Nanite/Lumen für moderne Hardware optimiert
- Für Flyff-Style wäre Performance kein Thema

### Nachteile
- **C++ Komplexität:** Deutlich komplexer als C#/Kotlin. Memory Management, Header Files, lange Compile-Zeiten. Du hast selbst gesagt: "war recht komplex"
- **Overkill:** Nanite, Lumen, MetaHumans - alles unnötig für Flyff-Style Grafik. Du zahlst den Komplexitätspreis ohne den Grafik-Vorteil zu nutzen
- **Steile Lernkurve:** Unreal's Architektur (GameMode, GameState, PlayerController, Pawn, etc.) ist komplex und eigen
- **Ressourcenhungrig:**
  - Minimum 32GB RAM für Entwicklung empfohlen
  - Projekte werden schnell 50-100 GB groß
  - Compile-Zeiten: Minuten statt Sekunden
- **Lizenz:** 5% Royalty ab $1M Umsatz (Brutto, nicht Netto)
- **MMO-Skalierung:** Das eingebaute Replication-System skaliert auf ~100 Spieler pro Server-Instanz. Für 5.000 CCU brauchst du trotzdem einen separaten Backend-Server + Zonen/Instanz-System
- **macOS:** Nur eingeschränkt unterstützt (kein Nanite, kein Lumen auf Mac)
- **Editor-lastig:** Vieles in Unreal funktioniert NUR über den Editor (Level Design, Material Editor, Animation Blueprints). Reiner Code-Workflow schwieriger als bei Unity
- **Blueprints + C++ Mix:** Kann verwirrend werden - wann Blueprint, wann C++?

### Server-Architektur
```
Client: Unreal (C++) ──TCP/UDP──► Game Server: Kotlin (Ktor/Netty)
                                        │
                                        ▼
                                 PostgreSQL + Redis
```
- Oder: Unreal Dedicated Server für Game Logic + Kotlin-Backend für Persistenz
- Unreal's Replication für Echtzeit-Sync, Kotlin für Auth/DB/Chat

---

## Direktvergleich

| Kriterium                    | Unity (C#)               | Unreal (C++)             |
|------------------------------|--------------------------|--------------------------|
| **KI-Unterstützung**         | ★★★★★ Beste Abdeckung    | ★★★★☆ Sehr gut           |
| **Lernkurve**                | ★★★★☆ Moderat            | ★★☆☆☆ Steil              |
| **Sprach-Komfort**           | ★★★★☆ C# ≈ Kotlin        | ★★★☆☆ C++ komplex        |
| **Time-to-MVP**              | ★★★★★ Schnellster Weg    | ★★★☆☆ Deutlich länger    |
| **Grafik (Flyff-Level)**     | ★★★★★ Mehr als genug     | ★★★★★ Massiver Overkill  |
| **Grafik (Zukunft)**         | ★★★★☆ Gut ausbaubar      | ★★★★★ Unbegrenzt         |
| **MMO-Netzwerk-Ecosystem**   | ★★★★★ FishNet/Mirror     | ★★★★☆ Replication        |
| **Asset Store**              | ★★★★★ Riesig             | ★★★★☆ Groß               |
| **Solo-Dev-Freundlich**      | ★★★★★                    | ★★★☆☆                    |
| **Performance**              | ★★★★☆ IL2CPP gut         | ★★★★★ Nativ C++          |
| **Code-First Workflow**      | ★★★★☆ Gut möglich        | ★★★☆☆ Editor-lastiger    |
| **Ressourcen-Verbrauch**     | ★★★★☆ Moderat            | ★★☆☆☆ Hungrig            |
| **macOS-Support**            | ★★★★★ Voll               | ★★★☆☆ Eingeschränkt      |
| **Lizenzkosten**             | ★★★☆☆ Runtime Fee        | ★★★☆☆ 5% ab $1M         |
| **5.000 CCU**                | ★★★★★ Mit ext. Server    | ★★★★☆ Mit ext. Server    |
| **Community-Größe**          | ★★★★★                    | ★★★★★                    |

---

## Empfehlung

**Unity (C#) für den Client + Kotlin für den Server.**

### Warum Unity gewinnt für FlyAgain:

1. **KI-Unterstützung:** Du brauchst viel KI-Hilfe → Unity hat die beste Abdeckung
2. **C# ≈ Kotlin:** Der Umstieg ist minimal. Null-Safety, Lambdas, Properties, async/await - alles sehr ähnlich
3. **Time-to-MVP:** Schnellster Weg zu einem spielbaren Prototyp
4. **Simple Grafik:** URP reicht vollkommen, und der Asset Store liefert fertige Low-Poly Modelle
5. **Solo-Dev:** Unity ist die meistgenutzte Engine für Solo/Indie-Entwickler
6. **Server in Kotlin:** Die wichtigste Komponente (Authoritative Server, Sicherheit, Anti-Cheat) schreibst du in deiner Lieblingssprache

### Wann wäre Unreal besser?
- Wenn Grafik-Qualität oberste Priorität wäre (ist sie nicht)
- Wenn du ein Team mit C++-Erfahrung hättest
- Wenn du bereits Unreal-Erfahrung hättest
- Wenn das Projekt AAA-Grafik anstrebt

### Architektur-Vorschlag
```
┌──────────────────────────────────┐
│        Client: Unity (C#)        │
│  - 3D Rendering (URP)           │
│  - Input, UI, Audio             │
│  - Client-Side Prediction       │
│  - Netzwerk (TCP/UDP)           │
└───────────────┬──────────────────┘
                │ TCP/UDP
┌───────────────┴──────────────────┐
│     Game Server: Kotlin          │
│  - Ktor oder Netty              │
│  - Authoritative Game Logic     │
│  - Anti-Cheat Validierung       │
│  - Entity/Zone Management       │
│  - Chat, Party, Guild           │
└───────────────┬──────────────────┘
                │
┌───────────────┴──────────────────┐
│      Persistenz: Kotlin          │
│  - PostgreSQL (Spielerdaten)    │
│  - Redis (Sessions/Cache)       │
│  - Login/Auth Service           │
└──────────────────────────────────┘
```
