package dev.bluelink.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bluelink.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable private fun FeatureSurface(content:@Composable ColumnScope.()->Unit){
    Column(Modifier.fillMaxWidth().background(Panel,Chunky).border(1.dp,Line,Chunky).padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp),content=content)
}
@Composable private fun FormField(label:String,value:String,onValue:(String)->Unit,max:Int=240){
    OutlinedTextField(value=value,onValueChange={if(it.length<=max)onValue(it)},label={Text(label)},modifier=Modifier.fillMaxWidth(),maxLines=4,shape=RoundedCornerShape(16.dp))
}
@Composable fun LinkExtras(state:AppState,model:BlueLinkModel,action:(RadioAction,String?)->Unit){
    var create by remember{mutableStateOf(false)}
    var name by rememberSaveable{mutableStateOf("Slime circle")}
    var description by rememberSaveable{mutableStateOf("")}
    val context=LocalContext.current
    val clipboard=LocalClipboardManager.current
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            OutlinedButton(onClick={model.select(Destination.SOS)},modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text("SOS BEACON",color=Yellow)}
            OutlinedButton(onClick={model.select(Destination.ARCADE)},modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text("MINI ARCADE",color=Lime)}
        }
        val group=state.group
        if(group==null)OutlinedButton(onClick={create=true},enabled=!state.connected && !state.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
            Text("Create a group • host up to 4 peers",color=Paper)
        }else FeatureSurface{
            Text(group.name,color=Lime,fontWeight=FontWeight.Black,fontSize=19.sp)
            Text(group.description,color=Muted,fontSize=12.sp)
            Text("${group.members.size} members • ${if(group.hosting)"You host this room" else "Connected through host"}",color=Paper,fontSize=12.sp)
            Text("Group ${group.id}",color=Muted,fontSize=10.sp)
            group.members.forEach{member->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Text(member.name,color=Paper,modifier=Modifier.weight(1f))
                if(group.hosting && member.id!=state.selfId)TextButton(onClick={model.removeMember(member.id)}){Text("Remove",color=Pink)}
            }}
            if(group.hosting){
                TextButton(onClick={action(RadioAction.DISCOVER,null)}){Text("Invite nearby • make host visible",color=Lime)}
                TextButton(onClick={clipboard.setText(AnnotatedString("Join my BlueLink group ${group.name}. Enable Bluetooth, open BlueLink, tap Find a peer and choose ${state.localName}. Group ID ${group.id}."));model.notice("Joining instructions copied. Keep this host open while members join.")}){Text("Copy joining instructions",color=Lime)}
            }
        }
    }
    if(create)AlertDialog(onDismissRequest={create=false},title={Text("Create group")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
        FormField("Group name",name,{name=it},40);FormField("Description",description,{description=it},120)
        Text("Keep this phone in BlueLink. Members connect to this host; leaving closes the room.",fontSize=12.sp)
    }},confirmButton={TextButton(onClick={model.groupDraftName=name;model.groupDraftDescription=description;create=false;action(RadioAction.HOST_GROUP,null)},enabled=name.isNotBlank()){Text("Start hosting")}},dismissButton={TextButton(onClick={create=false}){Text("Cancel")}})
}

