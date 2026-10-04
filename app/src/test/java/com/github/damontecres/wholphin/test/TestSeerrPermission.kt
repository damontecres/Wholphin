package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.data.model.SeerrPermission
import com.github.damontecres.wholphin.data.model.hasPermission
import com.github.damontecres.wholphin.services.SeerrUserConfig
import org.junit.Assert
import org.junit.Test

class TestSeerrPermission {
    @Test
    fun `Is Admin`() {
        val user =
            SeerrUserConfig(
                id = 0,
                permissions = 2,
            )
        Assert.assertTrue(user.hasPermission(SeerrPermission.ADMIN))
        Assert.assertTrue(user.hasPermission(SeerrPermission.REQUEST_4K_MOVIE))
        Assert.assertTrue(user.hasPermission(SeerrPermission.REQUEST_4K_TV))
        Assert.assertTrue(user.hasPermission(SeerrPermission.REQUEST_4K))
    }

    @Test
    fun `Request any 4k has request movie 4k`() {
        val user =
            SeerrUserConfig(
                id = 0,
                permissions = 1024,
            )
        Assert.assertFalse(user.hasPermission(SeerrPermission.ADMIN))
        Assert.assertTrue(user.hasPermission(SeerrPermission.REQUEST_4K_MOVIE))
        Assert.assertTrue(user.hasPermission(SeerrPermission.REQUEST_4K_TV))
        Assert.assertTrue(user.hasPermission(SeerrPermission.REQUEST_4K))
    }

    @Test
    fun `Request movie 4k and does not have request tv 4k`() {
        val user =
            SeerrUserConfig(
                id = 0,
                permissions = 2048,
            )
        Assert.assertFalse(user.hasPermission(SeerrPermission.ADMIN))
        Assert.assertTrue(user.hasPermission(SeerrPermission.REQUEST_4K_MOVIE))
        Assert.assertFalse(user.hasPermission(SeerrPermission.REQUEST_4K_TV))
        Assert.assertFalse(user.hasPermission(SeerrPermission.REQUEST_4K))
    }

    @Test
    fun `Has explicit request 4k and children`() {
        val user =
            SeerrUserConfig(
                id = 0,
                permissions = 7168,
            )
        Assert.assertFalse(user.hasPermission(SeerrPermission.ADMIN))
        Assert.assertTrue(user.hasPermission(SeerrPermission.REQUEST_4K_MOVIE))
        Assert.assertTrue(user.hasPermission(SeerrPermission.REQUEST_4K_TV))
        Assert.assertTrue(user.hasPermission(SeerrPermission.REQUEST_4K))
    }

    @Test
    fun `Has explicit request 4k children`() {
        val user =
            SeerrUserConfig(
                id = 0,
                permissions = 6144,
            )
        Assert.assertFalse(user.hasPermission(SeerrPermission.ADMIN))
        Assert.assertTrue(user.hasPermission(SeerrPermission.REQUEST_4K_MOVIE))
        Assert.assertTrue(user.hasPermission(SeerrPermission.REQUEST_4K_TV))
        Assert.assertFalse(user.hasPermission(SeerrPermission.REQUEST_4K))
    }
}
