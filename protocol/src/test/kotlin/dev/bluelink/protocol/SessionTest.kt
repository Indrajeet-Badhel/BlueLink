package dev.bluelink.protocol

import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class SessionTest {
    private class Events:SessionObserver {
        val ready=CountDownLatch(1); val closed=CountDownLatch(1)
        val messages=CopyOnWriteArrayList<String>(); val status=ConcurrentHashMap<Long,String>()
        val metrics=AtomicReference(Metrics()); val logs=CopyOnWriteArrayList<String>()
        val features=CopyOnWriteArrayList<kotlin.Pair<PacketType,Record>>()
        var onFeature:((PacketType,Record)->Unit)?=null
        override fun feature(type:PacketType,payload:ByteArray){val r=Record.decode(payload);features.add(type to r);onFeature?.invoke(type,r)}
        override fun ready(maxPayload:Int){ready.countDown()}
        override fun received(sequence:Long,text:String){messages.add(text)}
        override fun delivery(id:Long,state:String){status[id]=state}
        override fun metrics(value:Metrics){metrics.set(value)}
        override fun event(text:String){logs.add(text)}
        override fun closed(reason:String){closed.countDown()}
    }
    private class Pairing(
        val filter:(ByteArray)->ByteArray?={it}, retries:Int=2, clientMinor:Int=1
    ):Closeable {
        val aEvents=Events(); val bEvents=Events()
        val aIn=PipedInputStream(32768); val bIn=PipedInputStream(32768)
        val aOut=PipedOutputStream(bIn); val bRaw=PipedOutputStream(aIn)
        val bOut=object:OutputStream(){
            override fun write(value:Int){bRaw.write(value)}
            override fun write(b:ByteArray,off:Int,len:Int){ filter(b.copyOfRange(off,off+len))?.let { bRaw.write(it) } }
            override fun flush(){bRaw.flush()}
            override fun close(){bRaw.close()}
        }
        val a=Session(aIn,aOut,Closeable{aIn.close();aOut.close()},true,aEvents,100,retries,supportedMinor=clientMinor)
        val b=Session(bIn,bOut,Closeable{bIn.close();bOut.close()},false,bEvents,100,retries)
        init {
            b.start();a.start()
            assertTrue(aEvents.ready.await(2,TimeUnit.SECONDS)); assertTrue(bEvents.ready.await(2,TimeUnit.SECONDS))
        }
        override fun close(){a.close();b.close()}
    }
    private fun until(predicate:()->Boolean) {
        val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3)
        while(!predicate() && System.nanoTime()<deadline) Thread.sleep(5)
        assertTrue("Condition did not become true",predicate())
    }
    @Test fun fullDuplexMessagesAndAckProcessing() { Pairing().use { p ->
        assertTrue(p.a.send(1,"A to B"));assertTrue(p.b.send(2,"B to A"))
        until { p.aEvents.status[1]=="Delivered" && p.bEvents.status[2]=="Delivered" }
        assertEquals(listOf("A to B"),p.bEvents.messages);assertEquals(listOf("B to A"),p.aEvents.messages)
    } }
    @Test fun extensionPacketsAreReliableBinaryMessages(){Pairing().use{p->
        val record=Record(mapOf("id" to "poll","question" to "Ready?"),byteArrayOf(0,-1))
        assertTrue(p.a.sendBytes(1,PacketType.POLL_CREATE,record.encode()))
        until{p.aEvents.status[1]=="Delivered"}
        assertEquals(PacketType.POLL_CREATE,p.bEvents.features.first().first)
        assertEquals(record.fields,p.bEvents.features.first().second.fields)
        assertTrue(p.bEvents.messages.isEmpty())
    }}
    @Test fun legacyMinorKeepsTextButGatesExtensions(){Pairing(clientMinor=0).use{p->
        assertEquals(0,p.a.minor);assertEquals(0,p.b.minor)
        assertFalse(p.a.sendBytes(1,PacketType.GROUP_JOIN,Record(mapOf("name" to "A")).encode()))
        assertTrue(p.a.send(2,"legacy text"));until{p.aEvents.status[2]=="Delivered"}
    }}
    @Test fun hostRelaysCanonicalGroupMessageAcrossTwoIndependentSessions(){
        Pairing().use{one->Pairing().use{two->
            val group=GroupHub("g","Room","",Member("host","Host"));group.join(Member("one","Alice"));group.join(Member("two","Bob"))
            one.bEvents.onFeature={type,r->if(type==PacketType.GROUP_MESSAGE){val message=group.message("one",r);if(message!=null){one.b.sendBytes(10,type,message.encode());two.b.sendBytes(11,type,message.encode())}}}
            val message=Record(mapOf("id" to "m1","text" to "Hello group","name" to "fake","sender" to "fake"))
            one.a.sendBytes(1,PacketType.GROUP_MESSAGE,message.encode())
            until{two.aEvents.features.isNotEmpty() && one.aEvents.features.isNotEmpty()}
            assertEquals("Alice",two.aEvents.features.first().second["name"])
            assertEquals("one",two.aEvents.features.first().second["sender"])
            assertEquals(1,two.aEvents.features.size)
        }}
    }
    @Test fun droppedAckRetriesWithoutDuplicateDelivery() {
        val dropped=AtomicInteger()
        Pairing(filter={ bytes -> if(PacketCodec.decode(bytes).type==PacketType.ACK && dropped.getAndIncrement()==0) null else bytes }).use { p ->
            p.a.send(1,"once")
            until { p.aEvents.status[1]=="Delivered" }
            assertEquals(listOf("once"),p.bEvents.messages)
            assertEquals(1,p.aEvents.metrics.get().retries)
            assertEquals(1,p.bEvents.metrics.get().duplicates)
            assertEquals(4,p.aEvents.metrics.get().txBytes)
        }
    }
    @Test fun exhaustedRetriesFailPendingAndQueuedMessages() {
        Pairing(filter={ if(PacketCodec.decode(it).type==PacketType.ACK) null else it },retries=1).use { p ->
            p.a.send(1,"first");p.a.send(2,"queued")
            assertTrue(p.aEvents.closed.await(3,TimeUnit.SECONDS))
            assertEquals("Failed",p.aEvents.status[1]);assertEquals("Failed",p.aEvents.status[2])
            assertEquals(listOf("first"),p.bEvents.messages)
            assertEquals(1,p.aEvents.metrics.get().retries)
        }
    }
    @Test fun queueUsesIndependentSequencesAndPingsProduceRtt() { Pairing().use { p ->
        repeat(10){assertTrue(p.a.send(it.toLong(),"message $it"))}
        until { p.aEvents.status[9]=="Delivered" }
        assertEquals(10,p.bEvents.messages.size)
        assertTrue(p.a.ping());until { p.aEvents.metrics.get().rttMs!=null }
        assertTrue(p.aEvents.metrics.get().rttMs!!>=0)
    } }
    @Test fun malformedAckFromAnotherSessionTerminates() {
        Pairing(filter={ bytes -> val packet=PacketCodec.decode(bytes)
            if(packet.type==PacketType.ACK) PacketCodec.encode(packet.copy(session=packet.session xor 1L)) else bytes }).use { p ->
            p.a.send(1,"test")
            assertTrue(p.aEvents.closed.await(2,TimeUnit.SECONDS));assertEquals("Failed",p.aEvents.status[1])
        }
    }
    @Test fun resetDoesNotResetDuplicateOrSequenceState() { Pairing().use { p ->
        p.a.send(1,"first");until { p.aEvents.status[1]=="Delivered" }
        p.a.resetStats();p.b.resetStats()
        p.a.send(2,"second");until { p.aEvents.status[2]=="Delivered" }
        assertEquals(listOf("first","second"),p.bEvents.messages)
        assertEquals(6,p.aEvents.metrics.get().txBytes)
    } }
    @Test fun gracefulDisconnectClosesBothEndpoints() { Pairing().use { p ->
        p.a.disconnect()
        assertTrue(p.aEvents.closed.await(2,TimeUnit.SECONDS));assertTrue(p.bEvents.closed.await(2,TimeUnit.SECONDS))
        assertFalse(p.a.send(1,"after close"))
    } }
    @Test fun handshakeTimesOutWithoutPeerResponse() {
        val input=PipedInputStream();val keepOpen=PipedOutputStream(input);val events=Events()
        val session=Session(input,java.io.ByteArrayOutputStream(),Closeable { input.close();keepOpen.close() },true,events,helloTimeoutMs=80)
        session.start()
        assertTrue(events.closed.await(2,TimeUnit.SECONDS));assertEquals(1,events.ready.count)
        session.close()
    }
    @Test fun sendingBeforeHelloFailsWithoutWritingData() {
        val events=Events()
        val session=Session(java.io.ByteArrayInputStream(byteArrayOf()),java.io.ByteArrayOutputStream(),Closeable{},true,events)
        assertFalse(session.send(1,"too soon"));session.close()
    }
}