@Composable fun ChatTools(state:AppState,model:BlueLinkModel,pick:()->Unit){
    var create by remember{mutableStateOf(false)}
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(9.dp)){
        OutlinedButton(onClick=pick,enabled=state.connected && state.extended,modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text("Attach document",fontSize=12.sp)}
        OutlinedButton(onClick={create=true},enabled=state.connected && state.extended,modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text("Create poll",fontSize=12.sp)}
    }
    if(state.group!=null)Text("${state.group.name} • ${state.group.members.size} members • routed through host",color=Muted,fontSize=10.sp,modifier=Modifier.padding(vertical=5.dp))
    if(create)PollDialog(model){create=false}
}
@Composable private fun PollDialog(model:BlueLinkModel,dismiss:()->Unit){
    var question by rememberSaveable{mutableStateOf("")}
    var options by rememberSaveable{mutableStateOf("Option one\nOption two")}
    var multiple by rememberSaveable{mutableStateOf(false)}
    var change by rememberSaveable{mutableStateOf(false)}
    var minutes by rememberSaveable{mutableIntStateOf(0)}
    AlertDialog(onDismissRequest=dismiss,title={Text("New poll")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        FormField("Question • 240 UTF-8 bytes",question,{question=it},240)
        FormField("2–8 options • one per line",options,{options=it},960)
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(multiple,{multiple=it});Text("Allow multiple selections")}
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(change,{change=it});Text("Allow vote changes")}
        Text("Close after")
        Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf(0,5,15).forEach{n->FilterChip(selected=minutes==n,onClick={minutes=n},label={Text(if(n==0)"No limit" else "$n min")})}}
    }},confirmButton={TextButton(onClick={model.createPoll(question,options.lines().filter{it.isNotBlank()},multiple,change,minutes);dismiss()},enabled=question.isNotBlank() && options.lines().filter{it.isNotBlank()}.size in 2..8){Text("Create")}},dismissButton={TextButton(onClick=dismiss){Text("Cancel")}})
}
@Composable fun PollCard(view:PollView,model:BlueLinkModel){
    val r=view.record;val id=r["id"]
    var selected by remember(id,view.choice){mutableStateOf(view.choice)}
    var now by remember{mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(id){while(true){kotlinx.coroutines.delay(1000);now=System.currentTimeMillis()}}
    val expired=(r["expires"].toLongOrNull() ?: 0).let{it>0 && now>=it}
    val closed=r["closed"]=="true" || expired
    val count=(r["count"].toIntOrNull() ?: 0).coerceIn(0,8)
    val total=(r["total"].toIntOrNull() ?: 0).coerceAtLeast(0)
    FeatureSurface{
        Text("LIVE POLL",color=Yellow,fontSize=10.sp,fontWeight=FontWeight.Bold)
        Text(r["question"],color=Paper,fontWeight=FontWeight.Bold,fontSize=16.sp)
        repeat(count){i->
            val votes=r["votes$i"].toIntOrNull() ?: 0
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Checkbox(checked=i in selected,enabled=!closed,onCheckedChange={on->selected=if(r["multiple"]=="true")if(on)selected+i else selected-i else if(on)setOf(i)else emptySet()})
                Column(Modifier.weight(1f)){
                    Text(r["option$i"],color=Paper,fontSize=13.sp)
                    Text("$votes votes • ${if(total==0)0 else votes*100/total}% of voters",color=Muted,fontSize=10.sp)
                }
            }
        }
        Text("$total voters • ${if(closed)"Closed" else "Open"}${if(r["multiple"]=="true")" • multi-select percentages may exceed 100%" else ""}",color=Muted,fontSize=11.sp)
        Text(view.status,color=Lime,fontSize=10.sp)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Button(onClick={model.vote(id,selected)},enabled=!closed && selected.isNotEmpty()){Text("Submit vote")}
            if(view.owned)TextButton(onClick={model.closePoll(id)},enabled=!closed){Text("Close poll")}
        }
    }
}

@Composable fun TransferCard(view:TransferView,model:BlueLinkModel,accept:(String,String)->Unit){
    val context=LocalContext.current
    val active=view.status in listOf("Offered","Receiving","Sending")
    FeatureSurface{
        Text(if(view.incoming)"INCOMING DOCUMENT" else "OUTGOING DOCUMENT",color=Yellow,fontWeight=FontWeight.Bold,fontSize=10.sp)
        Text(view.name,color=Paper,fontWeight=FontWeight.Bold)
        Text("${view.size} bytes • ${view.mime}",color=Muted,fontSize=10.sp)
        LinearProgressIndicator(progress={if(view.size>0)(view.progress.toFloat()/view.size).coerceIn(0f,1f)else 0f},modifier=Modifier.fillMaxWidth(),color=Lime,trackColor=Line)
        Text("${view.status} • ${view.progress}/${view.size} B • ${view.speed} B/s",color=Muted,fontSize=11.sp)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            if(view.incoming && view.status=="Offered"){
                Button(onClick={accept(view.id,view.name)}){Text("Accept and save")}
                TextButton(onClick={model.transfers.cancel(view.id,true)}){Text("Reject")}
            }else if(active)TextButton(onClick={model.transfers.cancel(view.id)}){Text("Cancel transfer")}
            if(view.uri!=null)TextButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(view.uri),view.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}.onFailure{model.notice("No compatible document viewer is installed.")}}){Text("Open file")}
        }
    }
}

