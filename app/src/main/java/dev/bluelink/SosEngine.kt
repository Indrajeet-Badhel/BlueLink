package dev.bluelink

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import dev.bluelink.protocol.Beacon
import dev.bluelink.protocol.Record
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

val SosCategories=listOf("Medical emergency","Need assistance","Lost or stranded","Accident","Safety emergency","Other")
data class SosCard(val id:Int,val category:Int,val title:String,val rssi:Int,val detected:Long,val expires:Long,val details:Map<String,String> = emptyMap())
data class SosState(val active:Boolean=false,val starting:Boolean=false,val scanning:Boolean=false,val expires:Long=0,
    val cards:List<SosCard> = emptyList(),val status:String="SOS is off",val fetching:Int?=null)

@SuppressLint("MissingPermission")
class SosEngine(private val context:Context,private val report:(String)->Unit):Closeable {
    private val manager=context.getSystemService(BluetoothManager::class.java)
    private val adapter get()=manager?.adapter
    private val mutable=MutableStateFlow(SosState())
    val state=mutable.asStateFlow()
    private val worker=Executors.newSingleThreadScheduledExecutor()
    private val serviceId=UUID.fromString("5d6e0a10-1f9c-4d13-b4f1-c0a117451001")
    private val titleId=UUID.fromString("5d6e0a11-1f9c-4d13-b4f1-c0a117451001")
    private val detailId=UUID.fromString("5d6e0a12-1f9c-4d13-b4f1-c0a117451001")
    private var server:BluetoothGattServer?=null
    private var client:BluetoothGatt?=null
    private var payload=byteArrayOf()
    private var broadcast:AdvertiseCallback?=null
    private var scan:ScanCallback?=null
    private var scanUntil=0L
    private val devices=ConcurrentHashMap<Int,BluetoothDevice>()
    private val seen=ConcurrentHashMap<Int,Long>()
    private val mtus=ConcurrentHashMap<String,Int>()
    private var generation=0L
    init {worker.scheduleAtFixedRate({
        val now=System.currentTimeMillis()
        if((state.value.active || state.value.starting) && now>=state.value.expires)stopBroadcast()
        if(state.value.scanning && now>=scanUntil)stopScan()
        mutable.update{it.copy(cards=it.cards.filter{c->c.expires>now && now-c.detected<45000})}
        val active=state.value.cards.map{it.id}.toSet();devices.keys.retainAll(active);seen.keys.retainAll(active)
    },1,1,TimeUnit.SECONDS)}
    private val gattCallback=object:BluetoothGattServerCallback(){
        override fun onMtuChanged(device:BluetoothDevice,mtu:Int){mtus[device.address]=mtu.coerceIn(23,517)}
        override fun onCharacteristicReadRequest(device:BluetoothDevice,requestId:Int,offset:Int,characteristic:BluetoothGattCharacteristic){
            val bytes=payload
            if(characteristic.uuid!=detailId || offset !in 0..bytes.size){server?.sendResponse(device,requestId,BluetoothGatt.GATT_INVALID_OFFSET,offset,null);return}
            val count=(mtus[device.address] ?: 23)-1
            server?.sendResponse(device,requestId,BluetoothGatt.GATT_SUCCESS,offset,bytes.copyOfRange(offset,minOf(offset+count,bytes.size)))
        }
        override fun onServiceAdded(status:Int,service:BluetoothGattService){
            if(!state.value.starting)return
            if(status==BluetoothGatt.GATT_SUCCESS)beginAdvertising() else {stopBroadcast();error("Could not publish SOS details service")}
        }
    }
    private var pendingBeacon:Beacon?=null
    private var pendingTitle=""
    @Synchronized fun host(title:String,message:String,name:String,category:Int,location:String,minutes:Int){
        try {
            require(category in 0..5 && minutes in listOf(5,15,30))
            require(title.isNotBlank() && title.toByteArray().size<=60 && message.toByteArray().size<=260 && name.toByteArray().size<=40 && location.toByteArray().size<=80){"SOS fields exceed the displayed byte limits"}
            if(!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) || adapter?.isMultipleAdvertisementSupported!=true || adapter?.bluetoothLeAdvertiser==null){error("This phone cannot host a BLE SOS advertisement");return}
            val data=Record(mapOf("title" to title,"message" to message,"name" to name,"category" to category.toString(),"location" to location)).encode()
            require(data.size<=512){"Shorten the SOS details to fit 512 bytes"}
            stopBroadcast();payload=data;pendingTitle=title
            val until=System.currentTimeMillis()+minutes*60000L
            pendingBeacon=Beacon(java.security.SecureRandom().nextInt().let{if(it==0)1 else it},category,(until+59999)/60000)
            mutable.update{it.copy(starting=true,expires=until,status="Publishing SOS service")}
            server=manager?.openGattServer(context,gattCallback) ?: throw IllegalStateException("GATT server unavailable")
            val service=BluetoothGattService(serviceId,BluetoothGattService.SERVICE_TYPE_PRIMARY)
            service.addCharacteristic(BluetoothGattCharacteristic(detailId,BluetoothGattCharacteristic.PROPERTY_READ,BluetoothGattCharacteristic.PERMISSION_READ))
            if(server?.addService(service)!=true){stopBroadcast();error("Could not register SOS service")}
        }catch(e:Exception){stopBroadcast();error(e.message ?: "SOS could not start")}
    }
    @Synchronized private fun beginAdvertising(){
        try {
            val beacon=pendingBeacon ?: return
            val callback=object:AdvertiseCallback(){
                override fun onStartSuccess(settingsInEffect:AdvertiseSettings){if(broadcast===this)mutable.update{it.copy(active=true,starting=false,status="SOS broadcasting • unverified public beacon")}}
                override fun onStartFailure(errorCode:Int){if(broadcast===this){stopBroadcast();error("BLE advertising failed with code $errorCode")}}
            }
            broadcast=callback
            val settings=AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY).setConnectable(true).setTimeout(0).build()
            val data=AdvertiseData.Builder().addServiceData(ParcelUuid(serviceId),beacon.encode()).setIncludeDeviceName(false).build()
            val response=AdvertiseData.Builder().addServiceData(ParcelUuid(titleId),Beacon.title(pendingTitle)).setIncludeDeviceName(false).build()
            adapter?.bluetoothLeAdvertiser?.startAdvertising(settings,data,response,callback)
        }catch(_:Exception){stopBroadcast();error("Cannot advertise. Check Bluetooth and permissions.")}
    }
    @Synchronized fun stopBroadcast(){
        broadcast?.let{runCatching{adapter?.bluetoothLeAdvertiser?.stopAdvertising(it)}};broadcast=null
        runCatching{server?.close()};server=null;pendingBeacon=null;payload=byteArrayOf();mtus.clear()
        mutable.update{it.copy(active=false,starting=false,expires=0,status=if(it.scanning)"Scanning nearby SOS beacons" else "SOS is off")}
    }
    @Synchronized fun startScan(){
        try {
            stopScan()
            val scanner=adapter?.bluetoothLeScanner ?: return error("BLE scanner unavailable on this phone")
            val callback=object:ScanCallback(){
                override fun onScanResult(callbackType:Int,result:ScanResult){
                    if(scan!==this)return
                    val now=System.currentTimeMillis()
                    val record=result.scanRecord ?: return
                    val beacon=Beacon.decode(record.getServiceData(ParcelUuid(serviceId)) ?: return,now/60000) ?: return
                    if(now-(seen[beacon.id] ?: 0)<500)return
                    seen[beacon.id]=now;devices[beacon.id]=result.device
                    val title=runCatching{dev.bluelink.protocol.PacketCodec.utf8(record.getServiceData(ParcelUuid(titleId)) ?: byteArrayOf())}.getOrDefault("").take(60).ifBlank{"SOS details available"}
                    mutable.update{s->val previous=s.cards.find{it.id==beacon.id};s.copy(cards=(s.cards.filterNot{it.id==beacon.id}+SosCard(beacon.id,beacon.category,title,result.rssi,now,beacon.expiresMinute*60000,previous?.details ?: emptyMap())).sortedByDescending{it.rssi}.take(50))}
                }
                override fun onScanFailed(errorCode:Int){if(scan===this){stopScan();error("SOS scan failed with code $errorCode")}}
            }
            scan=callback;scanUntil=System.currentTimeMillis()+10*60000
            scanner.startScan(listOf(ScanFilter.Builder().setServiceData(ParcelUuid(serviceId),byteArrayOf(1),byteArrayOf(-1)).build()),ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),callback)
            mutable.update{it.copy(scanning=true,status=if(it.active)"SOS broadcasting and scanning" else "Scanning nearby SOS beacons")}
        }catch(_:Exception){stopScan();error("SOS discovery needs Bluetooth and discovery permissions")}
    }
    @Synchronized fun stopScan(){scan?.let{runCatching{adapter?.bluetoothLeScanner?.stopScan(it)}};scan=null;mutable.update{it.copy(scanning=false,status=if(it.active)"SOS broadcasting" else "SOS is off")}}
    @Synchronized fun details(id:Int){
        val device=devices[id] ?: return error("Beacon no longer available. Scan again.")
        generation++;val token=generation;runCatching{client?.close()};client=null
        mutable.update{it.copy(fetching=id)}
        val cb=object:BluetoothGattCallback(){
            override fun onConnectionStateChange(gatt:BluetoothGatt,status:Int,newState:Int){
                if(token!=generation){gatt.close();return}
                if(status!=BluetoothGatt.GATT_SUCCESS || newState==BluetoothProfile.STATE_DISCONNECTED){finish(gatt,token,"SOS details connection closed");return}
                if(newState==BluetoothProfile.STATE_CONNECTED && !gatt.discoverServices())finish(gatt,token,"Cannot discover SOS details")
            }
            override fun onServicesDiscovered(gatt:BluetoothGatt,status:Int){
                val characteristic=gatt.getService(serviceId)?.getCharacteristic(detailId)
                if(token!=generation || status!=0 || characteristic==null || !gatt.readCharacteristic(characteristic))finish(gatt,token,"SOS details unavailable")
            }
            @Deprecated("Legacy callback") override fun onCharacteristicRead(gatt:BluetoothGatt,characteristic:BluetoothGattCharacteristic,status:Int){
                if(Build.VERSION.SDK_INT<33)receive(gatt,token,id,characteristic.value ?: byteArrayOf(),status)
            }
            override fun onCharacteristicRead(gatt:BluetoothGatt,characteristic:BluetoothGattCharacteristic,value:ByteArray,status:Int){receive(gatt,token,id,value,status)}
        }
        try {client=device.connectGatt(context,false,cb,BluetoothDevice.TRANSPORT_LE);worker.schedule({if(token==generation && state.value.fetching!=null)client?.let{finish(it,token,"SOS details timed out")}},15,TimeUnit.SECONDS)}
        catch(_:Exception){mutable.update{it.copy(fetching=null)};error("SOS GATT connection could not start")}
    }
    private fun receive(gatt:BluetoothGatt,token:Long,id:Int,value:ByteArray,status:Int){
        if(token!=generation){gatt.close();return}
        try {
            require(status==0 && value.size<=512)
            val details=Record.decode(value).fields
            require(details["title"]?.isNotBlank()==true && (details["category"]?.toIntOrNull() ?: -1) in 0..5)
            mutable.update{it.copy(cards=it.cards.map{c->if(c.id==id)c.copy(details=details)else c})}
            finish(gatt,token,null)
        }catch(_:Exception){finish(gatt,token,"Invalid or incomplete SOS details")}
    }
    @Synchronized private fun finish(gatt:BluetoothGatt,token:Long,message:String?){
        runCatching{gatt.close()};if(token==generation){generation++;client=null;mutable.update{it.copy(fetching=null)};if(message!=null)error(message)}
    }
    private fun error(text:String){mutable.update{it.copy(status=text)};report(text)}
    fun stopAll(){stopBroadcast();stopScan();generation++;runCatching{client?.close()};client=null;mutable.update{it.copy(fetching=null)}}
    override fun close(){stopAll();worker.shutdownNow()}
}
