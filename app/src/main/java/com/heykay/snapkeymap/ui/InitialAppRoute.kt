package com.heykay.snapkeymap.ui

internal fun initialAppRoute(introSeen: Boolean): String = if (introSeen) "home" else "intro"
