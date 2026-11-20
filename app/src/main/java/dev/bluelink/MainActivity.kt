package dev.bluelink

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import dev.bluelink.ui.BlueLinkApp

class MainActivity : ComponentActivity() {
    private val model: BlueLinkModel by viewModels()
    private var pendingAction: RadioAction? = null
    private var pendingPeer: String? = null
    private var systemFlow = false
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        systemFlow = false
        if (granted.values.all { it }) ensureEnabled()
        else { pendingAction = null; model.notice("Allow the requested Bluetooth permissions to continue. You can change them in Android Settings.") }
    }
    private val enable = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        systemFlow = false
        model.refresh()
        if (model.state.value.enabled) performAction()
        else { pendingAction = null; model.notice("Bluetooth must be enabled to link the phones.") }
    }
    private val discover = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        systemFlow = false
        model.notice(if (it.resultCode > 0) "Visible to nearby devices for ${it.resultCode} seconds. Keep server mode running." else "Discoverability was not enabled.")
    }
    private val pickDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        systemFlow = false
        if (uri != null) model.sendDocument(uri)
    }
    private var incomingFile: String? = null
    private val saveDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        systemFlow = false
        val id=incomingFile; incomingFile=null
        if(uri!=null && id!=null)model.acceptFile(id,uri)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        systemFlow=savedInstanceState?.getBoolean("systemFlow") ?: false
        incomingFile=savedInstanceState?.getString("incomingFile")
        pendingPeer=savedInstanceState?.getString("pendingPeer")
        pendingAction=savedInstanceState?.getString("pendingAction")?.let{runCatching{RadioAction.valueOf(it)}.getOrNull()}
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            BlueLinkApp(state, model, ::requestAction,
                onPickDocument={systemFlow=true;pickDocument.launch(arrayOf("application/pdf","text/plain","application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/msword","application/rtf"))},
                onAcceptDocument={id,name->incomingFile=id;systemFlow=true;saveDocument.launch(name)})
        }
    }
    private fun requestAction(action: RadioAction, peer: String?) {
        if (!model.state.value.supported) return model.notice("This device does not support Bluetooth Classic.")
        pendingAction = action; pendingPeer = peer
        val missing = PermissionPolicy.required(action).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) { systemFlow = true; permissions.launch(missing.toTypedArray()) } else ensureEnabled()
    }
    private fun ensureEnabled() {
        model.refresh()
        if (model.state.value.enabled) performAction()
        else runCatching { systemFlow = true; enable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
            .onFailure { systemFlow = false; pendingAction = null; model.notice("Enable Bluetooth in Android Settings and retry.") }
    }
    private fun performAction() {
        val action = pendingAction; pendingAction = null
        when (action) {
            RadioAction.HOST -> model.host()
            RadioAction.HOST_GROUP -> model.hostGroup()
            RadioAction.SCAN -> model.scan()
            RadioAction.CONNECT -> pendingPeer?.let(model::connect)
            RadioAction.SOS_HOST -> model.startSos()
            RadioAction.SOS_SCAN -> model.sos.startScan()
            RadioAction.SOS_DETAILS -> pendingPeer?.toIntOrNull()?.let(model.sos::details)
            RadioAction.DISCOVER -> runCatching {
                systemFlow = true
                discover.launch(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120))
            }.onFailure { systemFlow = false; model.notice("Could not request discoverability. Check Nearby devices permission.") }
            null -> Unit
        }
    }
    override fun onResume() { super.onResume(); model.refresh() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("systemFlow",systemFlow)
        outState.putString("incomingFile",incomingFile)
        outState.putString("pendingPeer",pendingPeer)
        outState.putString("pendingAction",pendingAction?.name)
        super.onSaveInstanceState(outState)
    }
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !systemFlow) model.stop("App left the foreground. Return and reconnect.")
    }
}
