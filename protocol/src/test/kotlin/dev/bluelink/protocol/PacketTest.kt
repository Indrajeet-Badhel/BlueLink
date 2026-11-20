package dev.bluelink.protocol

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32

class PacketTest {
    private fun packet(text: String = "Hello") = Packet(PacketType.DATA, 42, 1, timestamp = 123, payload = text.toByteArray())
    @Test fun goldenWireVectorMatchesIndependentCrcCalculation() {
        val hex = PacketCodec.encode(packet()).joinToString("") { "%02X".format(it.toInt() and 255) }
        assertEquals("424C4E4B01020000000000000000002A0000000100000000000000000000007B000000053614F2A548656C6C6F",hex)
    }
    @Test fun roundTripPreservesAllFields() {
        val original = packet("Hello नमस्ते 👽")
        val decoded = PacketCodec.decode(PacketCodec.encode(original))
        assertEquals(original.type, decoded.type); assertEquals(original.session, decoded.session)
        assertEquals(original.sequence, decoded.sequence); assertEquals(original.timestamp, decoded.timestamp)
        assertArrayEquals(original.payload, decoded.payload)
    }
    @Test fun wireHeaderUsesSpecifiedOffsetsAndBigEndian() {
        val bytes = PacketCodec.encode(packet())
        assertArrayEquals(byteArrayOf(0x42,0x4c,0x4e,0x4b,1,2,0,0),bytes.copyOfRange(0,8))
        val b = ByteBuffer.wrap(bytes)
        assertEquals(42,b.getLong(8)); assertEquals(1,b.getInt(16)); assertEquals(0,b.getInt(20))
        assertEquals(123,b.getLong(24)); assertEquals(5,b.getInt(32)); assertEquals(45,bytes.size)
    }
    @Test fun crcCoverageExcludesStoredCrcAndIncludesPayload() {
        val bytes=PacketCodec.encode(packet())
        val expected=CRC32().apply { update(bytes.copyOfRange(0,36) + bytes.copyOfRange(40,bytes.size)) }.value
        assertEquals(expected,ByteBuffer.wrap(bytes).getInt(36).toLong() and 0xffffffffL)
        assertEquals(0xcbf43926,CRC32().apply { update("123456789".toByteArray()) }.value)
    }
    @Test fun emptyControlPayloadAndMaximumDataPayloadRoundTrip() {
        val empty=Packet(PacketType.ACK,1,acknowledgement=1)
        assertEquals(40,PacketCodec.encode(empty).size)
        val max=packet().copy(payload=ByteArray(4096){it.toByte()})
        assertArrayEquals(max.payload,PacketCodec.decode(PacketCodec.encode(max)).payload)
    }
    @Test(expected=IllegalArgumentException::class) fun oversizedOutgoingPayloadRejected() { PacketCodec.encode(packet().copy(payload=ByteArray(4097))) }
    @Test(expected=ProtocolException::class) fun corruptPayloadRejected() {
        val bytes=PacketCodec.encode(packet()); bytes[40]=(bytes[40].toInt() xor 1).toByte(); PacketCodec.decode(bytes)
    }
    @Test(expected=ProtocolException::class) fun corruptHeaderRejected() {
        val bytes=PacketCodec.encode(packet()); bytes[8]=9; PacketCodec.decode(bytes)
    }
    @Test fun invalidLengthsRejectedBeforeAllocation() {
        for (n in listOf(-1,4097,Int.MAX_VALUE)) {
            val bytes=PacketCodec.encode(packet()); ByteBuffer.wrap(bytes).putInt(32,n)
            try { FrameReader(ByteArrayInputStream(bytes)).read(); fail("Length accepted: $n") } catch (_:ProtocolException) { }
        }
    }
    @Test fun badMagicVersionTypeAndFlagsRejected() {
        for ((offset,value) in listOf(0 to 0,4 to 2,5 to 99,6 to 1)) {
            val bytes=PacketCodec.encode(packet()); bytes[offset]=value.toByte()
            try { PacketCodec.decode(bytes); fail("Bad field accepted: $offset") } catch (_:ProtocolException) { }
        }
    }
    @Test fun everyStreamChunkSizeReassemblesFrames() {
        val bytes=PacketCodec.encode(packet("first"))+PacketCodec.encode(packet("second"))
        for (chunk in 1..bytes.size) {
            val input=object: FilterInputStream(ByteArrayInputStream(bytes)) {
                override fun read(b:ByteArray,off:Int,len:Int)=super.read(b,off,minOf(chunk,len))
            }
            val reader=FrameReader(input)
            assertEquals("first",PacketCodec.utf8(reader.read().payload))
            assertEquals("second",PacketCodec.utf8(reader.read().payload))
        }
    }
    @Test fun truncatedFramesNeverReturnPartialMessage() {
        val bytes=PacketCodec.encode(packet())
        for (size in 0 until bytes.size) {
            try { FrameReader(ByteArrayInputStream(bytes.copyOf(size))).read(); fail("Truncated frame returned") }
            catch (_:java.io.EOFException) { }
        }
    }
    @Test(expected=ProtocolException::class) fun trailingFrameBytesNotAcceptedByDecoder() { PacketCodec.decode(PacketCodec.encode(packet())+byteArrayOf(0)) }
    @Test fun unsignedSequencePreserved() { assertEquals(Blp.MAX_SEQUENCE,PacketCodec.decode(PacketCodec.encode(packet().copy(sequence=Blp.MAX_SEQUENCE))).sequence) }
    @Test(expected=IllegalArgumentException::class) fun outOfRangeSequenceRejected() { PacketCodec.encode(packet().copy(sequence=Blp.MAX_SEQUENCE+1)) }
    @Test(expected=ProtocolException::class) fun malformedUtf8Rejected() { PacketCodec.utf8(byteArrayOf(0xc3.toByte(),0x28)) }
    @Test fun helloNegotiatesLowerPayloadAndCompatibleMinorRange() {
        val h=Hello.decode(Hello(512,0,2).encode()); assertEquals(512,h.maxPayload); assertEquals(2,h.maxMinor)
    }
    @Test fun incompatibleHelloRejected() {
        val cases=listOf(Hello(minMinor=2,maxMinor=2).encode(), Hello(0).encode(), ByteArray(2), Hello().encode().also { it[9]=0 }, Hello().encode().also { it[11]=1 })
        for (bytes in cases) try { Hello.decode(bytes); fail("Incompatible HELLO accepted") } catch (_:ProtocolException) { }
    }
}
