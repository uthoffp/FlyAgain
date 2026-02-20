using System;
using System.Collections.Concurrent;
using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Threading;
using UnityEngine;

namespace FlyAgain.Network
{
    /// <summary>
    /// Manages a UDP connection to the server with HMAC-SHA256 authentication.
    /// Wire format: [8B session token] [4B sequence] [2B opcode] [N payload] [32B HMAC-SHA256]
    /// HMAC is computed over everything except the HMAC itself.
    /// </summary>
    public class UdpConnection : IDisposable
    {
        private const int SessionTokenSize = 8;
        private const int SequenceSize = 4;
        private const int OpcodeSize = 2;
        private const int HmacSize = 32;
        private const int HeaderSize = SessionTokenSize + SequenceSize + OpcodeSize;
        private const int MinPacketSize = HeaderSize + HmacSize; // 46 bytes
        private const int MaxPacketSize = 512;

        private UdpClient _client;
        private IPEndPoint _serverEndpoint;
        private Thread _readThread;
        private volatile bool _running;
        private uint _sequence;

        private long _sessionToken;
        private byte[] _hmacSecret;

        private readonly ConcurrentQueue<Packet> _incomingPackets = new ConcurrentQueue<Packet>();

        public bool IsConnected => _client != null && _running;

        public void Connect(string host, int port, long sessionToken, byte[] hmacSecret)
        {
            _sessionToken = sessionToken;
            _hmacSecret = hmacSecret;
            _sequence = 0;

            _serverEndpoint = new IPEndPoint(IPAddress.Parse(host), port);
            _client = new UdpClient();
            _client.Connect(_serverEndpoint);

            _running = true;
            _readThread = new Thread(ReadLoop)
            {
                Name = "UDP-Read",
                IsBackground = true
            };
            _readThread.Start();

            Debug.Log($"[UDP] Connected to {host}:{port}");
        }

        public void Send(int opcode, byte[] payload)
        {
            if (!IsConnected)
                return;

            uint seq = Interlocked.Increment(ref _sequence);

            // Build packet: [token][seq][opcode][payload]
            int dataLen = HeaderSize + payload.Length;
            byte[] data = new byte[dataLen];

            // Session token (big-endian int64)
            WriteLong(data, 0, _sessionToken);

            // Sequence (big-endian uint32)
            WriteUInt(data, SessionTokenSize, seq);

            // Opcode (big-endian uint16)
            data[SessionTokenSize + SequenceSize] = (byte)((opcode >> 8) & 0xFF);
            data[SessionTokenSize + SequenceSize + 1] = (byte)(opcode & 0xFF);

            // Payload
            Buffer.BlockCopy(payload, 0, data, HeaderSize, payload.Length);

            // Compute HMAC-SHA256
            byte[] hmac;
            using (var hmacAlg = new HMACSHA256(_hmacSecret))
            {
                hmac = hmacAlg.ComputeHash(data);
            }

            // Final packet: data + hmac
            byte[] packet = new byte[dataLen + HmacSize];
            Buffer.BlockCopy(data, 0, packet, 0, dataLen);
            Buffer.BlockCopy(hmac, 0, packet, dataLen, HmacSize);

            if (packet.Length > MaxPacketSize)
            {
                Debug.LogWarning($"[UDP] Packet too large: {packet.Length} bytes (max {MaxPacketSize})");
                return;
            }

            try
            {
                _client.Send(packet, packet.Length);
            }
            catch (Exception e)
            {
                Debug.LogError($"[UDP] Send failed: {e.Message}");
            }
        }

        public bool TryDequeue(out Packet packet)
        {
            return _incomingPackets.TryDequeue(out packet);
        }

        public void Disconnect()
        {
            _running = false;
            try { _client?.Close(); } catch { }
            _client = null;
        }

        private void ReadLoop()
        {
            try
            {
                while (_running)
                {
                    IPEndPoint remote = null;
                    byte[] data = _client.Receive(ref remote);

                    if (data.Length < MinPacketSize || data.Length > MaxPacketSize)
                        continue;

                    // Extract opcode (offset: token + sequence = 12)
                    int opcode = (data[SessionTokenSize + SequenceSize] << 8)
                               | data[SessionTokenSize + SequenceSize + 1];

                    // Extract payload (between header and HMAC)
                    int payloadLen = data.Length - HeaderSize - HmacSize;
                    byte[] payload = new byte[payloadLen];
                    if (payloadLen > 0)
                        Buffer.BlockCopy(data, HeaderSize, payload, 0, payloadLen);

                    _incomingPackets.Enqueue(new Packet(opcode, payload));
                }
            }
            catch (SocketException)
            {
                // Expected on disconnect
            }
            catch (ObjectDisposedException)
            {
                // Expected on disconnect
            }
            catch (Exception e)
            {
                if (_running)
                    Debug.LogError($"[UDP] Read error: {e.Message}");
            }

            _running = false;
        }

        private static void WriteLong(byte[] buf, int offset, long value)
        {
            buf[offset]     = (byte)((value >> 56) & 0xFF);
            buf[offset + 1] = (byte)((value >> 48) & 0xFF);
            buf[offset + 2] = (byte)((value >> 40) & 0xFF);
            buf[offset + 3] = (byte)((value >> 32) & 0xFF);
            buf[offset + 4] = (byte)((value >> 24) & 0xFF);
            buf[offset + 5] = (byte)((value >> 16) & 0xFF);
            buf[offset + 6] = (byte)((value >> 8) & 0xFF);
            buf[offset + 7] = (byte)(value & 0xFF);
        }

        private static void WriteUInt(byte[] buf, int offset, uint value)
        {
            buf[offset]     = (byte)((value >> 24) & 0xFF);
            buf[offset + 1] = (byte)((value >> 16) & 0xFF);
            buf[offset + 2] = (byte)((value >> 8) & 0xFF);
            buf[offset + 3] = (byte)(value & 0xFF);
        }

        public void Dispose()
        {
            Disconnect();
        }
    }
}
