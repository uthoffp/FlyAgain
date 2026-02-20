using UnityEngine;
using UnityEngine.UI;

namespace FlyAgain.UI.Core
{
    /// <summary>
    /// Factory for creating styled UI elements. All UI element creation should go
    /// through this class to ensure consistent styling via UITheme.
    /// </summary>
    public static class UIFactory
    {
        private static Font _defaultFont;
        private static Font DefaultFont =>
            _defaultFont ??= Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf");

        /// <summary>
        /// Create a full-screen overlay Canvas with proper scaling (1920x1080 reference).
        /// </summary>
        public static Canvas CreateScreenCanvas(string name = "UICanvas")
        {
            var go = new GameObject(name);

            var canvas = go.AddComponent<Canvas>();
            canvas.renderMode = RenderMode.ScreenSpaceOverlay;
            canvas.sortingOrder = 0;

            var scaler = go.AddComponent<CanvasScaler>();
            scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
            scaler.referenceResolution = new Vector2(1920, 1080);
            scaler.matchWidthOrHeight = 0.5f;

            go.AddComponent<GraphicRaycaster>();

            return canvas;
        }

        /// <summary>
        /// Create a colored Image panel.
        /// </summary>
        public static RectTransform CreatePanel(Transform parent, string name, Color color)
        {
            var go = new GameObject(name, typeof(RectTransform), typeof(Image));
            go.transform.SetParent(parent, false);
            go.GetComponent<Image>().color = color;
            return go.GetComponent<RectTransform>();
        }

        /// <summary>
        /// Create an Image panel with a sprite background and optional tint color.
        /// </summary>
        public static RectTransform CreateImagePanel(Transform parent, string name, Sprite sprite, Color tint)
        {
            var go = new GameObject(name, typeof(RectTransform), typeof(Image));
            go.transform.SetParent(parent, false);

            var image = go.GetComponent<Image>();
            image.sprite = sprite;
            image.color = tint;
            image.type = Image.Type.Simple;
            image.preserveAspect = false;

            return go.GetComponent<RectTransform>();
        }

        /// <summary>
        /// Create a UI Text element with consistent styling.
        /// </summary>
        public static Text CreateText(
            Transform parent,
            string text,
            float fontSize,
            Color color,
            TextAnchor alignment = TextAnchor.MiddleLeft)
        {
            var go = new GameObject("Text", typeof(RectTransform));
            go.transform.SetParent(parent, false);

            var t = go.AddComponent<Text>();
            t.text = text;
            t.font = DefaultFont;
            t.fontSize = (int)fontSize;
            t.color = color;
            t.alignment = alignment;
            t.horizontalOverflow = HorizontalWrapMode.Overflow;
            t.verticalOverflow = VerticalWrapMode.Overflow;
            t.supportRichText = false;

            return t;
        }

        /// <summary>
        /// Create a label + InputField pair. The label is placed as a sibling above the field.
        /// </summary>
        public static InputField CreateLabeledInputField(
            Transform parent,
            string label,
            string placeholder,
            bool isPassword = false)
        {
            var labelText = CreateText(parent, label, UITheme.LabelFontSize, UITheme.TextSecondary);
            var labelLayout = labelText.gameObject.AddComponent<LayoutElement>();
            labelLayout.preferredHeight = 20f;

            return CreateInputField(parent, placeholder, isPassword);
        }

        /// <summary>
        /// Create a styled InputField with placeholder.
        /// </summary>
        public static InputField CreateInputField(
            Transform parent,
            string placeholder,
            bool isPassword = false)
        {
            // Root with background Image
            var go = new GameObject("InputField", typeof(RectTransform), typeof(Image));
            go.transform.SetParent(parent, false);

            go.GetComponent<Image>().color = UITheme.InputBackground;

            var layout = go.AddComponent<LayoutElement>();
            layout.preferredHeight = UITheme.InputFieldHeight;
            layout.flexibleWidth = 1f;

            // Placeholder text
            var phGo = new GameObject("Placeholder", typeof(RectTransform));
            phGo.transform.SetParent(go.transform, false);
            SetPadded(phGo.GetComponent<RectTransform>(), 12);
            var phText = phGo.AddComponent<Text>();
            phText.text = placeholder;
            phText.font = DefaultFont;
            phText.fontSize = (int)UITheme.InputFontSize;
            phText.color = UITheme.TextPlaceholder;
            phText.alignment = TextAnchor.MiddleLeft;
            phText.horizontalOverflow = HorizontalWrapMode.Overflow;
            phText.supportRichText = false;

            // Input text
            var textGo = new GameObject("Text", typeof(RectTransform));
            textGo.transform.SetParent(go.transform, false);
            SetPadded(textGo.GetComponent<RectTransform>(), 12);
            var inputText = textGo.AddComponent<Text>();
            inputText.font = DefaultFont;
            inputText.fontSize = (int)UITheme.InputFontSize;
            inputText.color = UITheme.TextPrimary;
            inputText.alignment = TextAnchor.MiddleLeft;
            inputText.horizontalOverflow = HorizontalWrapMode.Overflow;
            inputText.supportRichText = false;

            // InputField component
            var inputField = go.AddComponent<InputField>();
            inputField.textComponent = inputText;
            inputField.placeholder = phText;
            inputField.caretColor = UITheme.TextPrimary;
            inputField.selectionColor = new Color(0.2f, 0.4f, 0.8f, 0.4f);

            if (isPassword)
            {
                inputField.contentType = InputField.ContentType.Password;
                inputField.asteriskChar = '\u25CF';
            }

            return inputField;
        }

