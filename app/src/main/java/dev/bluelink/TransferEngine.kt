package dev.bluelink

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dev.bluelink.protocol.*
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class TransferView(val id:String,val peer:String,val name:String,val size:Long,val incoming:Boolean,
    val status:String="Offered",val progress:Long=0,val speed:Long=0,val uri:String?=null,val mime:String="application/octet-stream")

class TransferEngine(private val context:Context,private val send:(String,PacketType,Record)->Boolean,
    private val update:(TransferView)->Unit,private val notice:(String)->Unit,
    private val forward:(File,String,String,String)->Unit):Closeable {
    private val worker=java.util.concurrent.ScheduledThreadPoolExecutor(1)
    private class Transfer(val view:TransferView,val offer:Record,val source:File?=null) {
        var target:Uri?=null;var next=0;var touched=System.nanoTime();var started=touched;var accepted=false;var done=false
        var input:InputStream?=null;var output:OutputStream?=null;var temp:File?=null
        var assembler:FileAssembler?=null
    }
    private val items=linkedMapOf<String,Transfer>()
    private val owned=mutableSetOf<File>()
    init {worker.scheduleAtFixedRate({items.values.filter{!it.done && System.nanoTime()-it.touched>TimeUnit.SECONDS.toNanos(if(it.accepted)45 else 120)}.toList().forEach{fail(it,"Timed out",PacketType.FILE_CANCEL)}},5,5,TimeUnit.SECONDS)}
    fun stage(uri:Uri,peers:List<String>){worker.execute{
        var temp:File?=null
        try {
            require(peers.isNotEmpty()){"Connect a compatible peer first"}
            var name="document.bin"
            context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())name=Files.safeName(it.getString(0) ?: name)}
            val mime=context.contentResolver.getType(uri) ?: "application/octet-stream"
            temp=File.createTempFile("outgoing-",".bin",context.cacheDir);owned.add(temp)
            val source=context.contentResolver.openInputStream(uri) ?: error("Cannot read document")
            source.use{input->temp.outputStream().use{out->val buffer=ByteArray(8192);var size=0L
                while(true){val n=input.read(buffer);if(n<0)break;size+=n;require(size<=Files.LIMIT){"Document limit is 10 MiB"};out.write(buffer,0,n)}}}
            require(temp.length()>0){"Empty documents are not supported"}
            offerInternal(temp,name,mime,peers)
        }catch(e:Exception){temp?.delete();notice(e.message ?: "Could not prepare document")}
    }}
    fun offer(file:File,name:String,mime:String,peers:List<String>){worker.execute{runCatching{offerInternal(file,name,mime,peers)}.onFailure{notice("Could not distribute document")}}}
    private fun offerInternal(file:File,name:String,mime:String,peers:List<String>){
        val sha=Files.sha(file.inputStream())
        peers.forEach{peer->
            if(items.values.count{!it.done}>=12){notice("Too many pending file transfers");return}
            val id=UUID.randomUUID().toString()
            val r=Record(mapOf("id" to id,"name" to Files.safeName(name),"size" to file.length().toString(),"chunks" to Files.chunks(file.length()).toString(),"sha" to sha,"mime" to mime.take(120)))
            val v=TransferView(id,peer,r["name"],file.length(),false,mime=mime)
            val t=Transfer(v,r,file);items[id]=t;update(v)
            if(!send(peer,PacketType.FILE_OFFER,r))fail(t,"Could not send offer")
        }
    }
    fun receive(peer:String,type:PacketType,r:Record){
        if(worker.queue.size>=64){send(peer,PacketType.FILE_ERROR,Record(mapOf("id" to r["id"].take(64))));return}
        worker.execute{
        try {
            if(type==PacketType.FILE_OFFER){
                if(!Files.validOffer(r) || items.values.count{!it.done}>=12){send(peer,PacketType.FILE_REJECT,Record(mapOf("id" to r["id"].take(64))));return@execute}
                if(items.containsKey(r["id"]))return@execute
                val v=TransferView(r["id"],peer,Files.safeName(r["name"]),r.long("size"),true,mime=r["mime"])
                items[v.id]=Transfer(v,r);update(v);return@execute
            }
            val t=items[r["id"]]?.takeIf{it.view.peer==peer && !it.done} ?: return@execute
            t.touched=System.nanoTime()
            when(type){
                PacketType.FILE_ACCEPT -> if(!t.view.incoming && !t.accepted){
                    if(items.values.count{it.accepted && !it.done}>=4){fail(t,"Too many active transfers",PacketType.FILE_CANCEL);return@execute}
                    t.accepted=true;t.input=t.source!!.inputStream().buffered();t.started=System.nanoTime();chunk(t)
                }
                PacketType.FILE_CHUNK -> {
                    require(t.view.incoming && t.output!=null){"Unaccepted file data"}
                    val index=r.long("index");require(index>=0)
                    if(index<t.next){send(peer,PacketType.FILE_ACK,Record(mapOf("id" to t.view.id,"index" to index.toString())));return@execute}
                    val expected=minOf(Files.CHUNK.toLong(),t.view.size-t.next.toLong()*Files.CHUNK).toInt()
                    require(index==t.next.toLong() && index<t.offer.long("chunks") && expected>0 && r.data.size==expected)
                    t.assembler!!.chunk(index,r.data);t.next=t.assembler!!.next;progress(t,"Receiving")
                    if(!send(peer,PacketType.FILE_ACK,Record(mapOf("id" to t.view.id,"index" to index.toString()))))fail(t,"Peer unavailable")
                }
                PacketType.FILE_ACK -> if(!t.view.incoming && t.accepted && r.long("index")==t.next.toLong()){
                    t.next++;progress(t,"Sending")
                    if(t.next.toLong()==t.offer.long("chunks"))send(peer,PacketType.FILE_COMPLETE,Record(mapOf("id" to t.view.id))) else chunk(t)
                }
                PacketType.FILE_COMPLETE -> if(t.view.incoming){
                    require(t.output!=null && t.next.toLong()==t.offer.long("chunks"))
                    t.assembler!!.complete();t.output=null
                    val target=t.target ?: error("Missing save destination")
                    (context.contentResolver.openOutputStream(target,"wt") ?: error("Cannot save document")).use{out->t.temp!!.inputStream().use{it.copyTo(out)}}
                    t.done=true;update(t.view.copy(status="Received and SHA-256 verified",progress=t.view.size,uri=target.toString()))
                    send(peer,PacketType.FILE_COMPLETE,Record(mapOf("id" to t.view.id,"verified" to "true")))
                    forward(t.temp!!,t.view.name,t.view.mime,peer)
                }else if(r["verified"]=="true" && t.next.toLong()==t.offer.long("chunks")){
                    t.done=true;t.input?.close();t.input=null;update(t.view.copy(status="Peer saved and verified",progress=t.view.size))
                }
                PacketType.FILE_CANCEL,PacketType.FILE_REJECT,PacketType.FILE_ERROR -> fail(t,if(type==PacketType.FILE_REJECT)"Rejected by recipient" else "Peer cancelled or reported an error")
                else -> Unit
            }
        }catch(_:Exception){items[r["id"]]?.takeIf{it.view.peer==peer}?.let{fail(it,"Transfer failed or integrity rejected",PacketType.FILE_ERROR)}}
    }}
    fun accept(id:String,target:Uri){worker.execute{
        val t=items[id]?.takeIf{it.view.incoming && !it.done && !it.accepted} ?: return@execute
        try {
            require(items.values.count{it.accepted && !it.done}<4)
            t.target=target;t.temp=File.createTempFile("incoming-",".bin",context.cacheDir).also{owned.add(it)}
            t.output=t.temp!!.outputStream().buffered();t.accepted=true;t.started=System.nanoTime();t.touched=t.started
            t.assembler=FileAssembler(t.view.size,t.offer["sha"],t.output!!)
            update(t.view.copy(status="Receiving"))
            if(!send(t.view.peer,PacketType.FILE_ACCEPT,Record(mapOf("id" to id))))fail(t,"Peer unavailable")
        }catch(_:Exception){fail(t,"Cannot create receive file",PacketType.FILE_ERROR)}
    }}
    fun cancel(id:String,reject:Boolean=false){worker.execute{items[id]?.let{fail(it,if(reject)"Rejected" else "Cancelled",if(reject)PacketType.FILE_REJECT else PacketType.FILE_CANCEL)}}}
    fun peerClosed(peer:String){worker.execute{items.values.filter{!it.done && it.view.peer==peer}.toList().forEach{fail(it,"Connection lost")}}}
    private fun chunk(t:Transfer){
        val n=minOf(Files.CHUNK.toLong(),t.view.size-t.next.toLong()*Files.CHUNK).toInt()
        val bytes=ByteArray(n);DataInputStream(t.input!!).readFully(bytes)
        if(!send(t.view.peer,PacketType.FILE_CHUNK,Record(mapOf("id" to t.view.id,"index" to t.next.toString()),bytes)))fail(t,"Send queue unavailable")
    }
    private fun progress(t:Transfer,status:String){val bytes=minOf(t.view.size,t.next.toLong()*Files.CHUNK);val elapsed=((System.nanoTime()-t.started)/1e9).coerceAtLeast(.001);update(t.view.copy(status=status,progress=bytes,speed=(bytes/elapsed).toLong()))}
    private fun fail(t:Transfer,status:String,notify:PacketType?=null){
        if(t.done)return;t.done=true;runCatching{t.input?.close()};runCatching{t.output?.close()};t.input=null;t.output=null;t.temp?.delete()
        t.target?.let{uri->runCatching{context.contentResolver.delete(uri,null,null)}}
        update(t.view.copy(status=status));if(notify!=null)send(t.view.peer,notify,Record(mapOf("id" to t.view.id)))
    }
    override fun close(){worker.execute{items.values.filter{!it.done}.toList().forEach{fail(it,"App closed")};owned.forEach{it.delete()};items.clear()};worker.shutdown()}
}
