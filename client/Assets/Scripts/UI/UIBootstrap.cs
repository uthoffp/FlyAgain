using FlyAgain.Auth;
using FlyAgain.UI.Screens;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.InputSystem.UI;

namespace FlyAgain.UI.Core
{
    /// <summary>
    /// Auto-initializes the UI system after scene load.
    /// Creates EventSystem (if missing) and UIManager, then shows the login screen.
    /// No manual scene setup required.
    /// </summary>
    public static class UIBootstrap
    {
        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        private static void Initialize()
        {
            if (UIManager.Instance != null)
                return;

            // Initialize localization based on system language
            LocalizationManager.Initialize();

            EnsureEventSystem();

            var go = new GameObject("[UISystem]");
            var manager = go.AddComponent<UIManager>();

            manager.RegisterScreen<LoginScreen>();
            manager.RegisterScreen<RegisterScreen>();
            manager.ShowScreen<LoginScreen>();

            // Initialize authentication controller
            var authGo = new GameObject("[AuthController]");
            authGo.AddComponent<AuthController>();
        }

        private static void EnsureEventSystem()
        {
            if (Object.FindAnyObjectByType<EventSystem>() != null)
                return;

            var go = new GameObject("EventSystem");
            go.AddComponent<EventSystem>();
            go.AddComponent<InputSystemUIInputModule>();
            Object.DontDestroyOnLoad(go);
        }
    }
}
