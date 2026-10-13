package com.ruich97.nijikaahoge.game

/**
 * Port of the Construct event sheet (see events_decoded.txt).
 * Variable names map: 多力多滋 ivars [數量,N,B數量,B]; p3 ivar [O];
 * globals Score, Rand; event var Ran.
 */
class Logic(val w: World) {

    var score = 0
    var randV = 0f
    var ran = 0f
    var groupS = false
    var sprite9TimerS = -1f    // seconds until OnTimer("S"), -1 = not running
    var bagOffscreenWas = false
    val firedMilestones = HashSet<Int>()

    // references resolved lazily
    fun bag() = w.byName("多力多滋")
    fun bag2() = w.byName("多力多滋2")
    fun ryo() = w.byName("涼")
    fun kita() = w.byName("喜多")
    fun body() = w.byName("p1")
    fun face() = w.byName("p2")
    fun mouth() = w.byName("Sprite")
    fun bocchi() = w.byName("Sprite7")
    fun flyer() = w.byName("Sprite9")
    fun banknote() = w.byName("Sprite5")
    fun font() = w.byName("位圖字體")

    fun bagCount() = bag()?.iv("數量")?.toInt() ?: 0
    fun bagBCount() = bag()?.iv("B數量")?.toInt() ?: 0
    fun bagN() = bag()?.ivb("N") == true
    fun bagB() = bag()?.ivb("B") == true
    fun holdingBag() = dragging === bag()

    // ---- input state ----
    var dragObj: Obj? = null
    var dragDX = 0f; var dragDY = 0f
    val dragging: Obj? get() = dragObj

    // pending physics contacts collected during world.step
    private val pendingContacts = ArrayList<Pair<Obj, Obj>>()

    /** adb debug entry: +-separated k=v pairs */
    fun debug(cmd: String) {
        try { debugInner(cmd) } catch (t: Throwable) {
            android.util.Log.w("Ahoge", "debug cmd failed: $cmd", t)
        }
    }

