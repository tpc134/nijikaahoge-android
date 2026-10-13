package com.ruich97.nijikaahoge.game

import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.SoundPool
import kotlin.math.pow

/** SoundPool wrapper matching C3 Audio: volume in dB, stereo pan -100..100. */
class Snd(private val am: AssetManager) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(12)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()
    // played from the GL thread, filled by the load path — keep them concurrent
    private val ids = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val fileNameById = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val requested: MutableSet<String> = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>())
    // ConcurrentHashMap.newKeySet() is API 24+; newSetFromMap gives the same
    // concurrent set semantics back to API 1, so minSdk can stay at 21.
    private val ready: MutableSet<Int> = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<Int, Boolean>())
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var released = false

    /** A play() that arrived while its clip was still decoding. */
    private class Pending(val name: String, val loop: Boolean, val volDb: Float, val pan: Float)
    // One slot per sample id: a burst of requests during the decode collapses
    // into a single late play rather than a clump of them firing together.
    private val pending = java.util.concurrent.ConcurrentHashMap<Int, Pending>()

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                ready.add(sampleId)
                // Anything that asked for this clip while it decoded fires now.
                pending.remove(sampleId)?.let { p ->
                    android.util.Log.i("Ahoge", "sound late: ${p.name}")
                    handler.post { startStream(sampleId, p.loop, p.volDb, p.pan) }
                }
            } else {
                android.util.Log.w("Ahoge", "sound load failed id=$sampleId status=$status")
                pending.remove(sampleId)
            }
        }
    }

    /**
     * Decode the [count] smallest clips up front, plus every id listed in
     * [extraIds], and leave the rest alone.
     *
     * SoundPool expands each clip to raw PCM on a single background thread, and
     * on a Cortex-A7 that runs at roughly real time: this bank is 28.5 s of
     * 48 kHz stereo AAC, so decoding all of it keeps one core busy for ~20 s
     * (measured on an MSM8909: 26 core-seconds of app CPU in the first 26 s with
     * audio on, versus 12-21 with the sound bank skipped). That thread competes
     * with the renderer — the game sat between 30 and 47 fps for the first ~24 s
     * with audio on, while the same build with audio disabled held 58-60 fps from
     * the 4th second on.
     *
     * The event sheet hands the frequent actions the short sounds (grab = 0.6 s,
     * bag collect = 0.26-0.49 s), so the four smallest clips cover everything a
     * player can trigger in the opening seconds for ~6 % of the decode work.
     * [extraIds] pulls the feeding cues (1 = 2.12 s, 2 = 0.64 s) in on top of
     * that: they are not among the four smallest, but a player feeds Ryo within
     * the first seconds, and a cold decode there is exactly what makes the
     * picture move before the sound arrives. The remaining clips are decoded on
     * first use — see [play] and [playLoaded].
     */
    fun preloadSmallest(names: List<String>, count: Int = 4, extraIds: Set<String> = emptySet()) {
        for (n in names) fileNameById[n.substringBeforeLast('.')] = n
        val bySize = names.sortedBy { n ->
            try {
                am.openFd("media/$n").use { it.length }
            } catch (e: Exception) {
                Long.MAX_VALUE
            }
        }
        val picks = LinkedHashSet<String>(bySize.take(count))
        if (extraIds.isNotEmpty()) {
            for (n in names) if (n.substringBeforeLast('.') in extraIds) picks.add(n)
        }
        for (n in picks) loadOne(n)
        android.util.Log.i("Ahoge", "sounds queued: ${ids.size}/${names.size} (rest on demand)")
    }

    private fun loadOne(name: String) {
        if (!requested.add(name)) return
        try {
            am.openFd("media/$name").use { fd ->
                ids[name.substringBeforeLast('.')] = pool.load(fd, 1)
            }
        } catch (e: Exception) {
            android.util.Log.w("Ahoge", "sound open failed $name")
        }
    }

    /** play("3", loop=false, volDb=-5, pan=-30..30) — pan -100..100 */
    fun play(name: String, loop: Boolean = false, volDb: Float = 0f, pan: Float = 0f) {
        // Decode on first use. The long cues (5-6 s each) are only reachable
        // after a lot of collecting, so paying for them at launch is wasted.
        if (ids[name] == null) fileNameById[name]?.let {
            android.util.Log.i("Ahoge", "sound on demand: $it")
            loadOne(it)
        }
        playLoaded(name, loop, volDb, pan)
    }

    private fun playLoaded(name: String, loop: Boolean, volDb: Float, pan: Float) {
        if (released) return
        val id = ids[name] ?: return          // unknown id, or the open failed
        if (id !in ready) {
            // Still decoding. Wait for onLoadComplete rather than polling on a
            // timer: the longest clip is 6 s of audio and takes longer to expand
            // than a short retry budget allows, so the old "4 x 400 ms then give
            // up" path dropped the cue outright. See [pending].
            pending[id] = Pending(name, loop, volDb, pan)
            return
        }
        startStream(id, loop, volDb, pan)
    }

    private fun startStream(id: Int, loop: Boolean, volDb: Float, pan: Float) {
        if (released || id !in ready) return
        val lin = 10f.pow(volDb / 20f).coerceIn(0f, 1f)
        val p = (pan / 100f).coerceIn(-1f, 1f)
        val l = if (p > 0) lin * (1f - p) else lin
        val r = if (p < 0) lin * (1f + p) else lin
        pool.play(id, l, r, 1, if (loop) -1 else 0, 1f)
    }

    fun release() {
        if (released) return
        released = true
        handler.removeCallbacksAndMessages(null)
        pool.release()
    }
}
