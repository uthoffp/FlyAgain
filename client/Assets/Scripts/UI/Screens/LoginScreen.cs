using System;
using FlyAgain.UI.Core;
using UnityEngine;
using UnityEngine.UI;

namespace FlyAgain.UI.Screens
{
    /// <summary>
    /// Login screen with username and password fields.
    /// Fires OnLoginRequested when the user submits valid credentials.
    /// </summary>
    public class LoginScreen : BaseScreen
    {
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
            // Dark overlay behind the window
            var overlay = UIFactory.CreatePanel(RootTransform, "Overlay", UITheme.ScreenOverlay);
            UIFactory.StretchToParent(overlay);

            // Centered login window
            var window = BaseWindow.Create(RootTransform, "FlyAgain", 400, 380);

            // Username field with label
            _usernameInput = UIFactory.CreateLabeledInputField(
                window.ContentArea, "Benutzername", "Benutzername eingeben...");

            UIFactory.CreateSpacer(window.ContentArea, 4);

            // Password field with label
            _passwordInput = UIFactory.CreateLabeledInputField(
                window.ContentArea, "Passwort", "Passwort eingeben...", isPassword: true);

            // Flexible spacer pushes button to the bottom
            UIFactory.CreateSpacer(window.ContentArea, flexibleHeight: 1);

            // Login button
            var loginButton = UIFactory.CreateButton(window.ContentArea, "Anmelden");
            loginButton.onClick.AddListener(HandleLogin);

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

        private void HandleLogin()
        {
            string username = _usernameInput.text.Trim();
            string password = _passwordInput.text;

            if (string.IsNullOrEmpty(username) || string.IsNullOrEmpty(password))
            {
                ShowError("Bitte Benutzername und Passwort eingeben.");
                return;
            }

            OnLoginRequested?.Invoke(username, password);
        }

        public void ShowError(string message)
        {
            _errorText.text = message;
            _errorText.gameObject.SetActive(true);
        }

        public void ClearError()
        {
            _errorText.text = "";
            _errorText.gameObject.SetActive(false);
        }
    }
}
