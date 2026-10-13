package com.ruich97.nijikaahoge.game

import android.content.Context

/** Glue: owns World + Logic, exposes touch entry points, advances simulation. */
class Game(context: Context) {
    val data: GameData = DataLoader.load(context.assets)
    val phys = Phys()
    val snd = Snd(context.assets)
    val vib = Vib(context)
    val parts = Parts()
    val world = World(data, phys, snd, vib, parts)
    val logic = Logic(world)

    init {
        for (d in data.insts) world.spawn(d)
        // Only a few clips are decoded here; the rest are decoded on first use.
        // Loading the whole bank up front keeps a decoder thread busy for ~20 s
        // and drags the frame rate down with it. "1" and "2" are the feeding
        // cues (C3 choose(1,2,2,2,2)) — not the smallest files, but reachable
        // within seconds of launch, so they ride along with the four short ones.
        snd.preloadSmallest(data.media, 4, setOf("1", "2"))
        logic.init()
    }

    fun update(dt: Float) {
        logic.prePhysics(dt)
        world.tick(dt)
        logic.tick(dt)
        // parent->child transform follow (多力多滋2 pinned to 多力多滋)
        for (o in world.objs) {
            val c = o.child ?: continue
            c.x = o.x; c.y = o.y; c.angle = o.angle
            c.scaleX = o.scaleX; c.scaleY = o.scaleY
        }
    }

    fun touchDown(x: Float, y: Float, headTouchRadius: Float = 0f) = logic.touchDown(x, y, headTouchRadius)
    fun touchMove(x: Float, y: Float) = logic.touchMove(x, y)
    fun touchUp(x: Float, y: Float) = logic.touchUp(x, y)

    fun release() { snd.release(); vib.release() }
}
