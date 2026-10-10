package empires.game

// Асинхронные сценарии партии (как async/await в game.js) - корутины в потоке рисования libGDX.
// tween(ms) ждёт анимацию, которую двигает Animator.update(dt) каждый кадр.

import com.badlogic.gdx.Gdx
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.math.pow

object GdxDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) { Gdx.app.postRunnable(block) }
}

class Animator {
    private class Tween(val ms: Float, val fn: (Float) -> Unit, val done: () -> Unit) { var t = 0f }
    private val list = ArrayList<Tween>()
    var clockMs = 0f
        private set
    private val timers = ArrayList<Pair<Float, () -> Unit>>()

    fun update(dt: Float) {
        clockMs += dt * 1000f
        for (tw in list.toList()) {
            tw.t += dt * 1000f
            val k = (tw.t / tw.ms).coerceAtMost(1f)
            tw.fn(k)
            if (k >= 1f) { list.remove(tw); tw.done() }
        }
        val due = timers.filter { it.first <= clockMs }
        if (due.isNotEmpty()) { timers.removeAll(due.toSet()); due.forEach { it.second() } }
    }

    suspend fun tween(ms: Float, fn: (Float) -> Unit) = suspendCancellableCoroutine { cont ->
        val tw = Tween(ms.coerceAtLeast(1f), fn) { if (cont.isActive) cont.resume(Unit) }
        list.add(tw)
        cont.invokeOnCancellation { Gdx.app.postRunnable { list.remove(tw) } }
    }

    // Пауза по игровым часам (не идёт, пока приложение свёрнуто)
    suspend fun sleep(ms: Float) = suspendCancellableCoroutine { cont ->
        val entry = (clockMs + ms) to { if (cont.isActive) cont.resume(Unit) }
        timers.add(entry)
        cont.invokeOnCancellation { Gdx.app.postRunnable { timers.remove(entry) } }
    }

    fun clear() { list.clear(); timers.clear() }
}

fun ease(k: Float) = if (k < 0.5f) 2 * k * k else 1 - (-2 * k + 2).pow(2) / 2

fun newGameScope() = CoroutineScope(SupervisorJob() + GdxDispatcher)
fun CoroutineScope.stop() = cancel()
