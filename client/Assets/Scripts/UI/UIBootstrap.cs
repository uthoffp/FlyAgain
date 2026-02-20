using FlyAgain.UI.Screens;
using UnityEngine;
using UnityEngine.EventSystems;

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

            EnsureEventSystem();

            var go = new GameObject("[UISystem]");
            var manager = go.AddComponent<UIManager>();

            manager.RegisterScreen<LoginScreen>();
            manager.ShowScreen<LoginScreen>();
        }

        private static void EnsureEventSystem()
        {
            if (Object.FindAnyObjectByType<EventSystem>() != null)
                return;

            var go = new GameObject("EventSystem");
            go.AddComponent<EventSystem>();
            go.AddComponent<StandaloneInputModule>();
            Object.DontDestroyOnLoad(go);
        }
    }
}
