using NUnit.Framework;
using FlyAgain.Network;

namespace FlyAgain.Tests.EditMode.Network
{
    [TestFixture]
    public class PacketTests
    {
        [Test]
        public void Constructor_SetsOpcodeAndPayload()
        {
            byte[] payload = { 0x01, 0x02, 0x03 };
            var packet = new Packet(0x1234, payload);

            Assert.AreEqual(0x1234, packet.Opcode);
            Assert.AreEqual(payload, packet.Payload);
        }

        [Test]
        public void Constructor_EmptyPayload()
        {
            var packet = new Packet(0x0001, new byte[0]);

            Assert.AreEqual(0x0001, packet.Opcode);
            Assert.IsEmpty(packet.Payload);
        }

        [Test]
        public void Constructor_ZeroOpcode()
        {
            var packet = new Packet(0, new byte[] { 0xFF });

            Assert.AreEqual(0, packet.Opcode);
            Assert.AreEqual(1, packet.Payload.Length);
        }
    }
}
