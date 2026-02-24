## Colors.gd
## Central color palette for FlyAgain client.
## All UI elements reference these constants for consistent theming.
class_name Colors
extends RefCounted

# -- Backgrounds --
const BG_DARK       := Color(0.047, 0.055, 0.094)        # Main screen background
const BG_PANEL      := Color(0.082, 0.094, 0.157, 0.96)  # Panel/card background
const BG_INPUT      := Color(0.055, 0.063, 0.110)        # LineEdit background
const BG_OVERLAY    := Color(0.0, 0.0, 0.0, 0.6)         # Modal overlays

# -- Accent / Brand --
const GOLD          := Color(0.784, 0.659, 0.318)   # Primary gold accent
const GOLD_DARK     := Color(0.510, 0.420, 0.180)   # Darker gold (borders, hover)
const GOLD_BRIGHT   := Color(0.941, 0.820, 0.471)   # Bright gold (highlights)

# -- Text --
const TEXT_PRIMARY   := Color(0.910, 0.878, 0.784)  # Main readable text
const TEXT_SECONDARY := Color(0.600, 0.576, 0.502)  # Muted / secondary text
const TEXT_TITLE     := Color(0.941, 0.820, 0.471)  # Title / header text
const TEXT_ERROR     := Color(0.871, 0.329, 0.329)  # Error / warning
const TEXT_SUCCESS   := Color(0.329, 0.671, 0.400)  # Success / confirmation
const TEXT_INFO      := Color(0.471, 0.690, 0.902)  # Neutral info

# -- Borders --
const BORDER_DEFAULT := Color(0.220, 0.235, 0.380)  # Default control border
const BORDER_FOCUS   := Color(0.784, 0.659, 0.318)  # Focused input border (gold)
const BORDER_PANEL   := Color(0.290, 0.263, 0.188)  # Panel outer border

# -- Buttons --
const BTN_NORMAL     := Color(0.157, 0.141, 0.078)  # Button background
const BTN_HOVER      := Color(0.235, 0.208, 0.102)  # Hovered button
const BTN_PRESSED    := Color(0.102, 0.094, 0.051)  # Pressed button
const BTN_DISABLED   := Color(0.094, 0.094, 0.094)  # Disabled button

# -- Utility --
const TRANSPARENT    := Color(0.0, 0.0, 0.0, 0.0)
