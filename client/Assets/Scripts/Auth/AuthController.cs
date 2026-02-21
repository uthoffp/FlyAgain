using System;
using FlyAgain.Network;
using FlyAgain.Proto;
using FlyAgain.UI.Core;
using FlyAgain.UI.Screens;
using Google.Protobuf;
using UnityEngine;

namespace FlyAgain.Auth
{
    /// <summary>
    /// Manages authentication flow: network connection, login, and registration.
    /// Bridges UI screens with NetworkManager and handles auth-related packet responses.
    /// </summary>
    public class AuthController : MonoBehaviour
    {
        public static AuthController Instance { get; private set; }

        [Header("Connection Settings")]
        [SerializeField] private float _connectionTimeout = 5f;
        [SerializeField] private bool _autoReconnectOnLogin = true;

        private NetworkManager _network;
        private LoginScreen _loginScreen;
        private RegisterScreen _registerScreen;
        private bool _isSubscribedToScreenEvents;
        private bool _isWaitingForLoginResponse;
        private float _loginRequestTime;

        private void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = this;
            DontDestroyOnLoad(gameObject);
        }

        private void Start()
        {
            InitializeNetwork();
            RegisterPacketHandlers();
            // Defer screen subscription to allow UIManager to initialize
            StartCoroutine(SubscribeToUIEventsDeferred());
        }

        private void Update()
        {
            // Monitor login timeout
            if (_isWaitingForLoginResponse && Time.time - _loginRequestTime > _connectionTimeout)
            {
                _isWaitingForLoginResponse = false;
                _loginScreen?.ShowError(LocalizationManager.Get("login.error.connection"));
                Debug.LogError("[AuthController] Login request timed out");
            }
        }

        private System.Collections.IEnumerator SubscribeToUIEventsDeferred()
        {
            // Wait one frame to ensure UIManager has registered all screens
            yield return null;
            SubscribeToUIEvents();
        }

        private void InitializeNetwork()
        {
            // Create NetworkManager GameObject if it doesn't exist
            if (NetworkManager.Instance == null)
            {
                var networkGo = new GameObject("[NetworkManager]");
                _network = networkGo.AddComponent<NetworkManager>();
            }
            else
            {
                _network = NetworkManager.Instance;
            }
        }

        private void RegisterPacketHandlers()
        {
            var handler = _network.PacketHandler;
            handler.Register((int)Opcode.LoginResponse, HandleLoginResponse);
            handler.Register((int)Opcode.RegisterResponse, HandleRegisterResponse);
        }

        private void SubscribeToUIEvents()
        {
            if (_isSubscribedToScreenEvents)
                return;

            var uiManager = UIManager.Instance;
            if (uiManager == null)
            {
                Debug.LogWarning("[AuthController] UIManager not available yet, deferring event subscription");
                return;
            }

            // Subscribe to login screen events
            _loginScreen = uiManager.GetScreen<LoginScreen>();
            if (_loginScreen != null)
            {
                _loginScreen.OnLoginRequested -= HandleLoginRequest; // Prevent duplicate subscriptions
                _loginScreen.OnLoginRequested += HandleLoginRequest;
                Debug.Log("[AuthController] Subscribed to LoginScreen events");
            }
            else
            {
                Debug.LogWarning("[AuthController] LoginScreen not found");
            }

            // Subscribe to register screen events
            _registerScreen = uiManager.GetScreen<RegisterScreen>();
            if (_registerScreen != null)
            {
                _registerScreen.OnRegisterRequested -= HandleRegisterRequest; // Prevent duplicate subscriptions
                _registerScreen.OnRegisterRequested += HandleRegisterRequest;
                Debug.Log("[AuthController] Subscribed to RegisterScreen events");
            }
            else
            {
                Debug.LogWarning("[AuthController] RegisterScreen not found");
            }

            _isSubscribedToScreenEvents = true;
        }

        private void HandleLoginRequest(string username, string password)
        {
            Debug.Log($"[AuthController] Login requested for user: {username}");

            // Prevent multiple simultaneous login requests
            if (_isWaitingForLoginResponse)
            {
                Debug.LogWarning("[AuthController] Login already in progress");
                return;
            }

            // Connect to login server if not already connected
            if (_network.State != ConnectionState.Connected)
            {
                _loginScreen?.ShowError(LocalizationManager.Get("login.error.connection"));
                if (!_network.ConnectToLogin())
                {
                    Debug.LogError("[AuthController] Failed to connect to login server");
                    return;
                }
            }

            try
            {
                // Send login request
                var request = new LoginRequest
                {
                    Username = username,
                    Password = password
                };

                _network.SendTcp((int)Opcode.LoginRequest, request);
                _isWaitingForLoginResponse = true;
                _loginRequestTime = Time.time;
                Debug.Log("[AuthController] Login request sent");
            }
            catch (Exception e)
            {
                _isWaitingForLoginResponse = false;
                _loginScreen?.ShowError(LocalizationManager.Get("login.error.connection"));
                Debug.LogError($"[AuthController] Failed to send login request: {e.Message}");
            }
        }

