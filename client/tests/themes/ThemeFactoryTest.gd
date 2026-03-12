## ThemeFactoryTest.gd
## Tests for ThemeFactory: verifies the main theme is created with expected styles.
class_name ThemeFactoryTest
extends GdUnitTestSuite


var _theme: Theme


func before_test() -> void:
	_theme = ThemeFactory.create_main_theme()


# ---- Theme creation ----

func test_create_main_theme_returns_theme() -> void:
	assert_object(_theme).is_not_null()
	assert_bool(_theme is Theme).is_true()


# ---- Panel styles ----

func test_panel_stylebox_exists() -> void:
	assert_bool(_theme.has_stylebox("panel", "Panel")).is_true()


func test_panel_container_stylebox_exists() -> void:
	assert_bool(_theme.has_stylebox("panel", "PanelContainer")).is_true()


func test_panel_has_content_margins() -> void:
	var sb: StyleBoxFlat = _theme.get_stylebox("panel", "Panel")
	assert_float(sb.content_margin_left).is_greater(0.0)
	assert_float(sb.content_margin_right).is_greater(0.0)
	assert_float(sb.content_margin_top).is_greater(0.0)
	assert_float(sb.content_margin_bottom).is_greater(0.0)


# ---- Button styles ----

func test_button_all_states_exist() -> void:
	assert_bool(_theme.has_stylebox("normal", "Button")).is_true()
	assert_bool(_theme.has_stylebox("hover", "Button")).is_true()
	assert_bool(_theme.has_stylebox("pressed", "Button")).is_true()
	assert_bool(_theme.has_stylebox("focus", "Button")).is_true()
	assert_bool(_theme.has_stylebox("disabled", "Button")).is_true()


func test_button_colors_exist() -> void:
	assert_bool(_theme.has_color("font_color", "Button")).is_true()
	assert_bool(_theme.has_color("font_hover_color", "Button")).is_true()
	assert_bool(_theme.has_color("font_pressed_color", "Button")).is_true()
	assert_bool(_theme.has_color("font_disabled_color", "Button")).is_true()


func test_button_font_color_is_gold() -> void:
	var color := _theme.get_color("font_color", "Button")
	assert_float(color.r).is_equal_approx(Colors.GOLD.r, 0.01)
	assert_float(color.g).is_equal_approx(Colors.GOLD.g, 0.01)
	assert_float(color.b).is_equal_approx(Colors.GOLD.b, 0.01)


func test_button_font_size_set() -> void:
	assert_bool(_theme.has_font_size("font_size", "Button")).is_true()
	assert_int(_theme.get_font_size("font_size", "Button")).is_greater(0)


# ---- LineEdit styles ----

func test_line_edit_styles_exist() -> void:
	assert_bool(_theme.has_stylebox("normal", "LineEdit")).is_true()
	assert_bool(_theme.has_stylebox("focus", "LineEdit")).is_true()


func test_line_edit_colors_exist() -> void:
	assert_bool(_theme.has_color("font_color", "LineEdit")).is_true()
	assert_bool(_theme.has_color("font_placeholder_color", "LineEdit")).is_true()
	assert_bool(_theme.has_color("caret_color", "LineEdit")).is_true()
	assert_bool(_theme.has_color("selection_color", "LineEdit")).is_true()


func test_line_edit_caret_is_gold() -> void:
	var color := _theme.get_color("caret_color", "LineEdit")
	assert_float(color.r).is_equal_approx(Colors.GOLD.r, 0.01)
	assert_float(color.g).is_equal_approx(Colors.GOLD.g, 0.01)
	assert_float(color.b).is_equal_approx(Colors.GOLD.b, 0.01)


# ---- Label styles ----

func test_label_font_color_exists() -> void:
	assert_bool(_theme.has_color("font_color", "Label")).is_true()


func test_label_font_color_is_text_primary() -> void:
	var color := _theme.get_color("font_color", "Label")
	assert_float(color.r).is_equal_approx(Colors.TEXT_PRIMARY.r, 0.01)
	assert_float(color.g).is_equal_approx(Colors.TEXT_PRIMARY.g, 0.01)
	assert_float(color.b).is_equal_approx(Colors.TEXT_PRIMARY.b, 0.01)


# ---- RichTextLabel styles ----

func test_rich_text_label_color_exists() -> void:
	assert_bool(_theme.has_color("default_color", "RichTextLabel")).is_true()


func test_rich_text_label_styles_exist() -> void:
	assert_bool(_theme.has_stylebox("normal", "RichTextLabel")).is_true()
	assert_bool(_theme.has_stylebox("focus", "RichTextLabel")).is_true()


# ---- CheckBox styles ----

func test_checkbox_color_exists() -> void:
	assert_bool(_theme.has_color("font_color", "CheckBox")).is_true()


# ---- Consistency: all styleboxes are StyleBoxFlat ----

func test_button_normal_is_stylebox_flat() -> void:
	var sb := _theme.get_stylebox("normal", "Button")
	assert_bool(sb is StyleBoxFlat).is_true()


func test_line_edit_focus_border_wider_than_normal() -> void:
	var normal: StyleBoxFlat = _theme.get_stylebox("normal", "LineEdit")
	var focus: StyleBoxFlat = _theme.get_stylebox("focus", "LineEdit")
	assert_int(focus.border_width_top).is_greater_equal(normal.border_width_top)
