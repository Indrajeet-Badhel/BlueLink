package dev.bluelink.protocol

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class FeaturesTest {
    @Test fun recordRoundTripRetainsUnicodeAndBinary(){
        val r=Record(mapOf("text" to "नमस्ते 🟢","name" to "abc"),byteArrayOf(0,-1,2))
        val decoded=Record.decode(r.encode());assertEquals(r.fields,decoded.fields);assertArrayEquals(r.data,decoded.data)
    }
    @Test fun allTruncatedRecordsRejected(){val bytes=Record(mapOf("id" to "abc"),ByteArray(20)).encode();for(n in 0 until bytes.size)try{Record.decode(bytes.copyOf(n));fail("Truncation accepted")}catch(_:ProtocolException){}}
    @Test(expected=IllegalArgumentException::class)fun oversizedRecordRejected(){Record(mapOf("text" to "x".repeat(2049))).encode()}
    @Test fun groupJoinsTracksAndRemovesClients(){val h=hub();assertTrue(h.join(Member("a","Alice")));assertTrue(h.join(Member("b","Bob")));assertEquals(3,h.list().size);h.leave("a");assertEquals(listOf("host","b"),h.list().map{it.id})}
    @Test fun groupCapacityIsBounded(){val h=hub();repeat(4){assertTrue(h.join(Member("$it","Peer $it")))};assertFalse(h.join(Member("overflow","No")));assertEquals(5,h.list().size)}
    @Test fun groupCanonicalizesSenderAndSuppressesDuplicate(){val h=hub();h.join(Member("a","Alice"));val r=Record(mapOf("id" to "m1","text" to "Hello","name" to "Spoofed host","sender" to "host"));val actual=h.message("a",r)!!;assertEquals("a",actual["sender"]);assertEquals("Alice",actual["name"]);assertNull(h.message("a",r));assertNull(h.message("stranger",r))}
    @Test fun departedMemberCannotBroadcast(){val h=hub();h.join(Member("a","A"));h.leave("a");assertNull(h.message("a",Record(mapOf("id" to "m","text" to "not allowed"))))}
    @Test fun memberIdentityCannotEvictHost(){val h=hub();assertFalse(h.join(Member("host","Impostor")));h.leave("host");assertEquals("Host",h.list().first().name)}
    @Test fun rosterRecordRoundTrip(){val h=hub();h.join(Member("a","A"));assertEquals("A",Record.decode(h.roster().encode())["member.a"])}
    private fun hub()=GroupHub("group","Room","Description",Member("host","Host"))
    private fun poll(multi:Boolean=false,change:Boolean=false,expiry:Long=0)=Poll("p","creator","Choose",listOf("One","Two","Three"),multi,change,expiry)
    @Test fun duplicateVotePrevented(){val p=poll();assertTrue(p.vote("a",setOf(1),0));assertFalse(p.vote("a",setOf(2),0));assertEquals("1",p.snapshot()["votes1"]);assertEquals("1",p.snapshot()["total"])}
    @Test fun changeableVotesReplaceWithoutDoubleCounting(){val p=poll(change=true);p.vote("a",setOf(0),0);assertTrue(p.vote("a",setOf(2),0));assertEquals("0",p.snapshot()["votes0"]);assertEquals("1",p.snapshot()["total"])}
    @Test fun multiSelectCountsVotersSeparately(){val p=poll(multi=true);assertTrue(p.vote("a",setOf(0,2),0));assertEquals("1",p.snapshot()["votes0"]);assertEquals("1",p.snapshot()["votes2"]);assertEquals("1",p.snapshot()["total"])}
    @Test fun invalidChoicesAndClosedPollRejected(){val p=poll();assertFalse(p.vote("a",emptySet(),0));assertFalse(p.vote("a",setOf(9),0));assertFalse(p.vote("a",setOf(0,1),0));p.closed=true;assertFalse(p.vote("a",setOf(0),0))}
    @Test fun expirationRejectsAtBoundary(){val p=poll(expiry=100);assertTrue(p.vote("a",setOf(1),99));assertFalse(p.vote("b",setOf(0),100))}
    @Test(expected=IllegalArgumentException::class)fun duplicateOptionsRejected(){Poll("p","a","Q",listOf("Same","Same"))}
    @Test fun pollSnapshotReconstructsDefinition(){val p=poll(true,true,1000);val r=Record.decode(p.snapshot().encode());val restored=Poll.from(r,"a");assertEquals(p.options,restored.options);assertTrue(restored.multiple);assertEquals(1000,restored.expires)}
    @Test fun documentNameIsSanitized(){assertEquals("paper.pdf",Files.safeName("../../paper.pdf"));assertEquals("paper.pdf",Files.safeName("C:\\folder\\paper.pdf"));assertFalse(Files.safeName("ab:c?.pdf").contains(':'))}
    @Test fun validFileOfferAndLimits(){val r=offer(1025);assertTrue(Files.validOffer(r));assertFalse(Files.validOffer(Record(r.fields+mapOf("size" to "0"))));assertFalse(Files.validOffer(Record(r.fields+mapOf("size" to "${Files.LIMIT+1}"))));assertFalse(Files.validOffer(Record(r.fields+mapOf("chunks" to "1"))))}
    private fun offer(size:Long)=Record(mapOf("id" to "transfer-1","name" to "paper.pdf","size" to "$size","chunks" to "${Files.chunks(size)}","sha" to "a".repeat(64),"mime" to "application/pdf"))
    @Test fun fileChunksStreamAndVerify(){val data=ByteArray(5001){(it%251).toByte()};val out=ByteArrayOutputStream();val a=FileAssembler(data.size.toLong(),Files.sha(ByteArrayInputStream(data)),out);data.asList().chunked(1024).forEachIndexed{i,bytes->a.chunk(i.toLong(),bytes.toByteArray())};a.complete();assertArrayEquals(data,out.toByteArray());assertTrue(a.ended)}
    @Test fun duplicateChunkNotWrittenAgain(){val bytes=ByteArray(1024){1};val a=FileAssembler(1024,Files.sha(ByteArrayInputStream(bytes)),ByteArrayOutputStream());assertTrue(a.chunk(0,bytes));assertFalse(a.chunk(0,bytes));a.complete()}
    @Test(expected=IllegalArgumentException::class)fun outOfOrderChunkRejected(){FileAssembler(2048,"a".repeat(64),ByteArrayOutputStream()).chunk(1,ByteArray(1024))}
    @Test(expected=IllegalArgumentException::class)fun corruptDocumentFailsHash(){val a=FileAssembler(1,"a".repeat(64),ByteArrayOutputStream());a.chunk(0,byteArrayOf(1));a.complete()}
    @Test(expected=IllegalArgumentException::class)fun interruptedTransferCannotComplete(){FileAssembler(2048,"a".repeat(64),ByteArrayOutputStream()).complete()}
    @Test(expected=IllegalStateException::class)fun cancelledTransferCannotAcceptMore(){val a=FileAssembler(1,"a".repeat(64),ByteArrayOutputStream());a.cancel();a.chunk(0,byteArrayOf(1))}
    @Test fun beaconFitsLegacyAdvertisingBudget(){val b=Beacon(123,1,120);assertEquals(10,b.encode().size);assertEquals(31,3+18+b.encode().size);assertEquals(b,Beacon.decode(b.encode(),100))}
    @Test fun beaconRejectsExpiredInvalidAndFarFuture(){assertNull(Beacon.decode(Beacon(1,1,100).encode(),100));assertNull(Beacon.decode(Beacon(1,1,200).encode(),100));assertNull(Beacon.decode(ByteArray(10),100));assertNull(Beacon.decode(byteArrayOf(1),100))}
    @Test fun shortTitlePreservesUtf8AndBudget(){val short=Beacon.title("नमस्ते 🟢 assistance");assertTrue(short.size<=13);assertTrue(PacketCodec.utf8(short).isNotEmpty())}
    @Test fun multipleBeaconIdsRemainDistinct(){val a=Beacon.decode(Beacon(1,0,110).encode(),100)!!;val b=Beacon.decode(Beacon(2,3,120).encode(),100)!!;assertNotEquals(a.id,b.id);assertEquals(2,listOf(a,b).associateBy{it.id}.size)}
}
