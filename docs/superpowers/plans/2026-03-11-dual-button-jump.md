# Dual-Button Jump Trigger Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Change the jump trigger from Space key to left+right mouse button simultaneous press, with a 100ms grace window and suppression of individual click actions.

**Architecture:** Client-only change in PlayerCharacter.gd. Add state tracking for each mouse button's press time, detect when both are pressed within a grace window, trigger the existing jump arc, and suppress left-click targeting / right-click camera drag when jump fires. No server, proto, or new file changes.

**Tech Stack:** Godot 4 (GDScript)

---

## Chunk 1: Implementation

### Task 1: Add jump combo constant to WorldConstants

**Files:**
- Modify: `client/scripts/world/WorldConstants.gd:16-17`

- [ ] **Step 1: Add JUMP_COMBO_WINDOW constant**

In `client/scripts/world/WorldConstants.gd`, add after line 17 (`const GRAVITY := 20.0`):

```gdscript
const JUMP_COMBO_WINDOW := 100.0  # ms grace window for LMB+RMB jump combo
```

- [ ] **Step 2: Verify no syntax errors**

Run: `grep -n "JUMP_COMBO_WINDOW" client/scripts/world/WorldConstants.gd`
Expected: One line with the new constant.

- [ ] **Step 3: Commit**

```bash
git add client/scripts/world/WorldConstants.gd
git commit -m "feat: add JUMP_COMBO_WINDOW constant for dual-button jump"
```

---

### Task 2: Add dual-button state variables to PlayerCharacter

**Files:**
- Modify: `client/scenes/game/PlayerCharacter.gd:30-36`

- [ ] **Step 1: Add dual-button tracking state**

In `client/scenes/game/PlayerCharacter.gd`, add after line 36 (`var _camera_base_y: float = 0.0`) and before the `# Click-to-Move state` comment on line 38:

```gdscript

# Dual-button jump trigger (LMB + RMB simultaneously)
var _lmb_pressed_time: float = -1.0
var _rmb_pressed_time: float = -1.0
var _lmb_jump_consumed: bool = false
var _rmb_jump_consumed: bool = false
```

- [ ] **Step 2: Commit**

```bash
git add client/scenes/game/PlayerCharacter.gd
git commit -m "feat: add dual-button jump state variables"
```

---

### Task 3: Add jump combo detection helper method

**Files:**
- Modify: `client/scenes/game/PlayerCharacter.gd`

- [ ] **Step 1: Add _try_jump_combo method**

Add a new method after the `_handle_flight_input()` method (after line 389) and before `_handle_jump_input()`:

```gdscript


## Attempt to trigger a jump from dual mouse-button press.
## Called when either LMB or RMB is pressed; checks if the other was pressed
## within the grace window. Returns true if jump was triggered.
func _try_jump_combo() -> bool:
	if _is_flying or not _is_grounded:
		return false
	if _lmb_pressed_time < 0.0 or _rmb_pressed_time < 0.0:
		return false
	var diff := absf(_lmb_pressed_time - _rmb_pressed_time)
	if diff <= WorldConstants.JUMP_COMBO_WINDOW:
		_jump_velocity = WorldConstants.JUMP_VELOCITY
		_is_grounded = false
		_lmb_jump_consumed = true
		_rmb_jump_consumed = true
		_lmb_pressed_time = -1.0
		_rmb_pressed_time = -1.0
		return true
	return false
```

- [ ] **Step 2: Commit**

```bash
git add client/scenes/game/PlayerCharacter.gd
git commit -m "feat: add _try_jump_combo helper method"
```

---

### Task 4: Wire dual-button detection into _unhandled_input

**Files:**
- Modify: `client/scenes/game/PlayerCharacter.gd:89-131`

- [ ] **Step 1: Replace the _unhandled_input method**

Replace the entire `_unhandled_input` method (lines 89-131) with the following. Key changes:
- LMB pressed: record timestamp, check for jump combo, skip targeting/click-to-move if jump fires
- RMB pressed: record timestamp, check for jump combo, skip camera drag tracking if jump fires
- LMB/RMB released: skip individual actions if that button's jump-consumed flag is set
- Each button has its own consumed flag (`_lmb_jump_consumed`, `_rmb_jump_consumed`) so the second button release is also suppressed regardless of release order