    private fun debugInner(cmd: String) {
        for (kv in cmd.split(Regex("[;+]"))) {
            val p = kv.split("=")
            val k = p[0].trim(); val v = if (p.size > 1) p[1].trim() else ""
            val b = bag()
            when (k) {
                "cnt" -> b?.ivars?.set("數量", v.toDoubleOrNull() ?: continue)
                "bcnt" -> b?.ivars?.set("B數量", v.toDoubleOrNull() ?: continue)
                "n" -> b?.ivars?.set("N", v.toDoubleOrNull() ?: continue)
                "bflag" -> b?.ivars?.set("B", v.toDoubleOrNull() ?: continue)
                "score" -> score = v.toIntOrNull() ?: continue
                "peek" -> { // force a girl to peek target
                    val g = if (v == "ryo") ryo() else kita()
                    g?.let { w.tweens.to(it, if (v == "ryo") 1320f else 1346f, 0.5f, 15) }
                }
                "retreat" -> {
                    ryo()?.let { w.tweens.to(it, 1740f, 0.5f, 15) }
                    kita()?.let { w.tweens.to(it, 1740f, 0.5f, 15) }
                }
                "s9timer" -> sprite9TimerS = v.toFloatOrNull() ?: continue
                "s9dir" -> {
                    val direction = v.toIntOrNull() ?: continue
                    if (direction in 1..4) {
                        groupS = true
                        sprite9TimerS = 1200f
                        startFlyer(direction)
                    }
                }
                "font" -> font()?.text = v
                "ahoge" -> { // spawn debris ahoge anim "1" at x,y
                    val xy = v.split(","); if (xy.size < 2) continue
                    val o = w.createSprite("p3", 3, xy[0].toFloatOrNull() ?: continue,
                        xy[1].toFloatOrNull() ?: continue, "1", false)
                    if (o != null) {
                        o.ivars["O"] = 0.0
                        o.dragEnabled = true
                        o.setFrame(o.anim?.frames?.size?.minus(1) ?: 0) // finished state
                        o.body = w.phys.bodyFor(o, Phys.Props(false, 0, false, 1f, 0.5f, 0f, 0f, 0f, false, true))
                    }
                }
                "goldahoge" -> {
                    val xy = v.split(","); if (xy.size < 2) continue
                    val o = w.createSprite("p3", 3, xy[0].toFloatOrNull() ?: continue,
                        xy[1].toFloatOrNull() ?: continue, "3", false)
                    if (o != null) {
                        o.ivars["O"] = 0.0
                        o.dragEnabled = true
                        o.setFrame(o.anim?.frames?.size?.minus(1) ?: 0)
                        o.body = w.phys.bodyFor(o, Phys.Props(false, 0, false, 1f, 0.5f, 0f, 0f, 0f, false, true))
                    }
                }
                "bagpos" -> {
                    val xy = v.split(","); if (xy.size < 2) continue
                    val xx = xy[0].toFloatOrNull() ?: continue
                    val yy = xy[1].toFloatOrNull() ?: continue
                    b?.let {
                        it.x = xx; it.y = yy
                        it.body?.let { r -> w.phys.teleport(r, it.x, it.y) }
                    }
                }
                "s5pos" -> {
                    val xy = v.split(","); if (xy.size < 2) continue
                    val xx = xy[0].toFloatOrNull() ?: continue
                    val yy = xy[1].toFloatOrNull() ?: continue
                    banknote()?.let {
                        it.x = xx; it.y = yy
                        it.body?.let { r -> w.phys.teleport(r, it.x, it.y) }
                    }
                }
                "s9pos" -> {
                    val xy = v.split(","); if (xy.size < 2) continue
                    val xx = xy[0].toFloatOrNull() ?: continue
                    val yy = xy[1].toFloatOrNull() ?: continue
                    flyer()?.let { it.x = xx; it.y = yy }
                }
                "dump" -> {
                    val p3info = w.allOf("p3").joinToString("|") { o ->
                        val bd = o.body?.body
                        val v = if (bd != null && bd.isActive) "%.0f,%.0f,v%.0f,%.0f".format(
                            o.x, o.y, bd.linearVelocity.x * 50f, bd.linearVelocity.y * 50f)
                        else "%.0f,%.0f".format(o.x, o.y)
                        v
                    }
                    android.util.Log.i("Ahoge",
                        "STATE score=$score cnt=${b?.iv("數量")} bcnt=${b?.iv("B數量")} N=${b?.iv("N")} B=${b?.iv("B")} " +
                        "bagPos=${b?.x},${b?.y} frame=${b?.frameIndex} ryo=${ryo()?.x} kita=${kita()?.x} " +
                        "font='${font()?.text}' s9t=$sprite9TimerS p3s=${w.allOf("p3").size} objs=${w.objs.size} " +
                        "s5=${banknote()?.x},${banknote()?.y},awake=${banknote()?.body?.body?.isAwake} " +
                        "s9=${flyer()?.x},${flyer()?.y},f${flyer()?.frameIndex},mirror=${flyer()?.mirrored} gS=$groupS " +
                        "p3list=$p3info")
                }
            }
        }
    }

    fun init() {
        w.phys.contactHandler = { a, b ->
            pendingContacts.add(Pair(a, b))
        }
        onLayoutStart()
    }

    /** called before each physics step: drive kinematic bodies of dragged objects */
    fun prePhysics(dt: Float) {
        val o = dragObj ?: return
        val ref = o.body ?: return
        if (ref.body.isActive) w.phys.driveTo(ref, o.x, o.y, dt)
    }

    // ================= OnStartOfLayout =================
    private fun onLayoutStart() {
        val b = bag(); if (b != null) {
            b.setScale(0.8f)
            b.ivars["N"] = 0.0
        }
        bag2()?.let { it.setScale(0.8f); it.visible = true }
        mouth()?.setAnim("2")
        val r = ryo(); if (r != null) w.tweens.to(r, 1740f, 2f, 15)
        val k = kita(); if (k != null) w.tweens.to(k, 1740f, 2f, 15)
        groupS = false
        sprite9TimerS = w.rand(20f, 50f)
        // 多力多滋2 attaches as child of 多力多滋
        bag2()?.let { child -> child.child = null; bag()?.child = child }
        // initial p3s: physics disabled (already inactive via props)
    }

