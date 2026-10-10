package empires.game

// Второй канал игры вдвоём: одна сеть Wi-Fi (TCP), без сервисов Google.
// Хозяин слушает TCP-порт и раз в секунду объявляет игру UDP-рассылкой; гость ловит рассылку или вводит адрес вручную.
// Сообщения - строки JSON, по одной на строку (JSON не содержит переводов строк).

import android.content.Context
import android.net.wifi.WifiManager
import com.badlogic.gdx.Gdx
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import kotlin.concurrent.thread

const val LAN_PORT = 47123
private const val LAN_BEACON_PORT = 47124
private const val LAN_MAGIC = "EMPIRES1"

class LanLink(private val context: Context) : NearbyLink {
    @Volatile private var listener: NearbyListener? = null
    @Volatile private var running = false
    private var server: ServerSocket? = null
    private var socket: Socket? = null
    private var out: PrintWriter? = null
    private var beacon: DatagramSocket? = null
    private var lock: WifiManager.MulticastLock? = null
    private val sender = Executors.newSingleThreadExecutor()
    private var myName = "Игрок"

    private fun ui(f: NearbyListener.() -> Unit) { val l = listener ?: return; Gdx.app.postRunnable { if (listener === l) l.f() } }

    override fun host(name: String, listener: NearbyListener) {
        stop()
        this.listener = listener; myName = name; running = true
        thread(name = "lan-host", isDaemon = true) {
            try {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(LAN_PORT))
                server = ss
                startBeacon(name)
                val s = ss.accept()
                stopBeacon()
                runCatching { ss.close() }
                attach(s)
            } catch (e: Exception) {
                if (running) ui { onError("Сеть Wi-Fi: не удалось открыть игру (${e.message ?: "ошибка"}).") }
            }
        }
    }

    private fun startBeacon(name: String) {
        thread(name = "lan-beacon", isDaemon = true) {
            try {
                val ds = DatagramSocket()
                ds.broadcast = true
                beacon = ds
                val data = "$LAN_MAGIC|$LAN_PORT|$name".toByteArray(Charsets.UTF_8)
                while (running && server != null && socket == null) {
                    runCatching { ds.send(DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), LAN_BEACON_PORT)) }
                    Thread.sleep(1000)
                }
            } catch (_: Exception) {}
        }
    }

    private fun stopBeacon() { runCatching { beacon?.close() }; beacon = null }

    override fun discover(name: String, listener: NearbyListener) {
        stop()
        this.listener = listener; myName = name; running = true
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        lock = wifi?.createMulticastLock("empires")?.apply { setReferenceCounted(false); runCatching { acquire() } }
        thread(name = "lan-discover", isDaemon = true) {
            try {
                val ds = DatagramSocket(null)
                ds.reuseAddress = true
                ds.bind(InetSocketAddress(LAN_BEACON_PORT))
                ds.soTimeout = 1000
                beacon = ds
                val buf = ByteArray(512)
                val seen = HashSet<String>()
                while (running && socket == null) {
                    val p = DatagramPacket(buf, buf.size)
                    try { ds.receive(p) } catch (_: java.net.SocketTimeoutException) { continue }
                    val parts = String(p.data, 0, p.length, Charsets.UTF_8).split("|", limit = 3)
                    if (parts.size < 3 || parts[0] != LAN_MAGIC) continue
                    val id = "${p.address.hostAddress}:${parts[1]}"
                    if (seen.add(id)) ui { onFound(id, parts[2] + " (Wi-Fi)") }
                }
            } catch (_: Exception) {
                // порт рассылки занят или сети нет: остаётся ввод адреса вручную
            }
        }
    }

    // endpointId - "адрес:порт" (из рассылки или введённый вручную)
    override fun connect(endpointId: String) {
        val host = endpointId.substringBeforeLast(":")
        val port = endpointId.substringAfterLast(":").toIntOrNull() ?: LAN_PORT
        running = true
        thread(name = "lan-connect", isDaemon = true) {
            try {
                val s = Socket()
                s.connect(InetSocketAddress(host, port), 5000)
                stopBeacon()
                attach(s)
            } catch (e: Exception) {
                ui { onError("Не удалось подключиться к $host:$port.") }
            }
        }
    }

    // Соединение есть: знакомимся именами и читаем строки до обрыва
    private fun attach(s: Socket) {
        socket = s
        s.tcpNoDelay = true
        val w = PrintWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8), true)
        out = w
        sender.execute { w.println("$LAN_MAGIC|$myName") }
        val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
        try {
            val hello = reader.readLine() ?: throw java.io.EOFException()
            val parts = hello.split("|", limit = 2)
            if (parts[0] != LAN_MAGIC) throw IllegalStateException("чужой протокол")
            ui { onConnected(parts.getOrElse(1) { "Соперник" }) }
            while (running) {
                val line = reader.readLine() ?: break
                ui { onMessage(line) }
            }
        } catch (_: Exception) {}
        if (running) ui { onDisconnected() }
        closeSocket()
    }

    override fun send(text: String) {
        val w = out ?: return
        sender.execute { w.println(text) }
    }

    private fun closeSocket() {
        runCatching { socket?.close() }; socket = null; out = null
    }

    override fun stop() {
        running = false
        listener = null
        stopBeacon()
        runCatching { server?.close() }; server = null
        closeSocket()
        runCatching { lock?.release() }; lock = null
    }
}

// Оба канала сразу: хозяин объявляет игру и через Nearby, и в Wi-Fi; первое соединение выигрывает
class DualLink(private val nearby: NearbyLink, private val lan: NearbyLink) : NearbyLink {
    private var active: NearbyLink? = null

    private fun wrap(own: NearbyLink, other: NearbyLink, prefix: String, l: NearbyListener) = object : NearbyListener {
        override fun onFound(endpointId: String, name: String) = l.onFound(prefix + endpointId, name)
        override fun onLost(endpointId: String) = l.onLost(prefix + endpointId)
        override fun onConnected(peerName: String) {
            if (active != null && active !== own) return
            active = own
            other.stop()
            l.onConnected(peerName)
        }
        override fun onMessage(text: String) { if (active === own) l.onMessage(text) }
        override fun onDisconnected() { if (active === own) l.onDisconnected() }
        // ошибка одного канала не мешает другому, пока ни один не соединился
        override fun onError(text: String) { if (active === own || (active == null && own === lan)) l.onError(text) }
    }

    override fun host(name: String, listener: NearbyListener) {
        active = null
        lan.host(name, wrap(lan, nearby, "lan:", listener))
        nearby.host(name, wrap(nearby, lan, "nb:", listener))
    }

    override fun discover(name: String, listener: NearbyListener) {
        active = null
        lan.discover(name, wrap(lan, nearby, "lan:", listener))
        nearby.discover(name, wrap(nearby, lan, "nb:", listener))
    }

    override fun connect(endpointId: String) = when {
        endpointId.startsWith("lan:") -> lan.connect(endpointId.removePrefix("lan:"))
        endpointId.startsWith("nb:") -> nearby.connect(endpointId.removePrefix("nb:"))
        else -> lan.connect(endpointId)
    }

    override fun send(text: String) { active?.send(text) }

    override fun stop() { active = null; nearby.stop(); lan.stop() }
}
