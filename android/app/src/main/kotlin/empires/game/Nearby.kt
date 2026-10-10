package empires.game

// Игра вдвоём на двух телефонах рядом: Nearby Connections (Wi-Fi и Bluetooth, без сервера и интернета).
// Хозяин объявляет игру, гость находит её и подключается. Дальше обе стороны обмениваются строками.

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import com.badlogic.gdx.Gdx
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy

// События связи приходят в поток игры (через Gdx.app.postRunnable)
interface NearbyListener {
    fun onFound(endpointId: String, name: String) {}
    fun onLost(endpointId: String) {}
    fun onConnected(peerName: String) {}
    fun onMessage(text: String) {}
    fun onDisconnected() {}
    fun onError(text: String) {}
}

interface NearbyLink {
    fun host(name: String, listener: NearbyListener)
    fun discover(name: String, listener: NearbyListener)
    fun connect(endpointId: String)
    fun send(text: String)
    fun stop()
}

class AndroidNearby(private val activity: AndroidLauncher) : NearbyLink {
    private val client by lazy { Nearby.getConnectionsClient(activity) }
    private var listener: NearbyListener? = null
    private var peer: String? = null
    private var myName = "Игрок"
    private var pendingStart: (() -> Unit)? = null

    private fun ui(f: NearbyListener.() -> Unit) { val l = listener ?: return; Gdx.app.postRunnable { l.f() } }

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            ui { onMessage(String(bytes, Charsets.UTF_8)) }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private val peerNames = HashMap<String, String>()
    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            peerNames[endpointId] = info.endpointName
            client.acceptConnection(endpointId, payloads)
        }
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                peer = endpointId
                client.stopAdvertising(); client.stopDiscovery()
                ui { onConnected(peerNames[endpointId] ?: "Соперник") }
            } else ui { onError("Не удалось подключиться.") }
        }
        override fun onDisconnected(endpointId: String) {
            if (endpointId == peer) { peer = null; ui { onDisconnected() } }
        }
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId == SERVICE_ID) ui { onFound(endpointId, info.endpointName) }
        }
        override fun onEndpointLost(endpointId: String) { ui { onLost(endpointId) } }
    }

    override fun host(name: String, listener: NearbyListener) {
        this.listener = listener; myName = name
        withPermissions {
            client.startAdvertising(name, SERVICE_ID, lifecycle, AdvertisingOptions.Builder().setStrategy(STRATEGY).build())
                .addOnFailureListener { e -> ui { onError("Не удалось объявить игру: ${e.message ?: "ошибка"}") } }
        }
    }

    override fun discover(name: String, listener: NearbyListener) {
        this.listener = listener; myName = name
        withPermissions {
            client.startDiscovery(SERVICE_ID, discovery, DiscoveryOptions.Builder().setStrategy(STRATEGY).build())
                .addOnFailureListener { e -> ui { onError("Не удалось начать поиск: ${e.message ?: "ошибка"}") } }
        }
    }

    override fun connect(endpointId: String) {
        client.requestConnection(myName, endpointId, lifecycle)
            .addOnFailureListener { e -> ui { onError("Не удалось подключиться: ${e.message ?: "ошибка"}") } }
    }

    override fun send(text: String) {
        val p = peer ?: return
        client.sendPayload(p, Payload.fromBytes(text.toByteArray(Charsets.UTF_8)))
    }

    override fun stop() {
        if (peer == null && listener == null) return
        runCatching { client.stopAdvertising(); client.stopDiscovery(); client.stopAllEndpoints() }
        peer = null
        listener = null
    }

    /* ---------- Разрешения: Bluetooth и Wi-Fi рядом (Android 12+), геолокация (до Android 12) ---------- */
    private fun required(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= 31) { add(Manifest.permission.BLUETOOTH_ADVERTISE); add(Manifest.permission.BLUETOOTH_CONNECT); add(Manifest.permission.BLUETOOTH_SCAN) }
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
        if (Build.VERSION.SDK_INT <= 31) add(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun withPermissions(start: () -> Unit) {
        activity.runOnUiThread {
            val missing = required().filter { activity.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isEmpty()) start()
            else { pendingStart = start; activity.requestPermissions(missing.toTypedArray(), REQ) }
        }
    }

    fun onPermissions(requestCode: Int, results: IntArray) {
        if (requestCode != REQ) return
        val start = pendingStart; pendingStart = null
        if (results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED }) start?.invoke()
        else ui { onError("Без разрешения на поиск устройств рядом сетевая игра недоступна.") }
    }

    companion object {
        private const val SERVICE_ID = "io.github.wiverhome.empires"
        private val STRATEGY = Strategy.P2P_POINT_TO_POINT
        private const val REQ = 4217
    }
}
