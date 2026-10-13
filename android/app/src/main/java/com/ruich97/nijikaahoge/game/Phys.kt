package com.ruich97.nijikaahoge.game

import org.jbox2d.callbacks.ContactImpulse
import org.jbox2d.callbacks.ContactListener
import org.jbox2d.collision.Manifold
import org.jbox2d.dynamics.contacts.Contact
import org.jbox2d.collision.shapes.CircleShape
import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.Body
import org.jbox2d.dynamics.BodyDef
import org.jbox2d.dynamics.BodyType
import org.jbox2d.dynamics.FixtureDef
import org.jbox2d.dynamics.World
import kotlin.math.abs

/**
 * Box2D wrapper matching Construct's physics behavior:
 * world scale 0.02 m/px, gravity 10 m/s^2 (y-down -> +y).
 * Angles map directly: C3 clockwise-positive in y-down space equals
 * Box2D's positive rotation direction.
 */
class Phys {
    companion object {
        const val S = 0.02f // px -> m
        const val PX = 1f / S // m -> px
    }

    val world = World(Vec2(0f, 10f))

    /** props: [immovable, shape, preventRot, density, friction, elasticity, linDamp, angDamp, bullet, enabled] */
    class Props(
        val immovable: Boolean, val shape: Int, val preventRot: Boolean,
        val density: Float, val friction: Float, val elasticity: Float,
        val linDamp: Float, val angDamp: Float, val bullet: Boolean,
        val enabled: Boolean
    ) {
        companion object {
            fun from(a: DoubleArray?): Props? {
                if (a == null || a.size < 10) return null
                return Props(
                    a[0] != 0.0, a[1].toInt(), a[2] != 0.0,
                    a[3].toFloat(), a[4].toFloat(), a[5].toFloat(),
                    a[6].toFloat(), a[7].toFloat(), a[8] != 0.0, a[9] != 0.0
                )
            }
        }
    }

    inner class BodyRef(val body: Body, val obj: Obj, var shapeKind: Int) {
        var dirty = false
        var props: Props? = null
        fun markDirty() { dirty = true }
    }

    private val bodies = ArrayList<BodyRef>()
    var contactHandler: ((Obj, Obj) -> Unit)? = null

    init {
        world.setContactListener(object : ContactListener {
            override fun beginContact(c: Contact) {
                val a = c.fixtureA.body.userData as? Obj
                val b = c.fixtureB.body.userData as? Obj
                if (a != null && b != null) contactHandler?.invoke(a, b)
            }
            override fun endContact(c: Contact) {}
            override fun preSolve(c: Contact, m: Manifold) {}
            override fun postSolve(c: Contact, i: ContactImpulse) {}
        })
    }

    /** create a static axis-aligned box centered at (cx,cy), size w x h, rotated by angle */
    fun staticBox(obj: Obj, cx: Float, cy: Float, w: Float, h: Float, angle: Float, p: Props): BodyRef {
        val def = BodyDef()
        def.type = BodyType.STATIC
        def.position.set(cx * S, cy * S)
        def.angle = angle
        val body = world.createBody(def)
        val shape = PolygonShape()
        shape.setAsBox(w / 2 * S, h / 2 * S)
        val fd = FixtureDef()
        fd.shape = shape; fd.density = p.density; fd.friction = p.friction; fd.restitution = p.elasticity
        body.createFixture(fd)
        body.userData = obj
        val ref = BodyRef(body, obj, 1); ref.props = p
        bodies.add(ref)
        return ref
    }

    /** create body for obj using its current frame polygon (or circle/box per shapeKind) */
    fun bodyFor(obj: Obj, p: Props): BodyRef {
        val def = BodyDef()
        def.type = if (p.immovable) BodyType.STATIC else BodyType.DYNAMIC
        def.position.set(obj.x * S, obj.y * S)
        def.angle = obj.angle
        def.fixedRotation = p.preventRot
        def.linearDamping = p.linDamp
        def.angularDamping = p.angDamp
        def.bullet = p.bullet
        val body = world.createBody(def)
        body.userData = obj
        val ref = BodyRef(body, obj, p.shape)
        ref.props = p
        rebuildFixtures(ref)
        body.isActive = p.enabled
        bodies.add(ref)
        return ref
    }

