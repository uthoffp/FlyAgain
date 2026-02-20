using System;
using NUnit.Framework;
using FlyAgain.Network;

namespace FlyAgain.Tests.EditMode.Network
{
    [TestFixture]
    public class PacketHandlerTests
    {
        private PacketHandler _handler;

        [SetUp]
        public void SetUp()
        {
            _handler = new PacketHandler();
        }

        [Test]
        public void Register_And_Dispatch_InvokesHandler()
        {
            byte[] received = null;
            _handler.Register(0x0001, payload => received = payload);

            byte[] expected = { 0xAA, 0xBB };
            _handler.Dispatch(new Packet(0x0001, expected));

            Assert.AreEqual(expected, received);
        }

        [Test]
        public void Dispatch_UnregisteredOpcode_DoesNotThrow()
        {
            Assert.DoesNotThrow(() =>
                _handler.Dispatch(new Packet(0x9999, new byte[0])));
        }

        [Test]
        public void Register_OverwritesPreviousHandler()
        {
            int callCount1 = 0;
            int callCount2 = 0;

            _handler.Register(0x0001, _ => callCount1++);
            _handler.Register(0x0001, _ => callCount2++);
            _handler.Dispatch(new Packet(0x0001, new byte[0]));

            Assert.AreEqual(0, callCount1);
            Assert.AreEqual(1, callCount2);
        }

        [Test]
        public void Unregister_RemovesHandler()
        {
            bool called = false;
            _handler.Register(0x0001, _ => called = true);
            _handler.Unregister(0x0001);
            _handler.Dispatch(new Packet(0x0001, new byte[0]));

            Assert.IsFalse(called);
        }

        [Test]
        public void Unregister_NonExistentOpcode_DoesNotThrow()
        {
            Assert.DoesNotThrow(() => _handler.Unregister(0xFFFF));
        }

        [Test]
        public void Clear_RemovesAllHandlers()
        {
            bool called = false;
            _handler.Register(0x0001, _ => called = true);
            _handler.Register(0x0002, _ => called = true);
            _handler.Clear();

            _handler.Dispatch(new Packet(0x0001, new byte[0]));
            _handler.Dispatch(new Packet(0x0002, new byte[0]));

            Assert.IsFalse(called);
        }

        [Test]
        public void Dispatch_HandlerThrows_DoesNotPropagate()
        {
            _handler.Register(0x0001, _ => throw new InvalidOperationException("test"));

            Assert.DoesNotThrow(() =>
                _handler.Dispatch(new Packet(0x0001, new byte[0])));
        }

        [Test]
        public void Dispatch_MultipleOpcodes_RoutesCorrectly()
        {
            int handler1Calls = 0;
            int handler2Calls = 0;

            _handler.Register(0x0001, _ => handler1Calls++);
            _handler.Register(0x0002, _ => handler2Calls++);

            _handler.Dispatch(new Packet(0x0001, new byte[0]));
            _handler.Dispatch(new Packet(0x0002, new byte[0]));
            _handler.Dispatch(new Packet(0x0001, new byte[0]));

            Assert.AreEqual(2, handler1Calls);
            Assert.AreEqual(1, handler2Calls);
        }
    }
}
