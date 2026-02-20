namespace FlyAgain.Network
{
    /// <summary>
    /// Represents a decoded network packet with opcode and protobuf payload.
    /// Mirrors the server's Packet data class.
    /// </summary>
    public readonly struct Packet
    {
        public readonly int Opcode;
        public readonly byte[] Payload;

        public Packet(int opcode, byte[] payload)
        {
            Opcode = opcode;
            Payload = payload;
        }
    }
}