    // ================= input =================
    fun touchDown(x: Float, y: Float, headTouchRadius: Float = 0f) {
        // topmost draggable under point (walk objects in reverse draw order)
        val candidates = objsDrawOrder().asReversed()
        for (o in candidates) {
            if (!o.dragEnabled || o.destroyed || !o.visible) continue
            if (o.containsPoint(x, y)) {
                beginDrag(o, x, y)
                return
            }
        }
        // Finger tolerance is a fallback; an exact hit on another item always wins.
        if (headTouchRadius <= 0f || x !in 0f..1280f || y !in 0f..1024f) return
        val padding = headTouchRadius.coerceAtMost(96f)
        for (o in candidates) {
            if (o.type.name != "p3" || !o.ivb("O") || !o.dragEnabled ||
                o.destroyed || !o.visible) continue
            if (o.containsPointWithPadding(x, y, padding)) {
                beginDrag(o, x, y)
                return
            }
        }
    }

    private fun beginDrag(o: Obj, x: Float, y: Float) {
        dragObj = o
        dragDX = o.x - x; dragDY = o.y - y
        o.body?.let { w.phys.setKinematic(it, true) }
        o.bodyDrives = false
        if (o.type.name == "p3") {
            if (o.ivb("O")) face()?.let { f -> f.setAnim("Animation 1", true) }
            w.vib.tick()
            w.snd.play("0", false, -20f, 0f)
        }
        if (o.type.name == "多力多滋") {
            w.snd.play(w.choose(3, 6).toString(), false, -5f, w.rand(-50f, 50f))
        }
    }

    fun touchMove(x: Float, y: Float) {
        val o = dragObj ?: return
        o.x = x + dragDX; o.y = y + dragDY
    }

    fun touchUp(x: Float, y: Float) {
        val o = dragObj ?: return
        dragObj = null
        val ref = o.body
        when (o.type.name) {
            "p3" -> {
                if (o.ivb("O")) {
                    // released the head ahoge: grow a new one, this becomes debris
                    grow()
                    o.ivars["O"] = 0.0
                    if (ref != null) {
                        w.phys.setKinematic(ref, false)
                        w.phys.setEnabled(ref, true)
                    }
                } else if (ref != null) {
                    w.phys.setKinematic(ref, false)
                }
                o.bodyDrives = true
            }
            "多力多滋" -> {
                if (ref != null) w.phys.setKinematic(ref, false)
                o.bodyDrives = true
            }
        }
    }

    // ================= 長出來 =================
    private fun grow() {
        // create p3 at (461,227), random anim (26/27 normal "1", 1/27 golden "3")
        val anim = if (w.rnd.nextInt(27) == 26) "3" else "1"
        val o = w.createSprite("p3", 3, 461f, 227f, anim, false) ?: return
        o.ivars["O"] = 1.0          // template copies the layout instance (O=1)
        o.dragEnabled = true
        // physics body created but disabled (matches 物理.SetEnabled(0))
        val pp = Phys.Props(false, 0, false, 1f, 0.5f, 0f, 0f, 0f, false, false)
        o.body = w.phys.bodyFor(o, pp)
    }

