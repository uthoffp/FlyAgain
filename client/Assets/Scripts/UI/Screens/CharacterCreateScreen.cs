using System;
using FlyAgain.UI.Core;
using UnityEngine;
using UnityEngine.UI;
using UnityEngine.InputSystem;

namespace FlyAgain.UI.Screens
{
    /// <summary>
    /// Character creation screen with name input and class selection.
    /// Validates input and fires OnCharacterCreateRequested event when complete.
    /// </summary>
    public class CharacterCreateScreen : BaseScreen
    {
        private const float BackgroundDarken = 0.7f;

        private InputField _nameInput;
        private Toggle[] _classToggles = new Toggle[4];
        private string _selectedClass = "krieger";  // Default to Krieger
        private Text _errorText;
        private Button _createButton;
        private Button _backButton;

        public event Action<string, string> OnCharacterCreateRequested;  // (name, class)
        public event Action OnBackRequested;

        protected override void OnBuild()
        {
            // Background with same style as LoginScreen
            Sprite backgroundSprite = Resources.Load<Sprite>("UI/login-background");
            RectTransform overlay;
            if (backgroundSprite != null)
            {
                Color tint = new Color(BackgroundDarken, BackgroundDarken, BackgroundDarken, 1f);
                overlay = UIFactory.CreateImagePanel(RootTransform, "Background", backgroundSprite, tint);
            }
            else
            {
                overlay = UIFactory.CreatePanel(RootTransform, "Overlay", UITheme.ScreenOverlay);
            }
            UIFactory.StretchToParent(overlay);

            // Window with semi-transparent background
            var window = BaseWindow.Create(RootTransform, LocalizationManager.Get("character.create.title"), 450, 620);
            var windowImage = window.GetComponent<Image>();
            if (windowImage != null)
            {
                Color windowColor = windowImage.color;
                windowColor.a = 0.75f;
                windowImage.color = windowColor;
            }

            // Name input field
            _nameInput = UIFactory.CreateLabeledInputField(
                window.ContentArea,
                LocalizationManager.Get("character.create.name"),
                LocalizationManager.Get("character.create.name.placeholder"));
            _nameInput.characterLimit = 16;

            UIFactory.CreateSpacer(window.ContentArea, 12);

            // Class selection label
            var classLabel = UIFactory.CreateText(
                window.ContentArea,
                LocalizationManager.Get("character.create.class"),
                UITheme.LabelFontSize,
                UITheme.TextSecondary);
            var classLabelLayout = classLabel.gameObject.AddComponent<LayoutElement>();
            classLabelLayout.preferredHeight = 20f;

            UIFactory.CreateSpacer(window.ContentArea, 4);

            // Create toggle group for radio button behavior
            var toggleGroupGo = new GameObject("ClassToggleGroup");
            toggleGroupGo.transform.SetParent(window.ContentArea, false);
            var toggleGroup = toggleGroupGo.AddComponent<ToggleGroup>();
            var toggleGroupLayout = UIFactory.AddVerticalLayout(toggleGroupGo.GetComponent<RectTransform>(), 4, 4);
            var toggleGroupLayoutElement = toggleGroupGo.AddComponent<LayoutElement>();
            toggleGroupLayoutElement.preferredHeight = 240f;

            // Create 4 class toggles
            CreateClassToggle(toggleGroup, "krieger", 0);
            CreateClassToggle(toggleGroup, "magier", 1);
            CreateClassToggle(toggleGroup, "assassine", 2);
            CreateClassToggle(toggleGroup, "kleriker", 3);

            // Set first toggle active by default
            _classToggles[0].isOn = true;

            // Flexible spacer
            UIFactory.CreateSpacer(window.ContentArea, flexibleHeight: 1);

            // Create button
            _createButton = UIFactory.CreateButton(window.ContentArea, LocalizationManager.Get("character.create.button"));
            _createButton.onClick.AddListener(HandleCreate);

            UIFactory.CreateSpacer(window.ContentArea, 8);

            // Back button
            _backButton = UIFactory.CreateSecondaryButton(window.ContentArea, LocalizationManager.Get("character.create.back"));
            _backButton.onClick.AddListener(HandleBack);

            // Error text
            _errorText = UIFactory.CreateText(
                window.ContentArea, "", UITheme.ErrorFontSize, UITheme.TextError,
                TextAnchor.MiddleCenter);
            var errorLayout = _errorText.gameObject.AddComponent<LayoutElement>();
            errorLayout.preferredHeight = 20f;
            _errorText.gameObject.SetActive(false);
        }

