package com.ruich97.nijikaahoge.game

import kotlin.math.cos
import kotlin.math.sin

/**
 * One live instance. Mirrors a C3 sprite instance:
 * - position (x,y) is the origin point
 * - (ox,oy) is the origin within the frame's displayed rect
 * - scaleX/scaleY are relative to current frame size, so changing
 *   animation frames resizes the object exactly like Construct
 */
class Obj(
    val type: ObjType,
    var x: Float, var y: Float,
    var layer: Int, var z: Int
) {
    var angle = 0f          // radians, clockwise-positive (C3 convention)
    var scaleX = 1f
    var scaleY = 1f
    var visible = true
    var mirrored = false
    val color = floatArrayOf(1f, 1f, 1f, 1f)

    var ox = 0.5f
    var oy = 0.5f

    var anim: Anim? = null
    var frameIndex = 0
    var playing = false
    private var animT = 0f
    private var animForwards = true
    private var animRepeats = 0

    val ivars = HashMap<String, Double>()
    var dragEnabled = false
    var sine: SineState? = null

    var body: Phys.BodyRef? = null
    var bodyDrives = true          // physics writes back to x/y/angle when dynamic
    var destroyed = false
    var child: Obj? = null         // attached child (多力多滋2)
    var uid = 0

    // per-instance plugin data
    var particleProps: DoubleArray? = null
    var fontCharset = ""
    var fontW = 0f
    var fontH = 0f
    var fontSpacing = 0f
    var fontScale = 1f
    var fontBoxW = 0f
    var fontBoxH = 0f
    var fontHAlign = 0  // 0=left 1=center 2=right (C3 SpriteFont)
    var fontVAlign = 0  // 0=top 1=center 2=bottom
    var text = ""

    private var worldPoly = FloatArray(0)
    private var worldTriangles = FloatArray(0)
    private val bounds = FloatArray(4)
    private var cachedFrame: Frame? = null
    private var cachedX = Float.NaN
    private var cachedY = Float.NaN
    private var cachedAngle = Float.NaN
    private var cachedScaleX = Float.NaN
    private var cachedScaleY = Float.NaN
    private var cachedOx = Float.NaN
    private var cachedOy = Float.NaN
    private var cachedMirror = false
    private var fallbackPoly = FloatArray(0)
    private var fallbackTriangles = FloatArray(0)

    var sineBaseX = 0f
    var sineBaseY = 0f

    fun iv(name: String): Double = ivars[name] ?: 0.0
    fun ivSet(name: String, v: Double) { ivars[name] = v }
    fun ivb(name: String): Boolean = iv(name) != 0.0

    fun frame(): Frame? {
        val a = anim ?: return null
        val idx = frameIndex.coerceIn(0, a.frames.size - 1)
        return a.frames[idx]
    }

    fun displayW(): Float {
        val f = frame()
        return if (f != null) f.w * scaleX else 0f
    }

    fun displayH(): Float {
        val f = frame()
        return if (f != null) f.h * scaleY else 0f
    }

    fun setAnim(name: String, restart: Boolean = false) {
        val a = type.anims[name] ?: return
        if (anim !== a) {
            anim = a
            frameIndex = 0
            animT = 0f
            animForwards = true
            animRepeats = 0
            playing = true
            body?.markDirty()
            val f = frame()
            if (f != null) { ox = f.ox; oy = f.oy }
        } else if (restart) {
            frameIndex = 0
            animT = 0f
            animForwards = true
            animRepeats = 0
            playing = true
            body?.markDirty()
            val f = frame()
            if (f != null) { ox = f.ox; oy = f.oy }
        }
    }

    fun setFrame(i: Int) {
        val newIdx = i.coerceIn(0, (anim?.frames?.size ?: 1) - 1)
        if (newIdx != frameIndex) body?.markDirty()   // C3 rebuilds collision poly per frame
        frameIndex = newIdx
        val f = frame()
        if (f != null) { ox = f.ox; oy = f.oy }
    }

    fun updateAnim(dt: Float) {
        val a = anim
        if (!playing || a == null || a.speed <= 0f || a.frames.size <= 1) return
        animT += dt
        // C3 semantics: each frame displays for frame.dur/speed seconds;
        // pingPong reverses at ends without repeating endpoint frames;
        // looping wraps to repeatTo; non-looping stops after repeatCount cycles.
        var guard = 0
        val before = frameIndex
        while (guard++ < 100 && playing) {
            val f = a.frames.getOrNull(frameIndex) ?: break
            val ft = f.dur / a.speed
            if (animT < ft) break
            animT -= ft
            stepFrame(a)
        }
        if (frameIndex != before) body?.markDirty()
        val cf = frame()
        if (cf != null) { ox = cf.ox; oy = cf.oy }
    }

    private fun stepFrame(a: Anim) {
        if (animForwards) frameIndex++ else frameIndex--
        val n = a.frames.size
        if (frameIndex >= n) {
            if (a.pingPong) {
                animForwards = false
                frameIndex = n - 2
            } else if (a.loop) {
                frameIndex = a.repeatTo
            } else {
                animRepeats++
                if (animRepeats >= a.repeatCount) { playing = false; frameIndex = n - 1 }
                else frameIndex = a.repeatTo
            }
        }
        if (frameIndex < 0) {
            if (a.pingPong) {
                frameIndex = 1
                animForwards = true
                if (!a.loop) {
                    animRepeats++
                    if (animRepeats >= a.repeatCount) playing = false
                }
            } else {
                frameIndex = n - 1
            }
        }
        frameIndex = frameIndex.coerceIn(0, n - 1)
    }

    fun setScale(s: Float) {
        scaleX = s; scaleY = s
        // physics body follows scale
        body?.markDirty()
    }

    fun isOnScreen(cw: Float, ch: Float): Boolean {
        // SpriteFont objects have no frame, so displayW()/displayH() are 0 and
        // their bbox collapses to the anchor point. Culling those by bbox would
        // drop text as soon as the anchor drifts past the canvas edge, so treat
        // any object we cannot measure as always visible.
        if (frame() == null) return true
        val b = boundingBox()
        return b[2] >= 0 && b[0] <= cw && b[3] >= 0 && b[1] <= ch
    }

    /**
     * Rotated bbox as [l,t,r,b].
     *
     * Returns this object's own cache array, not a copy: it is overwritten by
     * the next [updateGeometry] call (any move, rotate, scale or frame change,
     * on any object). Read it immediately, or copy it if it must outlive that.
     */
    fun boundingBox(): FloatArray {
        updateGeometry()
        return bounds
    }

    private fun updateGeometry() {
        val f = frame()
        if (f === cachedFrame && x == cachedX && y == cachedY && angle == cachedAngle &&
            scaleX == cachedScaleX && scaleY == cachedScaleY && ox == cachedOx &&
            oy == cachedOy && mirrored == cachedMirror) return
        cachedFrame = f; cachedX = x; cachedY = y; cachedAngle = angle
        cachedScaleX = scaleX; cachedScaleY = scaleY
        cachedOx = ox; cachedOy = oy; cachedMirror = mirrored
        val w = displayW(); val h = displayH()
        val l = -ox * w; val t = -oy * h
        val cos = cos(angle); val sin = sin(angle)
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in 0 until 4) {
            val px = (if (i == 0 || i == 3) l else l + w) * if (mirrored) -1f else 1f
            val py = if (i < 2) t else t + h
            val rx = px * cos - py * sin
            val ry = px * sin + py * cos
            minX = minOf(minX, rx); maxX = maxOf(maxX, rx)
            minY = minOf(minY, ry); maxY = maxOf(maxY, ry)
        }
        bounds[0] = x + minX; bounds[1] = y + minY
        bounds[2] = x + maxX; bounds[3] = y + maxY
        val src: FloatArray
        val tris: FloatArray
        if (f != null && f.collisionPoly.size >= 6) {
            src = f.collisionPoly
            tris = f.collisionTriangles
        } else {
            if (fallbackPoly.isEmpty() || fallbackPoly[0] != -ox || fallbackPoly[1] != -oy) {
                fallbackPoly = floatArrayOf(-ox, -oy, 1f - ox, -oy,
                    1f - ox, 1f - oy, -ox, 1f - oy)
                fallbackTriangles = Triangulate.run(fallbackPoly)
            }
            src = fallbackPoly; tris = fallbackTriangles
        }
        if (worldPoly.size != src.size) worldPoly = FloatArray(src.size)
        if (worldTriangles.size != tris.size) worldTriangles = FloatArray(tris.size)
        transformPoly(src, worldPoly, w, h, cos, sin)
        transformPoly(tris, worldTriangles, w, h, cos, sin)
    }

    /**
     * Collision polygon in world coords (fallback: bbox corners).
     *
     * Shared cache array, see [boundingBox]: it is recycled on the next
     * geometry update on any object, so copy it if you need to keep it.
     */
    fun polyWorld(): FloatArray {
        updateGeometry()
        return worldPoly
    }

    /** Ear-clipped triangles of [polyWorld]. Same shared-cache caveat. */
    fun trianglesWorld(): FloatArray {
        updateGeometry()
        return worldTriangles
    }

    private fun transformPoly(src: FloatArray, out: FloatArray, w: Float, h: Float,
                              cos: Float, sin: Float) {
        var i = 0
        while (i < src.size) {
            var px = src[i] * w
            var py = src[i + 1] * h
            if (mirrored) px = -px
            out[i] = x + px * cos - py * sin
            out[i + 1] = y + px * sin + py * cos
            i += 2
        }
    }

    /** world position of image point i on the current frame (fallback: origin) */
    fun imagePoint(i: Int): FloatArray {
        val f = frame()
        if (f == null || f.ips.size < (i + 1) * 2) return floatArrayOf(x, y)
        val w = displayW(); val h = displayH()
        var px = (f.ips[i * 2] - f.ox) * w
        var py = (f.ips[i * 2 + 1] - f.oy) * h
        if (mirrored) px = -px
        val cos = cos(angle); val sin = sin(angle)
        return floatArrayOf(x + px * cos - py * sin, y + px * sin + py * cos)
    }

    fun containsPoint(px: Float, py: Float): Boolean {
        val poly = polyWorld()
        // ray cast
        var inside = false
        var j = poly.size - 2
        var i = 0
        while (i < poly.size) {
            val xi = poly[i]; val yi = poly[i + 1]
            val xj = poly[j]; val yj = poly[j + 1]
            if ((yi > py) != (yj > py) &&
                px < (xj - xi) * (py - yi) / (yj - yi) + xi
            ) inside = !inside
            j = i; i += 2
        }
        return inside
    }

    fun containsPointWithPadding(px: Float, py: Float, padding: Float): Boolean {
        if (containsPoint(px, py)) return true
        if (padding <= 0f) return false
        val b = boundingBox()
        if (px < b[0] - padding || px > b[2] + padding ||
            py < b[1] - padding || py > b[3] + padding) return false
        val p = polyWorld()
        val limit = padding * padding
        var j = p.size - 2
        for (i in p.indices step 2) {
            val dx = p[i] - p[j]; val dy = p[i + 1] - p[j + 1]
            val lengthSquared = dx * dx + dy * dy
            val t = if (lengthSquared > 0f)
                (((px - p[j]) * dx + (py - p[j + 1]) * dy) / lengthSquared).coerceIn(0f, 1f)
            else 0f
            val ex = px - (p[j] + t * dx); val ey = py - (p[j + 1] + t * dy)
            if (ex * ex + ey * ey <= limit) return true
            j = i
        }
        return false
    }
}

/** Vertical sine (C3 Sine behavior, movement=1) */
class SineState(val period: Float, val magnitude: Float) {
    var t = 0f
    fun tick(dt: Float, obj: Obj) {
        t += dt
        val phase = (t / period) * (Math.PI * 2)
        obj.y = obj.sineBaseY + sin(phase).toFloat() * magnitude
    }
}
