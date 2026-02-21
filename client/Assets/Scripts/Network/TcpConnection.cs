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
        private const int ReadTimeoutMs = 30000; // 30 second read timeout
        private const int WriteTimeoutMs = 5000;  // 5 second write timeout
        private const int MaxQueuedPackets = 1000; // Prevent memory leak from unbounded queue

        private TcpClient _client;
        private NetworkStream _stream;
        private Thread _readThread;
        private volatile bool _running;
        private readonly object _streamLock = new object();

        private readonly ConcurrentQueue<Packet> _incomingPackets = new ConcurrentQueue<Packet>();

        public bool IsConnected => _client != null && _client.Connected && _running;
        public int QueuedPacketCount => _incomingPackets.Count;

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
                _stream.ReadTimeout = ReadTimeoutMs;
                _stream.WriteTimeout = WriteTimeoutMs;
                _running = true;

                _readThread = new Thread(ReadLoop)
                {
                    Name = "TCP-Read",
                    IsBackground = true
                };
                _readThread.Start();

                Debug.Log($"[TCP] Connected to {host}:{port} (read timeout: {ReadTimeoutMs}ms)");
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

            if (payload == null || payload.Length == 0)
            {
                Debug.LogWarning($"[TCP] Cannot send opcode 0x{opcode:X4} with null/empty payload");
                return;
            }

            int length = OpcodeSize + payload.Length;
            if (length > MaxFrameSize)
            {
                Debug.LogError($"[TCP] Packet too large: {length} bytes (max: {MaxFrameSize})");
                return;
            }

            try
            {
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

                lock (_streamLock)
                {
                    if (_stream != null && _stream.CanWrite)
                    {
                        _stream.Write(frame, 0, frame.Length);
                        _stream.Flush(); // Ensure packet is sent immediately
                    }
                    else
                    {
                        Debug.LogWarning("[TCP] Stream not writable");
                        Disconnect();
                    }
                }
            }
            catch (System.IO.IOException e)
            {
                Debug.LogError($"[TCP] Send failed (I/O error): {e.Message}");
                Disconnect();
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
                    // Check queue size to prevent memory leak
                    if (_incomingPackets.Count >= MaxQueuedPackets)
                    {
                        Debug.LogError($"[TCP] Packet queue overflow ({_incomingPackets.Count} packets). Disconnecting.");
                        break;
                    }

                    // Read length prefix
                    if (!ReadExact(lengthBuf, LengthFieldSize, out string readError))
                    {
                        if (_running)
                            Debug.LogWarning($"[TCP] Failed to read length prefix: {readError}");
                        break;
                    }

                    int length = (lengthBuf[0] << 24) | (lengthBuf[1] << 16)
                               | (lengthBuf[2] << 8) | lengthBuf[3];

                    if (length < OpcodeSize || length > MaxFrameSize)
                    {
                        Debug.LogError($"[TCP] Invalid frame length: {length} (expected {OpcodeSize}-{MaxFrameSize})");
                        break;
                    }

                    // Read opcode + payload
                    byte[] data = new byte[length];
                    if (!ReadExact(data, length, out readError))
                    {
                        if (_running)
                            Debug.LogWarning($"[TCP] Failed to read packet data: {readError}");
                        break;
                    }

                    int opcode = (data[0] << 8) | data[1];
                    byte[] payload = new byte[length - OpcodeSize];
                    Buffer.BlockCopy(data, OpcodeSize, payload, 0, payload.Length);

                    _incomingPackets.Enqueue(new Packet(opcode, payload));
                }
            }
            catch (System.IO.IOException e) when (e.InnerException is SocketException se)
            {
                if (_running)
                {
                    if (se.SocketErrorCode == SocketError.TimedOut)
                        Debug.LogWarning($"[TCP] Read timeout after {ReadTimeoutMs}ms - connection may be stalled");
                    else
                        Debug.LogError($"[TCP] Socket error: {se.SocketErrorCode} - {e.Message}");
                }
            }
            catch (System.IO.IOException e)
            {
                if (_running)
                    Debug.LogWarning($"[TCP] Connection closed: {e.Message}");
            }
            catch (Exception e)
            {
                if (_running)
                    Debug.LogError($"[TCP] Unexpected read error: {e.GetType().Name} - {e.Message}");
            }

            _running = false;
        }

        private bool ReadExact(byte[] buffer, int count, out string error)
        {
            error = null;
            int offset = 0;

            try
            {
                while (offset < count)
                {
                    if (!_running || _stream == null || !_stream.CanRead)
                    {
                        error = "Stream not readable";
                        return false;
                    }

                    int read = _stream.Read(buffer, offset, count - offset);
                    if (read <= 0)
                    {
                        error = "Connection closed by remote host";
                        return false;
                    }
                    offset += read;
                }
                return true;
            }
            catch (System.IO.IOException e)
            {
                error = $"I/O error: {e.Message}";
                return false;
            }
            catch (Exception e)
            {
                error = $"Unexpected error: {e.Message}";
                return false;
            }
        }

        private void Cleanup()
        {
            lock (_streamLock)
            {
                try
                {
                    _stream?.Close();
                    _stream?.Dispose();
                }
                catch { /* Ignore cleanup errors */ }

                try
                {
                    _client?.Close();
                    _client?.Dispose();
                }
                catch { /* Ignore cleanup errors */ }

                _stream = null;
                _client = null;
            }

            // Clear packet queue to free memory
            while (_incomingPackets.TryDequeue(out _)) { }
        }

        public void Dispose()
        {
            Disconnect();
        }
    }
}
