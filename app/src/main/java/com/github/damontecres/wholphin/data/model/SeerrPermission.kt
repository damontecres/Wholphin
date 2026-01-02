package com.github.damontecres.wholphin.data.model

import com.github.damontecres.wholphin.services.SeerrUserConfig

enum class SeerrPermission(
    private val flag: Int,
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
    AUTO_APPROVE_MOVIE(256),
    AUTO_APPROVE_TV(512),
    REQUEST_4K(1024),
    REQUEST_4K_MOVIE(2048),
    REQUEST_4K_TV(4096),
    REQUEST_ADVANCED(8192),
    REQUEST_VIEW(16384),
    AUTO_APPROVE_4K(32768),
    AUTO_APPROVE_4K_MOVIE(65536),
    AUTO_APPROVE_4K_TV(131072),
    REQUEST_MOVIE(262144),
    REQUEST_TV(524288),
    MANAGE_ISSUES(1048576),
    VIEW_ISSUES(2097152),
    CREATE_ISSUES(4194304),
    AUTO_REQUEST(8388608),
    AUTO_REQUEST_MOVIE(16777216),
    AUTO_REQUEST_TV(33554432),
    RECENT_VIEW(67108864),
    WATCHLIST_VIEW(134217728),
    MANAGE_BLACKLIST(268435456),
    VIEW_BLACKLIST(1073741824),
    ;

    internal fun hasPermission(permissions: Int) = flag.and(permissions) == flag
}

/**
 * Check whether the user has the given permissions (or is an admin)
 */
fun SeerrUserConfig?.hasPermission(permission: SeerrPermission): Boolean {
    return permission.hasPermission(this?.permissions ?: return false) || SeerrPermission.ADMIN.hasPermission(permissions)
}
