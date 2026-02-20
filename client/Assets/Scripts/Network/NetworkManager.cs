using System;
using FlyAgain.Proto;
using Google.Protobuf;
using UnityEngine;

namespace FlyAgain.Network
{
    /// <summary>
    /// Central network manager. Singleton MonoBehaviour that orchestrates
    /// TCP and UDP connections, heartbeat, reconnect, and packet dispatch.
    /// </summary>
    public class NetworkManager : MonoBehaviour
    {
        public static NetworkManager Instance { get; private set; }

        [Header("Connection Settings")]
        [SerializeField] private string _loginHost = "127.0.0.1";
        [SerializeField] private int _loginPort = 7777;
        [SerializeField] private int _connectTimeoutMs = 5000;

        [Header("Heartbeat")]
        [SerializeField] private float _heartbeatIntervalSec = 5f;

        [Header("Reconnect")]
        [SerializeField] private int _maxReconnectAttempts = 3;
        [SerializeField] private float _reconnectDelaySec = 2f;

        private TcpConnection _tcp;
        private UdpConnection _udp;
        private readonly PacketHandler _packetHandler = new PacketHandler();

        private float _heartbeatTimer;
        private int _reconnectAttempts;
        private float _reconnectTimer;
        private bool _wasConnected;

        // Session data received from login
        private string _currentHost;
        private int _currentPort;
        private string _jwt;
        private string _hmacSecret;
        private long _sessionToken;

        public ConnectionState State { get; private set; } = ConnectionState.Disconnected;
        public PacketHandler PacketHandler => _packetHandler;

        public event Action OnConnected;
        public event Action<string> OnDisconnected;
        public event Action OnReconnectFailed;

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

        private void Update()
        {
            if (State == ConnectionState.Reconnecting)
            {
                HandleReconnect();
                return;
            }

            if (State != ConnectionState.Connected)
                return;

            // Check TCP connection health
            if (_tcp == null || !_tcp.IsConnected)
            {
                HandleConnectionLost();
                return;
            }

            // Process incoming TCP packets on main thread
            while (_tcp.TryDequeue(out Packet tcpPacket))
            {
                _packetHandler.Dispatch(tcpPacket);
            }

            // Process incoming UDP packets on main thread
            if (_udp != null)
            {
                while (_udp.TryDequeue(out Packet udpPacket))
                {
                    _packetHandler.Dispatch(udpPacket);
                }
            }

            // Heartbeat
            _heartbeatTimer += Time.unscaledDeltaTime;
            if (_heartbeatTimer >= _heartbeatIntervalSec)
            {
                _heartbeatTimer = 0f;
                SendHeartbeat();
            }
        }

        /// <summary>
        /// Connect TCP to the login service.
        /// </summary>
        public bool ConnectToLogin()
        {
            return ConnectTcp(_loginHost, _loginPort);
        }

        /// <summary>
        /// Connect TCP to a specific host/port (e.g. account-service or world-service).
        /// </summary>
        public bool ConnectTcp(string host, int port)
        {
            DisconnectTcp();

            State = ConnectionState.Connecting;
            _tcp = new TcpConnection();

            if (_tcp.Connect(host, port, _connectTimeoutMs))
            {
                _currentHost = host;
                _currentPort = port;
                _reconnectAttempts = 0;
                _heartbeatTimer = 0f;
                _wasConnected = true;
                State = ConnectionState.Connected;
                OnConnected?.Invoke();
                return true;
            }

            State = ConnectionState.Disconnected;
            return false;
        }

        /// <summary>
        /// Connect UDP to the world service after receiving EnterWorldResponse.
        /// </summary>
        public void ConnectUdp(string host, int port, long sessionToken, byte[] hmacSecret)
        {
            DisconnectUdp();

            _sessionToken = sessionToken;
            _udp = new UdpConnection();
            _udp.Connect(host, port, sessionToken, hmacSecret);
        }

