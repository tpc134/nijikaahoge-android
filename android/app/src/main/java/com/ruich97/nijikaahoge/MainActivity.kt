package com.ruich97.nijikaahoge

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView

class MainActivity : Activity() {
    private lateinit var glView: GameView
    private var debugReceiver: BroadcastReceiver? = null
    private val handler = Handler(Looper.getMainLooper())

    /** after the device's default orientation settles, let the sensor take over */
    private val goSensor = Runnable {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // open in whatever the device considers default (portrait on most phones);
        // shortly after, sensor-driven rotation (both orientations)
        handler.postDelayed(goSensor, 1500)

        glView = GameView(this)
        val root = FrameLayout(this)
        root.addView(glView)

        // corner orientation toggle: landscape <-> portrait
        val b = ImageView(this)
        val dp = resources.displayMetrics.density
        val compact = glView.lowEndProfile
        val size = ((if (compact) 32 else 48) * dp).toInt()
        val iconSize = ((if (compact) 24 else 48) * dp).toInt()
        b.scaleType = ImageView.ScaleType.CENTER
        b.setImageDrawable(BitmapDrawable(resources, rotateIcon(iconSize)))
        b.contentDescription = "Rotate screen"
        b.alpha = 0.55f
        val lp = FrameLayout.LayoutParams(size, size)
        lp.gravity = android.view.Gravity.BOTTOM or
            if (compact) android.view.Gravity.CENTER_HORIZONTAL else android.view.Gravity.END
        val m = ((if (compact) 4 else 12) * dp).toInt()
        lp.setMargins(m, m, m, m)
        b.layoutParams = lp
        b.setOnClickListener {
            handler.removeCallbacks(goSensor)
            requestedOrientation =
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                else
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        root.addView(b)
        setContentView(root)
        hideSystemUi()

        // adb: am broadcast -a com.ruich97.nijikaahoge.DEBUG -p com.ruich97.nijikaahoge --es cmd "cnt=10"
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            val r = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) {
                    val cmd = i?.getStringExtra("cmd") ?: return
                    android.util.Log.i("Ahoge", "debug recv: $cmd")
                    glView.queueEvent { glView.renderer.game?.logic?.debug(cmd) }
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                registerReceiver(r, IntentFilter("com.ruich97.nijikaahoge.DEBUG"), Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION", "UnspecifiedRegisterReceiverFlag")
                registerReceiver(r, IntentFilter("com.ruich97.nijikaahoge.DEBUG"))
            }
            debugReceiver = r
        }
    }

    /** draw a simple rotate-screen glyph: rounded phone rect + circular arrow */
    private fun rotateIcon(px: Int): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // dark backdrop circle
        p.color = 0x66000000; p.style = Paint.Style.FILL
        c.drawCircle(px / 2f, px / 2f, px / 2f - 1, p)
        // white circular arrow
        p.color = 0xFFFFFFFF.toInt(); p.style = Paint.Style.STROKE
        p.strokeWidth = px * 0.08f; p.strokeCap = Paint.Cap.ROUND
        val pad = px * 0.26f
        val arc = android.graphics.RectF(pad, pad, px - pad, px - pad)
        c.drawArc(arc, 40f, 280f, false, p)
        // arrowhead at arc end (~320deg)
        val aRad = Math.toRadians(320.0)
        val cx = px / 2f + (px / 2f - pad) * Math.cos(aRad).toFloat()
        val cy = px / 2f + (px / 2f - pad) * Math.sin(aRad).toFloat()
        val ah = Path()
        ah.moveTo(cx, cy)
        ah.lineTo(cx - px * 0.16f, cy - px * 0.02f)
        ah.lineTo(cx - px * 0.02f, cy - px * 0.16f)
        ah.close()
        p.style = Paint.Style.FILL
        c.drawPath(ah, p)
        return bmp
    }

    private fun hideSystemUi() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        glView.onResume()
    }

    override fun onPause() {
        glView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        debugReceiver?.let { unregisterReceiver(it) }
        debugReceiver = null
        // onPause has stopped drawing; queued GL events may never run again.
        glView.renderer.game?.release()
        super.onDestroy()
    }
}