    // ================= every tick =================
    fun tick(dt: Float) {
        // drive kinematic drag targets via physics step happens in World.tick;
        // here: apply pending contacts (bag collects ahoge)
        if (pendingContacts.isNotEmpty()) {
            val cs = pendingContacts.toList(); pendingContacts.clear()
            for ((a, b) in cs) onContact(a, b)
        }

        val b = bag() ?: return
        val cnt = b.iv("數量").toInt()
        val bcnt = b.iv("B數量").toInt()
        val n = b.ivb("N")
        val bb = b.ivb("B")

        // ---- character approach tweens ----
        val r = ryo(); val k = kita()
        if (cnt >= 5 && bcnt <= 1) {
            if (r != null) w.tweens.to(r, 1320f, 2f, 15)
            if (k != null) w.tweens.to(k, 1740f, 2f, 15)
        }
        if (cnt >= 5 && bcnt >= 2) {
            if (bb) {
                if (r != null) w.tweens.to(r, 1320f, 2f, 15)
                if (k != null) w.tweens.to(k, 1740f, 2f, 15)
            } else {
                if (r != null) w.tweens.to(r, 1740f, 2f, 15)
                if (k != null) w.tweens.to(k, 1346f, 2f, 15)
            }
        }

        // ---- bag frame / overlay / mouth anim ----
        if (cnt >= 10) {
            b.setFrame(if (n) 2 else 1)
            bag2()?.visible = false
            mouth()?.setAnim("1")
        } else {
            b.setFrame(0)
            bag2()?.visible = true
            mouth()?.setAnim("2")
        }

        // ---- bag offscreen reset ----
        // original detail: respawning keeps the bag contents (數量/B數量/N/B/scale)
        val on = b.isOnScreen(w.data.canvasW, w.data.canvasH)
        if (!on && !bagOffscreenWas) {
            bagOffscreenWas = true
            // C3 DragDrop: disabling mid-drag ends the drag
            if (dragObj === b) {
                dragObj = null
                b.body?.let { w.phys.setKinematic(it, false) }
                b.bodyDrives = true
            }
            b.dragEnabled = false
            b.x = 940f; b.y = -200f
            b.body?.let { w.phys.teleport(it, 940f, -200f) }
            w.sched.seq(
                Scheduler.Wait(1f),
                Scheduler.Act { b.dragEnabled = true }
            )
        } else if (on) bagOffscreenWas = false

        // ---- face frames while holding ----
        if (holdingBag()) {
            if (cnt >= 10 && !n) r?.setFrame(1)
            if (cnt >= 10 && n) { r?.setFrame(4); k?.setFrame(1) }
        } else {
            if (cnt >= 3) { r?.setFrame(0); k?.setFrame(0) }
        }

        // ---- feeding ----
        feeding(b, r, k)

        // ---- flyer timer ----
        if (sprite9TimerS >= 0) {
            sprite9TimerS -= dt
            if (sprite9TimerS < 0) onTimerS()
        }

        // ---- group S: Sprite5 x Sprite9 ----
        if (groupS) {
            val s5 = banknote(); val s9 = flyer()
            if (s5 != null && s9 != null && overlap(s5, s9) && !s5BanknoteBusy) {
                s5BanknoteBusy = true
                s5.x = 1182f; s5.y = -1109f
                s5.body?.let { w.phys.teleport(it, 1182f, -1109f) }
                s9.setFrame(1)
                w.spawnParticles("粒子6", s9, 5)
                w.snd.play("10", false, -15f, w.rand(-50f, 50f))
                w.sched.signal("Z")
                w.sched.seq(
                    Scheduler.Wait(2f),
                    Scheduler.Act {
                        s9.setFrame(0)
                        s5.x = 1182f; s5.y = -633f
                        s5.body?.let { w.phys.teleport(it, 1182f, -633f) }
                        s5BanknoteBusy = false
                    }
                )
            }
        }

        // ---- score milestones ----
        for (m in intArrayOf(10, 50, 100, 500, 1000, 5000)) {
            if (score == m && !firedMilestones.contains(m)) {
                firedMilestones.add(m)
                milestone(m)
            }
        }

        // ---- offscreen p3 cleanup / respawn ----
        val ahoges = w.allOf("p3")
        for (i in ahoges.size - 1 downTo 0) {
            val o = ahoges[i]
            if (!o.isOnScreen(w.data.canvasW, w.data.canvasH)) {
                if (o.ivb("O")) {
                    grow()
                    o.ivars["O"] = 0.0
                    o.body?.let { w.phys.setEnabled(it, true) }
                }
                w.destroy(o)
            }
        }

        // drag drive happens in PhysicsDrive (renderer calls before step)
    }

