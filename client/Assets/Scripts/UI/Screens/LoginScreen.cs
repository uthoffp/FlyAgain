using System;
using FlyAgain.UI.Core;
using UnityEngine;
using UnityEngine.UI;
using UnityEngine.InputSystem;

namespace FlyAgain.UI.Screens
{
    /// <summary>
    /// Login screen with username and password fields.
    /// Fires OnLoginRequested when the user submits valid credentials.
    /// </summary>
    public class LoginScreen : BaseScreen
    {
        private const float BackgroundDarken = 0.7f; // 0=black, 1=full brightness

        private InputField _usernameInput;
        private InputField _passwordInput;
        private Text _errorText;

        /// <summary>
        /// Fired when the user clicks login with non-empty credentials.
        /// Parameters: (username, password).
        /// </summary>
        public event Action<string, string> OnLoginRequested;

        protected override void OnBuild()
        {
            // Load background image from Resources
            Sprite backgroundSprite = Resources.Load<Sprite>("UI/login-background");

            // Background image or dark overlay
            RectTransform overlay;
            if (backgroundSprite != null)
            {
                Debug.Log($"Login background loaded successfully: {backgroundSprite.name}, size: {backgroundSprite.rect.size}");

                // Use background image with darkening tint
                Color tint = new Color(BackgroundDarken, BackgroundDarken, BackgroundDarken, 1f);
                overlay = UIFactory.CreateImagePanel(RootTransform, "Background", backgroundSprite, tint);
            }
            else
            {
                // Fallback to solid color overlay if image not found
                Debug.LogWarning("Login background image not found at Resources/UI/login-background. Using solid color overlay.");
                overlay = UIFactory.CreatePanel(RootTransform, "Overlay", UITheme.ScreenOverlay);
            }
            UIFactory.StretchToParent(overlay);

            // Centered login window
            var window = BaseWindow.Create(RootTransform, LocalizationManager.Get("login.title"), 400, 380);

            // Make window semi-transparent
            var windowImage = window.GetComponent<Image>();
            if (windowImage != null)
            {
                Color windowColor = windowImage.color;
                windowColor.a = 0.75f; // 75% opacity
                windowImage.color = windowColor;
            }

            // Make title bar semi-transparent
            var titleBar = window.transform.Find("TitleBar");
            if (titleBar != null)
            {
                var titleBarImage = titleBar.GetComponent<Image>();
                if (titleBarImage != null)
                {
                    Color titleColor = titleBarImage.color;
                    titleColor.a = 0.85f; // 85% opacity
                    titleBarImage.color = titleColor;
                }
            }

            // Username field with label
            _usernameInput = UIFactory.CreateLabeledInputField(
                window.ContentArea,
                LocalizationManager.Get("login.username"),
                LocalizationManager.Get("login.username.placeholder"));

            UIFactory.CreateSpacer(window.ContentArea, 4);

            // Password field with label
            _passwordInput = UIFactory.CreateLabeledInputField(
                window.ContentArea,
                LocalizationManager.Get("login.password"),
                LocalizationManager.Get("login.password.placeholder"),
                isPassword: true);

            // Flexible spacer pushes button to the bottom
            UIFactory.CreateSpacer(window.ContentArea, flexibleHeight: 1);

            // Login button
            var loginButton = UIFactory.CreateButton(window.ContentArea, LocalizationManager.Get("login.button"));
            loginButton.onClick.AddListener(HandleLogin);

            UIFactory.CreateSpacer(window.ContentArea, 8);

            // Register button (secondary style)
            var registerButton = UIFactory.CreateSecondaryButton(window.ContentArea, LocalizationManager.Get("login.register"));
            registerButton.onClick.AddListener(HandleRegister);

            // Error text (hidden by default)
            _errorText = UIFactory.CreateText(
                window.ContentArea, "", UITheme.ErrorFontSize, UITheme.TextError,
                TextAnchor.MiddleCenter);
            var errorLayout = _errorText.gameObject.AddComponent<LayoutElement>();
            errorLayout.preferredHeight = 20f;
            _errorText.gameObject.SetActive(false);
        }

        protected override void OnShow()
        {
            ClearError();
            _usernameInput.text = "";
            _passwordInput.text = "";
            _usernameInput.ActivateInputField();
        }

        private void Update()
        {
            if (!IsVisible) return;

            var keyboard = Keyboard.current;
            if (keyboard == null) return;

            // Tab key navigation
            if (keyboard.tabKey.wasPressedThisFrame)
            {
                if (_usernameInput.isFocused)
                {
                    _passwordInput.ActivateInputField();
                }
                else if (_passwordInput.isFocused && (keyboard.leftShiftKey.isPressed || keyboard.rightShiftKey.isPressed))
                {
                    // Shift+Tab goes back to username
                    _usernameInput.ActivateInputField();
                }
            }

            // Enter key submits the form from either field
            if (keyboard.enterKey.wasPressedThisFrame || keyboard.numpadEnterKey.wasPressedThisFrame)
            {
                if (_usernameInput.isFocused || _passwordInput.isFocused)
                {
                    HandleLogin();
                }
            }
        }

        private void HandleLogin()
        {
            string username = _usernameInput.text.Trim();
            string password = _passwordInput.text;

            if (string.IsNullOrEmpty(username) || string.IsNullOrEmpty(password))
            {
                ShowError(LocalizationManager.Get("login.error.empty"));
                return;
            }
            else
            {
                HideError();
            }

            OnLoginRequested?.Invoke(username, password);
        }

        private void HandleRegister()
        {
            UIManager.Instance.ShowScreen<RegisterScreen>();
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

        public void HideError()
        {
            _errorText.gameObject.SetActive(false);
        }

        public void ClearError()
        {
            _errorText.text = "";
            _errorText.color = UITheme.TextError;
            _errorText.gameObject.SetActive(false);
        }
    }
}
