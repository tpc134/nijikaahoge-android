package com.ruich97.nijikaahoge.game

/** SAT polygon overlap for convex polys; concave polys are triangulated first. */
object Sat {
    fun objectsOverlap(a: Obj, b: Obj): Boolean {
        val ab = a.boundingBox(); val bb = b.boundingBox()
        if (ab[2] < bb[0] || bb[2] < ab[0] || ab[3] < bb[1] || bb[3] < ab[1]) return false
        return trianglesOverlap(a.trianglesWorld(), b.trianglesWorld())
    }

    fun polysOverlap(a: FloatArray, b: FloatArray): Boolean {
        // quick AABB reject
        val ab = bbox(a); val bb = bbox(b)
        if (ab[2] < bb[0] || bb[2] < ab[0] || ab[3] < bb[1] || bb[3] < ab[1]) return false
        val trisA = decompose(a)
        val trisB = decompose(b)
        return trianglesOverlap(trisA, trisB)
    }

    private fun trianglesOverlap(trisA: FloatArray, trisB: FloatArray): Boolean {
        var i = 0
        while (i < trisA.size) {
            var j = 0
            while (j < trisB.size) {
                if (convexOverlap(trisA, i, trisB, j)) return true
                j += 6
            }
            i += 6
        }
        return false
    }

    private fun bbox(p: FloatArray): FloatArray {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        var i = 0
        while (i < p.size) {
            l = minOf(l, p[i]); r = maxOf(r, p[i])
            t = minOf(t, p[i + 1]); b = maxOf(b, p[i + 1])
            i += 2
        }
        return floatArrayOf(l, t, r, b)
    }

    /** same ear-clip decomposition as physics fixtures so SAT and Box2D agree */
    private fun decompose(p: FloatArray): FloatArray {
        val n = p.size / 2
        if (n <= 3) return p
        return Triangulate.run(p)
    }

    /** SAT on two triangles at offsets i,j (6 floats each) */
    private fun convexOverlap(a: FloatArray, ai: Int, b: FloatArray, bi: Int): Boolean {
        if (!projectOverlap(a, ai, b, bi)) return false
        if (!projectOverlap(b, bi, a, ai)) return false
        return true
    }

    /** project both tris on each edge normal of poly a; false if a separating axis exists */
    private fun projectOverlap(a: FloatArray, ai: Int, b: FloatArray, bi: Int): Boolean {
        for (e in 0 until 3) {
            val i0 = ai + e * 2
            val i1 = ai + ((e + 1) % 3) * 2
            val nx = -(a[i1 + 1] - a[i0 + 1])
            val ny = a[i1] - a[i0]
            var minA = Float.MAX_VALUE; var maxA = -Float.MAX_VALUE
            var minB = Float.MAX_VALUE; var maxB = -Float.MAX_VALUE
            for (k in 0 until 3) {
                var d = a[ai + k * 2] * nx + a[ai + k * 2 + 1] * ny
                minA = minOf(minA, d); maxA = maxOf(maxA, d)
                d = b[bi + k * 2] * nx + b[bi + k * 2 + 1] * ny
                minB = minOf(minB, d); maxB = maxOf(maxB, d)
            }
            if (maxA < minB || maxB < minA) return false
        }
        return true
    }
}
