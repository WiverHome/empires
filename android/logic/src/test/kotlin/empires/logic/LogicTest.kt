package empires.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LogicTest {
    // Строки получены из JS: encodeMapCode({...SKIRMISH_MAPS[0], title: 'Тест'}) и SKIRMISH_MAPS[2] с заголовком T2
    private val jsValley =
        "EMP1:eyJ0Ijoi0KLQtdGB0YIiLCJtIjpbIk1mLi4sQywuLmZNIiwiZi4udiwsLHYuLmYiLCIuLmguLC4sLmguLiIsInYuLmZmLGZmLi52IiwiLiwuLi4uLmguLC4iLCJoZi52Li4uLmYuaCIsIn5-PX5-aH5-PX5-IiwiaC5mLi4uLnYuZmgiLCIuLC5oLi4uLi4sLiIsInYuLmZmLGZmLi52IiwiLi5oLiwuLC5oLi4iLCJmLi52LCwsdi4uZiIsIk1mLi4sQywuLmZNIl0sInMiOltbImJsdWUiLDUsMTIsNSwxMV0sWyJyZWQiLDUsMCw1LDFdXX0"
    private val jsFourTitle = "Карта \"А\"" + '\\' + " " + String(Character.toChars(0x1F600)) + "\n\t\u0001"
    private val jsFour =
        "EMP1:eyJ0Ijoi0JrQsNGA0YLQsCBcItCQXCJcXCDwn5iAXG5cdFx1MDAwMSIsIm0iOlsiZmYuLC4uLiwuZmYiLCJmQywsLnYuLCxDZiIsIi4sLmYuLC5mLiwuIiwiLixmaC4sLmhmLC4iLCIuLmguZixmLmguLiIsInYsLCwsQywsLCx2IiwiLi5oLmYsZi5oLi4iLCIuLGZoLiwuaGYsLiIsIi4sLmYuLC5mLiwuIiwiZkMsLC52LiwsQ2YiLCJmZi4sLi4uLC5mZiJdLCJzIjpbWyJibHVlIiwxLDksMiw5XSxbInllbGxvdyIsMSwxLDIsMV0sWyJyZWQiLDksMSw4LDFdLFsiZ3JlZW4iLDksOSw4LDldXX0"

    @Test
    fun encodeMatchesJs() {
        val valley = SKIRMISH_MAPS[0]
        val m0 = SkirmishMap(valley.id, "Тест", valley.desc, valley.map, valley.slots)
        assertEquals(jsValley, encodeMapCode(m0))
        val four = SKIRMISH_MAPS[2]
        assertEquals(jsFour, encodeMapCode(SkirmishMap(four.id, jsFourTitle, four.desc, four.map, four.slots)))
    }

    @Test
    fun decodeRoundTrips() {
        val r = decodeMapCode(jsValley, "my1")
        assertTrue(r is SkirmishMap)
        r as SkirmishMap
        assertEquals("Тест", r.title)
        assertEquals(SKIRMISH_MAPS[0].map, r.map)
        assertEquals(2, r.slots.size)
        assertEquals(jsValley, encodeMapCode(r))
        val r4 = decodeMapCode(" " + jsFour.substring(0, 20) + "\n" + jsFour.substring(20), "my2") as SkirmishMap
        assertEquals(jsFourTitle, r4.title)
        assertEquals(4, r4.slots.size)
        assertEquals("Это не код карты.", decodeMapCode("abc", "x"))
        assertEquals("Код повреждён.", decodeMapCode("EMP1:!!!", "x"))
        assertEquals("Код повреждён.", decodeMapCode("EMP1:e30", "x"))
    }

    @Test
    fun mapsAreValidSizes() {
        for (m in SKIRMISH_MAPS) assertTrue(m.map.size <= 15 && m.map.all { it.length <= 11 && it.length == m.map[0].length })
        for (m in MISSIONS) assertTrue(m.map.size <= 15 && m.map.all { it.length <= 11 })
    }

    @Test
    fun stateJsonRoundTrips() {
        val m = missionById("m2")!!
        val st = createState(m, CreateOpts(seed = 7))
        st.teams["blue"]!!.ai = true
        st.rinit = CreateOpts(seed = 7, commanderLevel = 0, difficulty = "normal")
        st.log = ArrayList()
        beginTurn(st)
        repeat(30) {
            val a = aiNextAction(st, m)
            a.s = st.seed
            st.log!!.add(a.copy())
            aiApply(st, a)
        }
        trackEvents(st, listOf(Event("raise")))
        val j1 = stateToJson(st)
        val back = stateFromJson(j1)
        val j2 = stateToJson(back)
        assertEquals(j1, j2)
        assertEquals(j1, stateToJson(st.deepCopy()))
        assertNotEquals(0, st.log!!.size)
        // копия живёт отдельно и играет так же
        val copy = st.deepCopy()
        for (i in 0 until 20) {
            val a1 = aiNextAction(st, m); aiApply(st, a1)
            val a2 = aiNextAction(copy, m); aiApply(copy, a2)
        }
        assertEquals(stateToJson(st), stateToJson(copy))
    }
}