    fun rebuildFixtures(ref: BodyRef) {
        val obj = ref.obj
        val p = ref.props ?: return
        // remove old fixtures
        var f = ref.body.fixtureList
        while (f != null) {
            val n = f.next
            ref.body.destroyFixture(f)
            f = n
        }
        val w = obj.displayW(); val h = obj.displayH()
        val fd = FixtureDef()
        fd.density = p.density; fd.friction = p.friction; fd.restitution = p.elasticity
        when (ref.shapeKind) {
            1 -> { // box
                val shape = PolygonShape()
                shape.setAsBox(w / 2 * S, h / 2 * S,
                    Vec2(((0.5f - obj.ox) * w) * S, ((0.5f - obj.oy) * h) * S), 0f)
                fd.shape = shape
                ref.body.createFixture(fd)
            }
            2 -> { // circle
                val shape = CircleShape()
                shape.radius = minOf(w, h) / 2 * S
                shape.m_p.set(((0.5f - obj.ox) * w) * S, ((0.5f - obj.oy) * h) * S)
                fd.shape = shape
                ref.body.createFixture(fd)
            }
            else -> { // collision polygon -> triangles
                val poly = obj.frame()?.collisionPoly
                if (poly == null || poly.size < 6) {
                    val shape = PolygonShape()
                    shape.setAsBox(w / 2 * S, h / 2 * S,
                        Vec2(((0.5f - obj.ox) * w) * S, ((0.5f - obj.oy) * h) * S), 0f)
                    fd.shape = shape
                    ref.body.createFixture(fd)
                } else if (poly.size <= 16 && Triangulate.isConvex(poly)) {
                    val verts = Array(poly.size / 2) { i ->
                        Vec2(poly[i * 2] * w * S, poly[i * 2 + 1] * h * S)
                    }
                    val shape = PolygonShape()
                    shape.set(verts, verts.size)
                    fd.shape = shape
                    ref.body.createFixture(fd)
                } else {
                    val tris = obj.frame()!!.collisionTriangles
                    var i = 0
                    while (i + 5 < tris.size) {
                        val shape = PolygonShape()
                        val verts = arrayOf(
                            Vec2(tris[i] * w * S, tris[i + 1] * h * S),
                            Vec2(tris[i + 2] * w * S, tris[i + 3] * h * S),
                            Vec2(tris[i + 4] * w * S, tris[i + 5] * h * S)
                        )
                        // ensure CCW winding for box2d
                        if (cross(verts[0], verts[1], verts[2]) < 0) {
                            verts[1] = verts[2].also { verts[2] = verts[1] }
                        }
                        shape.set(verts, 3)
                        fd.shape = shape
                        ref.body.createFixture(fd)
                        i += 6
                    }
                }
            }
        }
        ref.dirty = false
        ref.body.resetMassData()
    }

    private fun cross(a: Vec2, b: Vec2, c: Vec2): Float =
        (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

    fun destroy(ref: BodyRef) {
        bodies.remove(ref)
        world.destroyBody(ref.body)
    }

    fun setEnabled(ref: BodyRef, on: Boolean) {
        ref.body.isActive = on
        if (on) {
            ref.body.setTransform(Vec2(ref.obj.x * S, ref.obj.y * S), ref.obj.angle)
            ref.body.setLinearVelocity(Vec2(0f, 0f))
            ref.body.angularVelocity = 0f
        }
    }

    fun setKinematic(ref: BodyRef, on: Boolean) {
        if (on) {
            ref.body.type = BodyType.KINEMATIC
            ref.body.setLinearVelocity(Vec2(0f, 0f))
            ref.body.angularVelocity = 0f
        } else {
            ref.body.setTransform(Vec2(ref.obj.x * S, ref.obj.y * S), ref.obj.angle)
            ref.body.type = BodyType.DYNAMIC
            ref.body.setLinearVelocity(Vec2(0f, 0f))
            ref.body.angularVelocity = 0f
        }
        ref.body.isAwake = true
    }

    /** move a kinematic body toward (x,y) px with velocity so it sweeps */
    fun driveTo(ref: BodyRef, x: Float, y: Float, dt: Float) {
        val cur = ref.body.position
        val vx = (x * S - cur.x) / dt
        val vy = (y * S - cur.y) / dt
        ref.body.setLinearVelocity(Vec2(vx, vy))
    }

    fun teleport(ref: BodyRef, x: Float, y: Float) {
        ref.body.setTransform(Vec2(x * S, y * S), ref.obj.angle)
        ref.body.setLinearVelocity(Vec2(0f, 0f))
        ref.body.angularVelocity = 0f
        ref.body.isAwake = true
    }

    fun step(dt: Float) {
        for (ref in bodies) if (ref.dirty) rebuildFixtures(ref)
        world.step(dt, 8, 3)
        for (ref in bodies) {
            val b = ref.body
            val obj = ref.obj
            if (b.isActive && b.type == BodyType.DYNAMIC && obj.bodyDrives) {
                obj.x = b.position.x * PX
                obj.y = b.position.y * PX
                obj.angle = b.angle
            }
        }
    }
}

/** ear-clipping triangulation of a simple polygon -> index list (x,y pairs of verts) */
object Triangulate {
    fun clean(poly: FloatArray): FloatArray {
        if (poly.size < 6) return poly
        val out = FloatArray(poly.size)
        var size = 0
        var i = 0
        while (i < poly.size) {
            if (size == 0 || abs(poly[i] - out[size - 2]) > 0.000001f ||
                abs(poly[i + 1] - out[size - 1]) > 0.000001f) {
                out[size++] = poly[i]
                out[size++] = poly[i + 1]
            }
            i += 2
        }
        if (size >= 4 && abs(out[0] - out[size - 2]) < 0.000001f &&
            abs(out[1] - out[size - 1]) < 0.000001f) size -= 2
        return out.copyOf(size)
    }

