package com.ruich97.nijikaahoge.gfx

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Minimal textured-quad batcher. Logical coordinates are the 1280x1024
 * canvas, y-down. The projection matrix is a plain ortho set by caller.
 * Textures are uploaded premultiplied (Android bitmaps are premultiplied),
 * so blending is ONE, ONE_MINUS_SRC_ALPHA.
 */
class SpriteBatch {
    companion object {
        private const val VERT_STRIDE = 8 // pos2 uv2 color4
        private const val MAX_QUADS = 1024

        private const val VERT = """
uniform mat4 u_mvp;
attribute vec2 a_pos;
attribute vec2 a_uv;
attribute vec4 a_col;
varying vec2 v_uv;
varying vec4 v_col;
void main() {
    v_uv = a_uv;
    v_col = a_col;
    gl_Position = u_mvp * vec4(a_pos, 0.0, 1.0);
}
"""
        private const val FRAG = """
precision mediump float;
varying vec2 v_uv;
varying vec4 v_col;
uniform sampler2D u_tex;
void main() {
    gl_FragColor = texture2D(u_tex, v_uv) * v_col;
}
"""
    }

    private var program = 0
    private var uMvp = 0
    private var uTex = 0
    private var vbo = 0
    private var ibo = 0

    private val verts = FloatArray(MAX_QUADS * 4 * VERT_STRIDE)
    private var vertCount = 0
    private var quadCount = 0
    private var curTex = -1
    private val mvp = FloatArray(16)
    private var vertexBuf: FloatBuffer? = null
    private var indexBuf: java.nio.ShortBuffer? = null
    var drawCalls = 0
        private set

    fun init() {
        val vs = compile(GLES20.GL_VERTEX_SHADER, VERT)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, FRAG)
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glBindAttribLocation(program, 0, "a_pos")
        GLES20.glBindAttribLocation(program, 1, "a_uv")
        GLES20.glBindAttribLocation(program, 2, "a_col")
        GLES20.glLinkProgram(program)
        uMvp = GLES20.glGetUniformLocation(program, "u_mvp")
        uTex = GLES20.glGetUniformLocation(program, "u_tex")

        val ids = IntArray(2)
        GLES20.glGenBuffers(2, ids, 0)
        vbo = ids[0]; ibo = ids[1]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verts.size * 4, null, GLES20.GL_DYNAMIC_DRAW)

        val idx = ShortArray(MAX_QUADS * 6)
        for (i in 0 until MAX_QUADS) {
            val b = i * 4; val o = i * 6
            idx[o] = b.toShort(); idx[o + 1] = (b + 1).toShort(); idx[o + 2] = (b + 2).toShort()
            idx[o + 3] = b.toShort(); idx[o + 4] = (b + 2).toShort(); idx[o + 5] = (b + 3).toShort()
        }
        indexBuf = ByteBuffer.allocateDirect(idx.size * 2).order(ByteOrder.nativeOrder())
            .asShortBuffer().put(idx).also { it.position(0) }
        vertexBuf = ByteBuffer.allocateDirect(MAX_QUADS * 4 * VERT_STRIDE * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, idx.size * 2, indexBuf, GLES20.GL_STATIC_DRAW)
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) throw RuntimeException("shader: " + GLES20.glGetShaderInfoLog(s))
        return s
    }

    fun begin(mvpMatrix: FloatArray) {
        System.arraycopy(mvpMatrix, 0, mvp, 0, 16)
        vertCount = 0; quadCount = 0; curTex = -1
        drawCalls = 0
        GLES20.glUseProgram(program)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    /**
     * Draw a textured rect rotated about pivot (px,py).
     * (x,y,w,h) = unrotated rect, angle in radians (clockwise positive,
     * Construct convention), mirrored reflects geometry about the pivot. r,g,b,a are 0..1,
     * already premultiplied by caller if needed.
     */
    fun quad(tex: Int, x: Float, y: Float, w: Float, h: Float,
             px: Float, py: Float, angle: Float, mirrored: Boolean,
             r: Float, g: Float, b: Float, a: Float) {
        quadUV(tex, 0f, 0f, 1f, 1f, x, y, w, h, px, py, angle, mirrored, r, g, b, a)
    }

    /** variant with explicit UV rect (font glyph atlas slicing) */
    fun quadUV(tex: Int, u0: Float, v0: Float, u1: Float, v1: Float,
               x: Float, y: Float, w: Float, h: Float,
               px: Float, py: Float, angle: Float, mirrored: Boolean,
               r: Float, g: Float, b: Float, a: Float) {
        if (tex != curTex) flush(); curTex = tex
        if (quadCount >= MAX_QUADS) { flush(); curTex = tex }
        val v = verts
        var o = quadCount * 4 * VERT_STRIDE
        val cos = kotlin.math.cos(angle)
        val sin = kotlin.math.sin(angle)
        for (i in 0 until 4) {
            val right = i == 1 || i == 2
            val bottom = i >= 2
            val dx = ((if (right) x + w else x) - px) * if (mirrored) -1f else 1f
            val dy = (if (bottom) y + h else y) - py
            v[o] = px + dx * cos - dy * sin
            v[o + 1] = py + dx * sin + dy * cos
            v[o + 2] = if (right) u1 else u0
            v[o + 3] = if (bottom) v1 else v0
            v[o + 4] = r; v[o + 5] = g; v[o + 6] = b; v[o + 7] = a
            o += VERT_STRIDE
        }
        quadCount++
    }

    fun end() { flush() }

    private fun flush() {
        if (quadCount == 0) return
        vertexBuf!!.position(0)
        vertexBuf!!.put(verts, 0, quadCount * 4 * VERT_STRIDE)
        vertexBuf!!.position(0)

        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, curTex)
        GLES20.glUniform1i(uTex, 0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        // Orphan the buffer before writing. Tilers (Mali especially) must
        // implicitly sync when you overwrite storage a pending draw is still
        // reading, which serialized every flush: ~10 GPU stalls per frame and
        // 35 fps on Kirin 960 while far weaker parts ran at full rate. Handing
        // the driver a fresh backing store (glBufferData with null) is the
        // standard streaming pattern and removes that hazard.
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verts.size * 4, null, GLES20.GL_DYNAMIC_DRAW)
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0,
            quadCount * 4 * VERT_STRIDE * 4, vertexBuf)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glEnableVertexAttribArray(1)
        GLES20.glEnableVertexAttribArray(2)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, VERT_STRIDE * 4, 0)
        GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, VERT_STRIDE * 4, 8)
        GLES20.glVertexAttribPointer(2, 4, GLES20.GL_FLOAT, false, VERT_STRIDE * 4, 16)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, quadCount * 6, GLES20.GL_UNSIGNED_SHORT, 0)
        drawCalls++
        GLES20.glDisableVertexAttribArray(0)
        GLES20.glDisableVertexAttribArray(1)
        GLES20.glDisableVertexAttribArray(2)
        quadCount = 0
    }
}