        private void CreateClassToggle(ToggleGroup toggleGroup, string className, int index)
        {
            // Create toggle container
            var toggleGo = new GameObject($"Toggle_{className}");
            toggleGo.transform.SetParent(toggleGroup.transform, false);

            var toggleBg = toggleGo.AddComponent<Image>();
            toggleBg.color = new Color(0.15f, 0.15f, 0.15f, 0.9f);

            var toggleRect = toggleGo.GetComponent<RectTransform>();
            var toggleLayout = toggleGo.AddComponent<LayoutElement>();
            toggleLayout.preferredHeight = 56f;
            toggleLayout.minHeight = 56f;

            // Add vertical layout for content
            var verticalLayout = UIFactory.AddVerticalLayout(toggleRect, 8, 8);

            // Create toggle component
            var toggle = toggleGo.AddComponent<Toggle>();
            toggle.group = toggleGroup;
            toggle.transition = Selectable.Transition.ColorTint;
            var colors = toggle.colors;
            colors.normalColor = new Color(0.15f, 0.15f, 0.15f, 1f);
            colors.highlightedColor = new Color(0.2f, 0.2f, 0.25f, 1f);
            colors.pressedColor = new Color(0.1f, 0.1f, 0.15f, 1f);
            colors.selectedColor = new Color(0.2f, 0.3f, 0.4f, 1f);
            toggle.colors = colors;
            toggle.targetGraphic = toggleBg;

            // Class name text
            var nameText = UIFactory.CreateText(
                toggleRect,
                LocalizationManager.Get($"class.{className}"),
                UITheme.LabelFontSize + 2,  // Slightly larger
                UITheme.TextPrimary,
                TextAnchor.MiddleLeft);
            nameText.fontStyle = FontStyle.Bold;
            var nameLayout = nameText.gameObject.AddComponent<LayoutElement>();
            nameLayout.preferredHeight = 18f;

            // Class description text
            var descText = UIFactory.CreateText(
                toggleRect,
                LocalizationManager.Get($"class.{className}.desc"),
                UITheme.ErrorFontSize,  // Smaller font
                UITheme.TextSecondary,
                TextAnchor.MiddleLeft);
            var descLayout = descText.gameObject.AddComponent<LayoutElement>();
            descLayout.preferredHeight = 16f;

            // Store toggle and set up listener
            _classToggles[index] = toggle;
            toggle.onValueChanged.AddListener((isOn) => {
                if (isOn) _selectedClass = className;
            });
        }

        protected override void OnShow()
        {
            ClearError();
            _nameInput.text = "";
            _selectedClass = "krieger";
            _classToggles[0].isOn = true;
            _nameInput.ActivateInputField();
        }

        private void Update()
        {
            if (!IsVisible) return;

            var keyboard = Keyboard.current;
            if (keyboard == null) return;

            // Enter submits form
            if (keyboard.enterKey.wasPressedThisFrame || keyboard.numpadEnterKey.wasPressedThisFrame)
            {
                if (_nameInput.isFocused)
                {
                    HandleCreate();
                }
            }

            // Escape goes back
            if (keyboard.escapeKey.wasPressedThisFrame)
            {
                HandleBack();
            }
        }

        private void HandleCreate()
        {
            string name = _nameInput.text.Trim();

            // Validate name
            if (string.IsNullOrEmpty(name))
            {
                ShowError(LocalizationManager.Get("character.error.name_empty"));
                return;
            }

            if (name.Length < 2 || name.Length > 16)
            {
                ShowError(LocalizationManager.Get("character.error.name_length"));
                return;
            }

            if (string.IsNullOrEmpty(_selectedClass))
            {
                ShowError(LocalizationManager.Get("character.error.class_empty"));
                return;
            }

            ClearError();
            OnCharacterCreateRequested?.Invoke(name, _selectedClass);
        }

        private void HandleBack()
        {
            OnBackRequested?.Invoke();
        }

        public void ShowError(string message)
        {
            _errorText.text = message;
            _errorText.color = UITheme.TextError;
            _errorText.gameObject.SetActive(true);
        }

        public void ShowSuccess(string message)
        {
            _errorText.text = message;
            _errorText.color = UITheme.TextSuccess;
            _errorText.gameObject.SetActive(true);
        }

        public void ClearError()
        {
            _errorText.text = "";
            _errorText.gameObject.SetActive(false);
        }
    }
}