        /// <summary>
        /// Send a protobuf message over TCP.
        /// </summary>
        public void SendTcp(int opcode, IMessage message)
        {
            if (_tcp == null || !_tcp.IsConnected)
            {
                Debug.LogWarning("[NetworkManager] Cannot send TCP, not connected");
                return;
            }
            _tcp.Send(opcode, message.ToByteArray());
        }

        /// <summary>
        /// Send a protobuf message over UDP (with HMAC signing).
        /// </summary>
        public void SendUdp(int opcode, IMessage message)
        {
            if (_udp == null || !_udp.IsConnected)
            {
                Debug.LogWarning("[NetworkManager] Cannot send UDP, not connected");
                return;
            }
            _udp.Send(opcode, message.ToByteArray());
        }

        /// <summary>
        /// Store session credentials received from LoginResponse.
        /// </summary>
        public void SetSessionData(string jwt, string hmacSecret)
        {
            _jwt = jwt;
            _hmacSecret = hmacSecret;
        }

        public string Jwt => _jwt;
        public string HmacSecret => _hmacSecret;

        public void Disconnect()
        {
            _wasConnected = false;
            DisconnectTcp();
            DisconnectUdp();
            State = ConnectionState.Disconnected;
            _packetHandler.Clear();
        }

        private void DisconnectTcp()
        {
            if (_tcp != null)
            {
                _tcp.Dispose();
                _tcp = null;
            }
        }

        private void DisconnectUdp()
        {
            if (_udp != null)
            {
                _udp.Dispose();
                _udp = null;
            }
        }

        private void SendHeartbeat()
        {
            var hb = new Heartbeat
            {
                ClientTime = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
            };
            SendTcp((int)Opcode.Heartbeat, hb);
        }

        private void HandleConnectionLost()
        {
            Debug.LogWarning("[NetworkManager] TCP connection lost");
            DisconnectTcp();
            DisconnectUdp();

            if (_wasConnected && _reconnectAttempts < _maxReconnectAttempts)
            {
                State = ConnectionState.Reconnecting;
                _reconnectTimer = _reconnectDelaySec;
                _reconnectAttempts++;
                Debug.Log($"[NetworkManager] Reconnect attempt {_reconnectAttempts}/{_maxReconnectAttempts} in {_reconnectDelaySec}s");
            }
            else
            {
                State = ConnectionState.Disconnected;
                OnDisconnected?.Invoke("Connection lost");
                if (_reconnectAttempts >= _maxReconnectAttempts)
                    OnReconnectFailed?.Invoke();
            }
        }

        private void HandleReconnect()
        {
            _reconnectTimer -= Time.unscaledDeltaTime;
            if (_reconnectTimer > 0f)
                return;

            Debug.Log($"[NetworkManager] Attempting reconnect {_reconnectAttempts}/{_maxReconnectAttempts}...");

            _tcp = new TcpConnection();
            if (_tcp.Connect(_currentHost, _currentPort, _connectTimeoutMs))
            {
                _heartbeatTimer = 0f;
                State = ConnectionState.Connected;
                _reconnectAttempts = 0;
                Debug.Log("[NetworkManager] Reconnected successfully");
                OnConnected?.Invoke();
            }
            else
            {
                _reconnectAttempts++;
                if (_reconnectAttempts >= _maxReconnectAttempts)
                {
                    State = ConnectionState.Disconnected;
                    Debug.LogError("[NetworkManager] All reconnect attempts failed");
                    OnReconnectFailed?.Invoke();
                }
                else
                {
                    _reconnectTimer = _reconnectDelaySec;
                    Debug.Log($"[NetworkManager] Reconnect failed, retry {_reconnectAttempts}/{_maxReconnectAttempts} in {_reconnectDelaySec}s");
                }
            }
        }

        private void OnDestroy()
        {
            Disconnect();
            if (Instance == this)
                Instance = null;
        }

        private void OnApplicationQuit()
        {
            Disconnect();
        }
    }
}
