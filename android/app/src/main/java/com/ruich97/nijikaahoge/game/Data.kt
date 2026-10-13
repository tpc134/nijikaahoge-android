package com.ruich97.nijikaahoge.game

import android.content.res.AssetManager
import org.json.JSONObject

class Frame(
    val file: String, val w: Float, val h: Float,
    val ox: Float, val oy: Float, val poly: FloatArray,
    val ips: FloatArray,  // image points: flattened [x0,y0,x1,y1...] normalized to frame size
    val dur: Float        // C3 frame duration multiplier: frame displays dur/speed seconds
) {
    val collisionPoly = Triangulate.clean(poly)
    val collisionTriangles by lazy { Triangulate.run(collisionPoly) }
}

class Anim(
    val name: String, val speed: Float, val loop: Boolean,
    val frames: List<Frame>,
    val repeatCount: Int = 1, val repeatTo: Int = 0, val pingPong: Boolean = false
)

class ObjType(
    val name: String, val kind: String,
    val ivars: List<String>, val behaviors: List<String>,
    val anims: Map<String, Anim>,
    val image: String?, val iw: Float, val ih: Float
)

class InstDesc(
    val type: String, val layer: Int, val z: Int,
    val x: Float, val y: Float, val w: Float, val h: Float,
    val angle: Float, val color: FloatArray,
    val ox: Float, val oy: Float,
    val ivars: DoubleArray,
    val anim: String?, val frame: Int, val playing: Boolean,
    val particles: DoubleArray?, val font: String?,
    val phys: DoubleArray?, val drag: DoubleArray?, val sine: DoubleArray?,
    val visible: Boolean
)

class GameData(
    val canvasW: Float, val canvasH: Float,
    val types: Map<String, ObjType>,
    val insts: List<InstDesc>,
    val layerCount: Int,
    val media: List<String>
)

object DataLoader {
    fun load(am: AssetManager): GameData {
        val root = JSONObject(am.open("game_data.json").bufferedReader().readText())
        val canvas = root.getJSONObject("canvas")
        val typesJ = root.getJSONObject("types")
        val types = HashMap<String, ObjType>()
        for (key in typesJ.keys()) {
            val t = typesJ.getJSONObject(key)
            val anims = HashMap<String, Anim>()
            if (t.has("anims")) {
                val aj = t.getJSONObject("anims")
                for (an in aj.keys()) {
                    val a = aj.getJSONObject(an)
                    val frames = ArrayList<Frame>()
                    val fj = a.getJSONArray("frames")
                    for (i in 0 until fj.length()) {
                        val f = fj.getJSONObject(i)
                        val poly = FloatArray(if (f.has("poly")) f.getJSONArray("poly").length() else 0)
                        if (f.has("poly")) {
                            val pj = f.getJSONArray("poly")
                            for (k in poly.indices) poly[k] = pj.getDouble(k).toFloat()
                        }
                        val ips = FloatArray(if (f.has("ips")) f.getJSONArray("ips").length() * 2 else 0)
                        if (f.has("ips")) {
                            val pj = f.getJSONArray("ips")
                            for (k in 0 until pj.length()) {
                                val p = pj.getJSONArray(k)
                                ips[k * 2] = p.getDouble(0).toFloat()
                                ips[k * 2 + 1] = p.getDouble(1).toFloat()
                            }
                        }
                        frames.add(
                            Frame(
                                f.getString("file"), f.getDouble("w").toFloat(),
                                f.getDouble("h").toFloat(), f.getDouble("ox").toFloat(),
                                f.getDouble("oy").toFloat(), poly, ips,
                                f.optDouble("dur", 1.0).toFloat()
                            )
                        )
                    }
                    anims[an] = Anim(
                        an, a.getDouble("speed").toFloat(), a.optBoolean("loop"), frames,
                        a.optInt("repeatCount", 1), a.optInt("repeatTo", 0),
                        a.optBoolean("pingPong")
                    )
                }
            }
            val ivars = ArrayList<String>()
            if (t.has("ivars")) {
                val ij = t.getJSONArray("ivars")
                for (i in 0 until ij.length()) ivars.add(ij.getString(i))
            }
            val behs = ArrayList<String>()
            if (t.has("behaviors")) {
                val bj = t.getJSONArray("behaviors")
                for (i in 0 until bj.length()) behs.add(bj.getString(i))
            }
            types[key] = ObjType(
                key, t.optString("kind", "sprite"), ivars, behs, anims,
                if (t.has("image")) t.getString("image") else null,
                t.optDouble("w", 0.0).toFloat(), t.optDouble("h", 0.0).toFloat()
            )
        }
        val insts = ArrayList<InstDesc>()
        val ij = root.getJSONArray("instances")
        for (i in 0 until ij.length()) {
            val e = ij.getJSONObject(i)
            fun num(a: org.json.JSONArray, i: Int): Double {
                return when (val v = a.get(i)) {
                    is Boolean -> if (v) 1.0 else 0.0
                    is Number -> v.toDouble()
                    else -> v.toString().toDoubleOrNull() ?: 0.0
                }
            }
            fun darr(name: String): DoubleArray? {
                if (!e.has(name)) return null
                val a = e.getJSONArray(name)
                return DoubleArray(a.length()) { num(a, it) }
            }
            val color = FloatArray(4) { 1f }
            if (e.has("color")) {
                val c = e.getJSONArray("color")
                for (k in 0 until 4) color[k] = c.getDouble(k).toFloat()
            }
            val ivars = DoubleArray(if (e.has("ivars")) e.getJSONArray("ivars").length() else 0)
            if (e.has("ivars")) {
                val v = e.getJSONArray("ivars")
                for (k in ivars.indices) ivars[k] = v.getDouble(k)
            }
            val props = e.optJSONObject("props")
            fun pd(b: String): DoubleArray? {
                val p = props?.optJSONArray(b) ?: return null
                return DoubleArray(p.length()) { num(p, it) }
            }
            insts.add(
                InstDesc(
                    e.getString("type"), e.getInt("layer"), e.optInt("z", 0),
                    e.getDouble("x").toFloat(), e.getDouble("y").toFloat(),
                    e.getDouble("w").toFloat(), e.getDouble("h").toFloat(),
                    e.getDouble("angle").toFloat(), color,
                    e.optDouble("ox", 0.5).toFloat(), e.optDouble("oy", 0.5).toFloat(),
                    ivars,
                    if (e.has("anim")) e.getString("anim") else null,
                    e.optInt("frame", 0), e.optBoolean("playing", true),
                    darr("particles"),
                    if (e.has("font")) e.getJSONArray("font").toString() else null,
                    pd("physics"), pd("drag"), pd("sine"),
                    e.optBoolean("visFlag", true)
                )
            )
        }
        val layers = root.getJSONArray("layers")
        val media = ArrayList<String>()
        if (root.has("media")) {
            val mj = root.getJSONArray("media")
            for (i in 0 until mj.length()) media.add(mj.getString(i))
        }
        return GameData(
            canvas.getDouble("w").toFloat(), canvas.getDouble("h").toFloat(),
            types, insts, layers.length(), media
        )
    }
}
