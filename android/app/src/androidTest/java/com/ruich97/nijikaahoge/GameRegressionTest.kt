package com.ruich97.nijikaahoge

import android.test.InstrumentationTestCase
import android.util.Log
import com.ruich97.nijikaahoge.game.Game
import com.ruich97.nijikaahoge.game.Obj
import com.ruich97.nijikaahoge.game.Phys
import com.ruich97.nijikaahoge.gfx.SpriteBatch

@Suppress("DEPRECATION")
class GameRegressionTest : InstrumentationTestCase() {
    private lateinit var game: Game

    override fun setUp() {
        super.setUp()
        game = Game(instrumentation.targetContext)
        game.world.rnd.setSeed(1234)
        game.logic.sprite9TimerRestart(1200f)
    }

    override fun tearDown() {
        game.release()
        super.tearDown()
    }

    private fun advance(seconds: Float, g: Game = game) {
        repeat((seconds * 60).toInt()) { g.update(1f / 60f) }
    }

    private fun center(o: Obj): Pair<Float, Float> {
        val p = o.polyWorld()
        var x = 0f; var y = 0f
        for (i in p.indices step 2) { x += p[i]; y += p[i + 1] }
        return Pair(x / (p.size / 2), y / (p.size / 2))
    }

    private fun justRightOf(o: Obj, distance: Float): Pair<Float, Float> {
        val p = o.polyWorld()
        val i = p.indices.step(2).maxBy { p[it] }
        return Pair(p[i] + distance, p[i + 1])
    }

    fun testHeadTouchPaddingPullsWithoutJumping() {
        advance(3f)
        val head = game.world.allOf("p3").first { it.ivb("O") }
        val p = justRightOf(head, 8f)
        val beforeX = head.x; val beforeY = head.y
        val polygon = head.polyWorld().copyOf()
        assertFalse(head.containsPoint(p.first, p.second))
        game.touchDown(p.first, p.second)
        assertNull(game.logic.dragObj)
        game.touchDown(p.first, p.second, 12f)
        assertSame(head, game.logic.dragObj)
        assertEquals(beforeX, head.x, 0f); assertEquals(beforeY, head.y, 0f)
        assertTrue(polygon.contentEquals(head.polyWorld()))
        game.touchMove(p.first + 100f, p.second - 100f)
        assertEquals(beforeX + 100f, head.x, .001f)
        assertEquals(beforeY - 100f, head.y, .001f)
        game.touchUp(0f, 0f)
        assertFalse(head.ivb("O"))
        assertEquals(1, game.world.allOf("p3").count { it.ivb("O") })
        advance(3f)
        val next = game.world.allOf("p3").first { it.ivb("O") }
        val far = justRightOf(next, 13f)
        game.touchDown(far.first, far.second, 12f)
        assertNull(game.logic.dragObj)
    }

    fun testExactItemWinsOverHeadPaddingAndOtherItemsStayPrecise() {
        advance(3f)
        val head = game.world.allOf("p3").first { it.ivb("O") }
        val p = justRightOf(head, 8f)
        val bag = game.logic.bag()!!
        val c = center(bag)
        bag.x += p.first - c.first; bag.y += p.second - c.second
        assertTrue(bag.containsPoint(p.first, p.second))
        game.touchDown(p.first, p.second, 12f)
        assertSame(bag, game.logic.dragObj)
        game.touchUp(0f, 0f)
        bag.x = 2500f
        val debris = game.world.createSprite("p3", 3, 0f, 0f, "1", false)!!
        debris.setFrame(5); debris.playing = false; debris.dragEnabled = true
        debris.ivars["O"] = 0.0
        val dc = center(debris)
        debris.x += p.first - dc.first; debris.y += p.second - dc.second
        game.touchDown(p.first, p.second, 12f)
        assertSame(debris, game.logic.dragObj)
        game.touchUp(0f, 0f)
        debris.x = 1000f; debris.y = 400f
        val miss = justRightOf(debris, 8f)
        game.touchDown(miss.first, miss.second, 12f)
        assertNull(game.logic.dragObj)
    }

    fun testPaddingFollowsMirroredRotatedPolygon() {
        val head = game.world.allOf("p3").first { it.ivb("O") }
        for (mirror in listOf(false, true)) {
            head.mirrored = mirror; head.angle = .7f
            val near = justRightOf(head, 8f)
            val far = justRightOf(head, 13f)
            assertFalse(head.containsPoint(near.first, near.second))
            assertTrue(head.containsPointWithPadding(near.first, near.second, 12f))
            assertFalse(head.containsPointWithPadding(far.first, far.second, 12f))
        }
    }

