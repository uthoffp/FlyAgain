using System;
using System.Collections.Generic;
using UnityEngine;

namespace FlyAgain.Network
{
    /// <summary>
    /// Event-based packet dispatch system.
    /// Handlers are registered per opcode and invoked on the main thread
    /// when NetworkManager processes incoming packets in Update().
    /// </summary>
    public class PacketHandler
    {
        private readonly Dictionary<int, Action<byte[]>> _handlers = new Dictionary<int, Action<byte[]>>();
        private readonly HashSet<int> _ignoredOpcodes = new HashSet<int>();
        private int _totalPacketsProcessed;
        private int _totalErrors;

        /// <summary>
        /// Register a handler for a specific opcode.
        /// Warns if overwriting an existing handler.
        /// </summary>
        public void Register(int opcode, Action<byte[]> handler)
        {
            if (handler == null)
            {
                Debug.LogError($"[PacketHandler] Cannot register null handler for opcode 0x{opcode:X4}");
                return;
            }

            if (_handlers.ContainsKey(opcode))
            {
                Debug.LogWarning($"[PacketHandler] Overwriting existing handler for opcode 0x{opcode:X4}");
            }

            _handlers[opcode] = handler;
            _ignoredOpcodes.Remove(opcode); // Re-enable if previously ignored
            Debug.Log($"[PacketHandler] Registered handler for opcode 0x{opcode:X4}");
        }

        /// <summary>
        /// Unregister a handler for a specific opcode.
        /// </summary>
        public bool Unregister(int opcode)
        {
            bool removed = _handlers.Remove(opcode);
            if (removed)
            {
                Debug.Log($"[PacketHandler] Unregistered handler for opcode 0x{opcode:X4}");
            }
            return removed;
        }

        /// <summary>
        /// Ignore warnings for a specific opcode (useful for unimplemented packets).
        /// </summary>
        public void IgnoreOpcode(int opcode)
        {
            _ignoredOpcodes.Add(opcode);
        }

        /// <summary>
        /// Dispatch a packet to its registered handler.
        /// Executes on the main thread (called from NetworkManager.Update).
        /// </summary>
        public void Dispatch(Packet packet)
        {
            _totalPacketsProcessed++;

            if (packet.Payload == null)
            {
                Debug.LogError($"[PacketHandler] Received packet with null payload for opcode 0x{packet.Opcode:X4}");
                _totalErrors++;
                return;
            }

            if (_handlers.TryGetValue(packet.Opcode, out var handler))
            {
                try
                {
                    handler(packet.Payload);
                }
                catch (Exception e)
                {
                    _totalErrors++;
                    Debug.LogError($"[PacketHandler] Error handling opcode 0x{packet.Opcode:X4}: {e}");
                    Debug.LogException(e);
                }
            }
            else if (!_ignoredOpcodes.Contains(packet.Opcode))
            {
                // Only warn about unhandled opcodes if not explicitly ignored
                Debug.LogWarning($"[PacketHandler] No handler for opcode 0x{packet.Opcode:X4} (payload size: {packet.Payload.Length} bytes)");
            }
        }

        /// <summary>
        /// Check if a handler is registered for an opcode.
        /// </summary>
        public bool HasHandler(int opcode)
        {
            return _handlers.ContainsKey(opcode);
        }

        /// <summary>
        /// Get the number of registered handlers.
        /// </summary>
        public int HandlerCount => _handlers.Count;

        /// <summary>
        /// Get packet processing statistics.
        /// </summary>
        public (int processed, int errors) GetStats()
        {
            return (_totalPacketsProcessed, _totalErrors);
        }

        /// <summary>
        /// Clear all handlers and reset statistics.
        /// </summary>
        public void Clear()
        {
            int count = _handlers.Count;
            _handlers.Clear();
            _ignoredOpcodes.Clear();
            _totalPacketsProcessed = 0;
            _totalErrors = 0;
            Debug.Log($"[PacketHandler] Cleared {count} handlers and reset statistics");
        }
    }
}