@Composable fun SosScreen(model:BlueLinkModel,action:(RadioAction,String?)->Unit){
    val state by model.sos.state.collectAsStateWithLifecycle()
    var title by rememberSaveable{mutableStateOf(model.sosDraft.title)}
    var message by rememberSaveable{mutableStateOf("")}
    var name by rememberSaveable{mutableStateOf("")}
    var location by rememberSaveable{mutableStateOf("")}
    var share by rememberSaveable{mutableStateOf(false)}
    var category by rememberSaveable{mutableIntStateOf(1)}
    var minutes by rememberSaveable{mutableIntStateOf(15)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        TextButton(onClick={model.select(Destination.LINK)}){Text("← Back to Link",color=Lime)}
        Text("SOS BEACON",color=Yellow,fontSize=30.sp,fontWeight=FontWeight.Black)
        Text("Public BLE assistance beacon. No chat pairing required. This is not a guaranteed emergency channel or a replacement for emergency services.",color=Muted,fontSize=12.sp)
        FeatureSurface{
            Text(state.status,color=if(state.active)Yellow else Paper,fontWeight=FontWeight.Bold)
            if(state.active || state.starting){
                Text("Expires ${SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date(state.expires))}",color=Muted)
                Button(onClick=model.sos::stopBroadcast,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp),colors=ButtonDefaults.buttonColors(containerColor=Yellow,contentColor=Plum)){Text("STOP SOS BROADCAST")}
            }else{
                FormField("Emergency title • 60 UTF-8 bytes",title,{title=it},60)
                FormField("Message • 260 UTF-8 bytes",message,{message=it},260)
                FormField("Optional name • 40 UTF-8 bytes",name,{name=it},40)
                Text("Category",color=Muted)
                SosCategories.forEachIndexed{i,label->Row(verticalAlignment=Alignment.CenterVertically){RadioButton(selected=category==i,onClick={category=i});Text(label,color=Paper,fontSize=12.sp)}}
                Row(verticalAlignment=Alignment.CenterVertically){Checkbox(share,{share=it});Text("Explicitly share a location I type",color=Paper,fontSize=12.sp)}
                if(share)FormField("Optional location • no GPS collection • 80 bytes",location,{location=it},80)
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(5,15,30).forEach{n->FilterChip(selected=n==minutes,onClick={minutes=n},label={Text("$n min")})}}
                Button(onClick={model.sosDraft=SosDraft(title,message,name,category,location,share,minutes);action(RadioAction.SOS_HOST,null)},enabled=title.isNotBlank(),modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("Host SOS")}
                Text("Title previews may be truncated to 13 bytes. Full details use a read-only GATT connection. Broadcasting starts only after Android reports success.",color=Muted,fontSize=10.sp)
            }
        }
        Button(onClick={if(state.scanning)model.sos.stopScan()else action(RadioAction.SOS_SCAN,null)},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text(if(state.scanning)"Stop SOS scan" else "Scan SOS")}
        Text("Keep this screen open. Discovery permissions support nearby beacon detection; no GPS coordinates are collected. RSSI is signal strength, not an exact distance.",color=Muted,fontSize=11.sp)
        if(state.cards.isEmpty())Text("No live beacons detected yet.",color=Muted)
        state.cards.forEach{card->FeatureSurface{
            Text("UNVERIFIED SOS • ${SosCategories[card.category]}",color=Yellow,fontWeight=FontWeight.Bold,fontSize=12.sp)
            Text(card.details["title"] ?: card.title,color=Paper,fontWeight=FontWeight.Bold)
            Text("Beacon ${Integer.toUnsignedString(card.id,16)} • ${card.rssi} dBm • ${if(card.rssi> -65)"Nearby signal" else if(card.rssi> -85)"Signal detected" else "Weak signal"}",color=Muted,fontSize=11.sp)
            Text("Detected ${SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(Date(card.detected))}",color=Muted,fontSize=10.sp)
            if(card.details.isEmpty())Text("Full emergency text requires an optional GATT read; pairing is not required.",color=Muted,fontSize=11.sp)
            else {
                Text(card.details["message"] ?: "",color=Paper)
                card.details["name"]?.takeIf{it.isNotBlank()}?.let{Text("From $it",color=Muted)}
                card.details["location"]?.takeIf{it.isNotBlank()}?.let{Text("Location shared by host: $it",color=Muted)}
            }
            TextButton(onClick={action(RadioAction.SOS_DETAILS,card.id.toString())},enabled=state.fetching==null){Text(if(state.fetching==card.id)"Reading…" else "Read full details")}
        }}
    }
}
