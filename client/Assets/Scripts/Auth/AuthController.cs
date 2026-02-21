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
        private CharacterSelectScreen _characterSelectScreen;
        private CharacterCreateScreen _characterCreateScreen;
        private CharacterInfo[] _currentCharacters;
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
            handler.Register((int)Opcode.CharacterSelect, HandleCharacterSelectResponse);
            handler.Register((int)Opcode.EnterWorld, HandleEnterWorldResponse);
            handler.Register((int)Opcode.CharacterCreate, HandleCharacterCreateResponse);
            handler.Register((int)Opcode.ErrorResponse, HandleErrorResponse);
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

            // Subscribe to character select screen events
            _characterSelectScreen = uiManager.GetScreen<CharacterSelectScreen>();
            if (_characterSelectScreen != null)
            {
                _characterSelectScreen.OnCharacterSelected -= HandleCharacterSelect;
                _characterSelectScreen.OnCharacterSelected += HandleCharacterSelect;
                _characterSelectScreen.OnCreateCharacterRequested -= HandleCreateCharacterRequest;
                _characterSelectScreen.OnCreateCharacterRequested += HandleCreateCharacterRequest;
                _characterSelectScreen.OnLogoutRequested -= HandleLogout;
                _characterSelectScreen.OnLogoutRequested += HandleLogout;
                Debug.Log("[AuthController] Subscribed to CharacterSelectScreen events");
            }
            else
            {
                Debug.LogWarning("[AuthController] CharacterSelectScreen not found");
            }

            // Subscribe to character create screen events
            _characterCreateScreen = uiManager.GetScreen<CharacterCreateScreen>();
            if (_characterCreateScreen != null)
            {
                _characterCreateScreen.OnCharacterCreateRequested -= HandleCharacterCreate;
                _characterCreateScreen.OnCharacterCreateRequested += HandleCharacterCreate;
                _characterCreateScreen.OnBackRequested -= HandleCreateScreenBack;
                _characterCreateScreen.OnBackRequested += HandleCreateScreenBack;
                Debug.Log("[AuthController] Subscribed to CharacterCreateScreen events");
            }
            else
            {
                Debug.LogWarning("[AuthController] CharacterCreateScreen not found");
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

                    // Store characters
                    _currentCharacters = new CharacterInfo[response.Characters.Count];
                    response.Characters.CopyTo(_currentCharacters, 0);

                    Debug.Log($"[AuthController] Login successful! JWT: {response.Jwt?.Substring(0, Math.Min(20, response.Jwt.Length))}...");
                    Debug.Log($"[AuthController] Characters: {response.Characters.Count}");

                    // Connect to Account Service
                    if (!string.IsNullOrEmpty(response.AccountServiceHost))
                    {
                        Debug.Log($"[AuthController] Connecting to Account service: {response.AccountServiceHost}:{response.AccountServicePort}");
                        bool connected = _network.ConnectTcp(response.AccountServiceHost, response.AccountServicePort);

                        if (!connected)
                        {
                            _loginScreen?.ShowError(LocalizationManager.Get("character.error.account_service"));
                            Debug.LogError("[AuthController] Failed to connect to Account service");
                            return;
                        }
                    }

                    // Navigate to character selection
                    UIManager.Instance.ShowScreen<CharacterSelectScreen>();
                    _characterSelectScreen?.SetCharacterList(_currentCharacters);
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

        private void HandleCharacterSelect(long characterId)
        {
            Debug.Log($"[AuthController] Character selected: {characterId}");

            try
            {
                var request = new CharacterSelectRequest
                {
                    CharacterId = characterId
                };

                _network.SendTcp((int)Opcode.CharacterSelect, request);
                Debug.Log("[AuthController] CharacterSelect request sent");
            }
            catch (Exception e)
            {
                _characterSelectScreen?.ShowError(LocalizationManager.Get("character.error.select"));
                Debug.LogError($"[AuthController] Failed to send CharacterSelect: {e.Message}");
            }
        }

        private void HandleCharacterCreate(string name, string characterClass)
        {
            Debug.Log($"[AuthController] Character creation requested: {name} ({characterClass})");

            try
            {
                var request = new CharacterCreateRequest
                {
                    Name = name,
                    CharacterClass = characterClass  // lowercase: "krieger", "magier", etc.
                };

                _network.SendTcp((int)Opcode.CharacterCreate, request);
                Debug.Log("[AuthController] CharacterCreate request sent");
            }
            catch (Exception e)
            {
                _characterCreateScreen?.ShowError(LocalizationManager.Get("character.error.create"));
                Debug.LogError($"[AuthController] Failed to send CharacterCreate: {e.Message}");
            }
        }

        private void HandleCharacterSelectResponse(byte[] payload)
        {
            // Server responds with ENTER_WORLD after character selection
            HandleEnterWorldResponse(payload);
        }

        private void HandleCharacterCreateResponse(byte[] payload)
        {
            // Server responds with ENTER_WORLD after character creation
            try
            {
                var response = EnterWorldResponse.Parser.ParseFrom(payload);
                Debug.Log($"[AuthController] CharacterCreate response received. Success: {response.Success}");

                if (response.Success)
                {
                    // Character created successfully - show success and navigate back to select
                    _characterCreateScreen?.ShowSuccess(LocalizationManager.Get("character.create.success"));

                    // Wait a moment then navigate back to character select
                    StartCoroutine(NavigateToCharacterSelectAfterDelay(1.5f));
                }
                else
                {
                    string errorMessage = string.IsNullOrEmpty(response.ErrorMessage)
                        ? LocalizationManager.Get("character.error.create")
                        : response.ErrorMessage;

                    _characterCreateScreen?.ShowError(errorMessage);
                    Debug.LogWarning($"[AuthController] Character creation failed: {errorMessage}");
                }
            }
            catch (Exception e)
            {
                Debug.LogError($"[AuthController] Failed to parse CharacterCreateResponse: {e}");
                _characterCreateScreen?.ShowError(LocalizationManager.Get("character.error.create"));
            }
        }

        private System.Collections.IEnumerator NavigateToCharacterSelectAfterDelay(float delay)
        {
            yield return new UnityEngine.WaitForSeconds(delay);
            UIManager.Instance.ShowScreen<CharacterSelectScreen>();
            // TODO: Request updated character list from server
        }

        private void HandleEnterWorldResponse(byte[] payload)
        {
            try
            {
                var response = EnterWorldResponse.Parser.ParseFrom(payload);
                Debug.Log($"[AuthController] EnterWorld response received. Success: {response.Success}");

                if (response.Success)
                {
                    // Connect to World Service (TCP + UDP)
                    string worldHost = response.WorldServiceHost;
                    int tcpPort = response.WorldServiceTcpPort;
                    int udpPort = response.WorldServiceUdpPort;

                    Debug.Log($"[AuthController] Connecting to World Service: {worldHost}:{tcpPort} (TCP), :{udpPort} (UDP)");

                    // Connect TCP to world service
                    bool tcpConnected = _network.ConnectTcp(worldHost, tcpPort);
                    if (!tcpConnected)
                    {
                        _characterSelectScreen?.ShowError(LocalizationManager.Get("character.error.world_service"));
                        Debug.LogError("[AuthController] Failed to connect to World Service TCP");
                        return;
                    }

                    // TODO: Connect UDP (requires session token from response)
                    // _network.ConnectUdp(worldHost, udpPort, response.SessionToken, hmacSecret);

                    // TODO: Navigate to game world screen (Phase 1.4)
                    Debug.Log("[AuthController] Successfully entered world! Ready for gameplay (Phase 1.4).");
                }
                else
                {
                    string errorMessage = string.IsNullOrEmpty(response.ErrorMessage)
                        ? LocalizationManager.Get("character.error.enter_world")
                        : response.ErrorMessage;

                    _characterSelectScreen?.ShowError(errorMessage);
                    Debug.LogWarning($"[AuthController] Enter world failed: {errorMessage}");
                }
            }
            catch (Exception e)
            {
                Debug.LogError($"[AuthController] Failed to parse EnterWorldResponse: {e}");
                _characterSelectScreen?.ShowError(LocalizationManager.Get("character.error.enter_world"));
            }
        }

        private void HandleErrorResponse(byte[] payload)
        {
            try
            {
                var response = ErrorResponse.Parser.ParseFrom(payload);
                Debug.LogError($"[AuthController] Error response: Code={response.ErrorCode}, Message={response.Message}, OriginalOpcode=0x{response.OriginalOpcode:X4}");

                // Route error to appropriate screen based on original opcode
                switch ((Opcode)response.OriginalOpcode)
                {
                    case Opcode.CharacterSelect:
                    case Opcode.CharacterCreate:
                        _characterSelectScreen?.ShowError(response.Message);
                        _characterCreateScreen?.ShowError(response.Message);
                        break;
                    case Opcode.LoginRequest:
                        _loginScreen?.ShowError(response.Message);
                        break;
                    case Opcode.RegisterRequest:
                        _registerScreen?.ShowError(response.Message);
                        break;
                    default:
                        Debug.LogWarning($"[AuthController] Unhandled error for opcode 0x{response.OriginalOpcode:X4}");
                        break;
                }
            }
            catch (Exception e)
            {
                Debug.LogError($"[AuthController] Failed to parse ErrorResponse: {e}");
            }
        }

        private void HandleCreateCharacterRequest()
        {
            UIManager.Instance.ShowScreen<CharacterCreateScreen>();
        }

        private void HandleCreateScreenBack()
        {
            UIManager.Instance.ShowScreen<CharacterSelectScreen>();
        }

        private void HandleLogout()
        {
            Debug.Log("[AuthController] Logout requested");

            // Disconnect from server
            _network.Disconnect();

            // Clear session data
            _currentCharacters = null;

            // Navigate to login screen
            UIManager.Instance.ShowScreen<LoginScreen>();
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

            if (_characterSelectScreen != null)
            {
                _characterSelectScreen.OnCharacterSelected -= HandleCharacterSelect;
                _characterSelectScreen.OnCreateCharacterRequested -= HandleCreateCharacterRequest;
                _characterSelectScreen.OnLogoutRequested -= HandleLogout;
            }

            if (_characterCreateScreen != null)
            {
                _characterCreateScreen.OnCharacterCreateRequested -= HandleCharacterCreate;
                _characterCreateScreen.OnBackRequested -= HandleCreateScreenBack;
            }

            // Unregister packet handlers
            if (_network != null)
            {
                _network.PacketHandler.Unregister((int)Opcode.LoginResponse);
                _network.PacketHandler.Unregister((int)Opcode.RegisterResponse);
                _network.PacketHandler.Unregister((int)Opcode.CharacterSelect);
                _network.PacketHandler.Unregister((int)Opcode.EnterWorld);
                _network.PacketHandler.Unregister((int)Opcode.CharacterCreate);
                _network.PacketHandler.Unregister((int)Opcode.ErrorResponse);
            }

            if (Instance == this)
            {
                Instance = null;
            }
        }
    }
}
