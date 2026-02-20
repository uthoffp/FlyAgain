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

        private NetworkManager _network;
        private LoginScreen _loginScreen;
        private RegisterScreen _registerScreen;

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
            // Subscribe to login screen events
            _loginScreen = UIManager.Instance.GetScreen<LoginScreen>();
            if (_loginScreen != null)
            {
                _loginScreen.OnLoginRequested += HandleLoginRequest;
            }

            // Subscribe to register screen events
            _registerScreen = UIManager.Instance.GetScreen<RegisterScreen>();
            if (_registerScreen != null)
            {
                _registerScreen.OnRegisterRequested += HandleRegisterRequest;
            }
        }

        private void HandleLoginRequest(string username, string password)
        {
            Debug.Log($"[AuthController] Login requested for user: {username}");

            // Connect to login server if not already connected
            if (_network.State != ConnectionState.Connected)
            {
                if (!_network.ConnectToLogin())
                {
                    _loginScreen?.ShowError(LocalizationManager.Get("login.error.connection"));
                    Debug.LogError("[AuthController] Failed to connect to login server");
                    return;
                }
            }

            // Send login request
            var request = new LoginRequest
            {
                Username = username,
                Password = password
            };

            _network.SendTcp((int)Opcode.LoginRequest, request);
            Debug.Log("[AuthController] Login request sent");
        }

        private void HandleRegisterRequest(string username, string email, string password)
        {
            Debug.Log($"[AuthController] Registration requested for user: {username}, email: {email}");

            // Connect to login server if not already connected
            if (_network.State != ConnectionState.Connected)
            {
                if (!_network.ConnectToLogin())
                {
                    _registerScreen?.ShowError(LocalizationManager.Get("register.error.connection"));
                    Debug.LogError("[AuthController] Failed to connect to login server");
                    return;
                }
            }

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

        private void HandleLoginResponse(byte[] payload)
        {
            try
            {
                var response = LoginResponse.Parser.ParseFrom(payload);
                Debug.Log($"[AuthController] Login response received. Success: {response.Success}");

                if (response.Success)
                {
                    // Store session data
                    _network.SetSessionData(response.Jwt, response.HmacSecret);

                    // TODO: Navigate to character selection screen
                    Debug.Log($"[AuthController] Login successful! JWT: {response.Jwt?.Substring(0, 20)}...");
                    Debug.Log($"[AuthController] Characters: {response.Characters.Count}");
                    Debug.Log($"[AuthController] Account service: {response.AccountServiceHost}:{response.AccountServicePort}");

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