    private var s5BanknoteBusy = false

    private fun onContact(a: Obj, b: Obj) {
        val bagO = bag() ?: return
        val p3 = if (a === bagO) b else if (b === bagO) a else null
        if (p3 == null || p3.type.name != "p3" || p3.destroyed) return
        if (bagO.frameIndex != 0) return
        if (p3.ivb("O")) return
        val animName = p3.anim?.name ?: return
        when (animName) {
            "1" -> {
                w.destroy(p3)
                bagO.ivars["數量"] = bagO.iv("數量") + 1
                bagO.setScale(0.9f + (bagO.iv("數量").toFloat() * 0.03f))
                w.vib.tick()
                w.snd.play(w.choose(4, 3, 6).toString(), false, -5f, w.rand(-50f, 50f))
                if (bagO.iv("數量").toInt() == 10) w.snd.play("4", false, -5f, 0f)
            }
            "3" -> {
                w.destroy(p3)
                bagO.ivars["N"] = 1.0
                bagO.ivars["數量"] = bagO.iv("數量") + 1
                bagO.ivars["B數量"] = bagO.iv("B數量") + 1
                bagO.setScale(0.9f + (bagO.iv("數量").toFloat() * 0.03f))
                w.vib.tick()
                w.snd.play(w.choose(4, 3, 6).toString(), false, -5f, w.rand(-50f, 50f))
                if (bagO.iv("數量").toInt() == 10) w.snd.play("4", false, -5f, 0f)
            }
        }
    }

    // ================= feeding =================
    private fun feeding(b: Obj, r: Obj?, k: Obj?) {
        val cnt = b.iv("數量").toInt()
        val bcnt = b.iv("B數量").toInt()
        val n = b.ivb("N"); val bb = b.ivb("B")
        if (cnt < 10 || !holdingBag()) return

        if (r != null && overlap(b, r)) {
            if (!n) {
                feedRyoNormal(b, r); return
            }
            if (bcnt <= 5) { feedRyoGoldenLow(b, r); return }
            if (bb && bcnt in 6..9) { feedRyoGoldenMid(b, r); return }
            if (bb && bcnt >= 10) { feedRyoGoldenMax(b, r); return }
        }
        if (k != null && overlap(b, k)) {
            if (!n) return
            if (bcnt <= 5) { feedKitaGoldenLow(b, k); return }
            if (!bb && bcnt in 6..9) { feedKitaGoldenMid(b, k); return }
            if (!bb && bcnt >= 10) { feedKitaGoldenMax(b, k); return }
        }
    }

    /** shared bag reset + score + 抽 + re-enable sequence */
    private fun feedReset(b: Obj) {
        score += 1
        font()?.text = score.toString()
        // C3 DragDrop: disabling mid-drag ends the drag
        if (dragObj === b) {
            dragObj = null
            b.body?.let { w.phys.setKinematic(it, false) }
            b.bodyDrives = true
        }
        b.dragEnabled = false
        b.setScale(0.8f)
        bag2()?.setScale(0.8f)
        b.x = 940f; b.y = -200f
        b.body?.let { w.phys.teleport(it, 940f, -200f) }
        bag2()?.visible = true
        chou()
        b.ivars["數量"] = 0.0
        b.ivars["B數量"] = 0.0
    }

    /** last step of every feed sequence: girl back to idle frame, drag re-enabled */
    private fun girlIdle(b: Obj, girl: Obj) {
        girl.setFrame(0)
        w.tweens.to(girl, 1740f, 2f, 14)
        b.dragEnabled = true
    }

    /** plain feed tail: wait out the bag tween, then [girlIdle] */
    private fun feedTail(b: Obj, girl: Obj) {
        w.sched.seq(
            Scheduler.Wait(1f),
            Scheduler.WaitTweens(b, w.tweens),
            Scheduler.Wait(1.5f),
            Scheduler.Act { girlIdle(b, girl) }
        )
    }

