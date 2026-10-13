package com.ruich97.nijikaahoge

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.app.ActivityManager
import android.content.pm.PackageManager
import com.ruich97.nijikaahoge.game.Game

@SuppressLint("ViewConstructor")
class GameView(context: Context) : GLSurfaceView(context) {

    val renderer: GameRenderer

    /** pointer id that owns the active drag; -1 = none */
    private var dragPointerId = -1

    /**
     * True on watches, low-RAM devices, MSM8909 (Snapdragon 210) and small
     * square screens. It now only selects the compact UI layout: the 30 fps
     * Choreographer pacing was removed because on-device testing on Snapdragon
     * 210 and Wear 4 Gen 1 showed no thermal benefit, while the lower frame
     * rate did make dragging feel less responsive. Long frames are absorbed by
     * the dt clamp in GameRenderer instead.
     */
    val lowEndProfile = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH) ||
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice ||
        android.os.Build.HARDWARE.contains("msm8909", ignoreCase = true) ||
        (resources.displayMetrics.widthPixels <= 480 && resources.displayMetrics.heightPixels <= 480)

    init {
        setEGLContextClientVersion(2)
        // The default chooser asks for a 16-bit depth buffer this 2-D game
        // never uses; dropping depth and stencil saves the bandwidth of
        // allocating and clearing it on every frame.
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        renderer = GameRenderer(context)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause = true
        android.util.Log.i("Ahoge", "render profile: ${if (lowEndProfile) "low-end" else "standard"}")
    }

    override fun onPause() {
        dragPointerId = -1
        queueEvent { renderer.game?.touchUp(0f, 0f) }
        super.onPause()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragPointerId = e.getPointerId(0)
                val (lx, ly) = renderer.toLogical(e.x, e.y)
                val radius = fingerRadius(e, 0)
                queueEvent { renderer.game?.touchDown(lx, ly, radius) }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // secondary pointers ignored while a drag is active
                if (dragPointerId == -1) {
                    dragPointerId = e.getPointerId(e.actionIndex)
                    val (lx, ly) = renderer.toLogical(
                        e.getX(e.actionIndex), e.getY(e.actionIndex))
                    val radius = fingerRadius(e, e.actionIndex)
                    queueEvent { renderer.game?.touchDown(lx, ly, radius) }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val idx = e.findPointerIndex(dragPointerId)
                if (idx >= 0) {
                    val (lx, ly) = renderer.toLogical(e.getX(idx), e.getY(idx))
                    queueEvent { renderer.game?.touchMove(lx, ly) }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (e.getPointerId(e.actionIndex) == dragPointerId) {
                    val (lx, ly) = renderer.toLogical(
                        e.getX(e.actionIndex), e.getY(e.actionIndex))
                    queueEvent { renderer.game?.touchUp(lx, ly) }
                    dragPointerId = -1
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragPointerId != -1) {
                    val idx = e.findPointerIndex(dragPointerId).coerceAtLeast(0)
                    val (lx, ly) = renderer.toLogical(e.getX(idx), e.getY(idx))
                    queueEvent { renderer.game?.touchUp(lx, ly) }
                }
                dragPointerId = -1
                if (e.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun fingerRadius(e: MotionEvent, index: Int): Float {
        val tool = e.getToolType(index)
        val finger = tool == MotionEvent.TOOL_TYPE_FINGER ||
            (tool == MotionEvent.TOOL_TYPE_UNKNOWN && e.isFromSource(android.view.InputDevice.SOURCE_TOUCHSCREEN))
        return if (finger) renderer.toLogicalDistance(12f * resources.displayMetrics.density).coerceAtMost(96f)
        else 0f
    }
}
