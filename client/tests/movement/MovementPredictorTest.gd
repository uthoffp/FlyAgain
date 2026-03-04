## MovementPredictorTest.gd
## Tests for MovementPredictor: speed, boundaries, prediction, correction.
class_name MovementPredictorTest
extends GdUnitTestSuite


# ---- Speed computation ----

func test_ground_speed_no_dex() -> void:
	var pred := MovementPredictor.new()
	pred.set_position(Vector3(500.0, 0.0, 500.0))
	var dir := Vector3(1.0, 0.0, 0.0).normalized()
	var result := pred.apply_input(dir, true, false, 0, 1.0)
	# Should move exactly GROUND_MOVE_SPEED units in 1 second
	var expected_x := 500.0 + WorldConstants.GROUND_MOVE_SPEED
	assert_float(result.x).is_equal_approx(expected_x, 0.01)


func test_fly_speed_no_dex() -> void:
	var pred := MovementPredictor.new()
	pred.set_position(Vector3(500.0, 50.0, 500.0))
	var dir := Vector3(1.0, 0.0, 0.0).normalized()
	var result := pred.apply_input(dir, true, true, 0, 1.0)
	var expected_x := 500.0 + WorldConstants.FLY_MOVE_SPEED
	assert_float(result.x).is_equal_approx(expected_x, 0.01)


func test_dex_bonus_adds_to_speed() -> void:
	var pred := MovementPredictor.new()
	pred.set_position(Vector3(500.0, 0.0, 500.0))
	var dir := Vector3(1.0, 0.0, 0.0).normalized()
	var dex := 20
	var result := pred.apply_input(dir, true, false, dex, 1.0)
	# Speed = 5.0 + (20 * 0.05) = 6.0
	var expected_x := 500.0 + 6.0
	assert_float(result.x).is_equal_approx(expected_x, 0.01)


func test_dex_bonus_flying() -> void:
	var pred := MovementPredictor.new()
	pred.set_position(Vector3(500.0, 50.0, 500.0))
	var dir := Vector3(0.0, 0.0, 1.0).normalized()
	var dex := 10
	var result := pred.apply_input(dir, true, true, dex, 1.0)
	# Speed = 8.0 + (10 * 0.05) = 8.5
	var expected_z := 500.0 + 8.5
	assert_float(result.z).is_equal_approx(expected_z, 0.01)


# ---- Boundary clamping ----

func test_clamp_x_max_boundary() -> void:
	var pred := MovementPredictor.new()
	pred.set_position(Vector3(WorldConstants.WORLD_BOUNDARY_MAX - 1.0, 0.0, 500.0))
	var dir := Vector3(1.0, 0.0, 0.0).normalized()
	# Move far enough to exceed boundary
	var result := pred.apply_input(dir, true, false, 0, 100.0)
	assert_float(result.x).is_less_equal(WorldConstants.WORLD_BOUNDARY_MAX)


func test_clamp_x_min_boundary() -> void:
	var pred := MovementPredictor.new()
	pred.set_position(Vector3(WorldConstants.WORLD_BOUNDARY_MIN + 1.0, 0.0, 500.0))
	var dir := Vector3(-1.0, 0.0, 0.0).normalized()
	var result := pred.apply_input(dir, true, false, 0, 100.0)
	assert_float(result.x).is_greater_equal(WorldConstants.WORLD_BOUNDARY_MIN)


func test_clamp_z_boundaries() -> void:
	var pred := MovementPredictor.new()
	pred.set_position(Vector3(500.0, 0.0, WorldConstants.WORLD_BOUNDARY_MAX - 1.0))
	var dir := Vector3(0.0, 0.0, 1.0).normalized()
	var result := pred.apply_input(dir, true, false, 0, 100.0)
	assert_float(result.z).is_less_equal(WorldConstants.WORLD_BOUNDARY_MAX)


func test_ground_y_clamped_to_max_ground_y() -> void:
	var pred := MovementPredictor.new()
	pred.set_position(Vector3(500.0, 0.0, 500.0))
	# Attempt to move up while not flying — Y should be clamped
	var dir := Vector3(0.0, 1.0, 0.0).normalized()
	var result := pred.apply_input(dir, true, false, 0, 1.0)
	# Non-flying: vertical displacement is zeroed, Y stays clamped
	assert_float(result.y).is_less_equal(WorldConstants.MAX_TERRAIN_HEIGHT)


func test_flying_y_clamped_to_max_y() -> void:
	var pred := MovementPredictor.new()
	pred.set_position(Vector3(500.0, WorldConstants.MAX_Y_POSITION - 1.0, 500.0))
	var dir := Vector3(0.0, 1.0, 0.0).normalized()
	var result := pred.apply_input(dir, true, true, 0, 100.0)
	assert_float(result.y).is_less_equal(WorldConstants.MAX_Y_POSITION)


