## ColorsTest.gd
## Tests for Colors: validates color constants are well-formed and consistent.
class_name ColorsTest
extends GdUnitTestSuite


# ---- Background colors have appropriate alpha ----

func test_bg_dark_is_opaque() -> void:
	assert_float(Colors.BG_DARK.a).is_equal(1.0)


func test_bg_panel_is_nearly_opaque() -> void:
	assert_float(Colors.BG_PANEL.a).is_greater(0.9)


func test_bg_overlay_is_semi_transparent() -> void:
	assert_float(Colors.BG_OVERLAY.a).is_less(1.0)
	assert_float(Colors.BG_OVERLAY.a).is_greater(0.0)


# ---- Gold accent variants form a brightness gradient ----

func test_gold_variants_form_gradient() -> void:
	# GOLD_DARK < GOLD < GOLD_BRIGHT in luminance
	var dark_lum := Colors.GOLD_DARK.get_luminance()
	var gold_lum := Colors.GOLD.get_luminance()
	var bright_lum := Colors.GOLD_BRIGHT.get_luminance()
	assert_float(dark_lum).is_less(gold_lum)
	assert_float(gold_lum).is_less(bright_lum)


# ---- Text colors are distinct ----

func test_text_primary_brighter_than_secondary() -> void:
	assert_float(Colors.TEXT_PRIMARY.get_luminance()).is_greater(
		Colors.TEXT_SECONDARY.get_luminance())


func test_error_color_is_reddish() -> void:
	assert_float(Colors.TEXT_ERROR.r).is_greater(Colors.TEXT_ERROR.g)
	assert_float(Colors.TEXT_ERROR.r).is_greater(Colors.TEXT_ERROR.b)


func test_success_color_is_greenish() -> void:
	assert_float(Colors.TEXT_SUCCESS.g).is_greater(Colors.TEXT_SUCCESS.r)
	assert_float(Colors.TEXT_SUCCESS.g).is_greater(Colors.TEXT_SUCCESS.b)


func test_info_color_is_bluish() -> void:
	assert_float(Colors.TEXT_INFO.b).is_greater(Colors.TEXT_INFO.r)
	assert_float(Colors.TEXT_INFO.b).is_greater(Colors.TEXT_INFO.g)


# ---- Button states form a brightness sequence ----

func test_button_hover_brighter_than_normal() -> void:
	assert_float(Colors.BTN_HOVER.get_luminance()).is_greater(
		Colors.BTN_NORMAL.get_luminance())


func test_button_pressed_darker_than_normal() -> void:
	assert_float(Colors.BTN_PRESSED.get_luminance()).is_less(
		Colors.BTN_NORMAL.get_luminance())


# ---- Border focus matches gold accent ----

func test_border_focus_is_gold() -> void:
	assert_float(Colors.BORDER_FOCUS.r).is_equal_approx(Colors.GOLD.r, 0.01)
	assert_float(Colors.BORDER_FOCUS.g).is_equal_approx(Colors.GOLD.g, 0.01)
	assert_float(Colors.BORDER_FOCUS.b).is_equal_approx(Colors.GOLD.b, 0.01)


# ---- Transparent is fully transparent ----

func test_transparent_is_zero_alpha() -> void:
	assert_float(Colors.TRANSPARENT.a).is_equal(0.0)


# ---- All colors have valid component ranges [0,1] ----

func test_all_colors_in_valid_range() -> void:
	var all_colors := [
		Colors.BG_DARK, Colors.BG_PANEL, Colors.BG_INPUT, Colors.BG_OVERLAY,
		Colors.GOLD, Colors.GOLD_DARK, Colors.GOLD_BRIGHT,
		Colors.TEXT_PRIMARY, Colors.TEXT_SECONDARY, Colors.TEXT_TITLE,
		Colors.TEXT_ERROR, Colors.TEXT_SUCCESS, Colors.TEXT_INFO,
		Colors.BORDER_DEFAULT, Colors.BORDER_FOCUS, Colors.BORDER_PANEL,
		Colors.BTN_NORMAL, Colors.BTN_HOVER, Colors.BTN_PRESSED, Colors.BTN_DISABLED,
		Colors.TRANSPARENT,
	]
	for c: Color in all_colors:
		assert_float(c.r).is_greater_equal(0.0)
		assert_float(c.r).is_less_equal(1.0)
		assert_float(c.g).is_greater_equal(0.0)
		assert_float(c.g).is_less_equal(1.0)
		assert_float(c.b).is_greater_equal(0.0)
		assert_float(c.b).is_less_equal(1.0)
		assert_float(c.a).is_greater_equal(0.0)
		assert_float(c.a).is_less_equal(1.0)
