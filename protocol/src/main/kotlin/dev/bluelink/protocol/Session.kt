package dev.bluelink.protocol

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class Metrics(
    val framesSent: Long = 0, val framesReceived: Long = 0,
    val dataSent: Long = 0, val dataReceived: Long = 0,
    val ackSent: Long = 0, val ackReceived: Long = 0,
    val retries: Long = 0, val duplicates: Long = 0,
    val checksumFailures: Long = 0, val errors: Long = 0, val failed: Long = 0,
    val txBytes: Long = 0, val rxBytes: Long = 0,
    val rttMs: Long? = null, val averageRttMs: Double? = null,
    val txBps: Double = 0.0, val rxBps: Double = 0.0, val seconds: Long = 0
)

interface SessionObserver {
    fun negotiated(minor: Int) {}
    fun feature(type: PacketType, payload: ByteArray) {}
    fun ready(maxPayload: Int)
    fun received(sequence: Long, text: String)
    fun delivery(id: Long, state: String)
    fun metrics(value: Metrics)
    fun event(text: String)
    fun closed(reason: String)
}

/** Blocking streams are isolated from Android and all output is serialized. */
class Session(
    private val input: InputStream,
    private val output: OutputStream,
    private val transport: Closeable,
    private val client: Boolean,
    private val observer: SessionObserver,
    private val ackTimeoutMs: Long = 2000,
    private val retries: Int = 3,
    private val helloTimeoutMs: Long = 10000,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val supportedMinor: Int = 1
) : Closeable {
    private val ended = AtomicBoolean(false)
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "blp-writer").apply { isDaemon = true } }
    private val timer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "blp-timer").apply { isDaemon = true } }
    private val lock = Any()
    private var sessionId = if (client) generateSession() else 0L
    private var isReady = false
    @Volatile var minor: Int = 0
        private set
    private var nextSend = 1L
    private var expectedReceive = 1L
    private var limit = Blp.MAX_PAYLOAD
    private var count = Metrics()
    private var statsStart = clock()
    private val connectionStart = statsStart
    private var rttSum = 0L
    private var rttSamples = 0L
    private var pingId = 0L
    private val pings = mutableMapOf<Long, Long>()
    private var lastInput = clock()
    private var handshakeTask: ScheduledFuture<*>? = null
    private data class Send(val id: Long, val payload: ByteArray, val type: PacketType)
    private data class Pending(val send: Send, val packet: Packet, var attempts: Int = 0, var deadline: ScheduledFuture<*>? = null)
    private val queue = ArrayDeque<Send>()
    private val bulk = ArrayDeque<Send>()
    private var pending: Pending? = null

    fun start() {
        observer.event("RFCOMM connected; starting BLP 1 handshake")
        handshakeTask = timer.schedule({ end("HELLO timed out") }, helloTimeoutMs, TimeUnit.MILLISECONDS)
        if (client) write(Packet(PacketType.HELLO, sessionId, payload = Hello(maxMinor=supportedMinor).encode()))
        Thread({ readLoop() }, "blp-reader").apply { isDaemon = true; start() }
        timer.scheduleAtFixedRate({ synchronized(lock) {
            if (isReady && !ended.get()) {
                if (clock() - lastInput > 30000) end("Peer did not respond for 30 seconds")
                else if (pings.isEmpty()) ping()
                publish()
            }
        } }, 5, 5, TimeUnit.SECONDS)
    }

    fun send(id: Long, text: String): Boolean = sendBytes(id, PacketType.DATA, text.toByteArray(Charsets.UTF_8))

    fun sendBytes(id: Long, type: PacketType, bytes: ByteArray): Boolean = synchronized(lock) {
        if (!isReady || ended.get() || !type.reliable || (type != PacketType.DATA && minor < 1) || bytes.isEmpty() || bytes.size > limit || queue.size + bulk.size >= 50) return false
        (if (type == PacketType.FILE_CHUNK) bulk else queue).add(Send(id, bytes.copyOf(), type))
        observer.delivery(id, "Sending")
        drain()
        true
    }

    private fun drain() {
        if (pending != null || (queue.isEmpty() && bulk.isEmpty()) || ended.get()) return
        if (nextSend > Blp.MAX_SEQUENCE) { end("Sequence limit reached; reconnect"); return }
        val item = if (queue.isNotEmpty()) queue.removeFirst() else bulk.removeFirst()
        pending = Pending(item, Packet(item.type, sessionId, nextSend++, timestamp = clock(), payload = item.payload))
        transmit(pending!!)
    }

    private fun transmit(item: Pending) {
        write(item.packet) {
            synchronized(lock) {
                if (pending !== item || ended.get()) return@synchronized
                observer.delivery(item.send.id, "Sent")
                item.deadline = timer.schedule({ synchronized(lock) {
                    if (pending === item && !ended.get()) {
                        if (item.attempts >= retries) end("ACK timeout; delivery unconfirmed")
                        else {
                            item.attempts++
                            count = count.copy(retries = count.retries + 1)
                            observer.event("Retransmission DATA #${item.packet.sequence} attempt ${item.attempts}")
                            transmit(item)
                        }
                    }
                } }, ackTimeoutMs, TimeUnit.MILLISECONDS)
            }
        }
    }

    fun ping(): Boolean = synchronized(lock) {
        if (!isReady || ended.get() || pings.size >= 4) return false
        val id = ++pingId
        pings[id] = clock()
        write(Packet(PacketType.PING, sessionId, sequence = id, timestamp = clock()))
        true
    }

    fun resetStats() = synchronized(lock) {
        count = Metrics(); statsStart = clock(); rttSamples = 0; rttSum = 0
        publish(); observer.event("Statistics reset; reliability state preserved")
    }

    private fun readLoop() {
        try {
            val reader = FrameReader(input)
            while (!ended.get()) {
                val packet = reader.read()
                synchronized(lock) { if (!ended.get()) process(packet) }
            }
        } catch (error: Exception) {
            if (!ended.get()) synchronized(lock) {
                if (error is ProtocolException) {
                    count = count.copy(errors = count.errors + 1,
                        checksumFailures = count.checksumFailures + if (error.message == "CRC32 mismatch") 1 else 0)
                }
                end(error.message ?: "Connection lost")
            }
        }
    }

    private fun process(packet: Packet) {
        count = count.copy(framesReceived = count.framesReceived + 1)
        lastInput = clock()
        observer.event("RX ${packet.type} seq=${packet.sequence} ack=${packet.acknowledgement}; CRC valid")
        if (!isReady) {
            if (packet.type != PacketType.HELLO || packet.session == 0L || packet.sequence != 0L || packet.acknowledgement != 0L)
                throw ProtocolException("Expected session HELLO")
            if (client && packet.session != sessionId) throw ProtocolException("HELLO session mismatch")
            val hello = Hello.decode(packet.payload)
            if(hello.minMinor>supportedMinor)throw ProtocolException("No shared protocol minor")
            minor = minOf(supportedMinor, hello.maxMinor)
            if (!client) sessionId = packet.session
            limit = minOf(hello.maxPayload, Blp.MAX_PAYLOAD)
            if (!client) write(Packet(PacketType.HELLO, sessionId, payload = Hello(limit,maxMinor=supportedMinor).encode()))
            isReady = true; handshakeTask?.cancel(false)
            observer.event("BLP 1.$minor ready; payload limit $limit bytes")
            observer.negotiated(minor)
            observer.ready(limit); publish(); return
        }
        if (packet.session != sessionId) throw ProtocolException("Stale session frame")
        if (packet.payload.size > limit) throw ProtocolException("Negotiated payload limit exceeded")
        when {
            packet.type.reliable -> {
                if (packet.type != PacketType.DATA && minor < 1) throw ProtocolException("Extension requires BLP 1.1")
                if (packet.acknowledgement != 0L || packet.sequence == 0L || packet.sequence > expectedReceive || packet.payload.isEmpty())
                    throw ProtocolException("Unexpected DATA sequence or fields")
                val text = if (packet.type == PacketType.DATA) PacketCodec.utf8(packet.payload) else ""
                count = count.copy(dataReceived = count.dataReceived + 1)
                if (packet.sequence == expectedReceive) {
                    if (packet.type == PacketType.DATA) observer.received(packet.sequence, text)
                    else observer.feature(packet.type, packet.payload)
                    expectedReceive++
                    count = count.copy(rxBytes = count.rxBytes + packet.payload.size)
                } else count = count.copy(duplicates = count.duplicates + 1)
                write(Packet(PacketType.ACK, sessionId, acknowledgement = packet.sequence))
            }
            packet.type == PacketType.ACK -> {
                control(packet)
                if (packet.acknowledgement == 0L) throw ProtocolException("ACK references zero")
                count = count.copy(ackReceived = count.ackReceived + 1)
                val item = pending
                if (item != null && packet.acknowledgement == item.packet.sequence) {
                    item.deadline?.cancel(false)
                    pending = null
                    count = count.copy(txBytes = count.txBytes + item.send.payload.size)
                    observer.delivery(item.send.id, "Delivered"); drain()
                }
            }
            packet.type == PacketType.PING -> {
                if (packet.sequence == 0L || packet.acknowledgement != 0L || packet.payload.isNotEmpty()) throw ProtocolException("Invalid PING")
                write(Packet(PacketType.PONG, sessionId, acknowledgement = packet.sequence))
            }
            packet.type == PacketType.PONG -> {
                control(packet)
                val sent = pings.remove(packet.acknowledgement)
                if (sent != null) {
                    val rtt = (clock() - sent).coerceAtLeast(0)
                    rttSum += rtt; rttSamples++
                    count = count.copy(rttMs = rtt, averageRttMs = rttSum.toDouble() / rttSamples)
                }
            }
            packet.type == PacketType.DISCONNECT -> { control(packet); end("Peer disconnected") }
            packet.type == PacketType.ERROR -> { control(packet); end("Peer reported a protocol error") }
            else -> throw ProtocolException("Unexpected packet")
        }
        publish()
    }

    private fun control(packet: Packet) {
        if (packet.sequence != 0L || packet.payload.isNotEmpty()) throw ProtocolException("Invalid control frame")
        if (packet.type in listOf(PacketType.ERROR, PacketType.DISCONNECT) && packet.acknowledgement != 0L)
            throw ProtocolException("Invalid control acknowledgement")
    }

    private fun write(packet: Packet, after: () -> Unit = {}) {
        if (ended.get()) return
        try { writer.execute {
            try {
                if (!ended.get()) {
                    output.write(PacketCodec.encode(packet)); output.flush()
                    synchronized(lock) {
                        count = count.copy(framesSent = count.framesSent + 1,
                            dataSent = count.dataSent + if (packet.type.reliable) 1 else 0,
                            ackSent = count.ackSent + if (packet.type == PacketType.ACK) 1 else 0)
                        observer.event("TX ${packet.type} seq=${packet.sequence} ack=${packet.acknowledgement}")
                        publish(); after()
                    }
                }
            } catch (error: Exception) { end(error.message ?: "Write failed") }
        } } catch (_: java.util.concurrent.RejectedExecutionException) { /* session closing */ }
    }

    private fun publish() {
        val elapsed = ((clock() - statsStart).coerceAtLeast(1)) / 1000.0
        observer.metrics(count.copy(txBps = count.txBytes / elapsed, rxBps = count.rxBytes / elapsed,
            seconds = (clock() - connectionStart).coerceAtLeast(0) / 1000))
    }

    fun disconnect() {
        synchronized(lock) {
            if (ended.get()) return
            write(Packet(PacketType.DISCONNECT, sessionId)) { end("Disconnected") }
            if (!ended.get()) timer.schedule({ end("Disconnected") }, 300, TimeUnit.MILLISECONDS)
        }
    }

    private fun end(reason: String) {
        if (!ended.compareAndSet(false, true)) return
        runCatching { transport.close() }
        runCatching { input.close() }; runCatching { output.close() }
        synchronized(lock) {
            isReady = false
            pending?.let { observer.delivery(it.send.id, "Failed"); count = count.copy(failed = count.failed + 1) }
            queue.forEach { observer.delivery(it.id, "Failed"); count = count.copy(failed = count.failed + 1) }
            bulk.forEach { observer.delivery(it.id, "Failed"); count = count.copy(failed = count.failed + 1) }
            pending = null; queue.clear(); bulk.clear(); pings.clear()
            publish(); observer.event("Session closed: $reason"); observer.closed(reason)
        }
        timer.shutdownNow(); writer.shutdownNow()
    }

    override fun close() = end("Disconnected")

    private fun generateSession(): Long {
        val random = SecureRandom()
        var id = random.nextLong()
        while (id == 0L) id = random.nextLong()
        return id
    }
}
