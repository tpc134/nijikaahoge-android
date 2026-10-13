package com.ruich97.nijikaahoge.game

import kotlin.math.PI
import kotlin.math.sin

/** Construct Tween eases actually used by the game: 1=linear(noease) 14=inSine 15=outSine 16=inOutSine */
object Ease {
    fun apply(idx: Int, t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return when (idx) {
            14 -> 1f - kotlin.math.cos(x * PI.toFloat() / 2f)
            16 -> -(kotlin.math.cos(PI.toFloat() * x) - 1f) / 2f
            15 -> sin(x * PI.toFloat() / 2f)
            else -> x
        }
    }
}

class Tween(
    val obj: Obj, var ease: Int, var dur: Float,
    var sx: Float, var sy: Float, var ex: Float, var ey: Float,
    var moveX: Boolean, var moveY: Boolean
) {
    var t = 0f
    val done get() = t >= dur
    fun tick(dt: Float) {
        t += dt
        val k = Ease.apply(ease, t / dur)
        if (moveX) obj.x = sx + (ex - sx) * k
        if (moveY) obj.y = sy + (ey - sy) * k
    }
}

class Tweens {
    private val list = ArrayList<Tween>()
    private val reusable = HashMap<Obj, Tween>()
    fun to(obj: Obj, ex: Float, dur: Float, ease: Int): Tween {
        return restart(obj, ex, obj.y, dur, ease, false)
    }
    fun toXY(obj: Obj, ex: Float, ey: Float, dur: Float, ease: Int): Tween {
        return restart(obj, ex, ey, dur, ease, true)
    }
    private fun restart(obj: Obj, ex: Float, ey: Float, dur: Float, ease: Int, moveY: Boolean): Tween {
        val tw = reusable.getOrPut(obj) { Tween(obj, ease, dur, obj.x, obj.y, ex, ey, true, moveY) }
        tw.sx = obj.x; tw.sy = obj.y; tw.ex = ex; tw.ey = ey
        tw.ease = ease; tw.dur = dur; tw.moveY = moveY; tw.t = 0f
        kill(obj)
        list.add(tw)
        return tw
    }
    fun kill(obj: Obj) { list.removeAll { it.obj === obj } }
    fun forget(obj: Obj) { kill(obj); reusable.remove(obj) }
    fun busy(obj: Obj): Boolean = list.any { it.obj === obj && !it.done }
    fun tick(dt: Float) {
        val it = list.iterator()
        while (it.hasNext()) {
            val tw = it.next()
            tw.tick(dt)
            if (tw.done) it.remove()
        }
    }
}

/**
 * Deferred-action scheduler reproducing C3's Wait / WaitForSignal /
 * WaitForPreviousActions semantics. Sequences are written as step lists:
 *   sched.seq( Wait(1f), Act{...}, WaitSignal("Z"), Act{...} )
 * Steps after a wait resume on the same object context (passed via lambda).
 */
class Scheduler {
    interface Step { fun advance(s: Seq): Boolean }
    class Act(val f: () -> Unit) : Step {
        override fun advance(s: Seq): Boolean { f(); return true }
    }
    class Wait(val sec: Float) : Step {
        override fun advance(s: Seq): Boolean {
            if (sec <= 0f) return true
            s.waitT = sec; return false
        }
    }
    class WaitSignal(val sig: String) : Step {
        override fun advance(s: Seq): Boolean { s.waitSig = sig; return false }
    }
    class WaitTweens(val obj: Obj, val tweens: Tweens) : Step {
        override fun advance(s: Seq): Boolean { s.waitTw = Pair(obj, tweens); return false }
    }

    class Seq(val steps: List<Step>, var i: Int = 0) {
        var waitT = 0f
        var waitSig: String? = null
        var waitTw: Pair<Obj, Tweens>? = null
        var dead = false
    }

    private val seqs = ArrayList<Seq>()
    private val pendingAdd = ArrayList<Seq>()
    private var ticking = false
    private val signals = HashMap<String, ArrayList<Seq>>()

    fun seq(vararg steps: Step) {
        val s = Seq(steps.toList())
        // actions may run new sequences while we iterate -> defer the add
        if (ticking) pendingAdd.add(s) else seqs.add(s)
        progress(s)
    }

    fun signal(name: String) {
        signals.remove(name)?.let { list -> for (s in list) progress(s) }
    }

    private fun progress(s: Seq) {
        s.waitSig = null; s.waitTw = null; s.waitT = 0f
        while (s.i < s.steps.size) {
            if (!s.steps[s.i].advance(s)) {
                s.i++
                s.waitSig?.let { sig -> signals.getOrPut(sig) { ArrayList() }.add(s) }
                return
            }
            s.i++
        }
        s.dead = true
    }

    fun tick(dt: Float) {
        ticking = true
        val it = seqs.iterator()
        while (it.hasNext()) {
            val s = it.next()
            if (s.dead) { it.remove(); continue }
            if (s.waitT > 0) {
                s.waitT -= dt
                if (s.waitT <= 0) { s.waitT = 0f; progress(s) }
            } else if (s.waitTw != null) {
                val (o, tw) = s.waitTw!!
                if (!tw.busy(o)) { s.waitTw = null; progress(s) }
            }
            // waitSig seqs resume via signal()
        }
        ticking = false
        if (pendingAdd.isNotEmpty()) { seqs.addAll(pendingAdd); pendingAdd.clear() }
    }
}
