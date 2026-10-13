package com.ruich97.nijikaahoge

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.ruich97.nijikaahoge.game.Emitter
import com.ruich97.nijikaahoge.game.Game
import com.ruich97.nijikaahoge.game.Obj
import com.ruich97.nijikaahoge.gfx.SpriteBatch
import com.ruich97.nijikaahoge.gfx.Textures
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GameRenderer(private val context: Context) : GLSurfaceView.Renderer {

    @Volatile var game: Game? = null
        private set

    private val batch = SpriteBatch()
    private lateinit var textures: Textures
    private val proj = FloatArray(16)
    private var viewW = 0
    private var viewH = 0
    private var lastT = System.nanoTime()
    private val debugMetrics = context.applicationInfo.flags and
        android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    private var metricStart = lastT
    private var metricFrames = 0
    private var metricCpuNanos = 0L

    // letterbox transform
    private var lScale = 1f
    private var lOffX = 0f
    private var lOffY = 0f

    companion object {
        const val CW = 1280f
        const val CH = 1024f
        // app background color #f58484
        const val BG_R = 0xf5 / 255f
        const val BG_G = 0x84 / 255f
        const val BG_B = 0x84 / 255f
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        batch.init()
        textures = Textures(context.assets)
        // Context loss invalidates GL assets, but the live game remains valid.
        if (game == null) game = Game(context)
        lastT = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        viewW = w; viewH = h
        GLES20.glViewport(0, 0, w, h)
        lScale = minOf(w / CW, h / CH)
        lOffX = (w - CW * lScale) / 2f
        lOffY = (h - CH * lScale) / 2f
        // ortho: logical (0..CW,0..CH) -> centered letterbox; y down
        Matrix.orthoM(proj, 0, -lOffX / lScale, (w - lOffX) / lScale,
            (h - lOffY) / lScale, -lOffY / lScale, -1f, 1f)
    }

    fun toLogical(sx: Float, sy: Float): Pair<Float, Float> =
        Pair((sx - lOffX) / lScale, (sy - lOffY) / lScale)

    fun toLogicalDistance(pixels: Float): Float = pixels / lScale

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        var dt = (now - lastT) / 1e9f
        lastT = now
        // Clamp long frames (GC pause, app switch) so physics never jumps.
        // 0.05 s = 20 fps floor; the old 1/30 cap truncated any frame slower
        // than 33 ms and made the sim run behind real time on slow devices.
        if (dt > 0.05f) dt = 0.05f
        if (dt <= 0f) dt = 1f / 60f

        val g = game
        g?.update(dt)

        GLES20.glClearColor(BG_R, BG_G, BG_B, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (g == null) return

        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
        GLES20.glScissor(
            lOffX.toInt(), (viewH - lOffY - CH * lScale).toInt(),
            (CW * lScale).toInt(), (CH * lScale).toInt()
        )
        batch.begin(proj)
        val objects = g.logic.objsDrawOrder()
        var index = 0
        for (layer in 0 until g.data.layerCount) {
            while (index < objects.size && objects[index].layer == layer) {
                val o = objects[index++]
                if (!o.destroyed && o.visible && o.isOnScreen(CW, CH)) drawObj(o)
            }
            for (e in g.parts.emitters) if (e.layer == layer) drawEmitter(e)
        }
        batch.end()
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        if (debugMetrics) {
            metricFrames++
            metricCpuNanos += System.nanoTime() - now
            if (now - metricStart >= 2_000_000_000L) {
                val fps = metricFrames * 1e9 / (now - metricStart)
                val cpuMs = metricCpuNanos / 1e6 / metricFrames
                android.util.Log.i("AhogeRender", "fps=$fps cpuMs=$cpuMs drawCalls=${batch.drawCalls} debris=${g.world.allOf("p3").size}")
                metricStart = now; metricFrames = 0; metricCpuNanos = 0L
            }
        }
    }

    private fun drawEmitter(e: Emitter) {
        val img = e.type.image ?: return
        val tex = textures.get(img)
        for (q in e.particles) {
            val s = q.size
            if (q.x + s < 0 || q.x - s > CW || q.y + s < 0 || q.y - s > CH) continue
            batch.quadUV(tex.id, tex.u0, tex.v0, tex.u1, tex.v1,
                q.x - s / 2, q.y - s / 2, s, s,
                q.x, q.y, q.angle, false,
                1f, 1f, 1f, e.opacity(q).coerceIn(0f, 1f))
        }
    }

    private fun drawObj(o: Obj) {
        when (o.type.kind) {
            "sprite" -> {
                val f = o.frame() ?: return
                val tex = textures.get(f.file)
                val w = f.w * o.scaleX; val h = f.h * o.scaleY
                val a = o.color[3].coerceIn(0f, 1f)
                batch.quadUV(
                    tex.id, tex.u0, tex.v0, tex.u1, tex.v1,
                    o.x - o.ox * w, o.y - o.oy * h, w, h,
                    o.x, o.y, o.angle, o.mirrored,
                    o.color[0] * a, o.color[1] * a, o.color[2] * a, a
                )
            }
            "spritefont" -> drawFont(o)
        }
    }

    private fun drawFont(o: Obj) {
        val img = o.type.image ?: return
        val tex = textures.get(img)
        val chars = o.fontCharset
        val cw = o.fontW; val ch = o.fontH
        val scale = o.fontScale
        val text = o.text.ifEmpty { return }
        val texCols = (o.type.iw / cw).toInt().coerceAtLeast(1)
        var totalW = 0f
        for (c in text) totalW += (cw + o.fontSpacing) * scale
        if (text.isNotEmpty()) totalW -= o.fontSpacing * scale
        val boxW = o.fontBoxW; val boxH = o.fontBoxH
        val bx = o.x - o.ox * boxW
        val by = o.y - o.oy * boxH
        var cx = when (o.fontHAlign) {   // 0=left 1=center 2=right
            1 -> bx + (boxW - totalW) / 2f
            2 -> bx + boxW - totalW
            else -> bx
        }
        val cy = when (o.fontVAlign) {   // 0=top 1=center 2=bottom
            1 -> by + (boxH - ch * scale) / 2f
            2 -> by + boxH - ch * scale
            else -> by
        }
        for (c in text) {
            val idx = chars.indexOf(c)
            if (idx >= 0) {
                val gx = (idx % texCols) * cw
                val gy = (idx / texCols) * ch
                batch.quadUV(tex.id, gx / o.type.iw, gy / o.type.ih,
                    (gx + cw) / o.type.iw, (gy + ch) / o.type.ih,
                    cx, cy, cw * scale, ch * scale, cx, cy, 0f, false,
                    o.color[0] * o.color[3], o.color[1] * o.color[3],
                    o.color[2] * o.color[3], o.color[3])
            }
            cx += (cw + o.fontSpacing) * scale
        }
    }
}
