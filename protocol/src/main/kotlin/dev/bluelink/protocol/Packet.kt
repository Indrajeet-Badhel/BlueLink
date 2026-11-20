package dev.bluelink.protocol

import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import java.util.zip.CRC32

object Blp {
    const val HEADER = 40
    const val MAX_PAYLOAD = 4096
    const val MAGIC = 0x424c4e4b
    const val MAJOR = 1
    const val MAX_SEQUENCE = 0xffffffffL
    const val UUID = "9f0c4b2e-6a71-4d83-94b0-2f78e6a1c305"
}

enum class PacketType(val code: Int) {
    HELLO(1), DATA(2), ACK(3), PING(4), PONG(5), DISCONNECT(6), ERROR(7),
    GROUP_CREATE(16), GROUP_JOIN(17), GROUP_LEAVE(18), GROUP_MESSAGE(19), GROUP_MEMBER_UPDATE(20), GROUP_CLOSE(21),
    POLL_CREATE(32), POLL_VOTE(33), POLL_UPDATE(34), POLL_CLOSE(35),
    FILE_OFFER(48), FILE_ACCEPT(49), FILE_REJECT(50), FILE_CHUNK(51), FILE_ACK(52), FILE_COMPLETE(53), FILE_CANCEL(54), FILE_ERROR(55);
    val reliable get() = this == DATA || code >= 16
    companion object {
        fun from(code: Int) = entries.firstOrNull { it.code == code }
            ?: throw ProtocolException("Unknown packet type")
    }
}

class ProtocolException(message: String) : java.io.IOException(message)

data class Packet(
    val type: PacketType,
    val session: Long,
    val sequence: Long = 0,
    val acknowledgement: Long = 0,
    val timestamp: Long = 0,
    val payload: ByteArray = byteArrayOf()
)

object PacketCodec {
    fun encode(packet: Packet): ByteArray {
        require(packet.payload.size <= Blp.MAX_PAYLOAD)
        require(packet.sequence in 0..Blp.MAX_SEQUENCE)
        require(packet.acknowledgement in 0..Blp.MAX_SEQUENCE)
        val buffer = ByteBuffer.allocate(Blp.HEADER + packet.payload.size).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(Blp.MAGIC).put(Blp.MAJOR.toByte()).put(packet.type.code.toByte()).putShort(0)
        buffer.putLong(packet.session).putInt(packet.sequence.toInt()).putInt(packet.acknowledgement.toInt())
        buffer.putLong(packet.timestamp).putInt(packet.payload.size).putInt(0).put(packet.payload)
        val bytes = buffer.array()
        buffer.putInt(36, checksum(bytes).toInt())
        return bytes
    }

    fun validateHeader(header: ByteArray): Int {
        if (header.size != Blp.HEADER) throw ProtocolException("Incomplete header")
        val b = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
        if (b.int != Blp.MAGIC) throw ProtocolException("Invalid BLP magic")
        if ((b.get().toInt() and 255) != Blp.MAJOR) throw ProtocolException("Incompatible protocol major")
        PacketType.from(b.get().toInt() and 255)
        if (b.short.toInt() != 0) throw ProtocolException("Unsupported flags")
        val length = b.getInt(32)
        if (length !in 0..Blp.MAX_PAYLOAD) throw ProtocolException("Payload length exceeds hard limit")
        return length
    }

    fun decode(bytes: ByteArray): Packet {
        if (bytes.size < Blp.HEADER) throw ProtocolException("Incomplete frame")
        val length = validateHeader(bytes.copyOfRange(0, Blp.HEADER))
        if (bytes.size != Blp.HEADER + length) throw ProtocolException("Frame length mismatch")
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val expected = b.getInt(36).toLong() and 0xffffffffL
        if (checksum(bytes) != expected) throw ProtocolException("CRC32 mismatch")
        return Packet(PacketType.from(bytes[5].toInt() and 255), b.getLong(8),
            b.getInt(16).toLong() and 0xffffffffL, b.getInt(20).toLong() and 0xffffffffL,
            b.getLong(24), bytes.copyOfRange(Blp.HEADER, bytes.size))
    }

    fun checksum(bytes: ByteArray): Long = CRC32().apply {
        update(bytes, 0, 36)
        if (bytes.size > Blp.HEADER) update(bytes, Blp.HEADER, bytes.size - Blp.HEADER)
    }.value

    fun utf8(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: Exception) { throw ProtocolException("Invalid UTF-8 payload") }
}

class FrameReader(private val input: InputStream) {
    fun read(): Packet {
        val header = exact(Blp.HEADER)
        val size = PacketCodec.validateHeader(header)
        return PacketCodec.decode(header + exact(size))
    }
    private fun exact(count: Int): ByteArray {
        val bytes = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val n = input.read(bytes, offset, count - offset)
            if (n < 0) throw EOFException("Peer closed the stream")
            if (n == 0) throw ProtocolException("Stream made no progress")
            offset += n
        }
        return bytes
    }
}

data class Hello(val maxPayload: Int = Blp.MAX_PAYLOAD, val minMinor: Int = 0, val maxMinor: Int = 1) {
    fun encode(): ByteArray = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
        .put(minMinor.toByte()).put(maxMinor.toByte()).putInt(maxPayload)
        .putInt(3).putShort(0).array()
    companion object {
        fun decode(payload: ByteArray): Hello {
            if (payload.size != 12) throw ProtocolException("Invalid HELLO size")
            val b = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
            val low = b.get().toInt() and 255
            val high = b.get().toInt() and 255
            val max = b.int
            val features = b.int
            if (b.short.toInt() != 0 || low > high || low > 1 || max !in 1..Blp.MAX_PAYLOAD || features != 3)
                throw ProtocolException("Incompatible HELLO capabilities")
            return Hello(max, low, high)
        }
    }
}
