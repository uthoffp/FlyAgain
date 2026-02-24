## InputValidatorTest.gd
## Unit tests for shared input validation rules.
class_name InputValidatorTest
extends GdUnitTestSuite


# ==== Username validation (registration: 3-16 chars, alphanumeric + hyphens) ====

func test_username_valid() -> void:
	assert_str(InputValidator.validate_username("Player1")).is_empty()
	assert_str(InputValidator.validate_username("abc")).is_empty()
	assert_str(InputValidator.validate_username("my-name-123")).is_empty()


func test_username_too_short() -> void:
	assert_str(InputValidator.validate_username("ab")).is_not_empty()
	assert_str(InputValidator.validate_username("")).is_not_empty()


func test_username_too_long() -> void:
	assert_str(InputValidator.validate_username("a".repeat(17))).is_not_empty()


func test_username_at_boundaries() -> void:
	# Exactly 3 = valid
	assert_str(InputValidator.validate_username("abc")).is_empty()
	# Exactly 16 = valid
	assert_str(InputValidator.validate_username("a".repeat(16))).is_empty()


func test_username_invalid_characters() -> void:
	assert_str(InputValidator.validate_username("user name")).is_not_empty()    # space
	assert_str(InputValidator.validate_username("user@name")).is_not_empty()    # @
	assert_str(InputValidator.validate_username("user.name")).is_not_empty()    # dot
	assert_str(InputValidator.validate_username("user_name")).is_not_empty()    # underscore
	assert_str(InputValidator.validate_username("user!")).is_not_empty()        # special char


func test_username_valid_with_hyphens() -> void:
	assert_str(InputValidator.validate_username("my-name")).is_empty()
	assert_str(InputValidator.validate_username("a-b-c")).is_empty()


# ==== Login username (only minimum length) ====

func test_login_username_valid() -> void:
	assert_str(InputValidator.validate_login_username("abc")).is_empty()
	assert_str(InputValidator.validate_login_username("a".repeat(50))).is_empty()


func test_login_username_too_short() -> void:
	assert_str(InputValidator.validate_login_username("ab")).is_not_empty()
	assert_str(InputValidator.validate_login_username("")).is_not_empty()


# ==== Email validation ====

func test_email_valid() -> void:
	assert_str(InputValidator.validate_email("user@example.com")).is_empty()
	assert_str(InputValidator.validate_email("a@b.de")).is_empty()
	assert_str(InputValidator.validate_email("user+tag@mail.co.uk")).is_empty()


func test_email_invalid_no_at() -> void:
	assert_str(InputValidator.validate_email("userexample.com")).is_not_empty()


func test_email_invalid_no_domain() -> void:
	assert_str(InputValidator.validate_email("user@")).is_not_empty()


func test_email_invalid_no_tld() -> void:
	assert_str(InputValidator.validate_email("user@host")).is_not_empty()


func test_email_invalid_empty() -> void:
	assert_str(InputValidator.validate_email("")).is_not_empty()


func test_email_invalid_spaces() -> void:
	assert_str(InputValidator.validate_email("user @example.com")).is_not_empty()
	assert_str(InputValidator.validate_email("user@ example.com")).is_not_empty()


# ==== Password validation ====

func test_password_valid() -> void:
	assert_str(InputValidator.validate_password("12345678")).is_empty()
	assert_str(InputValidator.validate_password("a very long password")).is_empty()


func test_password_too_short() -> void:
	assert_str(InputValidator.validate_password("1234567")).is_not_empty()
	assert_str(InputValidator.validate_password("")).is_not_empty()


func test_password_at_boundary() -> void:
	# Exactly 8 = valid
	assert_str(InputValidator.validate_password("12345678")).is_empty()
	# 7 = invalid
	assert_str(InputValidator.validate_password("1234567")).is_not_empty()


# ==== Password match ====

func test_password_match_identical() -> void:
	assert_str(InputValidator.validate_password_match("secret123", "secret123")).is_empty()


func test_password_match_different() -> void:
	assert_str(InputValidator.validate_password_match("secret123", "secret124")).is_not_empty()


func test_password_match_empty() -> void:
	assert_str(InputValidator.validate_password_match("", "")).is_empty()


func test_password_match_case_sensitive() -> void:
	assert_str(InputValidator.validate_password_match("Secret", "secret")).is_not_empty()


# ==== Character name validation ====

func test_char_name_valid() -> void:
	assert_str(InputValidator.validate_character_name("Krieger")).is_empty()
	assert_str(InputValidator.validate_character_name("My-Char")).is_empty()
	assert_str(InputValidator.validate_character_name("abc")).is_empty()


func test_char_name_too_short() -> void:
	assert_str(InputValidator.validate_character_name("ab")).is_not_empty()
	assert_str(InputValidator.validate_character_name("")).is_not_empty()


func test_char_name_too_long() -> void:
	assert_str(InputValidator.validate_character_name("a".repeat(17))).is_not_empty()


func test_char_name_invalid_characters() -> void:
	assert_str(InputValidator.validate_character_name("my char")).is_not_empty()    # space
	assert_str(InputValidator.validate_character_name("char@1")).is_not_empty()     # @
	assert_str(InputValidator.validate_character_name("char_1")).is_not_empty()     # underscore


func test_char_name_at_boundaries() -> void:
	assert_str(InputValidator.validate_character_name("abc")).is_empty()             # 3
	assert_str(InputValidator.validate_character_name("a".repeat(16))).is_empty()   # 16


# ==== Class selection validation ====

func test_class_selection_valid() -> void:
	assert_str(InputValidator.validate_class_selection("Krieger")).is_empty()
	assert_str(InputValidator.validate_class_selection("Magier")).is_empty()


func test_class_selection_empty() -> void:
	assert_str(InputValidator.validate_class_selection("")).is_not_empty()
