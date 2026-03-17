## LevelUpEffect.gd
## Self-destructing floating "Level Up!" text for the HUD.
## Spawned center-screen on level-up, animates with scale pop + float + fade.
extends Label


const DISPLAY_DURATION := 3.0
const FLOAT_SPEED := 30.0  # pixels per second upward


var _elapsed: float = 0.0


func _ready() -> void:
	text = tr("LEVEL_UP")
	horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	add_theme_font_size_override("font_size", 36)
	add_theme_color_override("font_color", Colors.GOLD_BRIGHT)
	mouse_filter = Control.MOUSE_FILTER_IGNORE

	# Start invisible, then animate in
	modulate.a = 0.0
	pivot_offset = size / 2.0
	scale = Vector2(0.5, 0.5)

	# Scale pop-in
	var scale_tw := create_tween()
	scale_tw.tween_property(self, "scale", Vector2(1.2, 1.2), 0.3) \
		.set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	scale_tw.tween_property(self, "scale", Vector2(1.0, 1.0), 0.2)

	# Fade in
	var fade_tw := create_tween()
	fade_tw.tween_property(self, "modulate:a", 1.0, 0.2)


func _process(delta: float) -> void:
	_elapsed += delta
	position.y -= FLOAT_SPEED * delta

	# Fade out in the last 40% of display time
	if _elapsed > DISPLAY_DURATION * 0.6:
		var fade_progress := (_elapsed - DISPLAY_DURATION * 0.6) / (DISPLAY_DURATION * 0.4)
		modulate.a = 1.0 - clampf(fade_progress, 0.0, 1.0)

	if _elapsed >= DISPLAY_DURATION:
		queue_free()