    fun testFingerNearMissPullsThroughGameView() {
        val activity = instrumentation.startActivitySync(
            android.content.Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            val field = MainActivity::class.java.getDeclaredField("glView")
            field.isAccessible = true
            val view = field.get(activity) as GameView
            val deadline = System.currentTimeMillis() + 10000
            while (view.renderer.game == null && System.currentTimeMillis() < deadline) Thread.sleep(50)
            val live = view.renderer.game!!
            lateinit var head: Obj
            var sx = 0f; var sy = 0f
            fun onGl(action: () -> Unit) {
                val done = java.util.concurrent.CountDownLatch(1)
                var failure: Throwable? = null
                view.queueEvent {
                    try { action() } catch (t: Throwable) { failure = t }
                    finally { done.countDown() }
                }
                assertTrue(done.await(5, java.util.concurrent.TimeUnit.SECONDS))
                failure?.let { throw it }
            }
            onGl {
                live.logic.sprite9TimerRestart(1200f)
                head = live.world.allOf("p3").first { it.ivb("O") }
                head.setFrame(5); head.playing = false
                val miss = justRightOf(head, 8f)
                assertFalse(head.containsPoint(miss.first, miss.second))
                val origin = view.renderer.toLogical(0f, 0f)
                val unit = view.renderer.toLogical(1f, 1f)
                sx = (miss.first - origin.first) / (unit.first - origin.first)
                sy = (miss.second - origin.second) / (unit.second - origin.second)
            }
            fun send(action: Int, tool: Int, source: Int, x: Float = sx, y: Float = sy) {
                val now = android.os.SystemClock.uptimeMillis()
                val properties = android.view.MotionEvent.PointerProperties().apply { id = 0; toolType = tool }
                val coords = android.view.MotionEvent.PointerCoords().apply {
                    this.x = x; this.y = y; pressure = 1f; size = 1f
                }
                val event = android.view.MotionEvent.obtain(now, now, action, 1,
                    arrayOf(properties), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, source, 0)
                try { instrumentation.runOnMainSync { view.onTouchEvent(event) } }
                finally { event.recycle() }
            }
            for (tool in listOf(android.view.MotionEvent.TOOL_TYPE_MOUSE, android.view.MotionEvent.TOOL_TYPE_STYLUS)) {
                val source = if (tool == android.view.MotionEvent.TOOL_TYPE_MOUSE)
                    android.view.InputDevice.SOURCE_MOUSE else android.view.InputDevice.SOURCE_STYLUS
                send(android.view.MotionEvent.ACTION_DOWN, tool, source)
                onGl { assertNull(live.logic.dragObj) }
                send(android.view.MotionEvent.ACTION_UP, tool, source)
            }
            val finger = android.view.MotionEvent.TOOL_TYPE_FINGER
            val touch = android.view.InputDevice.SOURCE_TOUCHSCREEN
            send(android.view.MotionEvent.ACTION_DOWN, finger, touch)
            onGl { assertSame(head, live.logic.dragObj) }
            send(android.view.MotionEvent.ACTION_MOVE, finger, touch, sx + 40f, sy - 40f)
            send(android.view.MotionEvent.ACTION_UP, finger, touch, sx + 40f, sy - 40f)
            onGl {
                assertFalse(head.ivb("O"))
                assertEquals(1, live.world.allOf("p3").count { it.ivb("O") })
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    fun testFlyerDirectionsMatchOriginal() {
        game.logic.banknote()!!.body!!.body.isActive = false
        val expected = arrayOf(
            floatArrayOf(-186f, 333f, 46f, 0f),
            floatArrayOf(1228f, 1081f, 341f, 1f),
            floatArrayOf(-30f, 1050f, 10f, 0f),
            floatArrayOf(1201f, -81f, 217f, 1f))
        for (i in expected.indices) {
            game.logic.debug("s9dir=${i + 1}")
            advance(2.2f)
            val s9 = game.logic.flyer()!!
            assertEquals(expected[i][0], s9.x, .02f)
            assertEquals(expected[i][1], s9.y, .02f)
            assertEquals(expected[i][2], Math.toDegrees(s9.angle.toDouble()).toFloat(), .02f)
            assertEquals(expected[i][3] == 1f, s9.mirrored)
            assertTrue(s9.isOnScreen(1280f, 1024f))
            game.world.sched.signal("Z")
            advance(2.7f)
            assertFalse(game.logic.groupS)
        }
    }

    fun testSleepingBocchiReturnsRepeatedly() {
        advance(10f)
        val s5 = game.logic.banknote()!!
        val body = s5.body!!.body
        assertFalse(body.isAwake)
        repeat(3) {
            game.phys.teleport(s5.body!!, 1182f, -1109f)
            assertTrue(body.isAwake)
            advance(2f)
            game.phys.teleport(s5.body!!, 1182f, -633f)
            advance(8f)
            assertTrue("Bocchi should return, y=${s5.y}", s5.y in 800f..1050f)
            assertFalse(body.isAwake)
            assertEquals(1, game.world.allOf("Sprite5").size)
        }
    }

    fun testSleepingBocchiCollisionAndReturn() {
        advance(10f)
        val s5 = game.logic.banknote()!!
        assertFalse(s5.body!!.body.isAwake)
        val s9 = game.logic.flyer()!!
        game.logic.debug("s9dir=1")
        game.world.tweens.kill(s9)
        s9.angle = 0f; s9.mirrored = false
        s9.x = s5.x - 50f; s9.y = s5.y + 100f
        assertTrue(game.logic.overlap(s5, s9))
        game.update(1f / 60f)
        assertEquals(1, s9.frameIndex)
        assertTrue(s5.y < -1000f)
        assertTrue(s5.body!!.body.isAwake)
        advance(2.1f)
        assertEquals(0, s9.frameIndex)
        assertTrue(s5.y < -500f)
        advance(6f)
        assertTrue(s5.y in 800f..1050f)
        assertFalse(game.logic.groupS)
    }

    fun testReleaseStopsDragVelocity() {
        advance(3f)
        val bag = game.logic.bag()!!
        game.phys.setKinematic(bag.body!!, true)
        game.phys.driveTo(bag.body!!, 500f, 200f, 1f / 60f)
        assertTrue(bag.body!!.body.linearVelocity.lengthSquared() > 0f)
        game.phys.setKinematic(bag.body!!, false)
        assertEquals(0f, bag.body!!.body.linearVelocity.lengthSquared(), 0f)
        assertEquals(0f, bag.body!!.body.angularVelocity, 0f)
    }

    fun testMirrorGeometryAndCache() {
        val s9 = game.logic.flyer()!!
        s9.x = 500f; s9.y = 600f; s9.angle = 0f
        s9.mirrored = false
        val normal = s9.polyWorld().copyOf()
        val oldBounds = s9.boundingBox().copyOf()
        s9.mirrored = true
        val mirror = s9.polyWorld()
        for (i in normal.indices step 2) {
            assertEquals(1000f - normal[i], mirror[i], .001f)
            assertEquals(normal[i + 1], mirror[i + 1], .001f)
        }
        assertEquals(1000f - oldBounds[2], s9.boundingBox()[0], .001f)
        assertSame(s9.polyWorld(), s9.polyWorld())
        assertSame(s9.trianglesWorld(), s9.trianglesWorld())
        assertSame(game.world.drawOrder(), game.world.drawOrder())
    }

    fun testMirroredDrawMatchesCollisionBounds() {
        val o = game.logic.flyer()!!
        o.x = 500f; o.y = 600f; o.angle = .4f; o.mirrored = true
        val batch = SpriteBatch()
        val w = o.displayW(); val h = o.displayH()
        batch.quad(1, o.x - o.ox * w, o.y - o.oy * h, w, h,
            o.x, o.y, o.angle, true, 1f, 1f, 1f, 1f)
        val field = SpriteBatch::class.java.getDeclaredField("verts")
        field.isAccessible = true
        val vertices = field.get(batch) as FloatArray
        val bounds = o.boundingBox()
        assertEquals(bounds[0], (0..3).minOf { vertices[it * 8] }, .001f)
        assertEquals(bounds[2], (0..3).maxOf { vertices[it * 8] }, .001f)
        assertEquals(bounds[1], (0..3).minOf { vertices[it * 8 + 1] }, .001f)
        assertEquals(bounds[3], (0..3).maxOf { vertices[it * 8 + 1] }, .001f)
    }

    fun testFullAhogeUsesOneFixture() {
        for (animation in listOf("1", "3")) {
            val o = game.world.createSprite("p3", 3, 800f, 400f, animation, true)!!
            o.setFrame(5)
            o.body = game.phys.bodyFor(o, Phys.Props(false, 0, false, 1f, .5f, 0f, 0f, 0f, false, true))
            assertNull(o.body!!.body.fixtureList.next)
            assertEquals(5, (o.body!!.body.fixtureList.shape as org.jbox2d.collision.shapes.PolygonShape).vertexCount)
        }
    }

    fun testBagOffscreenKeepsContents() {
        advance(3f)
        game.logic.debug("cnt=10+bcnt=3+n=1+bflag=1")
        game.update(1f / 60f)
        val b = game.logic.bag()!!
        val before = center(b)
        game.touchDown(before.first, before.second)
        assertSame(b, game.logic.dragObj)
        game.touchMove(before.first, before.second + 1500f)
        game.update(1f / 60f)
        assertNull(game.logic.dragObj)
        advance(5f)
        assertEquals(10, game.logic.bagCount())
        assertEquals(3, game.logic.bagBCount())
        assertTrue(game.logic.bagN())
        assertEquals(2, b.frameIndex)
        assertTrue(b.y in 700f..1000f)
        assertTrue(b.dragEnabled)
    }

    fun testTrueCollectAndFeedLoop() {
        advance(3f)
        repeat(10) {
            val head = game.world.allOf("p3").first { it.ivb("O") }
            val c = center(head)
            game.touchDown(c.first, c.second)
            assertSame(head, game.logic.dragObj)
            game.touchMove(c.first + 940f - head.x, c.second + 600f - head.y)
            game.touchUp(0f, 0f)
            advance(2.5f)
            assertEquals(it + 1, game.logic.bagCount())
        }
        val b = game.logic.bag()!!
        val r = game.logic.ryo()!!
        val bc = center(b); val rc = center(r)
        game.touchDown(bc.first, bc.second)
        game.touchMove(rc.first, rc.second)
        game.update(1f / 60f)
        assertEquals(1, game.logic.score)
        assertEquals(0, game.logic.bagCount())
        advance(5f)
        assertTrue(b.dragEnabled)
    }

    fun testFeedingBranchMatrixAndRepeatedCelebration() {
        val cases = arrayOf(
            intArrayOf(0, 0, 0, 0, 2), intArrayOf(0, 1, 2, 1, 3),
            intArrayOf(0, 1, 7, 1, 5), intArrayOf(0, 1, 10, 1, 5),
            intArrayOf(0, 1, 10, 1, 5), intArrayOf(1, 1, 2, 0, 2),
            intArrayOf(1, 1, 7, 0, 3), intArrayOf(1, 1, 10, 0, 3))
        for ((index, c) in cases.withIndex()) {
            advance(6f)
            game.logic.debug("cnt=10+n=${c[1]}+bcnt=${c[2]}+bflag=${c[3]}")
            advance(4f)
            val girl = if (c[0] == 0) game.logic.ryo()!! else game.logic.kita()!!
            val b = game.logic.bag()!!
            val bc = center(b); val gc = center(girl)
            game.touchDown(bc.first, bc.second)
            game.touchMove(gc.first, gc.second)
            game.update(1f / 60f)
            assertEquals("Branch $index", index + 1, game.logic.score)
            assertEquals(c[4], girl.frameIndex)
            assertTrue(game.parts.emitters.any { it.layer == 3 })
            if (c[2] >= 10) {
                assertEquals(0, game.logic.bocchi()!!.frameIndex)
                advance(1.2f)
                assertEquals(1, game.logic.bocchi()!!.frameIndex)
            }
        }
    }

    fun testMilestones() {
        for (score in listOf(10, 50, 100, 500, 1000, 5000)) {
            game.logic.debug("score=$score")
            game.update(1f / 60f)
            assertEquals("2", game.logic.bocchi()!!.anim!!.name)
            if (score >= 50) assertEquals(10f, game.logic.sprite9TimerS, .02f)
            advance(5f)
            assertEquals(1515f, game.logic.bocchi()!!.x, .02f)
        }
    }

    fun testEglRecreationKeepsLiveGame() {
        val activity = instrumentation.startActivitySync(
            android.content.Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            val field = MainActivity::class.java.getDeclaredField("glView")
            field.isAccessible = true
            val view = field.get(activity) as GameView
            val deadline = System.currentTimeMillis() + 10000
            while (view.renderer.game == null && System.currentTimeMillis() < deadline) Thread.sleep(50)
            val live = view.renderer.game!!
            val ready = java.util.concurrent.CountDownLatch(1)
            view.queueEvent { live.logic.score = 37; ready.countDown() }
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                view.preserveEGLContextOnPause = false
                instrumentation.callActivityOnPause(activity)
                instrumentation.callActivityOnResume(activity)
            }
            Thread.sleep(500)
            assertSame(live, view.renderer.game)
            assertEquals(37, view.renderer.game!!.logic.score)
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    fun testManyAhogePhysicsAndAllocationBudget() {
        advance(3f)
        for (count in listOf(60, 120, 240)) {
            for (o in game.world.allOf("p3").toList()) if (!o.ivb("O")) game.world.destroy(o)
            repeat(count) { i -> game.logic.debug("ahoge=${100 + (i % 20) * 52},${300 + (i / 20) * 20}") }
            val samples = DoubleArray(600)
            for (i in samples.indices) {
                val start = System.nanoTime()
                game.update(1f / 60f)
                for (o in game.world.drawOrder()) o.boundingBox()
                samples[i] = (System.nanoTime() - start) / 1e6
            }
            samples.sort()
            Log.i("AhogePerf", "debris=$count medianMs=${samples[300]} p95Ms=${samples[570]} p99Ms=${samples[594]} live=${game.world.allOf("p3").size}")
            assertTrue(samples.all { it.isFinite() })
            assertEquals(1, game.world.allOf("p3").count { it.ivb("O") })
        }
    }
}
