package dev.bluelink.protocol

import java.io.*
import java.security.MessageDigest
import java.nio.ByteBuffer
import kotlin.random.Random

data class Record(val fields: Map<String,String> = emptyMap(), val data: ByteArray = byteArrayOf()) {
    operator fun get(key: String) = fields[key] ?: ""
    fun long(key: String) = get(key).toLongOrNull() ?: throw ProtocolException("Invalid numeric feature field")
    fun encode(): ByteArray {
        require(fields.size <= 32 && data.size <= 3072)
        val bytes=ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeByte(1);out.writeByte(fields.size)
            fields.forEach { (key,value) ->
                val k=key.toByteArray();val v=value.toByteArray()
                require(k.size in 1..64 && v.size <= 2048)
                out.writeByte(k.size);out.write(k);out.writeShort(v.size);out.write(v)
            }
            out.writeShort(data.size);out.write(data)
        }
        return bytes.toByteArray().also { require(it.size <= Blp.MAX_PAYLOAD) }
    }
    companion object {
        fun decode(bytes: ByteArray): Record = try {
            if (bytes.size > Blp.MAX_PAYLOAD) throw ProtocolException("Oversized feature record")
            val input=DataInputStream(ByteArrayInputStream(bytes))
            if(input.readUnsignedByte()!=1) throw ProtocolException("Unknown feature encoding")
            val count=input.readUnsignedByte()
            if(count>32) throw ProtocolException("Too many fields")
            val fields=linkedMapOf<String,String>()
            repeat(count){
                val k=input.readUnsignedByte();if(k !in 1..64) throw ProtocolException("Invalid key")
                val key=ByteArray(k).also(input::readFully).let(PacketCodec::utf8)
                val n=input.readUnsignedShort();if(n>2048) throw ProtocolException("Invalid value")
                if(fields.containsKey(key)) throw ProtocolException("Duplicate field")
                fields[key]=ByteArray(n).also(input::readFully).let(PacketCodec::utf8)
            }
            val size=input.readUnsignedShort();if(size>3072 || size!=input.available()) throw ProtocolException("Invalid blob")
            Record(fields,ByteArray(size).also(input::readFully))
        } catch(e:ProtocolException){throw e} catch(_:Exception){throw ProtocolException("Malformed feature record")}
    }
}

data class Member(val id:String,val name:String)
class GroupHub(val id:String,val name:String,val description:String,val host:Member,val capacity:Int=5) {
    private val members=linkedMapOf(host.id to host)
    private val seen=linkedSetOf<String>()
    @Synchronized fun join(member:Member): Boolean {
        if(member.id==host.id || member.name.isBlank() || member.name.toByteArray().size>80) return false
        if(member.id !in members && members.size>=capacity) return false
        members[member.id]=member;return true
    }
    @Synchronized fun leave(id:String) { if(id!=host.id) members.remove(id) }
    @Synchronized fun list()=members.values.toList()
    @Synchronized fun message(sender:String,record:Record):Record? {
        val member=members[sender] ?: return null
        if(record["id"].length !in 1..64 || record["text"].isBlank() || record["text"].toByteArray().size>1800) return null
        val key="$sender:${record["id"]}"
        if(!seen.add(key)) return null
        while(seen.size>1000) seen.remove(seen.first())
        return Record(mapOf("id" to record["id"],"text" to record["text"],"sender" to member.id,"name" to member.name,"group" to id,"sent" to record["sent"].take(20)))
    }
    fun roster()=Record(list().associate { "member.${it.id}" to it.name } + mapOf("group" to id))
}

