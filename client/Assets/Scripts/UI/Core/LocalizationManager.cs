using System.Collections.Generic;
using UnityEngine;

namespace FlyAgain.UI.Core
{
    /// <summary>
    /// Simple localization system that loads strings based on system language.
    /// Supports German and English with English as fallback.
    /// </summary>
    public static class LocalizationManager
    {
        private static Dictionary<string, string> _strings;
        private static SystemLanguage _currentLanguage;

        public static void Initialize()
        {
            _currentLanguage = Application.systemLanguage;
            LoadStrings(_currentLanguage);
            Debug.Log($"[LocalizationManager] Initialized with language: {_currentLanguage}");
        }

        /// <summary>
        /// Get a localized string by key. Returns the key itself if not found.
        /// </summary>
        public static string Get(string key)
        {
            if (_strings == null)
            {
                Debug.LogWarning("[LocalizationManager] Not initialized, using English as fallback");
                LoadStrings(SystemLanguage.English);
            }

            return _strings.TryGetValue(key, out var value) ? value : key;
        }

        /// <summary>
        /// Get the current language being used.
        /// </summary>
        public static SystemLanguage CurrentLanguage => _currentLanguage;

        private static void LoadStrings(SystemLanguage language)
        {
            _strings = language switch
            {
                SystemLanguage.German => GetGermanStrings(),
                SystemLanguage.English => GetEnglishStrings(),
                _ => GetEnglishStrings() // Default fallback for all other languages
            };
        }

        private static Dictionary<string, string> GetGermanStrings()
        {
            return new Dictionary<string, string>
            {
                // Login Screen
                { "login.title", "Anmeldung" },
                { "login.username", "Benutzername" },
                { "login.username.placeholder", "Benutzername eingeben..." },
                { "login.password", "Passwort" },
                { "login.password.placeholder", "Passwort eingeben..." },
                { "login.button", "Anmelden" },
                { "login.register", "Registrieren" },
                { "login.error.empty", "Bitte Benutzername und Passwort eingeben." },
                { "login.error.connection", "Verbindung zum Server fehlgeschlagen." },
                { "login.error.failed", "Anmeldung fehlgeschlagen. Bitte überprüfe deine Anmeldedaten." },
                { "login.success", "Anmeldung erfolgreich!" },

                // Register Screen
                { "register.title", "Konto erstellen" },
                { "register.username", "Benutzername" },
                { "register.username.placeholder", "Benutzername eingeben..." },
                { "register.email", "E-Mail" },
                { "register.email.placeholder", "E-Mail eingeben..." },
                { "register.password", "Passwort" },
                { "register.password.placeholder", "Passwort eingeben..." },
                { "register.confirm", "Passwort bestätigen" },
                { "register.confirm.placeholder", "Passwort erneut eingeben..." },
                { "register.button", "Registrieren" },
                { "register.back", "Zurück zur Anmeldung" },
                { "register.error.username", "Bitte Benutzername eingeben." },
                { "register.error.email", "Bitte E-Mail eingeben." },
                { "register.error.email.invalid", "Bitte gültige E-Mail eingeben." },
                { "register.error.password", "Bitte Passwort eingeben." },
                { "register.error.password.length", "Passwort muss mindestens 8 Zeichen lang sein." },
                { "register.error.password.mismatch", "Passwörter stimmen nicht überein." },
                { "register.error.connection", "Verbindung zum Server fehlgeschlagen." },
                { "register.error.failed", "Registrierung fehlgeschlagen." },
                { "register.success", "Registrierung erfolgreich! Du kannst dich jetzt anmelden." },
            };
        }

        private static Dictionary<string, string> GetEnglishStrings()
        {
            return new Dictionary<string, string>
            {
                // Login Screen
                { "login.title", "Login" },
                { "login.username", "Username" },
                { "login.username.placeholder", "Enter username..." },
                { "login.password", "Password" },
                { "login.password.placeholder", "Enter password..." },
                { "login.button", "Login" },
                { "login.register", "Register" },
                { "login.error.empty", "Please enter username and password." },
                { "login.error.connection", "Failed to connect to server." },
                { "login.error.failed", "Login failed. Please check your credentials." },
                { "login.success", "Login successful!" },

                // Register Screen
                { "register.title", "Create Account" },
                { "register.username", "Username" },
                { "register.username.placeholder", "Enter username..." },
                { "register.email", "Email" },
                { "register.email.placeholder", "Enter email..." },
                { "register.password", "Password" },
                { "register.password.placeholder", "Enter password..." },
                { "register.confirm", "Confirm Password" },
                { "register.confirm.placeholder", "Re-enter password..." },
                { "register.button", "Register" },
                { "register.back", "Back to Login" },
                { "register.error.username", "Please enter a username." },
                { "register.error.email", "Please enter an email." },
                { "register.error.email.invalid", "Please enter a valid email." },
                { "register.error.password", "Please enter a password." },
                { "register.error.password.length", "Password must be at least 8 characters long." },
                { "register.error.password.mismatch", "Passwords do not match." },
                { "register.error.connection", "Failed to connect to server." },
                { "register.error.failed", "Registration failed." },
                { "register.success", "Registration successful! You can now log in." },
            };
        }
    }
}
