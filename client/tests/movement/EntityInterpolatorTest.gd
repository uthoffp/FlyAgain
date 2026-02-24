## EntityInterpolatorTest.gd
## Tests for EntityInterpolator: buffer management, interpolation, edge cases.
class_name EntityInterpolatorTest
extends GdUnitTestSuite


# ---- Empty buffer ----

func test_sample_empty_returns_zero() -> void:
	var interp := EntityInterpolator.new()
	var result := interp.sample()
	assert_float(result["position"].x).is_equal(0.0)
	assert_float(result["position"].y).is_equal(0.0)
	assert_float(result["position"].z).is_equal(0.0)
	assert_float(result["rotation"]).is_equal(0.0)


func test_sample_at_empty_returns_zero() -> void:
	var interp := EntityInterpolator.new()
	var result := interp.sample_at(1000.0)
	assert_float(result["position"].x).is_equal(0.0)
	assert_float(result["rotation"]).is_equal(0.0)


# ---- Single snapshot ----

func test_single_snapshot_returns_that_position() -> void:
	var interp := EntityInterpolator.new()
	interp.push_snapshot_at(Vector3(10.0, 20.0, 30.0), 1.57, true, false, 1000.0)
	var result := interp.sample_at(900.0)
	assert_float(result["position"].x).is_equal_approx(10.0, 0.01)
	assert_float(result["position"].y).is_equal_approx(20.0, 0.01)
	assert_float(result["position"].z).is_equal_approx(30.0, 0.01)
	assert_float(result["rotation"]).is_equal_approx(1.57, 0.01)


func test_single_snapshot_always_returned_regardless_of_time() -> void:
	var interp := EntityInterpolator.new()
	interp.push_snapshot_at(Vector3(5.0, 0.0, 5.0), 0.0, false, false, 500.0)
	# Before snapshot
	var r1 := interp.sample_at(100.0)
	assert_float(r1["position"].x).is_equal_approx(5.0, 0.01)
	# After snapshot
	var r2 := interp.sample_at(2000.0)
	assert_float(r2["position"].x).is_equal_approx(5.0, 0.01)


# ---- Two snapshots: interpolation ----

func test_interpolation_midpoint() -> void:
	var interp := EntityInterpolator.new()
	interp.push_snapshot_at(Vector3(0.0, 0.0, 0.0), 0.0, true, false, 1000.0)
	interp.push_snapshot_at(Vector3(10.0, 0.0, 0.0), 0.0, true, false, 1100.0)
	# Midpoint at t=1050
	var result := interp.sample_at(1050.0)
	assert_float(result["position"].x).is_equal_approx(5.0, 0.1)


func test_interpolation_quarter() -> void:
	var interp := EntityInterpolator.new()
	interp.push_snapshot_at(Vector3(0.0, 0.0, 0.0), 0.0, true, false, 1000.0)
	interp.push_snapshot_at(Vector3(100.0, 0.0, 0.0), 0.0, true, false, 1100.0)
	# Quarter at t=1025
	var result := interp.sample_at(1025.0)
	assert_float(result["position"].x).is_equal_approx(25.0, 0.5)


func test_interpolation_at_exact_start() -> void:
	var interp := EntityInterpolator.new()
	interp.push_snapshot_at(Vector3(0.0, 0.0, 0.0), 0.0, true, false, 1000.0)
	interp.push_snapshot_at(Vector3(10.0, 0.0, 0.0), 0.0, true, false, 1100.0)
	var result := interp.sample_at(1000.0)
	assert_float(result["position"].x).is_equal_approx(0.0, 0.1)


func test_interpolation_at_exact_end() -> void:
	var interp := EntityInterpolator.new()
	interp.push_snapshot_at(Vector3(0.0, 0.0, 0.0), 0.0, true, false, 1000.0)
	interp.push_snapshot_at(Vector3(10.0, 0.0, 0.0), 0.0, true, false, 1100.0)
	var result := interp.sample_at(1100.0)
	assert_float(result["position"].x).is_equal_approx(10.0, 0.1)


# ---- Rotation interpolation ----

