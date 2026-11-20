package dev.bluelink

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import dev.bluelink.protocol.*
import dev.bluelink.protocol.Record
import kotlinx.coroutines.flow.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

enum class Destination(val title:String){LINK("Link"),CHAT("Chat"),LAB("Lab"),LOG("Log"),SOS("SOS"),ARCADE("Arcade")}
data class Peer(val address:String,val name:String,val paired:Boolean)
data class ChatMessage(val id:Long,val text:String,val mine:Boolean,val time:String,val status:String,val networkId:String="",val sender:String="")
data class GroupView(val id:String,val name:String,val description:String,val hosting:Boolean,val members:List<Member>)
data class PollView(val record:Record,val owned:Boolean=false,val status:String="Host state",val choice:Set<Int> = emptySet())
data class AppState(
    val page:Destination=Destination.LINK,val supported:Boolean=true,val enabled:Boolean=false,
    val status:String="Disconnected",val detail:String="Two phones. One weird connection.",val busy:Boolean=false,
    val connected:Boolean=false,val scanning:Boolean=false,val localName:String="This phone",val peerName:String="No peer yet",
    val peers:List<Peer> = emptyList(),val messages:List<ChatMessage> = emptyList(),val events:List<String> = emptyList(),
    val metrics:Metrics=Metrics(),val maxPayload:Int=Blp.MAX_PAYLOAD,val notice:String?=null,
    val group:GroupView?=null,val extended:Boolean=false,val selfId:String="",
    val polls:List<PollView> = emptyList(),val transfers:List<TransferView> = emptyList()
)
enum class RadioAction{HOST,HOST_GROUP,SCAN,DISCOVER,CONNECT,SOS_HOST,SOS_SCAN,SOS_DETAILS}
object PermissionPolicy {
    fun required(action:RadioAction):Array<String>{
        val location=arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION)
        return if(Build.VERSION.SDK_INT>=31)when(action){
            RadioAction.DISCOVER,RadioAction.SOS_HOST->arrayOf(Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_ADVERTISE)
            RadioAction.SOS_SCAN->arrayOf(Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN)+location
            RadioAction.SOS_DETAILS->arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
            else->arrayOf(Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN)
        }else if(action==RadioAction.SCAN || action==RadioAction.SOS_SCAN)location else emptyArray()
    }
}