```gdscript
func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventMouseButton:
		var now_ms := float(Time.get_ticks_msec())

		# ---- Left mouse button ----
		if event.button_index == MOUSE_BUTTON_LEFT:
			if event.pressed:
				_lmb_pressed_time = now_ms
				if _try_jump_combo():
					get_viewport().set_input_as_handled()
					return
				# Normal left-click: target entity or click-to-move
				if not _camera_pivot.is_rotating():
					if not _try_target_entity(event.position):
						if not _is_flying:
							_try_click_to_move(event.position)
			else:
				# LMB released — reset tracking
				_lmb_pressed_time = -1.0
				if _lmb_jump_consumed:
					_lmb_jump_consumed = false
					get_viewport().set_input_as_handled()
					return

		# ---- Right mouse button ----
		elif event.button_index == MOUSE_BUTTON_RIGHT:
			if event.pressed:
				_rmb_pressed_time = now_ms
				if _try_jump_combo():
					get_viewport().set_input_as_handled()
					return
				# Normal right-click: start tracking for target vs camera drag
				_right_click_start_pos = event.position
				_right_click_pressed = true
				_right_click_press_time = Time.get_ticks_msec() / 1000.0
			else:
				# RMB released — target on quick click, skip if jump consumed
				_rmb_pressed_time = -1.0
				if _rmb_jump_consumed:
					_rmb_jump_consumed = false
					_right_click_pressed = false
					get_viewport().set_input_as_handled()
					return
				if _right_click_pressed:
					_right_click_pressed = false
					var held_time := Time.get_ticks_msec() / 1000.0 - _right_click_press_time
					if held_time < TARGET_CLICK_TIME_THRESHOLD:
						_try_target_entity(_right_click_start_pos)

	# Double-tap W: toggle auto-run
	if event.is_action_pressed("move_forward") and not event.is_echo():
		var now := Time.get_ticks_msec() / 1000.0
		if _auto_run:
			_auto_run = false
		elif now - _last_forward_press_time < DOUBLE_TAP_THRESHOLD:
			_auto_run = true
		_last_forward_press_time = now

	if event is InputEventKey and event.pressed and not event.echo:
		match event.keycode:
			KEY_F:
				auto_attack_toggled.emit(
					not GameState.auto_attack_active,
					GameState.selected_target_id)
				get_viewport().set_input_as_handled()
			KEY_ESCAPE:
				_pending_target_id = 0
				target_cleared.emit()
				get_viewport().set_input_as_handled()
```

- [ ] **Step 2: Commit**

```bash
git add client/scenes/game/PlayerCharacter.gd
git commit -m "feat: wire dual-button jump detection into _unhandled_input"
```

---

### Task 5: Remove Space-key jump from _handle_jump_input

**Files:**
- Modify: `client/scenes/game/PlayerCharacter.gd:392-398`

- [ ] **Step 1: Remove the body of _handle_jump_input**

Replace the `_handle_jump_input` method with a pass-through (keeping the method for structure, since `_physics_process` calls it):

```gdscript
## Jump is now triggered by dual mouse-button press in _unhandled_input.
## This method is kept as a no-op for structural clarity.
func _handle_jump_input() -> void:
	pass
```

- [ ] **Step 2: Commit**

```bash
git add client/scenes/game/PlayerCharacter.gd
git commit -m "feat: remove Space-key jump trigger, now using dual mouse buttons"
```

---

### Task 6: Manual playtesting verification

- [ ] **Step 1: Launch the game client**

Run the Godot project and enter the game world with a character.

- [ ] **Step 2: Test jump triggers**

1. Press LMB and RMB simultaneously (or within ~100ms) → character should jump (visual arc).
2. Press only LMB → should target entity or click-to-move (no jump).
3. Press only RMB → should start camera drag or target on quick release (no jump).
4. Press Space while grounded → should NOT jump (Space is now fly_up only).
5. While flying, press LMB+RMB → should NOT jump (jump disabled during flight).
6. While already jumping, press LMB+RMB → should NOT double-jump (only works when grounded).

- [ ] **Step 3: Verify remote sync**

Have a second client (or check server logs) to confirm `jump_offset` is still being sent and remote players see the jump arc.

- [ ] **Step 4: Final commit with all changes if any tweaks were needed**

```bash
git add -A
git commit -m "feat: dual-button jump trigger (LMB+RMB) replaces Space key"
```
