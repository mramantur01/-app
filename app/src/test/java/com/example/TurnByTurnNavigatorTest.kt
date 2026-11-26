package com.example

import com.example.data.model.GeoPoint
import com.example.util.TurnByTurnNavigator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real OSRM answer for a route in Kant: turns, streets, Russian phrases, progress along the route. */
class TurnByTurnNavigatorTest {
    private val body = javaClass.classLoader!!.getResource("osrm_kant.json")!!.readText()
    private val target = GeoPoint(42.8950, 74.8600)

    @Test
    fun parsesStepsAndLine() {
        val r = TurnByTurnNavigator.parse(body, target)
        assertNotNull(r)
        r!!
        assertTrue(r.line.size > 10)
        assertEquals("depart", r.steps.first().type)
        assertEquals("arrive", r.steps.last().type)
        assertTrue(r.steps.any { it.street.isNotBlank() })
    }

    @Test
    fun russianPhrases() {
        val r = TurnByTurnNavigator.parse(body, target)!!
        val leftTurn = r.steps.first { it.type == "turn" && it.modifier == "left" }
        assertEquals("Поверните налево", TurnByTurnNavigator.phrase(leftTurn))
        val endRight = r.steps.first { it.type == "end of road" && it.modifier == "right" }
        assertEquals("Поверните направо", TurnByTurnNavigator.phrase(endRight))
    }

    @Test
    fun atStartTheNextManoeuvreIsTheFirstTurn() {
        val r = TurnByTurnNavigator.parse(body, target)!!
        val start = r.line.first()
        val idx = TurnByTurnNavigator.nextStepIndex(r, start, 0)
        assertTrue(r.steps[idx].type != "depart")
        val g = TurnByTurnNavigator.guidance(r, start, idx)
        assertFalse(g.arrived)
        assertTrue(g.remainingKm > 1.0)
        assertTrue(TurnByTurnNavigator.spoken(g, near = false).startsWith("Через"))
    }

    @Test
    fun arrivesAtTarget() {
        val r = TurnByTurnNavigator.parse(body, target)!!
        val end = r.line.last()
        val idx = TurnByTurnNavigator.nextStepIndex(r, end, 0)
        val g = TurnByTurnNavigator.guidance(r, end, idx)
        assertTrue("not arrived: " + g + " end=" + end + " target=" + target, g.arrived)
        assertEquals("Вы на месте", g.instruction)
    }

    @Test
    fun distanceLabels() {
        assertEquals("250 м", TurnByTurnNavigator.distanceLabel(260))
        assertEquals("40 м", TurnByTurnNavigator.distanceLabel(43))
        assertEquals("1,2 км", TurnByTurnNavigator.distanceLabel(1200))
    }
}
