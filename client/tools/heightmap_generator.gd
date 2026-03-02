## heightmap_generator.gd
## Editor tool script to generate heightmap binary files.
## Run via: Godot editor -> Script tab -> Run (Ctrl+Shift+X)
##
## Generates 512x512 raw float32 binary files (.heightmap) for each zone.
## Format: 512*512 little-endian float32 values, row-major (Z outer, X inner).
@tool
extends EditorScript

const SIZE := 512
const WORLD_SIZE := 10200.0

## Zone configurations: { zone_name: { seed, amplitude, frequency, octaves, lacunarity, gain } }
const ZONE_CONFIGS := {
	"aerheim": {
		"seed": 42,
		"amplitude": 3.0,
		"frequency": 0.0008,
		"octaves": 4,
		"lacunarity": 2.0,
		"gain": 0.5,
	},
	"green_plains": {
		"seed": 137,
		"amplitude": 8.0,
		"frequency": 0.0012,
		"octaves": 5,
		"lacunarity": 2.0,
		"gain": 0.45,
	},
	"dark_forest": {
		"seed": 256,
		"amplitude": 12.0,
		"frequency": 0.0018,
		"octaves": 6,
		"lacunarity": 2.1,
		"gain": 0.5,
	},
}

func _run() -> void:
	for zone_name in ZONE_CONFIGS:
		var config: Dictionary = ZONE_CONFIGS[zone_name]
		var heights := _generate_heightmap(config)
		var path := "res://../../shared/heightmaps/%s.heightmap" % zone_name
		_save_heightmap(path, heights)
		print("Generated heightmap: %s (%d samples, amplitude=%.1f)" % [
			zone_name, heights.size(), config["amplitude"]])
	print("All heightmaps generated.")


func _generate_heightmap(config: Dictionary) -> PackedFloat32Array:
	var noise := FastNoiseLite.new()
	noise.seed = config["seed"]
	noise.noise_type = FastNoiseLite.TYPE_SIMPLEX_SMOOTH
	noise.frequency = config["frequency"]
	noise.fractal_type = FastNoiseLite.FRACTAL_FBM
	noise.fractal_octaves = config["octaves"]
	noise.fractal_lacunarity = config["lacunarity"]
	noise.fractal_gain = config["gain"]

	var heights := PackedFloat32Array()
	heights.resize(SIZE * SIZE)
	var amplitude: float = config["amplitude"]
	var cell_size: float = WORLD_SIZE / float(SIZE)

	for z in range(SIZE):
		for x in range(SIZE):
			var world_x: float = float(x) * cell_size
			var world_z: float = float(z) * cell_size
			var h: float = noise.get_noise_2d(world_x, world_z) * amplitude
			# Clamp to non-negative (terrain always at or above sea level)
			heights[z * SIZE + x] = maxf(h, 0.0)

	return heights


func _save_heightmap(path: String, heights: PackedFloat32Array) -> void:
	var abs_path := ProjectSettings.globalize_path(path)
	var file := FileAccess.open(abs_path, FileAccess.WRITE)
	if not file:
		push_error("Failed to open %s for writing: %s" % [abs_path, FileAccess.get_open_error()])
		return
	var bytes := heights.to_byte_array()
	file.store_buffer(bytes)
	file.close()
