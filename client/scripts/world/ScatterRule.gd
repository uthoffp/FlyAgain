## ScatterRule.gd
## Defines placement rules for one category of scattered objects.
class_name ScatterRule
extends Resource

@export var scenes: Array[PackedScene] = []
@export var density: float = 1.0           # instances per 100m²
@export var height_range: Vector2 = Vector2(0.0, 500.0)
@export var slope_range: Vector2 = Vector2(0.0, 90.0)
@export var rotation_range: float = 360.0  # random Y-rotation degrees
@export var scale_variation: float = 0.2   # ± percentage
@export var use_multimesh: bool = false     # GPU instancing (no collision)
@export var collision_enabled: bool = false
@export var min_spacing: float = 1.0
@export var visibility_range: float = 80.0  # max render distance (0 = unlimited)
@export var visibility_fade_margin: float = 10.0  # fade-out zone before cutoff
