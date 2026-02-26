## WorldConstants.gd
## Shared constants mirroring the server's MovementHandler and ZoneManager.
## Any change here MUST be synchronized with the server-side values.
class_name WorldConstants
extends RefCounted


# ---- Movement speeds (must match server MovementHandler.kt) ----

const GROUND_MOVE_SPEED := 5.0   # units/sec on ground
const FLY_MOVE_SPEED := 8.0      # units/sec while flying
const SPEED_TOLERANCE := 1.5     # server tolerance multiplier for speed-hack detection

# ---- Jump physics (client-side visual) ----

const JUMP_VELOCITY := 7.0      # initial upward velocity (units/sec)
const GRAVITY := 20.0            # downward acceleration (units/sec²)


# ---- World boundaries (must match server MovementHandler.kt) ----

const WORLD_BOUNDARY_MIN := -100.0
const WORLD_BOUNDARY_MAX := 10100.0
const MAX_Y_POSITION := 500.0
const MIN_Y_POSITION := -10.0
const GROUND_Y := 0.0            # flat terrain baseline
const MAX_GROUND_Y := 1.0        # non-flying players Y ceiling


# ---- Zone IDs (must match server ZoneManager.kt) ----

const ZONE_AERHEIM := 1
const ZONE_GREEN_PLAINS := 2
const ZONE_DARK_FOREST := 3


# ---- Zone display name translation keys (use with tr()) ----

const ZONE_NAME_KEYS: Dictionary = {
	ZONE_AERHEIM: "ZONE_AERHEIM",
	ZONE_GREEN_PLAINS: "ZONE_GREEN_PLAINS",
	ZONE_DARK_FOREST: "ZONE_DARK_FOREST",
}


## Returns the localized display name for the given zone ID.
static func get_zone_name(zone_id: int) -> String:
	var key: String = ZONE_NAME_KEYS.get(zone_id, "")
	if key.is_empty():
		return tr("ZONE_UNKNOWN")
	return tr(key)


# ---- Zone spawn positions (must match server ZoneManager.kt) ----

const ZONE_SPAWNS: Dictionary = {
	ZONE_AERHEIM: Vector3(500.0, 0.0, 500.0),
	ZONE_GREEN_PLAINS: Vector3(200.0, 0.0, 200.0),
	ZONE_DARK_FOREST: Vector3(100.0, 0.0, 100.0),
}


# ---- Default spawn position (Aerheim town center) ----

const DEFAULT_SPAWN := Vector3(500.0, 0.0, 500.0)


# ---- Character classes (must match server CharacterRepositoryImpl.kt) ----

const CLASS_WARRIOR := 0
const CLASS_MAGE := 1
const CLASS_ASSASSIN := 2
const CLASS_CLERIC := 3


# ---- Entity types (from EntitySpawnMessage.entity_type) ----

const ENTITY_TYPE_PLAYER := 0
const ENTITY_TYPE_MONSTER := 1
const ENTITY_TYPE_NPC := 2
const ENTITY_TYPE_LOOT := 3


# ---- Network timing ----

const SERVER_TICK_RATE := 20          # Hz
const SERVER_TICK_MS := 50            # ms per tick
const INTERPOLATION_BUFFER_MS := 100  # ms delay for remote entity smoothing
const MOVEMENT_SEND_RATE := 20        # Hz — match server tick rate


# ---- Spatial grid (informational, for debug/future use) ----

const SPATIAL_GRID_CELL_SIZE := 50.0  # units per cell
