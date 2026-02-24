## FlyButton.gd
## Styled button component with PRIMARY and SECONDARY variants.
##
## PRIMARY  – filled gold button (default, uses ThemeFactory styles)
## SECONDARY – transparent background, gold border, subdued styling
##
## Usage in code:
##   var btn = preload("res://scenes/ui/components/FlyButton.tscn").instantiate()
##   btn.label_text = "Anmelden"
##   btn.variant = FlyButton.Variant.PRIMARY
@tool
class_name FlyButton
extends Button

enum Variant { PRIMARY, SECONDARY }

@export var variant: Variant = Variant.PRIMARY:
	set(v):
		variant = v
		_apply_variant()

@export var label_text: String = "":
	set(v):
		label_text = v
		text = v


func _ready() -> void:
	text = label_text
	_apply_variant()


func _apply_variant() -> void:
	if not is_inside_tree():
		return
	match variant:
		Variant.PRIMARY:
			# Clear any local overrides — let the inherited Theme handle styling
			remove_theme_stylebox_override("normal")
			remove_theme_stylebox_override("hover")
			remove_theme_stylebox_override("pressed")
			remove_theme_stylebox_override("focus")
			remove_theme_color_override("font_color")
			remove_theme_color_override("font_hover_color")
		Variant.SECONDARY:
			var sb_normal := _make_secondary_style(Colors.GOLD_DARK)
			var sb_hover  := _make_secondary_style(Colors.GOLD)
			var sb_focus  := _make_secondary_style(Colors.GOLD_BRIGHT)
			sb_focus.border_width_top    = 2
			sb_focus.border_width_bottom = 2
			sb_focus.border_width_left   = 2
			sb_focus.border_width_right  = 2
			add_theme_stylebox_override("normal",  sb_normal)
			add_theme_stylebox_override("hover",   sb_hover)
			add_theme_stylebox_override("pressed", sb_normal)
			add_theme_stylebox_override("focus",   sb_focus)
			add_theme_color_override("font_color",       Colors.TEXT_SECONDARY)
			add_theme_color_override("font_hover_color", Colors.GOLD)


static func _make_secondary_style(border_color: Color) -> StyleBoxFlat:
	var sb        := StyleBoxFlat.new()
	sb.bg_color   = Colors.TRANSPARENT
	sb.border_color          = border_color
	sb.border_width_top      = 1
	sb.border_width_bottom   = 1
	sb.border_width_left     = 1
	sb.border_width_right    = 1
	sb.corner_radius_top_left     = 4
	sb.corner_radius_top_right    = 4
	sb.corner_radius_bottom_left  = 4
	sb.corner_radius_bottom_right = 4
	sb.content_margin_top    = 8.0
	sb.content_margin_bottom = 8.0
	sb.content_margin_left   = 14.0
	sb.content_margin_right  = 14.0
	return sb