func test_rotation_interpolated() -> void:
	var interp := EntityInterpolator.new()
	interp.push_snapshot_at(Vector3.ZERO, 0.0, true, false, 1000.0)
	interp.push_snapshot_at(Vector3.ZERO, PI, true, false, 1100.0)
	var result := interp.sample_at(1050.0)
	# lerp_angle at midpoint between 0 and PI should be approximately PI/2
	assert_float(result["rotation"]).is_equal_approx(PI / 2.0, 0.1)


# ---- Before all snapshots ----

func test_before_all_snapshots_returns_earliest() -> void:
	var interp := EntityInterpolator.new()
	interp.push_snapshot_at(Vector3(10.0, 0.0, 0.0), 1.0, true, false, 1000.0)
	interp.push_snapshot_at(Vector3(20.0, 0.0, 0.0), 2.0, true, false, 1100.0)
	var result := interp.sample_at(500.0)
	assert_float(result["position"].x).is_equal_approx(10.0, 0.01)
	assert_float(result["rotation"]).is_equal_approx(1.0, 0.01)


# ---- Past all snapshots (no extrapolation) ----

func test_past_all_snapshots_returns_last() -> void:
	var interp := EntityInterpolator.new()
	interp.push_snapshot_at(Vector3(10.0, 0.0, 0.0), 1.0, true, false, 1000.0)
	interp.push_snapshot_at(Vector3(20.0, 0.0, 0.0), 2.0, true, false, 1100.0)
	var result := interp.sample_at(5000.0)
	assert_float(result["position"].x).is_equal_approx(20.0, 0.01)
	assert_float(result["rotation"]).is_equal_approx(2.0, 0.01)


# ---- Buffer size management ----

func test_buffer_size_increments() -> void:
	var interp := EntityInterpolator.new()
	assert_int(interp.buffer_size()).is_equal(0)
	interp.push_snapshot_at(Vector3.ZERO, 0.0, false, false, 100.0)
	assert_int(interp.buffer_size()).is_equal(1)
	interp.push_snapshot_at(Vector3.ZERO, 0.0, false, false, 200.0)
	assert_int(interp.buffer_size()).is_equal(2)


func test_buffer_caps_at_max() -> void:
	var interp := EntityInterpolator.new()
	for i in range(15):
		interp.push_snapshot_at(Vector3(float(i), 0.0, 0.0), 0.0, false, false, float(i * 100))
	# Max buffer size is 10
	assert_int(interp.buffer_size()).is_less_equal(10)


func test_clear_empties_buffer() -> void:
	var interp := EntityInterpolator.new()
	interp.push_snapshot_at(Vector3.ZERO, 0.0, false, false, 100.0)
	interp.push_snapshot_at(Vector3.ZERO, 0.0, false, false, 200.0)
	assert_int(interp.buffer_size()).is_equal(2)
	interp.clear()
	assert_int(interp.buffer_size()).is_equal(0)


# ---- Multiple snapshots: picks correct pair ----

func test_three_snapshots_interpolates_correct_pair() -> void:
	var interp := EntityInterpolator.new()
	interp.push_snapshot_at(Vector3(0.0, 0.0, 0.0), 0.0, true, false, 1000.0)
	interp.push_snapshot_at(Vector3(10.0, 0.0, 0.0), 0.0, true, false, 1100.0)
	interp.push_snapshot_at(Vector3(30.0, 0.0, 0.0), 0.0, true, false, 1200.0)
	# t=1150 should interpolate between snapshot 2 (10.0) and snapshot 3 (30.0)
	var result := interp.sample_at(1150.0)
	assert_float(result["position"].x).is_equal_approx(20.0, 0.5)


# ---- Y and Z interpolation ----

func test_all_axes_interpolated() -> void:
	var interp := EntityInterpolator.new()
	interp.push_snapshot_at(Vector3(0.0, 0.0, 0.0), 0.0, true, true, 1000.0)
	interp.push_snapshot_at(Vector3(10.0, 20.0, 30.0), 0.0, true, true, 1100.0)
	var result := interp.sample_at(1050.0)
	assert_float(result["position"].x).is_equal_approx(5.0, 0.1)
	assert_float(result["position"].y).is_equal_approx(10.0, 0.1)
	assert_float(result["position"].z).is_equal_approx(15.0, 0.1)
