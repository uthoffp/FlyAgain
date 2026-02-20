using UnityEngine;

namespace FlyAgain.UI.Core
{
    /// <summary>
    /// Centralized styling constants for all UI elements.
    /// Modify values here to update the look of the entire UI.
    /// </summary>
    public static class UITheme
    {
        // Window
        public static readonly Color WindowBackground = new(0.11f, 0.12f, 0.18f, 0.97f);
        public static readonly Color TitleBarBackground = new(0.08f, 0.08f, 0.13f, 1f);
        public static readonly Color ScreenOverlay = new(0f, 0f, 0f, 0.6f);

        // Input
        public static readonly Color InputBackground = new(0.15f, 0.16f, 0.22f, 1f);

        // Buttons
        public static readonly Color ButtonPrimary = new(0.22f, 0.45f, 0.85f, 1f);
        public static readonly Color ButtonPrimaryHover = new(0.28f, 0.52f, 0.95f, 1f);
        public static readonly Color ButtonPrimaryPressed = new(0.17f, 0.38f, 0.72f, 1f);
        public static readonly Color ButtonPrimaryDisabled = new(0.3f, 0.3f, 0.4f, 1f);

        // Text
        public static readonly Color TextPrimary = new(0.92f, 0.93f, 0.96f, 1f);
        public static readonly Color TextSecondary = new(0.6f, 0.62f, 0.7f, 1f);
        public static readonly Color TextPlaceholder = new(0.4f, 0.42f, 0.5f, 1f);
        public static readonly Color TextError = new(0.92f, 0.3f, 0.3f, 1f);

        // Font sizes
        public const float TitleFontSize = 20f;
        public const float LabelFontSize = 14f;
        public const float InputFontSize = 16f;
        public const float ButtonFontSize = 17f;
        public const float ErrorFontSize = 13f;

        // Layout
        public const float WindowPadding = 24f;
        public const float ElementSpacing = 8f;
        public const float TitleBarHeight = 44f;
        public const float InputFieldHeight = 42f;
        public const float ButtonHeight = 44f;
    }
}
