package com.ruich97.nijikaahoge.game

import kotlin.math.PI

/** The live scene: instances, physics, particles, tweens, scheduler, input, draw. */
class World(val data: GameData, val phys: Phys, val snd: Snd, val vib: Vib, val parts: Parts) {
    val objs = ArrayList<Obj>()
    private val byType = HashMap<String, ArrayList<Obj>>()
    private val ordered = ArrayList<Obj>()
    private var orderDirty = true
    private val drawComparator = compareBy<Obj>({ it.layer }, { it.z }, { it.uid })
    val tweens = Tweens()
    val sched = Scheduler()
    private var uidSeq = 1

    val rnd = java.util.Random()
    fun choose(vararg v: Int) = v[rnd.nextInt(v.size)]
    fun rand(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)

    /** per-type particle emitter params, from parked layout instances */
    val particleProps = HashMap<String, DoubleArray>()

    fun byName(type: String): Obj? = byType[type]?.firstOrNull()
    /**
     * Live view of the instances of [type], not a copy.
     *
     * The backing list is mutated by [add], [destroy] and the type index, so
     * never hold it across a step and never mutate it. Destroying while
     * iterating must use a descending index loop:
     *
     *     for (i in w.allOf("p3").lastIndex downTo 0) w.destroy(w.allOf("p3")[i])
     *
     * a for-each that calls destroy() will throw ConcurrentModificationException.
     */
    fun allOf(type: String): List<Obj> = byType[type] ?: emptyList()

    private fun add(o: Obj) {
        objs.add(o)
        byType.getOrPut(o.type.name) { ArrayList() }.add(o)
        orderDirty = true
    }

    fun drawOrder(): List<Obj> {
        if (orderDirty) {
            ordered.clear(); ordered.addAll(objs)
            ordered.sortWith(drawComparator)
            orderDirty = false
        }
        return ordered
    }

    fun spawn(d: InstDesc): Obj {
        val t = data.types[d.type] ?: error("unknown type " + d.type)
        if (d.particles != null) {
            particleProps[d.type] = d.particles
            // parked emitter templates are not rendered (already sprayed offscreen)
            val o = Obj(t, d.x, d.y, d.layer, d.z)
            o.visible = false
            o.particleProps = d.particles
            add(o)
            return o
        }
        val o = Obj(t, d.x, d.y, d.layer, d.z)
        o.uid = uidSeq++
        o.angle = d.angle
        o.ox = d.ox; o.oy = d.oy
        System.arraycopy(d.color, 0, o.color, 0, 4)
        for (i in d.ivars.indices) {
            if (i < t.ivars.size) o.ivars[t.ivars[i]] = d.ivars[i]
        }
        o.visible = d.visible
        if (t.kind == "sprite" && d.anim != null) {
            val a = t.anims[d.anim]
            if (a != null) {
                o.anim = a
                o.setFrame(d.frame)
                o.playing = d.playing
            }
        }
        // initial display size -> scale relative to frame
        val f = o.frame()
        if (f != null) {
            if (f.w > 0) o.scaleX = d.w / f.w
            if (f.h > 0) o.scaleY = d.h / f.h
        }
        // physics
        val pp = Phys.Props.from(d.phys)
        if (pp != null) {
            o.body = if (d.type == "牆壁") {
                // instance x,y is the center-ish origin; wall boxes use their own w/h,angle
                phys.staticBox(o, d.x + (0.5f - d.ox) * d.w, d.y + (0.5f - d.oy) * d.h,
                    d.w, d.h, d.angle, pp)
            } else {
                phys.bodyFor(o, pp)
            }
        }
        val drag = d.drag
        if (drag != null && drag.size >= 2) o.dragEnabled = drag[1] != 0.0
        val sine = d.sine
        if (sine != null && sine.size >= 9 && sine[8] != 0.0) {
            // [movement, wave, period, magnitude, magRand, periodOffset, periodRand, ?, active]
            if (sine[0].toInt() == 1) {
                o.sineBaseY = o.y
                o.sine = SineState(sine[2].toFloat(), sine[3].toFloat())
            }
        }
        if (d.font != null) {
            // C3 SpriteFont props: [text, ?, charW, charH, charset, spacingData,
            //   charScale, charSpacing, lineHeight, hAlign, vAlign, wrapByWord, ...]
            val fa = org.json.JSONArray(d.font)
            o.text = fa.optString(0)
            o.fontW = fa.optDouble(2, 46.0).toFloat()
            o.fontH = fa.optDouble(3, 71.0).toFloat()
            o.fontCharset = fa.optString(4)
            o.fontSpacing = fa.optDouble(7, 0.0).toFloat()
            o.fontHAlign = fa.optInt(9, 0)
            o.fontVAlign = fa.optInt(10, 0)
            o.fontScale = if (o.fontH > 0) d.h / o.fontH else 1f
            o.fontBoxW = d.w; o.fontBoxH = d.h
        }
        add(o)
        return o
    }

    /** CreateObject equivalent: spawn at (x,y) on a layer, anim chosen, physics state */
    fun createSprite(typeName: String, layer: Int, x: Float, y: Float,
                     anim: String?, physEnabled: Boolean): Obj? {
        val t = data.types[typeName] ?: return null
        val o = Obj(t, x, y, layer, objs.count { it.layer == layer })
        o.uid = uidSeq++
        o.visible = true
        if (anim != null) o.setAnim(anim)
        val f = o.frame()
        if (f != null) { o.ox = f.ox; o.oy = f.oy }
        add(o)
        return o
    }

    fun spawnParticles(typeName: String, at: Obj, layer: Int, angleDeg: Float? = null) {
        val ip = at.imagePoint(0)
        val ang = angleDeg ?: Math.toDegrees(at.angle.toDouble()).toFloat()
        val e = parts.spawn(data.types, particleProps, typeName, ip[0], ip[1],
            Math.toRadians(ang.toDouble()).toFloat(), layer)
        if (e != null && angleDeg != null) {
            // particles were emitted before angle set; re-orient their direction
            val rad = Math.toRadians(angleDeg.toDouble()).toFloat()
            val cone = Math.toRadians(e.p[1])
            val rndv = java.util.Random()
            for (q in e.particles) {
                q.angle = rad + ((rndv.nextFloat() - 0.5f) * cone).toFloat()
            }
        }
    }

    fun destroy(o: Obj) {
        if (o.destroyed) return
        o.destroyed = true
        o.body?.let { phys.destroy(it) }
        tweens.forget(o)
        objs.remove(o)
        byType[o.type.name]?.remove(o)
        orderDirty = true
    }

    fun tick(dt: Float) {
        phys.step(dt)
        tweens.tick(dt)
        sched.tick(dt)
        for (o in objs) {
            o.updateAnim(dt)
            o.sine?.tick(dt, o)
        }
        parts.tick(dt)
    }
}
