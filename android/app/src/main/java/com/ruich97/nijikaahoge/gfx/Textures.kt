package com.ruich97.nijikaahoge.gfx

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import org.json.JSONObject

class TextureRegion(val id: Int, val u0: Float = 0f, val v0: Float = 0f,
                    val u1: Float = 1f, val v1: Float = 1f)

/** Loads PNGs from assets into GL textures. */
class Textures(private val assets: AssetManager) {
    private val cache = HashMap<String, Int>()
    private val regions = HashMap<String, TextureRegion>()
    private val atlas = JSONObject(assets.open("render_atlas.json").bufferedReader().use { it.readText() })
    private val atlasRegions = atlas.getJSONObject("regions")

    fun get(path: String): TextureRegion {
        regions[path]?.let { return it }
        val r = if (atlasRegions.has(path)) {
            val rect = atlasRegions.getJSONArray(path)
            val side = atlas.getDouble("size").toFloat()
            val x = rect.getDouble(0).toFloat(); val y = rect.getDouble(1).toFloat()
            TextureRegion(load(atlas.getString("file")), x / side, y / side,
                (x + rect.getDouble(2).toFloat()) / side, (y + rect.getDouble(3).toFloat()) / side)
        } else TextureRegion(load(path))
        regions[path] = r
        return r
    }

    private fun load(path: String): Int {
        cache[path]?.let { return it }
        val bmp = assets.open(path).use { BitmapFactory.decodeStream(it) }
            ?: throw RuntimeException("missing asset: $path")
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        val t = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        bmp.recycle()
        cache[path] = t
        return t
    }
}