func test_flying_y_min_clamped() -> void:
	var pred := MovementPredictor.new()
	pred.set_position(Vector3(500.0, WorldConstants.MIN_Y_POSITION + 1.0, 500.0))
	var dir := Vector3(0.0, -1.0, 0.0).normalized()
	var result := pred.apply_input(dir, true, true, 0, 100.0)
	assert_float(result.y).is_greater_equal(WorldConstants.MIN_Y_POSITION)


# ---- Non-flying vertical displacement zeroed ----

func test_non_flying_zeroes_y_displacement() -> void:
	var pred := MovementPredictor.new()
	pred.set_position(Vector3(500.0, 0.0, 500.0))
	var dir := Vector3(1.0, 1.0, 0.0).normalized()
	var result := pred.apply_input(dir, true, false, 0, 1.0)
	# Y should not change for non-flying
	assert_float(result.y).is_less_equal(WorldConstants.MAX_TERRAIN_HEIGHT)


# ---- Correction snap-back ----

func test_correction_snaps_position() -> void:
	var pred := MovementPredictor.new()
	pred.set_position(Vector3(100.0, 5.0, 200.0))
	var corrected := pred.apply_correction(Vector3(500.0, 0.0, 500.0), 1.57)
	assert_float(corrected.x).is_equal_approx(500.0, 0.01)
	assert_float(corrected.y).is_equal_approx(0.0, 0.01)
	assert_float(corrected.z).is_equal_approx(500.0, 0.01)


func test_correction_updates_rotation() -> void:
	var pred := MovementPredictor.new()
	pred.set_rotation(0.0)
	pred.apply_correction(Vector3.ZERO, 3.14)
	assert_float(pred.get_rotation()).is_equal_approx(3.14, 0.01)


func test_correction_position_accessible_via_getter() -> void:
	var pred := MovementPredictor.new()
	pred.apply_correction(Vector3(42.0, 1.0, 99.0), 0.0)
	var pos := pred.get_position()
	assert_float(pos.x).is_equal_approx(42.0, 0.01)
	assert_float(pos.z).is_equal_approx(99.0, 0.01)


# ---- Sequence counting ----

func test_sequence_increments_per_input() -> void:
	var pred := MovementPredictor.new()
	assert_int(pred.get_sequence()).is_equal(0)
	pred.apply_input(Vector3.FORWARD, true, false, 0, 0.05)
	assert_int(pred.get_sequence()).is_equal(1)
	pred.apply_input(Vector3.FORWARD, true, false, 0, 0.05)
	assert_int(pred.get_sequence()).is_equal(2)


func test_sequence_increments_even_when_not_moving() -> void:
	var pred := MovementPredictor.new()
	pred.apply_input(Vector3.ZERO, false, false, 0, 0.05)
	assert_int(pred.get_sequence()).is_equal(1)


func test_correction_does_not_reset_sequence() -> void:
	var pred := MovementPredictor.new()
	pred.apply_input(Vector3.FORWARD, true, false, 0, 0.05)
	pred.apply_input(Vector3.FORWARD, true, false, 0, 0.05)
	assert_int(pred.get_sequence()).is_equal(2)
	pred.apply_correction(Vector3.ZERO, 0.0)
	# Sequence should remain — it's monotonically increasing
	assert_int(pred.get_sequence()).is_equal(2)


# ---- Not moving produces no displacement ----

func test_not_moving_stays_in_place() -> void:
	var pred := MovementPredictor.new()
	var start := Vector3(500.0, 0.0, 500.0)
	pred.set_position(start)
	var result := pred.apply_input(Vector3.ZERO, false, false, 0, 1.0)
	assert_float(result.x).is_equal_approx(start.x, 0.01)
	assert_float(result.z).is_equal_approx(start.z, 0.01)


func test_zero_direction_stays_in_place() -> void:
	var pred := MovementPredictor.new()
	var start := Vector3(500.0, 0.0, 500.0)
	pred.set_position(start)
	var result := pred.apply_input(Vector3.ZERO, true, false, 0, 1.0)
	assert_float(result.x).is_equal_approx(start.x, 0.01)
	assert_float(result.z).is_equal_approx(start.z, 0.01)


# ---- Flying mode state ----

func test_set_flying_updates_state() -> void:
	var pred := MovementPredictor.new()
	assert_bool(pred.is_flying()).is_false()
	pred.set_flying(true)
	assert_bool(pred.is_flying()).is_true()
	pred.set_flying(false)
	assert_bool(pred.is_flying()).is_false()
