package com.metrolist.music.deeplist

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeeplistCloudMergeTest {
    private fun json(s: String)=JSONObject(s)
    @Test fun `remote changes and local deletions both survive`() {
        val result=mergeCloud(json("""{"a":1,"b":2}"""), json("""{"b":2,"c":3}"""),json("""{"a":1,"b":4,"d":5}""")) as JSONObject
        assertFalse(result.has("a"));assertEquals(4,result.getInt("b"));assertEquals(3,result.getInt("c"));assertEquals(5,result.getInt("d"))
    }
    @Test fun `fresh install restores remote settings while keeping new local items`() {
        val result=mergeCloud(null,json("""{"settings":{"style":"APPLE"},"records":{"local":1}}"""),json("""{"settings":{"style":"SHIP"},"records":{"remote":2}}"""),true) as JSONObject
        assertEquals("SHIP",result.getJSONObject("settings").getString("style"));assertEquals(2,result.getJSONObject("records").length())
    }
    @Test fun `live edits are rebased and conflicting deletion preserves edited item`() {
        assertEquals(3,mergeCloud(1,3,2))
        assertEquals(2,mergeCloud(1,null,2))
        assertNull(mergeCloud(1,1,null))
        assertTrue(cloudEqual(json("""{"a":1,"b":2}"""),json("""{"b":2,"a":1}""")))
    }
}