@SuppressLint("MissingPermission")
class BlueLinkModel(application:Application):AndroidViewModel(application){
    private val context=application.applicationContext
    private val adapter=context.getSystemService(BluetoothManager::class.java)?.adapter
    private val self=UUID.randomUUID().toString().take(16)
    private val mutable=MutableStateFlow(AppState(supported=adapter!=null,selfId=self))
    val state=mutable.asStateFlow()
    private val io=Executors.newCachedThreadPool()
    private val features=Executors.newSingleThreadScheduledExecutor()
    private val generation=AtomicLong()
    private val ids=AtomicLong()
    private val pendingFeatures=java.util.concurrent.atomic.AtomicInteger()
    private val guard=Any()
    private var server:BluetoothServerSocket?=null
    private var connecting:BluetoothSocket?=null
    private class Link(val id:String,val name:String,val socket:BluetoothSocket){lateinit var session:Session;@Volatile var ready=false;@Volatile var minor=0;@Volatile var joined=false}
    private val links=ConcurrentHashMap<String,Link>()
    private val snapshots=ConcurrentHashMap<String,Metrics>()
    private val devices=ConcurrentHashMap<String,BluetoothDevice>()
    private val polls=ConcurrentHashMap<String,Poll>()
    private val receipts=ConcurrentHashMap<Long,MutableMap<String,String>>()
    @Volatile private var hub:GroupHub?=null
    var groupDraftName="Slime circle"
    var groupDraftDescription=""
    var sosDraft=SosDraft()
    val sos=SosEngine(context,::notice)
    val transfers:TransferEngine=TransferEngine(context,{peer,type,r->sendRecord(peer,type,r)},{v->mutable.update{it.copy(transfers=(it.transfers.filterNot{t->t.id==v.id}+v).takeLast(80))}},::notice,
        {file,name,mime,from->if(hub!=null)transfersForward(file,name,mime,from)})
    private fun transfersForward(file:File,name:String,mime:String,from:String){transfers.offer(file,name,mime,links.values.filter{it.ready && it.minor>=1 && it.id!=from}.map{it.id})}
    private val receiver=object:BroadcastReceiver(){
        override fun onReceive(context:Context,intent:Intent){try{when(intent.action){
            BluetoothDevice.ACTION_FOUND->{val device=if(Build.VERSION.SDK_INT>=33)intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE,BluetoothDevice::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);if(device!=null)remember(device)}
            BluetoothAdapter.ACTION_DISCOVERY_FINISHED->{mutable.update{it.copy(scanning=false)};log("Discovery finished")}
            BluetoothDevice.ACTION_BOND_STATE_CHANGED->refresh()
            BluetoothAdapter.ACTION_STATE_CHANGED->{refresh();if(!state.value.enabled)stop("Bluetooth disabled")}
        }}catch(_:SecurityException){stop("Bluetooth permission revoked")}}
    }
    init{
        ContextCompat.registerReceiver(context,receiver,IntentFilter().apply{addAction(BluetoothDevice.ACTION_FOUND);addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);addAction(BluetoothAdapter.ACTION_STATE_CHANGED);addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)},ContextCompat.RECEIVER_EXPORTED)
        refresh()
        features.scheduleAtFixedRate({polls.values.filter{!it.closed && it.expires>0 && System.currentTimeMillis()>=it.expires}.forEach{it.closed=true;publishPoll(it)}},1,1,TimeUnit.SECONDS)
    }
    private fun permitted(name:String)=ContextCompat.checkSelfPermission(context,name)==PackageManager.PERMISSION_GRANTED
    private fun canConnect()=Build.VERSION.SDK_INT<31 || permitted(Manifest.permission.BLUETOOTH_CONNECT)
    fun select(page:Destination){mutable.update{it.copy(page=page)}}
    fun notice(text:String){mutable.update{it.copy(notice=text)}}
    fun clearNotice(){mutable.update{it.copy(notice=null)}}
    fun refresh(){try{
        val allowed=canConnect()
        mutable.update{it.copy(supported=adapter!=null,enabled=if(allowed)adapter?.isEnabled==true else it.enabled,localName=if(allowed)adapter?.name?.take(60) ?: "This phone" else "This phone")}
        if(allowed && adapter?.isEnabled==true){adapter.bondedDevices.forEach(::remember);mutable.update{s->s.copy(peers=s.peers.map{it.copy(paired=devices[it.address]?.bondState==BluetoothDevice.BOND_BONDED)})}}
    }catch(_:SecurityException){notice("Allow Bluetooth permissions in Android Settings.")}}
    private fun remember(device:BluetoothDevice){if(!canConnect())return;devices[device.address]=device;val p=Peer(device.address,device.name?.take(60) ?: "Unnamed device",device.bondState==BluetoothDevice.BOND_BONDED);mutable.update{it.copy(peers=(it.peers.filterNot{p2->p2.address==p.address}+p).sortedByDescending{p2->p2.paired})}}
    fun scan(){if(state.value.busy || state.value.connected || hub!=null){notice("Close the current conversation before scanning.");return};try{cancelDiscovery();mutable.update{it.copy(peers=it.peers.filter(Peer::paired))};if(adapter?.startDiscovery()==true){mutable.update{it.copy(scanning=true,status="Discovering devices",detail="Make the host phone discoverable.")};log("Discovery started")}else notice("Discovery could not start. Check permissions and Location settings.")}catch(_:SecurityException){notice("Discovery permissions are required.")}}
    fun cancelDiscovery(){runCatching{if((Build.VERSION.SDK_INT<31 || permitted(Manifest.permission.BLUETOOTH_SCAN)) && adapter?.isDiscovering==true)adapter.cancelDiscovery()};mutable.update{it.copy(scanning=false)}}
    fun host()=startHost(false)
    fun hostGroup()=startHost(true)
    private fun startHost(group:Boolean){
        if(state.value.connected || state.value.busy || hub!=null)return
        stopResources();cancelDiscovery();val token=generation.get()
        if(group){hub=GroupHub(UUID.randomUUID().toString(),groupDraftName.trim().take(40).ifBlank{"Slime circle"},groupDraftDescription.take(120),Member(self,state.value.localName));updateGroup()}
        mutable.update{it.copy(busy=!group,status=if(group)"Hosting group" else "Waiting for connection",detail=if(group)"Host plus up to four peers. Make this phone discoverable." else "Make this phone visible and connect from the other phone.",extended=group,polls=emptyList(),selfId=self)}
        io.execute{try{
            val listener=adapter?.listenUsingRfcommWithServiceRecord("BlueLink",UUID.fromString(Blp.UUID)) ?: error("Bluetooth unavailable")
            synchronized(guard){if(token!=generation.get()){listener.close();return@execute};server=listener}
            log(if(group)"Group listener started" else "Private listener started")
            do {
                val socket=listener.accept()
                if(token!=generation.get()){socket.close();break}
                if(group && links.size>=4){socket.close();log("Group capacity reached");continue}
                attach(socket,false,token)
            }while(group && token==generation.get())
            listener.close()
        }catch(e:Exception){if(token==generation.get()){if(group){notice("Group listener stopped: ${e.javaClass.simpleName}");stop("Group listener failed")}else failed(e)}}}
    }
    fun connect(address:String){
        if(state.value.busy || state.value.connected || hub!=null)return
        val device=devices[address] ?: return notice("Scan again to find this phone.")
        stopResources();cancelDiscovery();val token=generation.get()
        mutable.update{it.copy(busy=true,status="Connecting",detail="Check both phones for Android pairing requests.",group=null,polls=emptyList(),selfId=self)}
        io.execute{try{
            val socket=device.createRfcommSocketToServiceRecord(UUID.fromString(Blp.UUID))
            synchronized(guard){if(token!=generation.get()){socket.close();return@execute};connecting=socket}
            socket.connect();if(token!=generation.get()){socket.close();return@execute};attach(socket,true,token)
        }catch(e:Exception){if(token==generation.get())failed(e)}}
    }
    private fun attach(socket:BluetoothSocket,client:Boolean,token:Long){
        val link=Link(UUID.randomUUID().toString().take(16),socket.remoteDevice.name?.take(60) ?: "Other phone",socket)
        val observer=object:SessionObserver{
            fun current()=token==generation.get() && links[link.id]===link
            override fun negotiated(minor:Int){link.minor=minor}
            override fun ready(maxPayload:Int){if(!current())return;link.ready=true
                mutable.update{it.copy(connected=true,busy=false,status="Connected",peerName=hub?.name ?: link.name,detail="Offline. Ready to ooze.",maxPayload=maxPayload,extended=link.minor>=1,page=Destination.CHAT)}
                if(hub!=null)features.execute{
                    if(link.minor<1){notice("Group members need BlueLink 2.0");link.session.close();return@execute}
                    hub?.let{h->
                        if(!h.join(Member(link.id,displayName(link.name)))){link.session.close();return@execute}
                        updateGroup()
                        if(!sendRecord(link.id,PacketType.GROUP_CREATE,Record(mapOf("id" to h.id,"name" to h.name,"description" to h.description,"you" to link.id,"host" to self)))){link.session.close();return@execute}
                        link.joined=true;broadcast(PacketType.GROUP_MEMBER_UPDATE,h.roster())
                    }
                }
            }
            override fun received(sequence:Long,text:String){if(current()){
                if(hub!=null){log("Rejected legacy DATA in group");link.session.close()}else append(text,false,link.name)
            }}
            override fun feature(type:PacketType,payload:ByteArray){if(current()){
                if(pendingFeatures.incrementAndGet()>64){pendingFeatures.decrementAndGet();link.session.close();return}
                features.execute{try{if(current())handle(link,type,Record.decode(payload))}catch(_:Exception){log("Rejected malformed feature $type");notice("Received an invalid feature record; it was rejected.")}finally{pendingFeatures.decrementAndGet()}}
            }}
            override fun delivery(id:Long,state:String){if(current()){
                val group=mutable.value.group
                if(group?.hosting==true){val map=receipts[id];if(map!=null)synchronized(map){map[link.id]=state;val count=map.values.count{it=="Delivered"};val label=if(map.values.any{it=="Failed"})"Some deliveries unconfirmed" else "ACK $count/${map.size}";messageStatus(id,label);if(map.values.all{it in listOf("Delivered","Failed")})receipts.remove(id)}}
                else messageStatus(id,if(group!=null && state=="Delivered")"Received by host" else state)
            }}
            override fun metrics(value:Metrics){if(current()){snapshots[link.id]=value;publishMetrics()}}
            override fun event(text:String){if(current())log(text)}
            override fun closed(reason:String){if(current()){
                links.remove(link.id);transfers.peerClosed(link.id)
                features.execute{hub?.let{it.leave(link.id);updateGroup();broadcast(PacketType.GROUP_MEMBER_UPDATE,it.roster())}}
                mutable.update{s->s.copy(connected=links.values.any{it.ready},busy=false,status=if(hub!=null)"Hosting group" else "Disconnected",detail=reason,group=if(hub!=null)s.group else null,
                    messages=s.messages.map{if(it.mine && it.status in listOf("Sending","Sent"))it.copy(status="Failed")else it})}
            }}
        }
        link.session=Session(socket.inputStream,socket.outputStream,socket,client,observer)
        synchronized(guard){if(token!=generation.get()){link.session.close();return};links[link.id]=link;connecting=null;link.session.start()}
    }
    private fun handle(link:Link,type:PacketType,r:Record){
        if(type.code>=48){transfers.receive(link.id,type,r);return}
        when(type){
            PacketType.GROUP_CREATE->{require(hub==null && r["id"].length in 1..64 && r["you"].length in 1..64 && r["name"].isNotBlank());mutable.update{it.copy(group=GroupView(r["id"],r["name"].take(40),r["description"].take(120),false,emptyList()),selfId=r["you"],peerName=r["name"])};sendRecord(link.id,PacketType.GROUP_JOIN,Record(mapOf("name" to state.value.localName)))}
            PacketType.GROUP_JOIN->{hub?.let{if(it.join(Member(link.id,r["name"].take(60).ifBlank{link.name}))){updateGroup();broadcast(PacketType.GROUP_MEMBER_UPDATE,it.roster())}}}
            PacketType.GROUP_MEMBER_UPDATE->{if(hub==null && r["group"]==state.value.group?.id){val members=r.fields.filterKeys{it.startsWith("member.")}.map{Member(it.key.removePrefix("member."),it.value.take(60))}.take(5);mutable.update{it.copy(group=it.group?.copy(members=members))}}}
            PacketType.GROUP_MESSAGE->{val h=hub
                if(h!=null){h.message(link.id,r)?.let{canonical->appendGroup(canonical);broadcast(type,canonical)}}
                else if(state.value.group!=null && r["group"]==state.value.group?.id){appendGroup(r)}
            }
            PacketType.GROUP_LEAVE->{hub?.leave(link.id);link.session.disconnect()}
            PacketType.GROUP_CLOSE->{if(hub==null && state.value.group!=null){stop("Group host closed the room")}}
            PacketType.POLL_CREATE->{
                require(polls.size+state.value.polls.size<50)
                val creator=if(hub!=null)link.id else "remote"
                val p=Poll.from(r,creator)
                if(hub!=null){polls.putIfAbsent(p.id,p);publishPoll(polls[p.id]!!)}
                else upsertPoll(r,false,"Received from peer")
            }
            PacketType.POLL_VOTE->{val p=polls[r["id"]] ?: return;val choices=parseChoices(r["choices"])
                val accepted=p.vote(link.id,choices,System.currentTimeMillis());publishPoll(p,link.id,accepted)
            }
            PacketType.POLL_UPDATE->{if(hub==null && polls[r["id"]]==null){validatePollSnapshot(r);upsertPoll(r,r["creator"]==state.value.selfId,if(r["voter"]==state.value.selfId || (state.value.group==null && r["voter"].isNotBlank()))if(r["accepted"]=="true")"Vote accepted by coordinator" else "Vote rejected" else "Coordinator results; not an all-member receipt")}}
            PacketType.POLL_CLOSE->{val p=polls[r["id"]];if(p!=null && (hub==null || p.creator==link.id)){p.closed=true;publishPoll(p)}}
            else->Unit
        }
    }
    private fun parseChoices(text:String)=text.split(',').filter{it.isNotBlank()}.map{it.toInt()}.toSet()
    private fun validatePollSnapshot(r:Record){Poll.from(r,r["creator"].ifBlank{"remote"});require(r.long("total") in 0..100);repeat(r.long("count").toInt()){require(r.long("votes$it") in 0..100)}}
    private fun updateGroup(){hub?.let{h->mutable.update{it.copy(group=GroupView(h.id,h.name,h.description,true,h.list()),peerName=h.name)}}}
    private fun sendRecord(peer:String,type:PacketType,r:Record,id:Long=ids.incrementAndGet()):Boolean=runCatching{links[peer]?.takeIf{it.ready && it.minor>=1}?.session?.sendBytes(id,type,r.encode())==true}.getOrDefault(false)
    private fun broadcast(type:PacketType,r:Record){links.values.filter{it.ready && it.minor>=1 && (hub==null || it.joined)}.forEach{if(!sendRecord(it.id,type,r))log("Feature queue unavailable: $type")}}
    private fun displayName(name:String)=String(Beacon.title(name,80),Charsets.UTF_8).ifBlank{"Peer"}
    private fun append(text:String,mine:Boolean,sender:String="",networkId:String="",id:Long=ids.incrementAndGet(),status:String=if(mine)"Sending" else "Received"){
        mutable.update{it.copy(messages=(it.messages+ChatMessage(id,text,mine,now(),status,networkId,sender)).takeLast(500))}
    }
    private fun appendGroup(r:Record){
        require(r["text"].toByteArray().size<=1800 && r["name"].toByteArray().size<=80)
        val existing=state.value.messages.find{it.networkId==r["id"]}
        if(existing!=null){messageStatus(existing.id,"Relayed by host");return}
        append(r["text"],r["sender"]==state.value.selfId,r["name"],r["id"])
    }
    private fun messageStatus(id:Long,status:String){mutable.update{s->s.copy(messages=s.messages.map{if(it.id==id)it.copy(status=status)else it})}}
    fun send(text:String):Boolean{
        val value=text.trim();if(value.isEmpty())return false
        val group=state.value.group;val max=if(group!=null)1800 else state.value.maxPayload
        if(value.toByteArray().size>max){notice("Message limit is $max UTF-8 bytes");return false}
        val active=links.values.filter{it.ready}
        if(active.isEmpty()){notice("Connect a peer first");return false}
        val id=ids.incrementAndGet()
        if(group==null){append(value,true,id=id);val ok=active.first().session.send(id,value);if(!ok)messageStatus(id,"Failed");return ok}
        val net=UUID.randomUUID().toString();val r=Record(mapOf("id" to net,"text" to value,"sender" to state.value.selfId,"name" to state.value.localName,"group" to group.id,"sent" to System.currentTimeMillis().toString()))
        append(value,true,state.value.localName,net,id)
        if(hub!=null){val canonical=hub!!.message(self,r) ?: return false;receipts[id]=Collections.synchronizedMap(active.associate{it.id to "Sending"}.toMutableMap());active.forEach{if(!sendRecord(it.id,PacketType.GROUP_MESSAGE,canonical,id))messageStatus(id,"Some deliveries unconfirmed")}}
        else if(!sendRecord(active.first().id,PacketType.GROUP_MESSAGE,r,id)){messageStatus(id,"Failed");return false}
        return true
    }
    fun removeMember(id:String){features.execute{if(hub!=null){links[id]?.session?.disconnect();hub?.leave(id);updateGroup();hub?.let{broadcast(PacketType.GROUP_MEMBER_UPDATE,it.roster())}}}}
    fun createPoll(question:String,options:List<String>,multiple:Boolean,change:Boolean,minutes:Int){features.execute{try{
        require(state.value.extended && links.values.any{it.ready}){"Connect a BlueLink 2.0 peer first"}
        require(state.value.polls.size<50)
        val p=Poll(UUID.randomUUID().toString(),state.value.selfId,question.trim(),options.map{it.trim()},multiple,change,if(minutes==0)0 else System.currentTimeMillis()+minutes*60000L)
        if(state.value.group!=null && hub==null){links.values.firstOrNull{it.ready}?.let{sendRecord(it.id,PacketType.POLL_CREATE,p.snapshot())}}
        else {polls[p.id]=p;upsertPoll(p.snapshot(),true,"Created locally");broadcast(PacketType.POLL_CREATE,p.snapshot());if(hub!=null)publishPoll(p)}
    }catch(e:Exception){notice(e.message ?: "Invalid poll")}}}
    fun vote(id:String,choices:Set<Int>){features.execute{
        val local=polls[id]
        if(local!=null){val accepted=local.vote(self,choices,System.currentTimeMillis());publishPoll(local,self,accepted);if(!accepted)notice("Vote rejected: check closure, expiry or change policy")}
        else links.values.firstOrNull{it.ready}?.let{sendRecord(it.id,PacketType.POLL_VOTE,Record(mapOf("id" to id,"choices" to choices.sorted().joinToString(","))));mutable.update{s->s.copy(polls=s.polls.map{p->if(p.record["id"]==id)p.copy(choice=choices,status="Vote sent; awaiting coordinator result")else p})}}
    }}
    fun closePoll(id:String){features.execute{val p=polls[id];if(p!=null){p.closed=true;publishPoll(p)}else if(state.value.polls.any{it.record["id"]==id && it.owned})links.values.firstOrNull{it.ready}?.let{sendRecord(it.id,PacketType.POLL_CLOSE,Record(mapOf("id" to id)))}}}
    private fun publishPoll(p:Poll,voter:String="",accepted:Boolean=false){val r=p.snapshot();val withVote=Record(r.fields+mapOf("voter" to voter,"accepted" to accepted.toString()));upsertPoll(r,p.creator==state.value.selfId,if(voter.isBlank())"Coordinator state" else if(accepted)"Vote accepted; update queued to peers" else "Vote rejected");broadcast(PacketType.POLL_UPDATE,withVote)}
    private fun upsertPoll(r:Record,owned:Boolean,status:String){mutable.update{s->val old=s.polls.find{it.record["id"]==r["id"]};s.copy(polls=(s.polls.filterNot{it.record["id"]==r["id"]}+PollView(r,owned,status,old?.choice ?: emptySet())).takeLast(50))}}
    fun sendDocument(uri:Uri){transfers.stage(uri,links.values.filter{it.ready && it.minor>=1}.map{it.id})}
    fun acceptFile(id:String,uri:Uri){transfers.accept(id,uri)}
    fun startSos(){val d=sosDraft;sos.host(d.title,d.message,d.name,d.category,if(d.shareLocation)d.location else "",d.minutes)}
    fun ping(){val active=links.values.filter{it.ready};if(active.isEmpty())notice("Connect a phone before pinging")else active.forEach{it.session.ping()}}
    fun resetStats(){snapshots.clear();links.values.forEach{it.session.resetStats()};publishMetrics()}
    private fun publishMetrics(){val a=snapshots.values.toList();if(a.isEmpty()){mutable.update{it.copy(metrics=Metrics())};return};val last=a.last();mutable.update{it.copy(metrics=last.copy(framesSent=a.sumOf{m->m.framesSent},framesReceived=a.sumOf{m->m.framesReceived},dataSent=a.sumOf{m->m.dataSent},dataReceived=a.sumOf{m->m.dataReceived},ackSent=a.sumOf{m->m.ackSent},ackReceived=a.sumOf{m->m.ackReceived},retries=a.sumOf{m->m.retries},duplicates=a.sumOf{m->m.duplicates},checksumFailures=a.sumOf{m->m.checksumFailures},errors=a.sumOf{m->m.errors},failed=a.sumOf{m->m.failed},txBps=a.sumOf{m->m.txBps},rxBps=a.sumOf{m->m.rxBps}))}}
    fun clearChat(){mutable.update{it.copy(messages=it.messages.filter{m->m.mine && m.status in listOf("Sending","Sent")})}}
    fun clearLogs(){mutable.update{it.copy(events=emptyList())}}
    fun disconnect(){if(hub!=null)broadcast(PacketType.GROUP_CLOSE,Record(mapOf("id" to hub!!.id)))else if(state.value.group!=null)broadcast(PacketType.GROUP_LEAVE,Record(mapOf("id" to state.value.group!!.id)));links.values.forEach{it.session.disconnect()};features.schedule({stop("Disconnected")},350,TimeUnit.MILLISECONDS)}
    fun stop(reason:String="Disconnected"){stopResources();cancelDiscovery();sos.stopAll();mutable.update{s->s.copy(connected=false,busy=false,status="Disconnected",detail=reason,group=null,extended=false,messages=s.messages.map{if(it.mine && it.status in listOf("Sending","Sent"))it.copy(status="Failed")else it})}}
    private fun stopResources(){generation.incrementAndGet();synchronized(guard){runCatching{server?.close()};server=null;runCatching{connecting?.close()};connecting=null;links.values.toList().forEach{runCatching{it.session.close()};transfers.peerClosed(it.id)};links.clear();hub=null;polls.clear();receipts.clear();snapshots.clear()}}
    private fun failed(e:Exception){stop("Connection failed. Start the host, accept pairing and retry. ${e.javaClass.simpleName}")}
    private fun log(text:String){mutable.update{it.copy(events=(it.events+"${now()}  $text").takeLast(350))}}
    private fun now()=SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(Date())
    override fun onCleared(){stopResources();sos.close();transfers.close();features.shutdownNow();io.shutdownNow();runCatching{context.unregisterReceiver(receiver)}}
}
data class SosDraft(val title:String="Need assistance",val message:String="",val name:String="",val category:Int=1,val location:String="",val shareLocation:Boolean=false,val minutes:Int=15)
