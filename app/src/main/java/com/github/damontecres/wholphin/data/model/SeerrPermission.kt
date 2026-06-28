package com.github.damontecres.wholphin.data.model

import com.github.damontecres.wholphin.services.SeerrUserConfig

/**
 * Permission levels for a user on a Seerr server
 */
enum class SeerrPermission(
    private val flag: Int,
    vararg val parents: SeerrPermission,
) {
    // Source: https://github.com/seerr-team/seerr/blob/develop/server/lib/permissions.ts
    NONE(0),
    ADMIN(2),
    MANAGE_SETTINGS(4),
    MANAGE_USERS(8),
    MANAGE_REQUESTS(16),
    REQUEST(32),
    VOTE(64),
    AUTO_APPROVE(128),
    AUTO_APPROVE_MOVIE(256, AUTO_APPROVE),
    AUTO_APPROVE_TV(512, AUTO_APPROVE),
    REQUEST_4K(1024),
    REQUEST_4K_MOVIE(2048, REQUEST_4K),
    REQUEST_4K_TV(4096, REQUEST_4K),
    REQUEST_ADVANCED(8192),
    REQUEST_VIEW(16384),
    AUTO_APPROVE_4K(32768),
    AUTO_APPROVE_4K_MOVIE(65536, AUTO_APPROVE_4K),
    AUTO_APPROVE_4K_TV(131072, AUTO_APPROVE_4K),
    REQUEST_MOVIE(262144, REQUEST),
    REQUEST_TV(524288, REQUEST),
    MANAGE_ISSUES(1048576),
    VIEW_ISSUES(2097152, MANAGE_ISSUES),
    CREATE_ISSUES(4194304, MANAGE_ISSUES),
    AUTO_REQUEST(8388608),
    AUTO_REQUEST_MOVIE(16777216, AUTO_REQUEST),
    AUTO_REQUEST_TV(33554432, AUTO_REQUEST),
    RECENT_VIEW(67108864),
    WATCHLIST_VIEW(134217728),
    MANAGE_BLACKLIST(268435456),
    VIEW_BLACKLIST(1073741824, MANAGE_BLACKLIST),
    ;

    internal fun hasPermission(permissions: Int) = flag.and(permissions) == flag
}

/**
 * Check whether the user has the given permissions (or is an admin)
 */
fun SeerrUserConfig?.hasPermission(permission: SeerrPermission): Boolean {
    if (this?.permissions == null) return false
    return permission.hasPermission(permissions) ||
        permission.parents.any { hasPermission(it) } ||
        SeerrPermission.ADMIN.hasPermission(permissions)
}