    fun isConvex(poly: FloatArray): Boolean {
        val n = poly.size / 2
        var sign = 0f
        for (i in 0 until n) {
            val a = i * 2
            val b = ((i + 1) % n) * 2
            val c = ((i + 2) % n) * 2
            val cross = (poly[b] - poly[a]) * (poly[c + 1] - poly[b + 1]) -
                (poly[b + 1] - poly[a + 1]) * (poly[c] - poly[b])
            if (abs(cross) < 0.000001f) continue
            if (sign != 0f && sign * cross < 0f) return false
            sign = cross
        }
        return sign != 0f
    }

    fun run(poly: FloatArray): FloatArray {
        val n = poly.size / 2
        if (n < 3) return poly.copyOf()
        if (n == 3) return poly.copyOf()
        // ensure consistent winding (use signed area)
        var area = 0f
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += poly[i * 2] * poly[j * 2 + 1] - poly[j * 2] * poly[i * 2 + 1]
        }
        val idx = IntArray(n) { if (area >= 0) it else n - 1 - it }
        val out = ArrayList<Float>()
        var guard = 0
        val ids = idx.toMutableList()
        while (ids.size > 3 && guard < 200) {
            guard++
            var clipped = false
            for (k in ids.indices) {
                val i0 = ids[(k + ids.size - 1) % ids.size]
                val i1 = ids[k]
                val i2 = ids[(k + 1) % ids.size]
                val ax = poly[i0 * 2]; val ay = poly[i0 * 2 + 1]
                val bx = poly[i1 * 2]; val by = poly[i1 * 2 + 1]
                val cx = poly[i2 * 2]; val cy = poly[i2 * 2 + 1]
                if ((bx - ax) * (cy - ay) - (by - ay) * (cx - ax) <= 0) continue // reflex
                var ok = true
                for (m in ids) {
                    if (m == i0 || m == i1 || m == i2) continue
                    if (inTri(poly[m * 2], poly[m * 2 + 1], ax, ay, bx, by, cx, cy)) { ok = false; break }
                }
                if (ok) {
                    out.add(ax); out.add(ay); out.add(bx); out.add(by); out.add(cx); out.add(cy)
                    ids.removeAt(k); clipped = true; break
                }
            }
            if (!clipped) { // fallback: fan
                for (k in 1 until ids.size - 1) {
                    out.add(poly[ids[0] * 2]); out.add(poly[ids[0] * 2 + 1])
                    out.add(poly[ids[k] * 2]); out.add(poly[ids[k] * 2 + 1])
                    out.add(poly[ids[k + 1] * 2]); out.add(poly[ids[k + 1] * 2 + 1])
                }
                return out.toFloatArray()
            }
        }
        out.add(poly[ids[0] * 2]); out.add(poly[ids[0] * 2 + 1])
        out.add(poly[ids[1] * 2]); out.add(poly[ids[1] * 2 + 1])
        out.add(poly[ids[2] * 2]); out.add(poly[ids[2] * 2 + 1])
        return out.toFloatArray()
    }

    private fun inTri(px: Float, py: Float, ax: Float, ay: Float,
                      bx: Float, by: Float, cx: Float, cy: Float): Boolean {
        fun s(x1: Float, y1: Float, x2: Float, y2: Float) =
            (x2 - x1) * (py - y1) - (y2 - y1) * (px - x1)
        val d1 = s(ax, ay, bx, by); val d2 = s(bx, by, cx, cy); val d3 = s(cx, cy, ax, ay)
        val neg = d1 < 0 || d2 < 0 || d3 < 0
        val pos = d1 > 0 || d2 > 0 || d3 > 0
        return !(neg && pos)
    }
}
