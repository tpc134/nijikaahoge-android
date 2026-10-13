package com.ruich97.nijikaahoge.game

import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of C3 Particles plugin. Params layout (from plugin source):
 * [rate, sprayConeDeg, type, spawnObjClass, speed, size, opacity,
 *  growRate, xRand, yRand, speedRand, sizeRand, growRand, accel,
 *  gravity, angleRand, lifeSpeedRand, lifeOpacityRand, destroyMode, timeout]
 */
class Emitter(
    val type: ObjType, val p: DoubleArray,
    var x: Float, var y: Float, var angle: Float, var layer: Int
) {
    class P(
        var x: Float, var y: Float, var speed: Float, var angle: Float,
        var opacity: Float, var size: Float, var grow: Float,
        var gs: Float, var age: Float
    )

    val particles = ArrayList<P>()
    private val rnd = Random()
    var dead = false

    private fun ro(v: Float) = (rnd.nextFloat() - 0.5f) * v

    fun burst() {
        val rate = p[0].toInt()
        val cone = Math.toRadians(p[1])
        for (i in 0 until rate) {
            particles.add(
                P(
                    x + ro(p[8].toFloat()), y + ro(p[9].toFloat()),
                    p[4].toFloat() + ro(p[10].toFloat()),
                    angle + ro(cone.toFloat()).toFloat(),
                    (p[6] / 100.0).toFloat(),
                    p[5].toFloat() + ro(p[11].toFloat()),
                    p[7].toFloat() + ro(p[12].toFloat()),
                    0f, 0f
                )
            )
        }
    }

    fun tick(dt: Float) {
        val accel = p[13].toFloat(); val grav = p[14].toFloat()
        val angR = Math.toRadians(p[15]).toFloat()
        val spdR = p[16].toFloat(); val opaR = (p[17] / 100.0).toFloat()
        val destroy = p[18].toInt(); val timeout = p[19].toFloat()
        val it = particles.iterator()
        while (it.hasNext()) {
            val q = it.next()
            val dist = q.speed * dt
            q.x += cos(q.angle) * dist
            q.y += sin(q.angle) * dist + q.gs * dt
            q.size += q.grow * dt
            q.speed += accel * dt
            q.gs += grav * dt
            q.age += dt
            if (angR != 0f) q.angle += ro(angR * dt)
            if (spdR != 0f) q.speed += ro(spdR * dt)
            if (opaR != 0f) q.opacity = (q.opacity + ro(opaR * dt)).coerceIn(0f, 1f)
            val alive = q.size >= 1 && if (destroy == 2) q.speed > 0 else q.age < timeout
            if (!alive) it.remove()
        }
        if (particles.isEmpty()) dead = true
    }

    fun opacity(q: P): Float {
        val base = q.opacity
        return if (p[18].toInt() == 0) base * (1f - q.age / p[19].toFloat()) else base
    }
}

class Parts {
    val emitters = ArrayList<Emitter>()

    /** Spawn a one-shot emitter at (x,y,angleRad). type name like 粒子, 粒子2 ... */
    fun spawn(types: Map<String, ObjType>, propsByType: Map<String, DoubleArray>,
              name: String, x: Float, y: Float, angle: Float, layer: Int): Emitter? {
        val t = types[name] ?: return null
        val p = propsByType[name] ?: return null
        val e = Emitter(t, p, x, y, angle, layer)
        e.burst()
        emitters.add(e)
        return e
    }

    fun tick(dt: Float) {
        val it = emitters.iterator()
        while (it.hasNext()) {
            val e = it.next()
            e.tick(dt)
            if (e.dead) it.remove()
        }
    }

    fun clear() = emitters.clear()
}
