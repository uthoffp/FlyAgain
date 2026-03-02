## ZoneScatter.gd
## Places assets on terrain using scatter rules.
## MultiMeshInstance3D for grass/flowers, individual scenes for trees/rocks.
class_name ZoneScatter
extends Node3D

var _terrain: HeightmapTerrain = null
var _rng: RandomNumberGenerator = RandomNumberGenerator.new()


func initialize(terrain: HeightmapTerrain, seed: int) -> void:
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

		var mmi := MultiMeshInstance3D.new()
		var mm := MultiMesh.new()
		mm.transform_format = MultiMesh.TRANSFORM_3D
		mm.mesh = mesh
		mm.instance_count = per_scene_count

		var placed := 0
		for i in range(per_scene_count * 3):
			if placed >= per_scene_count:
				break
			var x := _rng.randf_range(area_min.x, area_max.x)
			var z := _rng.randf_range(area_min.y, area_max.y)
			var y := _terrain.get_height_at(x, z)
			if y < rule.height_range.x or y > rule.height_range.y:
				continue

			var rot := deg_to_rad(_rng.randf_range(0.0, rule.rotation_range))
			var scale_val := 1.0 + _rng.randf_range(-rule.scale_variation, rule.scale_variation)
			var xform := Transform3D.IDENTITY
			xform = xform.rotated(Vector3.UP, rot)
			xform = xform.scaled(Vector3.ONE * scale_val)
			xform.origin = Vector3(x, y, z)
			mm.set_instance_transform(placed, xform)
			placed += 1

		mm.instance_count = placed
		mmi.multimesh = mm
		add_child(mmi)


func _scatter_individual(rule: ScatterRule, area_min: Vector2, area_max: Vector2, count: int) -> void:
	var placed := 0
	for i in range(count * 3):
		if placed >= count:
			break
		var x := _rng.randf_range(area_min.x, area_max.x)
		var z := _rng.randf_range(area_min.y, area_max.y)
		var y := _terrain.get_height_at(x, z)
		if y < rule.height_range.x or y > rule.height_range.y:
			continue

		var scene: PackedScene = rule.scenes[_rng.randi() % rule.scenes.size()]
		var instance := scene.instantiate()
		var rot := deg_to_rad(_rng.randf_range(0.0, rule.rotation_range))
		var scale_val := 1.0 + _rng.randf_range(-rule.scale_variation, rule.scale_variation)
		instance.position = Vector3(x, y, z)
		instance.rotation.y = rot
		instance.scale = Vector3.ONE * scale_val
		add_child(instance)
		placed += 1


func _find_mesh(node: Node) -> Mesh:
	if node is MeshInstance3D and node.mesh:
		return node.mesh
	for child in node.get_children():
		var m := _find_mesh(child)
		if m:
			return m
	return null
