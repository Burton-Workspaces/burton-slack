package com.burton.slack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlackSnapshotTest {
    private val ada = SlackUser("U1", "ada", "Ada Lovelace", "Ada", "", false, false)
    private val bob = SlackUser("U2", "bob", "Bob Jones", "Bobby", "", false, false)
    private val bot = SlackUser("U3", "helpbot", "Help Bot", "Help Bot", "", true, false)
    private val gone = SlackUser("U4", "oldada", "Ada Old", "Old Ada", "", false, true)
    private val snapshot = SlackSnapshot(
        users = listOf(ada, bob, bot, gone).associateBy { it.id },
    )

    @Test
    fun matchingUsersFindsUsernameAndRealName() {
        assertEquals(listOf(ada), snapshot.matchingUsers("ada"))
        assertEquals(listOf(ada), snapshot.matchingUsers("Lovelace"))
        assertEquals(listOf(bob), snapshot.matchingUsers("Bobby"))
        assertEquals(listOf(bob), snapshot.matchingUsers("Jones"))
        assertEquals(listOf(bob), snapshot.matchingUsers("bob"))
    }

    @Test
    fun matchingUsersSkipsBotsAndDeleted() {
        assertTrue(snapshot.matchingUsers("Help").isEmpty())
        assertTrue(snapshot.matchingUsers("Old").isEmpty())
        assertEquals(listOf(ada), snapshot.matchingUsers("Ada"))
    }

    @Test
    fun matchingUsersRanksExactAndPrefixFirst() {
        val ann = SlackUser("U5", "ann", "Ann", "Ann", "", false, false)
        val danny = SlackUser("U6", "danny", "Danny", "Danny", "", false, false)
        val ranked = SlackSnapshot(users = listOf(danny, ann).associateBy { it.id })
        assertEquals(listOf(ann, danny), ranked.matchingUsers("an"))
    }

    @Test
    fun matchingUsersIgnoresBlankQuery() {
        assertTrue(snapshot.matchingUsers(" ").isEmpty())
    }
}
