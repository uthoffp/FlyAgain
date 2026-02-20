using System;
using System.Collections.Concurrent;
using System.IO;
using System.Net.Sockets;
using System.Threading;
using UnityEngine;

namespace FlyAgain.Network
{
    /// <summary>
    /// Manages a TCP connection to the server with length-prefix framing.
    /// Wire format: [4-byte length (big-endian)] [2-byte opcode (big-endian)] [protobuf payload]
    /// Length covers opcode + payload only (not itself).
    /// </summary>
    public class TcpConnection : IDisposable
    {
        private const int MaxFrameSize = 65535;
        private const int LengthFieldSize = 4;
        private const int OpcodeSize = 2;

        private TcpClient _client;
        private NetworkStream _stream;
        private Thread _readThread;
        private volatile bool _running;

        private readonly ConcurrentQueue<Packet> _incomingPackets = new ConcurrentQueue<Packet>();

        public bool IsConnected => _client != null && _client.Connected && _running;

        public bool Connect(string host, int port, int timeoutMs = 5000)
        {
            try
            {
                _client = new TcpClient();
                _client.NoDelay = true;
                _client.ReceiveBufferSize = MaxFrameSize + LengthFieldSize;
                _client.SendBufferSize = MaxFrameSize + LengthFieldSize;

                var result = _client.BeginConnect(host, port, null, null);
                bool connected = result.AsyncWaitHandle.WaitOne(timeoutMs);
                if (!connected)
                {
                    _client.Close();
                    _client = null;
                    Debug.LogWarning($"[TCP] Connection to {host}:{port} timed out");
                    return false;
                }
                _client.EndConnect(result);

                _stream = _client.GetStream();
                _running = true;

                _readThread = new Thread(ReadLoop)
                {
                    Name = "TCP-Read",
                    IsBackground = true
                };
                _readThread.Start();

                Debug.Log($"[TCP] Connected to {host}:{port}");
                return true;
            }
            catch (Exception e)
            {
                Debug.LogError($"[TCP] Connection failed: {e.Message}");
                Cleanup();
                return false;
            }
        }

        public void Send(int opcode, byte[] payload)
        {
            if (!IsConnected)
            {
                Debug.LogWarning("[TCP] Cannot send, not connected");
                return;
            }

            try
            {
                int length = OpcodeSize + payload.Length;
                byte[] frame = new byte[LengthFieldSize + length];

                // Length field (big-endian)
                frame[0] = (byte)((length >> 24) & 0xFF);
                frame[1] = (byte)((length >> 16) & 0xFF);
                frame[2] = (byte)((length >> 8) & 0xFF);
                frame[3] = (byte)(length & 0xFF);

                // Opcode (big-endian)
                frame[4] = (byte)((opcode >> 8) & 0xFF);
                frame[5] = (byte)(opcode & 0xFF);

                // Payload
                Buffer.BlockCopy(payload, 0, frame, LengthFieldSize + OpcodeSize, payload.Length);

                lock (_stream)
                {
                    _stream.Write(frame, 0, frame.Length);
                }
            }
            catch (Exception e)
            {
                Debug.LogError($"[TCP] Send failed: {e.Message}");
                Disconnect();
            }
        }

        public bool TryDequeue(out Packet packet)
        {
            return _incomingPackets.TryDequeue(out packet);
        }

        public void Disconnect()
        {
            _running = false;
            Cleanup();
        }

        private void ReadLoop()
        {
            byte[] lengthBuf = new byte[LengthFieldSize];

            try
            {
                while (_running)
                {
                    // Read length prefix
                    if (!ReadExact(lengthBuf, LengthFieldSize))
                        break;

                    int length = (lengthBuf[0] << 24) | (lengthBuf[1] << 16)
                               | (lengthBuf[2] << 8) | lengthBuf[3];

                    if (length < OpcodeSize || length > MaxFrameSize)
                    {
                        Debug.LogWarning($"[TCP] Invalid frame length: {length}");
                        break;
                    }

                    // Read opcode + payload
                    byte[] data = new byte[length];
                    if (!ReadExact(data, length))
                        break;

                    int opcode = (data[0] << 8) | data[1];
                    byte[] payload = new byte[length - OpcodeSize];
                    Buffer.BlockCopy(data, OpcodeSize, payload, 0, payload.Length);

                    _incomingPackets.Enqueue(new Packet(opcode, payload));
                }
            }
            catch (Exception e)
            {
                if (_running)
                    Debug.LogError($"[TCP] Read error: {e.Message}");
            }

            _running = false;
        }

        private bool ReadExact(byte[] buffer, int count)
        {
            int offset = 0;
            while (offset < count)
            {
                int read = _stream.Read(buffer, offset, count - offset);
                if (read <= 0)
                    return false;
                offset += read;
            }
            return true;
        }

        private void Cleanup()
        {
            try { _stream?.Close(); } catch { }
            try { _client?.Close(); } catch { }
            _stream = null;
            _client = null;
        }

        public void Dispose()
        {
            Disconnect();
        }
    }
}
