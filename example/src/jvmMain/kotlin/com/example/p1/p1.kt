package com.example.p1

import com.example.p2.P2
import com.minikorp.drill.DefaultDrillable
import com.minikorp.drill.Drill

@Drill
data class P1(
    val x: String = "",
    val x2: String? = null,
    val p2: P2 = P2(x = "abc"),
    val p2Nullable: P2? = null,
    val a: List<String> = emptyList(),
    val b: List<P2> = emptyList(),
    val c: Map<String, P2> = emptyMap(),
    val d: List<String> = emptyList(),
    val e: Set<String> = emptySet(),
)

fun main() {
    val p1 = P1()
    p1.mutate {
        x = "hehe"
        a.add("wee")
        setP2Nullable(null)
        c["123"]?.x = "yeah"
        e.add("hello")
    }
}


class P1_Mutable(ref: P1) : DefaultDrillable<P1>(ref, null) {

    override fun freeze(): P1 {
        TODO("Not yet implemented")
    }
}