        /// <summary>
        /// Create a styled button with centered text label.
        /// </summary>
        public static Button CreateButton(Transform parent, string text)
        {
            return CreateButtonInternal(parent, text, false);
        }

        /// <summary>
        /// Create a secondary (less prominent) button with centered text label.
        /// </summary>
        public static Button CreateSecondaryButton(Transform parent, string text)
        {
            return CreateButtonInternal(parent, text, true);
        }

        private static Button CreateButtonInternal(Transform parent, string text, bool isSecondary)
        {
            var go = new GameObject(isSecondary ? "SecondaryButton" : "Button", typeof(RectTransform), typeof(Image), typeof(Button));
            go.transform.SetParent(parent, false);

            var image = go.GetComponent<Image>();
            image.color = Color.white;
            // Note: For rounded corners, use a sprite with rounded edges and set image.sprite here
            image.type = Image.Type.Sliced;

            var button = go.GetComponent<Button>();
            button.targetGraphic = image;
            var colors = button.colors;

            if (isSecondary)
            {
                colors.normalColor = UITheme.ButtonSecondary;
                colors.highlightedColor = UITheme.ButtonSecondaryHover;
                colors.pressedColor = UITheme.ButtonSecondaryPressed;
                colors.disabledColor = UITheme.ButtonSecondaryDisabled;
            }
            else
            {
                colors.normalColor = UITheme.ButtonPrimary;
                colors.highlightedColor = UITheme.ButtonPrimaryHover;
                colors.pressedColor = UITheme.ButtonPrimaryPressed;
                colors.disabledColor = UITheme.ButtonPrimaryDisabled;
            }

            colors.fadeDuration = 0.15f;
            button.colors = colors;

            var layout = go.AddComponent<LayoutElement>();
            layout.preferredHeight = UITheme.ButtonHeight;
            layout.flexibleWidth = 1f;

            // Button label
            var textGo = new GameObject("Text", typeof(RectTransform));
            textGo.transform.SetParent(go.transform, false);
            StretchToParent(textGo.GetComponent<RectTransform>());
            var t = textGo.AddComponent<Text>();
            t.text = text;
            t.font = DefaultFont;
            t.fontSize = (int)UITheme.ButtonFontSize;
            t.color = UITheme.TextPrimary;
            t.alignment = TextAnchor.MiddleCenter;
            t.horizontalOverflow = HorizontalWrapMode.Overflow;
            t.supportRichText = false;
            t.raycastTarget = false;

            return button;
        }

        /// <summary>
        /// Add a VerticalLayoutGroup to a GameObject.
        /// childControlHeight defaults to true so LayoutElement values are respected.
        /// </summary>
        public static VerticalLayoutGroup AddVerticalLayout(
            GameObject go,
            float spacing = 0,
            RectOffset padding = null)
        {
            var vlg = go.AddComponent<VerticalLayoutGroup>();
            vlg.spacing = spacing;
            vlg.padding = padding ?? new RectOffset();
            vlg.childAlignment = TextAnchor.UpperCenter;
            vlg.childControlWidth = true;
            vlg.childControlHeight = true;
            vlg.childForceExpandWidth = true;
            vlg.childForceExpandHeight = false;
            return vlg;
        }

        /// <summary>
        /// Create an invisible spacer for layout purposes.
        /// Use preferredHeight for fixed space, flexibleHeight for remaining space.
        /// </summary>
        public static void CreateSpacer(Transform parent, float height = 0, float flexibleHeight = 0)
        {
            var go = new GameObject("Spacer", typeof(RectTransform));
            go.transform.SetParent(parent, false);
            var layout = go.AddComponent<LayoutElement>();
            if (height > 0) layout.preferredHeight = height;
            if (flexibleHeight > 0) layout.flexibleHeight = flexibleHeight;
        }

        /// <summary>
        /// Stretch a RectTransform to fill its parent completely.
        /// </summary>
        public static void StretchToParent(RectTransform rt)
        {
            rt.anchorMin = Vector2.zero;
            rt.anchorMax = Vector2.one;
            rt.offsetMin = Vector2.zero;
            rt.offsetMax = Vector2.zero;
        }

        /// <summary>
        /// Stretch to parent with horizontal padding.
        /// </summary>
        private static void SetPadded(RectTransform rt, float horizontalPadding)
        {
            rt.anchorMin = Vector2.zero;
            rt.anchorMax = Vector2.one;
            rt.offsetMin = new Vector2(horizontalPadding, 0);
            rt.offsetMax = new Vector2(-horizontalPadding, 0);
        }
    }
}