    private fun feedRyoNormal(b: Obj, r: Obj) {
        r.setFrame(2)
        w.spawnParticles("粒子", r, 3)
        w.snd.play(w.choose(1, 2, 2, 2, 2).toString(), false, -10f, w.rand(-50f, 50f))
        feedReset(b)
        feedTail(b, r)
    }

    private fun feedRyoGoldenLow(b: Obj, r: Obj) {
        r.setFrame(3)
        b.ivars["N"] = 0.0
        w.spawnParticles("粒子2", r, 3)
        w.snd.play("5", false, -15f, w.rand(-50f, 50f))
        feedReset(b)
        feedTail(b, r)
    }

    private fun feedRyoGoldenMid(b: Obj, r: Obj) {
        r.setFrame(5)
        b.ivars["N"] = 0.0
        w.spawnParticles("粒子13", r, 3)
        w.snd.play("5", false, -15f, w.rand(-50f, 50f))
        feedReset(b)
        feedTail(b, r)
    }

    private fun feedRyoGoldenMax(b: Obj, r: Obj) {
        bocchi()?.setAnim("1", true)
        r.setFrame(5)
        b.ivars["N"] = 0.0
        w.spawnParticles("粒子13", r, 3)
        w.snd.play("5", false, -15f, w.rand(-50f, 50f))
        feedReset(b)
        val s7 = bocchi()
        if (s7 != null) w.tweens.to(s7, 1162f, 1f, 15)
        w.sched.seq(
            Scheduler.Wait(1f),
            Scheduler.WaitTweens(s7 ?: b, w.tweens),
            Scheduler.Act {
                s7?.setFrame(1)
                w.snd.play("7", false, -15f, w.rand(-50f, 50f))
                if (s7 != null) {
                    for (pn in listOf("粒子7", "粒子12", "粒子11", "粒子9", "粒子10", "粒子8"))
                        w.spawnParticles(pn, s7, 5, 234f)
                }
            },
            Scheduler.Wait(1.5f),
            Scheduler.Act { if (s7 != null) w.tweens.to(s7, 1515f, 2f, 14) },
            Scheduler.Wait(1f),
            Scheduler.WaitTweens(s7 ?: b, w.tweens),
            Scheduler.Act { girlIdle(b, r) }
        )
    }

    private fun feedKitaGoldenLow(b: Obj, k: Obj) {
        k.setFrame(2)
        b.ivars["N"] = 0.0
        w.spawnParticles("粒子3", k, 3)
        w.spawnParticles("粒子4", k, 3)
        w.snd.play("9", false, -10f, w.rand(-50f, 50f))
        feedReset(b)
        feedTail(b, k)
    }

    private fun feedKitaGoldenMid(b: Obj, k: Obj) {
        k.setFrame(3)
        b.ivars["N"] = 0.0
        w.spawnParticles("粒子5", k, 3)
        w.snd.play("8", false, -10f, w.rand(-50f, 50f))
        feedReset(b)
        feedTail(b, k)
    }

    private fun feedKitaGoldenMax(b: Obj, k: Obj) {
        val s7 = bocchi()
        s7?.setAnim("1", true)
        s7?.setFrame(0)
        k.setFrame(3)
        b.ivars["N"] = 0.0
        w.spawnParticles("粒子5", k, 3)
        w.snd.play("8", false, -10f, w.rand(-50f, 50f))
        feedReset(b)
        if (s7 != null) w.tweens.to(s7, 1162f, 1f, 15)
        w.sched.seq(
            Scheduler.Wait(1f),
            Scheduler.WaitTweens(s7 ?: b, w.tweens),
            Scheduler.Act {
                s7?.setFrame(1)
                w.snd.play("7", false, -15f, w.rand(-50f, 50f))
                if (s7 != null) {
                    for (pn in listOf("粒子7", "粒子12", "粒子11", "粒子9", "粒子10", "粒子8"))
                        w.spawnParticles(pn, s7, 5, 234f)
                }
            },
            Scheduler.Wait(1.5f),
            Scheduler.Act { if (s7 != null) w.tweens.to(s7, 1515f, 2f, 14) },
            Scheduler.Wait(1f),
            Scheduler.WaitTweens(s7 ?: b, w.tweens),
            Scheduler.Act { girlIdle(b, k) }
        )
    }

