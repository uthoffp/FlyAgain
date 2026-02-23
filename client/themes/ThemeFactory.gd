## ThemeFactory.gd
## Builds the shared Godot Theme resource for the FlyAgain UI.
## Call ThemeFactory.create_main_theme() once and assign it to a root Control
## so all child nodes inherit the styling automatically.
class_name ThemeFactory
extends RefCounted

const _CORNER_RADIUS  := 4
const _BORDER_WIDTH   := 1
const _BTN_BORDER_W   := 1
const _FONT_SIZE_SM   := 13
const _FONT_SIZE_MD   := 15
const _FONT_SIZE_LG   := 18
const _FONT_SIZE_XL   := 28

## Creates and returns the main game theme.
static func create_main_theme() -> Theme:
	var theme := Theme.new()

	# ---- Panel / PanelContainer ----
	var panel_sb := _make_flat(Colors.BG_PANEL, Colors.BORDER_PANEL, 1, _CORNER_RADIUS)
	panel_sb.content_margin_left   = 24.0
	panel_sb.content_margin_right  = 24.0
	panel_sb.content_margin_top    = 20.0
	panel_sb.content_margin_bottom = 20.0
	theme.set_stylebox("panel", "Panel", panel_sb)
	theme.set_stylebox("panel", "PanelContainer", panel_sb)

	# ---- Button ----
	var btn_normal  := _make_flat(Colors.BTN_NORMAL,  Colors.GOLD_DARK, _BTN_BORDER_W, _CORNER_RADIUS)
	var btn_hover   := _make_flat(Colors.BTN_HOVER,   Colors.GOLD,      _BTN_BORDER_W, _CORNER_RADIUS)
	var btn_pressed := _make_flat(Colors.BTN_PRESSED,  Colors.GOLD_DARK, _BTN_BORDER_W, _CORNER_RADIUS)
	var btn_focus   := _make_flat(Colors.BTN_NORMAL,  Colors.GOLD_BRIGHT, 2, _CORNER_RADIUS)
	var btn_disabled := _make_flat(Colors.BTN_DISABLED, Colors.BORDER_DEFAULT, _BTN_BORDER_W, _CORNER_RADIUS)
	_set_content_margin(btn_normal, 8.0, 14.0)
	_set_content_margin(btn_hover, 8.0, 14.0)
	_set_content_margin(btn_pressed, 8.0, 14.0)
	_set_content_margin(btn_focus, 8.0, 14.0)
	_set_content_margin(btn_disabled, 8.0, 14.0)

	theme.set_stylebox("normal",   "Button", btn_normal)
	theme.set_stylebox("hover",    "Button", btn_hover)
	theme.set_stylebox("pressed",  "Button", btn_pressed)
	theme.set_stylebox("focus",    "Button", btn_focus)
	theme.set_stylebox("disabled", "Button", btn_disabled)
	theme.set_color("font_color",          "Button", Colors.GOLD)
	theme.set_color("font_hover_color",    "Button", Colors.GOLD_BRIGHT)
	theme.set_color("font_pressed_color",  "Button", Colors.TEXT_PRIMARY)
	theme.set_color("font_disabled_color", "Button", Colors.TEXT_SECONDARY)
	theme.set_font_size("font_size", "Button", _FONT_SIZE_MD)

	# ---- LineEdit ----
	var input_normal := _make_flat(Colors.BG_INPUT, Colors.BORDER_DEFAULT, _BORDER_WIDTH, _CORNER_RADIUS)
	var input_focus  := _make_flat(Colors.BG_INPUT, Colors.BORDER_FOCUS, 2, _CORNER_RADIUS)
	_set_content_margin(input_normal, 8.0, 10.0)
	_set_content_margin(input_focus,  8.0, 10.0)

	theme.set_stylebox("normal", "LineEdit", input_normal)
	theme.set_stylebox("focus",  "LineEdit", input_focus)
	theme.set_color("font_color",            "LineEdit", Colors.TEXT_PRIMARY)
	theme.set_color("font_placeholder_color","LineEdit", Colors.TEXT_SECONDARY)
	theme.set_color("caret_color",           "LineEdit", Colors.GOLD)
	theme.set_color("selection_color",       "LineEdit", Colors.GOLD_DARK)
	theme.set_font_size("font_size",         "LineEdit", _FONT_SIZE_MD)

	# ---- Label ----
	theme.set_color("font_color",         "Label", Colors.TEXT_PRIMARY)
	theme.set_color("font_shadow_color",  "Label", Colors.TRANSPARENT)
	theme.set_font_size("font_size",      "Label", _FONT_SIZE_MD)

	# ---- RichTextLabel ----
	theme.set_color("default_color",  "RichTextLabel", Colors.TEXT_PRIMARY)
	theme.set_font_size("normal_font_size", "RichTextLabel", _FONT_SIZE_MD)
	var rtl_panel := _make_flat(Colors.TRANSPARENT, Colors.TRANSPARENT, 0, 0)
	theme.set_stylebox("normal", "RichTextLabel", rtl_panel)
	theme.set_stylebox("focus",  "RichTextLabel", rtl_panel)

	# ---- CheckBox / CheckButton ----
	theme.set_color("font_color",       "CheckBox", Colors.TEXT_PRIMARY)
	theme.set_font_size("font_size",    "CheckBox", _FONT_SIZE_MD)

	return theme


# -- Private helpers --

static func _make_flat(
	bg: Color,
	border: Color,
	border_w: int,
	corner: int
) -> StyleBoxFlat:
	var sb := StyleBoxFlat.new()
	sb.bg_color                 = bg
	sb.border_color             = border
	sb.border_width_top         = border_w
	sb.border_width_bottom      = border_w
	sb.border_width_left        = border_w
	sb.border_width_right       = border_w
	sb.corner_radius_top_left     = corner
	sb.corner_radius_top_right    = corner
	sb.corner_radius_bottom_left  = corner
	sb.corner_radius_bottom_right = corner
	return sb


static func _set_content_margin(sb: StyleBoxFlat, v: float, h: float) -> void:
	sb.content_margin_top    = v
	sb.content_margin_bottom = v
	sb.content_margin_left   = h
	sb.content_margin_right  = h