data class Poll(
    val id:String,val creator:String,val question:String,val options:List<String>,
    val multiple:Boolean=false,val changeable:Boolean=false,val expires:Long=0,
    val votes:MutableMap<String,Set<Int>> = linkedMapOf(),var closed:Boolean=false
) {
    init {
        require(id.length in 1..64 && creator.length in 1..64)
        require(question.isNotBlank() && question.toByteArray().size<=240)
        require(options.size in 2..8 && options.all { it.isNotBlank() && it.toByteArray().size<=120 })
        require(options.distinct().size==options.size && expires>=0)
    }
    @Synchronized fun vote(voter:String,choices:Set<Int>,now:Long):Boolean {
        if(closed || (expires>0 && now>=expires) || voter.isBlank() || choices.isEmpty() || choices.any{it !in options.indices} || (!multiple && choices.size!=1)) return false
        if(!changeable && votes.containsKey(voter)) return false
        if(votes.size>=100 && voter !in votes) return false
        votes[voter]=choices.toSet();return true
    }
    @Synchronized fun snapshot():Record {
        val f=linkedMapOf("id" to id,"creator" to creator,"question" to question,"multiple" to multiple.toString(),"change" to changeable.toString(),
            "expires" to expires.toString(),"closed" to closed.toString(),"total" to votes.size.toString(),"count" to options.size.toString())
        options.forEachIndexed { i,s -> f["option$i"]=s;f["votes$i"]=votes.values.count{i in it}.toString() }
        return Record(f)
    }
    companion object {
        fun from(r:Record,creator:String)=Poll(r["id"],creator,r["question"],(0 until r.long("count").toInt().also{require(it in 2..8)}).map{r["option$it"]},r["multiple"]=="true",r["change"]=="true",r.long("expires"))
    }
}

object Files {
    const val LIMIT=10L*1024*1024
    const val CHUNK=1024
    fun safeName(name:String)=name.substringAfterLast('/').substringAfterLast('\\').filter{it>=' ' && it !in ":*?\"<>|"}.take(100).ifBlank{"document.bin"}
    fun chunks(size:Long)=((size+CHUNK-1)/CHUNK).toInt()
    fun validOffer(r:Record):Boolean = runCatching {
        r["id"].matches(Regex("[a-zA-Z0-9-]{1,64}")) && r.long("size") in 1..LIMIT && r.long("chunks")==chunks(r.long("size")).toLong() &&
            r["sha"].matches(Regex("[a-f0-9]{64}")) && r["name"].isNotBlank() && r["name"].toByteArray().size<=400 && r["mime"].length<=120
    }.getOrDefault(false)
    fun sha(input:InputStream):String {
        val digest=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(8192)
        input.use { while(true){val n=it.read(buffer);if(n<0)break;if(n>0)digest.update(buffer,0,n)} }
        return digest.digest().joinToString(""){"%02x".format(it.toInt() and 255)}
    }
}

class FileAssembler(private val size:Long,private val hash:String,private val output:OutputStream) {
    var next=0;private set
    var ended=false;private set
    private val digest=MessageDigest.getInstance("SHA-256")
    init{require(size in 1..Files.LIMIT && hash.matches(Regex("[a-f0-9]{64}")))}
    fun chunk(index:Long,bytes:ByteArray):Boolean{
        check(!ended)
        require(index>=0)
        if(index<next)return false
        require(index==next.toLong() && index<Files.chunks(size))
        require(bytes.size==minOf(Files.CHUNK.toLong(),size-next.toLong()*Files.CHUNK).toInt())
        output.write(bytes);digest.update(bytes);next++;return true
    }
    fun complete(){
        check(!ended);require(next==Files.chunks(size)){"Incomplete file"}
        output.close();ended=true
        require(digest.digest().joinToString(""){"%02x".format(it.toInt() and 255)}==hash){"SHA-256 mismatch"}
    }
    fun cancel(){if(!ended){ended=true;output.close()}}
}

data class Beacon(val id:Int,val category:Int,val expiresMinute:Long) {
    fun encode():ByteArray {
        require(id!=0 && category in 0..5 && expiresMinute in 1..0xffffffffL)
        return ByteBuffer.allocate(10).put(1).put(category.toByte()).putInt(id).putInt(expiresMinute.toInt()).array()
    }
    companion object {
        fun decode(data:ByteArray,nowMinute:Long):Beacon? = runCatching {
            if(data.size!=10)return null
            val b=ByteBuffer.wrap(data)
            if(b.get().toInt()!=1)return null
            val category=b.get().toInt() and 255;val id=b.int;val expires=b.int.toLong() and 0xffffffffL
            if(category !in 0..5 || id==0 || expires<=nowMinute || expires>nowMinute+61)return null
            Beacon(id,category,expires)
        }.getOrNull()
        fun title(text:String,max:Int=13):ByteArray {
            var result=""
            text.codePoints().toArray().forEach { code -> val next=result+String(Character.toChars(code));if(next.toByteArray().size<=max)result=next }
            return result.toByteArray()
        }
    }
}