        private void HandleRegisterRequest(string username, string email, string password)
        {
            Debug.Log($"[AuthController] Registration requested for user: {username}, email: {email}");

            // Connect to login server if not already connected
            if (_network.State != ConnectionState.Connected)
            {
                _registerScreen?.ShowError(LocalizationManager.Get("register.error.connection"));
                if (!_network.ConnectToLogin())
                {
                    Debug.LogError("[AuthController] Failed to connect to login server");
                    return;
                }
            }

            try
            {
                // Send registration request
                var request = new RegisterRequest
                {
                    Username = username,
                    Email = email,
                    Password = password
                };

                _network.SendTcp((int)Opcode.RegisterRequest, request);
                Debug.Log("[AuthController] Registration request sent");
            }
            catch (Exception e)
            {
                _registerScreen?.ShowError(LocalizationManager.Get("register.error.connection"));
                Debug.LogError($"[AuthController] Failed to send registration request: {e.Message}");
            }
        }

        private void HandleLoginResponse(byte[] payload)
        {
            _isWaitingForLoginResponse = false;

            try
            {
                var response = LoginResponse.Parser.ParseFrom(payload);
                Debug.Log($"[AuthController] Login response received. Success: {response.Success}");

                if (response.Success)
                {
                    // Validate response data
                    if (string.IsNullOrEmpty(response.Jwt))
                    {
                        Debug.LogError("[AuthController] Login response missing JWT token");
                        _loginScreen?.ShowError(LocalizationManager.Get("login.error.failed"));
                        return;
                    }

                    // Store session data
                    _network.SetSessionData(response.Jwt, response.HmacSecret);

                    // TODO: Navigate to character selection screen
                    Debug.Log($"[AuthController] Login successful! JWT: {response.Jwt?.Substring(0, Math.Min(20, response.Jwt.Length))}...");
                    Debug.Log($"[AuthController] Characters: {response.Characters.Count}");

                    if (!string.IsNullOrEmpty(response.AccountServiceHost))
                    {
                        Debug.Log($"[AuthController] Account service: {response.AccountServiceHost}:{response.AccountServicePort}");
                    }

                    // Show success message
                    _loginScreen?.ShowSuccess(LocalizationManager.Get("login.success"));
                }
                else
                {
                    // Show error message
                    string errorMessage = string.IsNullOrEmpty(response.ErrorMessage)
                        ? LocalizationManager.Get("login.error.failed")
                        : response.ErrorMessage;

                    _loginScreen?.ShowError(errorMessage);
                    Debug.LogWarning($"[AuthController] Login failed: {errorMessage}");
                }
            }
            catch (Exception e)
            {
                Debug.LogError($"[AuthController] Failed to parse LoginResponse: {e}");
                _loginScreen?.ShowError(LocalizationManager.Get("login.error.failed"));
            }
        }

        private void HandleRegisterResponse(byte[] payload)
        {
            try
            {
                var response = RegisterResponse.Parser.ParseFrom(payload);
                Debug.Log($"[AuthController] Registration response received. Success: {response.Success}");

                if (response.Success)
                {
                    // Registration successful - navigate to login screen
                    Debug.Log("[AuthController] Registration successful! Navigating to login screen.");
                    UIManager.Instance.ShowScreen<LoginScreen>();

                    // Show success message on login screen
                    _loginScreen?.ShowSuccess(LocalizationManager.Get("register.success"));
                }
                else
                {
                    // Show error message on register screen
                    string errorMessage = string.IsNullOrEmpty(response.ErrorMessage)
                        ? LocalizationManager.Get("register.error.failed")
                        : response.ErrorMessage;

                    _registerScreen?.ShowError(errorMessage);
                    Debug.LogWarning($"[AuthController] Registration failed: {errorMessage}");
                }
            }
            catch (Exception e)
            {
                Debug.LogError($"[AuthController] Failed to parse RegisterResponse: {e}");
                _registerScreen?.ShowError(LocalizationManager.Get("register.error.failed"));
            }
        }

        private void OnDestroy()
        {
            // Unsubscribe from events
            if (_loginScreen != null)
            {
                _loginScreen.OnLoginRequested -= HandleLoginRequest;
            }

            if (_registerScreen != null)
            {
                _registerScreen.OnRegisterRequested -= HandleRegisterRequest;
            }

            // Unregister packet handlers
            if (_network != null)
            {
                _network.PacketHandler.Unregister((int)Opcode.LoginResponse);
                _network.PacketHandler.Unregister((int)Opcode.RegisterResponse);
            }

            if (Instance == this)
            {
                Instance = null;
            }
        }
    }
}
