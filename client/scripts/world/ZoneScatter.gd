## ZoneScatter.gd
## Places assets on terrain using scatter rules.
## MultiMeshInstance3D for grass/flowers, individual scenes for trees/rocks.
## The terrain node must provide a get_height_at(x, z) -> float method.
##
## MultiMesh instances are split into spatial chunks so that Godot's
## visibility_range culling works per-chunk instead of per-entire-zone.
class_name ZoneScatter
extends Node3D

const CHUNK_SIZE := 50.0

var _terrain: Node = null
var _rng: RandomNumberGenerator = RandomNumberGenerator.new()


func initialize(terrain: Node, seed: int) -> void:
	_terrain = terrain
	_rng.seed = seed


func scatter(rule: ScatterRule, area_min: Vector2, area_max: Vector2) -> void:
	if rule.scenes.is_empty():
		return
	var area_size := (area_max - area_min)
	var area_m2 := area_size.x * area_size.y
	var count := int(area_m2 * rule.density / 100.0)
	if count <= 0:
		return
	if rule.use_multimesh:
		_scatter_multimesh(rule, area_min, area_max, count)
	else:
		_scatter_individual(rule, area_min, area_max, count)


func _get_height(x: float, z: float) -> float:
	if _terrain and _terrain.has_method("get_height_at"):
		var h: float = _terrain.get_height_at(x, z)
		if is_finite(h):
			return h
	return 0.0


func _scatter_multimesh(rule: ScatterRule, area_min: Vector2, area_max: Vector2, count: int) -> void:
	for scene in rule.scenes:
		var per_scene_count := count / rule.scenes.size()
		if per_scene_count <= 0:
			continue

		var temp := scene.instantiate()
		var mesh: Mesh = _find_mesh(temp)
		temp.queue_free()
		if not mesh:
			continue

		# Generate all instance transforms
		var transforms: Array[Transform3D] = []
		for i in range(per_scene_count * 3):
			if transforms.size() >= per_scene_count:
				break
			var x := _rng.randf_range(area_min.x, area_max.x)
			var z := _rng.randf_range(area_min.y, area_max.y)
			var y := _get_height(x, z)
			if y < rule.height_range.x or y > rule.height_range.y:
				continue

			var rot := deg_to_rad(_rng.randf_range(0.0, rule.rotation_range))
			var scale_val := 1.0 + _rng.randf_range(-rule.scale_variation, rule.scale_variation)
			var xform := Transform3D.IDENTITY
			xform = xform.rotated(Vector3.UP, rot)
			xform = xform.scaled(Vector3.ONE * scale_val)
			xform.origin = Vector3(x, y, z)
			transforms.append(xform)

		# Bin transforms into spatial chunks
		var chunks: Dictionary = {}  # Vector2i -> Array[Transform3D]
		for xform in transforms:
			var cx := floori((xform.origin.x - area_min.x) / CHUNK_SIZE)
			var cz := floori((xform.origin.z - area_min.y) / CHUNK_SIZE)
			var key := Vector2i(cx, cz)
			if not chunks.has(key):
				chunks[key] = []
			chunks[key].append(xform)

		# Create one MultiMeshInstance3D per chunk
		for key in chunks:
			var chunk_transforms: Array = chunks[key]
			var mmi := MultiMeshInstance3D.new()
			var mm := MultiMesh.new()
			mm.transform_format = MultiMesh.TRANSFORM_3D
			mm.mesh = mesh
			mm.instance_count = chunk_transforms.size()
			for i in range(chunk_transforms.size()):
				mm.set_instance_transform(i, chunk_transforms[i])
			mmi.multimesh = mm

			# Distance-based culling per chunk
			if rule.visibility_range > 0.0:
				mmi.visibility_range_end = rule.visibility_range
				mmi.visibility_range_fade_mode = GeometryInstance3D.VISIBILITY_RANGE_FADE_SELF
				mmi.visibility_range_end_margin = rule.visibility_fade_margin

			mmi.custom_aabb = _compute_aabb(mm)
			add_child(mmi)


func _scatter_individual(rule: ScatterRule, area_min: Vector2, area_max: Vector2, count: int) -> void:
	var placed := 0
	for i in range(count * 3):
		if placed >= count:
			break
		var x := _rng.randf_range(area_min.x, area_max.x)
		var z := _rng.randf_range(area_min.y, area_max.y)
		var y := _get_height(x, z)
		if y < rule.height_range.x or y > rule.height_range.y:
			continue

		var scene: PackedScene = rule.scenes[_rng.randi() % rule.scenes.size()]
		var instance := scene.instantiate()
		var rot := deg_to_rad(_rng.randf_range(0.0, rule.rotation_range))
		var scale_val := 1.0 + _rng.randf_range(-rule.scale_variation, rule.scale_variation)
		instance.position = Vector3(x, y, z)
		instance.rotation.y = rot
		instance.scale = Vector3.ONE * scale_val

		# Distance-based culling for individual instances
		if rule.visibility_range > 0.0 and instance is VisualInstance3D:
			instance.visibility_range_end = rule.visibility_range
			instance.visibility_range_fade_mode = GeometryInstance3D.VISIBILITY_RANGE_FADE_SELF
			instance.visibility_range_end_margin = rule.visibility_fade_margin

		add_child(instance)
		placed += 1


## Compute a tight AABB enclosing all MultiMesh instances for frustum culling.
func _compute_aabb(mm: MultiMesh) -> AABB:
	if mm.instance_count == 0:
		return AABB()
	var mesh_aabb := mm.mesh.get_aabb() if mm.mesh else AABB(Vector3.ZERO, Vector3.ONE)
	var result := mm.get_instance_transform(0) * mesh_aabb
	for i in range(1, mm.instance_count):
		result = result.merge(mm.get_instance_transform(i) * mesh_aabb)
	return result


func _find_mesh(node: Node) -> Mesh:
	if node is MeshInstance3D and node.mesh:
		return node.mesh
	for child in node.get_children():
		var m := _find_mesh(child)
		if m:
			return m
	return null
