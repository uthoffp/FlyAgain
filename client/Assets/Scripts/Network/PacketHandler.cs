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

        public void Register(int opcode, Action<byte[]> handler)
        {
            _handlers[opcode] = handler;
        }

        public void Unregister(int opcode)
        {
            _handlers.Remove(opcode);
        }

        public void Dispatch(Packet packet)
        {
            if (_handlers.TryGetValue(packet.Opcode, out var handler))
            {
                try
                {
                    handler(packet.Payload);
                }
                catch (Exception e)
                {
                    Debug.LogError($"[PacketHandler] Error handling opcode 0x{packet.Opcode:X4}: {e}");
                }
            }
            else
            {
                Debug.LogWarning($"[PacketHandler] No handler for opcode 0x{packet.Opcode:X4}");
            }
        }

        public void Clear()
        {
            _handlers.Clear();
        }
    }
}
