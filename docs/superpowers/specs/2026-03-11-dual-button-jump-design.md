# Dual-Button Jump Trigger

**Date:** 2026-03-11
**Status:** Approved
**Scope:** Client-only change in PlayerCharacter.gd

## Summary

Replace the Space-key jump trigger with a left+right mouse button simultaneous press. Jump remains a visual-only arc (model bounces, root position stays grounded). No server or protocol changes needed.

## Design

### Input Detection

Use a grace window approach: when one mouse button is pressed, record its timestamp. When the second button is pressed within 100ms, trigger the jump. This handles natural human timing variance.

### New State (PlayerCharacter.gd)

- `_lmb_pressed_time: float = -1.0` -- timestamp (msec) when LMB last pressed, -1 when not pending
- `_rmb_pressed_time: float = -1.0` -- timestamp (msec) when RMB last pressed, -1 when not pending
- `_jump_input_consumed: bool = false` -- suppresses individual click actions when jump fires

### Constant

`JUMP_COMBO_WINDOW := 100.0` (milliseconds) -- added to WorldConstants or inline in PlayerCharacter.

### Flow in `_unhandled_input(event)`

1. **LMB pressed:** Record `_lmb_pressed_time = Time.get_ticks_msec()`. If RMB was pressed within grace window and jump conditions met (grounded, not flying) -> trigger jump, set `_jump_input_consumed = true`.
2. **RMB pressed:** Record `_rmb_pressed_time = Time.get_ticks_msec()`. If LMB was pressed within grace window and jump conditions met -> trigger jump, set `_jump_input_consumed = true`.
3. **LMB released:** If `_jump_input_consumed`, skip targeting/click-to-move, reset consumed flag for LMB tracking. Otherwise normal left-click behavior.
4. **RMB released:** If `_jump_input_consumed`, skip camera drag, reset consumed flag for RMB tracking. Otherwise normal right-click behavior.

### Jump Trigger (unchanged logic)

- Only fires if `not _is_flying and _is_grounded`
- Sets `_jump_velocity = WorldConstants.JUMP_VELOCITY`, `_is_grounded = false`
- Existing `_update_jump(delta)` and `_apply_jump_visual()` handle the arc and rendering

### What Changes

- `PlayerCharacter.gd`: Add dual-button detection in `_unhandled_input`, remove Space-key jump check from `_handle_jump_input` (Space remains `fly_up` only)

### What Stays the Same

- `_update_jump()`, `_apply_jump_visual()` -- unchanged
- Server-side code -- no changes
- Proto messages -- no changes, `jump_offset` still syncs via existing `MovementInput`
- Network packets -- unchanged
- `WorldConstants.JUMP_VELOCITY` and `GRAVITY` -- unchanged

## Risks

- **Accidental jumps:** 100ms window is tight enough to avoid accidental triggers from sequential clicks, but loose enough for intentional dual-press.
- **Input suppression:** When jump fires, both left-click (targeting) and right-click (camera drag) are suppressed for that press-release cycle. This prevents unintended side effects.
