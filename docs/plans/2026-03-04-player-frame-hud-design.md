# Player Frame HUD Design

## Goal
Rudimentary HUD showing player HP, MP, and XP % to next level.

## Layout
Position: top-left corner. Single `PlayerFrame.gd` (extends `PanelContainer`).

```
┌──────────────────────────┐
│ Lv. 5  Charactername     │
│ ████████████░░░░  250/400│  ← HP (green)
│ ██████░░░░░░░░░░   30/50 │  ← MP (blue)
│ ══════════░░░░░░  72.5%  │  ← XP (gold, thinner)
└──────────────────────────┘
```

## Technical Details
- Single file: `client/scenes/ui/game_hud/PlayerFrame.gd`
- Extends `PanelContainer`, follows TargetFrame pattern
- Reads from `GameState.player_*` in `_process()`
- Colors: green HP, blue MP, gold XP (from `Colors.gd`)
- Dark translucent panel with gold border (matching TargetFrame style)
- XP bar slightly thinner than HP/MP bars
- Integrated into `GameWorld._setup_hud()` in existing HUD CanvasLayer
- No localization needed (numbers and bars only)

## Data Sources (GameState)
- `player_level`, `player_hp`, `player_max_hp`
- `player_mp`, `player_max_mp`
- `player_xp`, `player_xp_to_next_level`
- Character name from `player_name` or entity data
