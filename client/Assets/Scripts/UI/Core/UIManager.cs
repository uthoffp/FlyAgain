using System;
using System.Collections.Generic;
using UnityEngine;

namespace FlyAgain.UI.Core
{
    /// <summary>
    /// Singleton that manages UI screens. Creates the Canvas and handles
    /// screen registration, transitions, and lifecycle.
    /// </summary>
    public class UIManager : MonoBehaviour
    {
        public static UIManager Instance { get; private set; }

        private Canvas _canvas;
        private readonly Dictionary<Type, BaseScreen> _screens = new();

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }

            Instance = this;
            DontDestroyOnLoad(gameObject);

            _canvas = UIFactory.CreateScreenCanvas("UICanvas");
            _canvas.transform.SetParent(transform, false);
        }

        /// <summary>
        /// Register a screen type. Creates the screen GameObject and calls Initialize().
        /// </summary>
        public T RegisterScreen<T>() where T : BaseScreen
        {
            var go = new GameObject(typeof(T).Name, typeof(RectTransform));
            go.transform.SetParent(_canvas.transform, false);

            var screen = go.AddComponent<T>();
            screen.Initialize();

            _screens[typeof(T)] = screen;
            return screen;
        }

        /// <summary>
        /// Hide all screens, then show the requested one.
        /// </summary>
        public void ShowScreen<T>() where T : BaseScreen
        {
            foreach (var screen in _screens.Values)
                screen.Hide();

            if (_screens.TryGetValue(typeof(T), out var target))
                target.Show();
            else
                Debug.LogWarning($"[UIManager] Screen {typeof(T).Name} not registered");
        }

        public void HideAllScreens()
        {
            foreach (var screen in _screens.Values)
                screen.Hide();
        }

        public T GetScreen<T>() where T : BaseScreen
        {
            return _screens.TryGetValue(typeof(T), out var screen) ? (T)screen : null;
        }

        private void OnDestroy()
        {
            if (Instance == this)
                Instance = null;
        }
    }
}