    // ================= 抽 =================
    private fun chou() {
        ran = (w.rnd.nextFloat() * 100f)
        bag()?.ivars?.set("B", if (ran <= 30.0) 1.0 else 0.0)
    }

    // ================= Sprite9 timer S =================
    private fun onTimerS() {
        groupS = true
        randV = w.rnd.nextFloat() * 100f
        sprite9TimerS = w.rand(180f, 1200f)
        startFlyer(when {
            randV <= 25 -> 1
            randV <= 50 -> 2
            randV <= 75 -> 3
            else -> 4
        })
    }

    private fun startFlyer(direction: Int) {
        when (direction) {
            1 -> xingge1()
            2 -> xingge2()
            3 -> xingge3()
            4 -> xingge4()
        }
    }

    fun sprite9TimerRestart(sec: Float) { sprite9TimerS = sec }

    private fun xingge1() {
        val s9 = flyer() ?: return
        s9.mirrored = false
        s9.x = -544f; s9.y = 333f
        s9.angle = Math.toRadians(46.0).toFloat()
        w.tweens.to(s9, -186f, 2f, 16)
        w.sched.seq(
            Scheduler.WaitSignal("Z"),
            Scheduler.Wait(0.5f),
            Scheduler.Act { w.tweens.to(s9, -544f, 2f, 16) },
            Scheduler.Act { groupS = false }
        )
    }

    private fun xingge2() {
        val s9 = flyer() ?: return
        s9.mirrored = true
        s9.x = 1557f; s9.y = 1544f
        s9.angle = Math.toRadians(341.0).toFloat()
        w.tweens.toXY(s9, 1228f, 1081f, 2f, 16)
        w.sched.seq(
            Scheduler.WaitSignal("Z"),
            Scheduler.Wait(0.5f),
            Scheduler.Act { w.tweens.toXY(s9, 1557f, 1544f, 2f, 16) },
            Scheduler.Act { groupS = false }
        )
    }

    private fun xingge3() {
        val s9 = flyer() ?: return
        s9.mirrored = false
        s9.x = -154f; s9.y = 1578f
        s9.angle = Math.toRadians(10.0).toFloat()
        w.tweens.toXY(s9, -30f, 1050f, 2f, 16)
        w.sched.seq(
            Scheduler.WaitSignal("Z"),
            Scheduler.Wait(0.5f),
            Scheduler.Act { w.tweens.toXY(s9, -154f, 1578f, 2f, 16) },
            Scheduler.Act { groupS = false }
        )
    }

    private fun xingge4() {
        val s9 = flyer() ?: return
        s9.mirrored = true
        s9.x = 1520f; s9.y = -544f
        s9.angle = Math.toRadians(217.0).toFloat()
        w.tweens.toXY(s9, 1201f, -81f, 2f, 16)
        w.sched.seq(
            Scheduler.WaitSignal("Z"),
            Scheduler.Wait(0.5f),
            Scheduler.Act { w.tweens.toXY(s9, 1520f, -544f, 2f, 16) },
            Scheduler.Act { groupS = false }
        )
    }

    // ================= milestones =================
    private fun milestone(m: Int) {
        val s7 = bocchi() ?: return
        s7.setAnim("2")
        w.vib.reward()
        w.snd.play("11", false, -5f, w.rand(-50f, 50f))
        w.tweens.to(s7, 1000f, 1f, 15)
        w.sched.seq(
            Scheduler.WaitTweens(s7, w.tweens),
            Scheduler.Wait(1f),
            Scheduler.Act { w.tweens.to(s7, 1515f, 2f, 14) }
        )
        if (m >= 50) sprite9TimerRestart(10f)
    }

    // ================= geometry =================
    fun overlap(a: Obj, b: Obj): Boolean {
        return Sat.objectsOverlap(a, b)
    }

    fun objsDrawOrder(): List<Obj> =
        w.drawOrder()
}
