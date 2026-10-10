package empires.game

import android.content.Intent
import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration

// Вход в приложение: libGDX рисует сам (OpenGL ES), без WebView
class AndroidLauncher : AndroidApplication(), Platform {
    override lateinit var nearby: NearbyLink
    private lateinit var nearbyLink: AndroidNearby

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // игра рядом: Nearby (Bluetooth и Wi-Fi Direct) и одна сеть Wi-Fi одновременно
        nearbyLink = AndroidNearby(this)
        nearby = DualLink(nearbyLink, LanLink(this))
        val config = AndroidApplicationConfiguration().apply {
            useAccelerometer = false
            useCompass = false
            useImmersiveMode = true
            numSamples = 0
        }
        initialize(EmpiresGame(this), config)
    }

    // Поделиться кодом карты через любой мессенджер
    override fun shareText(title: String, text: String) {
        runOnUiThread {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(send, title))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        nearbyLink.onPermissions(requestCode, grantResults)
    }
}

// То, что игре нужно от платформы
interface Platform {
    val nearby: NearbyLink
    fun shareText(title: String, text: String)
}
