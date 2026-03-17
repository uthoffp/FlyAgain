## NotificationStack.gd
## Right-aligned notification stack for XP gains, gold, and future item drops.
## Shows floating text labels that auto-fade and remove themselves.
extends VBoxContainer


const MAX_VISIBLE := 5
const NOTIFICATION_LIFETIME := 3.0


func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_theme_constant_override("separation", 4)


## Adds a notification label that fades in, stays visible, then fades out and self-destructs.
func show_notification(text: String, color: Color = Colors.GOLD) -> void:
	# Remove oldest if at capacity
	while get_child_count() >= MAX_VISIBLE:
		var oldest := get_child(0)
		oldest.queue_free()

	var label := Label.new()
	label.text = text
	label.add_theme_font_size_override("font_size", 16)
	label.add_theme_color_override("font_color", color)
	label.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	label.modulate.a = 0.0
	add_child(label)

	# Fade in
	var fade_in := create_tween()
	fade_in.tween_property(label, "modulate:a", 1.0, 0.2)

	# Fade out after lifetime, then remove
	var fade_out := create_tween()
	fade_out.tween_interval(NOTIFICATION_LIFETIME - 0.5)
	fade_out.tween_property(label, "modulate:a", 0.0, 0.5)
	fade_out.tween_callback(label.queue_free)
