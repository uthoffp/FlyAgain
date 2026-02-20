using System;
using FlyAgain.UI.Core;
using UnityEngine;
using UnityEngine.UI;
using UnityEngine.InputSystem;

namespace FlyAgain.UI.Screens
{
    /// <summary>
    /// Registration screen with username, email, password, and password confirmation fields.
    /// Fires OnRegisterRequested when the user submits valid registration data.
    /// </summary>
    public class RegisterScreen : BaseScreen
    {
        private const float BackgroundDarken = 0.7f; // 0=black, 1=full brightness

        private InputField _usernameInput;
        private InputField _emailInput;
        private InputField _passwordInput;
        private InputField _confirmPasswordInput;
        private Text _errorText;

        /// <summary>
        /// Fired when the user clicks register with valid data.
        /// Parameters: (username, email, password).
        /// </summary>
        public event Action<string, string, string> OnRegisterRequested;

        protected override void OnBuild()
        {
            // Load background image from Resources
            Sprite backgroundSprite = Resources.Load<Sprite>("UI/login-background");

            // Background image or dark overlay
            RectTransform overlay;
            if (backgroundSprite != null)
            {
                Debug.Log($"Register background loaded successfully: {backgroundSprite.name}, size: {backgroundSprite.rect.size}");

                // Use background image with darkening tint
                Color tint = new Color(BackgroundDarken, BackgroundDarken, BackgroundDarken, 1f);
                overlay = UIFactory.CreateImagePanel(RootTransform, "Background", backgroundSprite, tint);
            }
            else
            {
                // Fallback to solid color overlay if image not found
                Debug.LogWarning("Register background image not found at Resources/UI/login-background. Using solid color overlay.");
                overlay = UIFactory.CreatePanel(RootTransform, "Overlay", UITheme.ScreenOverlay);
            }
            UIFactory.StretchToParent(overlay);

            // Centered registration window (taller than login window)
            var window = BaseWindow.Create(RootTransform, LocalizationManager.Get("register.title"), 400, 520);

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
                LocalizationManager.Get("register.username"),
                LocalizationManager.Get("register.username.placeholder"));

            UIFactory.CreateSpacer(window.ContentArea, 4);

            // Email field with label
            _emailInput = UIFactory.CreateLabeledInputField(
                window.ContentArea,
                LocalizationManager.Get("register.email"),
                LocalizationManager.Get("register.email.placeholder"));

            UIFactory.CreateSpacer(window.ContentArea, 4);

            // Password field with label
            _passwordInput = UIFactory.CreateLabeledInputField(
                window.ContentArea,
                LocalizationManager.Get("register.password"),
                LocalizationManager.Get("register.password.placeholder"),
                isPassword: true);

            UIFactory.CreateSpacer(window.ContentArea, 4);

            // Confirm password field with label
            _confirmPasswordInput = UIFactory.CreateLabeledInputField(
                window.ContentArea,
                LocalizationManager.Get("register.confirm"),
                LocalizationManager.Get("register.confirm.placeholder"),
                isPassword: true);

            // Flexible spacer pushes buttons to the bottom
            UIFactory.CreateSpacer(window.ContentArea, flexibleHeight: 1);

            // Register button
            var registerButton = UIFactory.CreateButton(window.ContentArea, LocalizationManager.Get("register.button"));
            registerButton.onClick.AddListener(HandleRegister);

            UIFactory.CreateSpacer(window.ContentArea, 8);

            // Back to login button (secondary style)
            var backButton = UIFactory.CreateSecondaryButton(window.ContentArea, LocalizationManager.Get("register.back"));
            backButton.onClick.AddListener(HandleBackToLogin);

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
            _emailInput.text = "";
            _passwordInput.text = "";
            _confirmPasswordInput.text = "";
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
                bool isShiftPressed = keyboard.leftShiftKey.isPressed || keyboard.rightShiftKey.isPressed;

                if (_usernameInput.isFocused)
                {
                    if (isShiftPressed)
                        _confirmPasswordInput.ActivateInputField();
                    else
                        _emailInput.ActivateInputField();
                }
                else if (_emailInput.isFocused)
                {
                    if (isShiftPressed)
                        _usernameInput.ActivateInputField();
                    else
                        _passwordInput.ActivateInputField();
                }
                else if (_passwordInput.isFocused)
                {
                    if (isShiftPressed)
                        _emailInput.ActivateInputField();
                    else
                        _confirmPasswordInput.ActivateInputField();
                }
                else if (_confirmPasswordInput.isFocused)
                {
                    if (isShiftPressed)
                        _passwordInput.ActivateInputField();
                    else
                        _usernameInput.ActivateInputField();
                }
            }

            // Enter key submits the form from any field
            if (keyboard.enterKey.wasPressedThisFrame || keyboard.numpadEnterKey.wasPressedThisFrame)
            {
                if (_usernameInput.isFocused || _emailInput.isFocused ||
                    _passwordInput.isFocused || _confirmPasswordInput.isFocused)
                {
                    HandleRegister();
                }
            }

            // Escape key goes back to login
            if (keyboard.escapeKey.wasPressedThisFrame)
            {
                HandleBackToLogin();
            }
        }

        private void HandleRegister()
        {
            string username = _usernameInput.text.Trim();
            string email = _emailInput.text.Trim();
            string password = _passwordInput.text;
            string confirmPassword = _confirmPasswordInput.text;

            // Basic validation
            if (string.IsNullOrEmpty(username))
            {
                ShowError(LocalizationManager.Get("register.error.username"));
                return;
            }

            if (string.IsNullOrEmpty(email))
            {
                ShowError(LocalizationManager.Get("register.error.email"));
                return;
            }

            if (!IsValidEmail(email))
            {
                ShowError(LocalizationManager.Get("register.error.email.invalid"));
                return;
            }

            if (string.IsNullOrEmpty(password))
            {
                ShowError(LocalizationManager.Get("register.error.password"));
                return;
            }

            if (password.Length < 8)
            {
                ShowError(LocalizationManager.Get("register.error.password.length"));
                return;
            }

            if (password != confirmPassword)
            {
                ShowError(LocalizationManager.Get("register.error.password.mismatch"));
                return;
            }

            HideError();
            OnRegisterRequested?.Invoke(username, email, password);
        }

        private void HandleBackToLogin()
        {
            UIManager.Instance.ShowScreen<LoginScreen>();
        }

        private bool IsValidEmail(string email)
        {
            // Basic email validation
            if (string.IsNullOrWhiteSpace(email))
                return false;

            try
            {
                var addr = new System.Net.Mail.MailAddress(email);
                return addr.Address == email;
            }
            catch
            {
                return false;
            }
        }

        public void ShowError(string message)
        {
            _errorText.text = message;
            _errorText.gameObject.SetActive(true);
        }

        public void HideError()
        {
            _errorText.gameObject.SetActive(false);
        }

        public void ClearError()
        {
            _errorText.text = "";
            _errorText.gameObject.SetActive(false);
        }
    }
}
