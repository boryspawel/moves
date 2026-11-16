package com.motionecosystem.moves.mobile.app

import java.time.ZoneId

/** Only the specialist's explicit IANA calendar zone is authoritative for local times.
 * Never silently replace a missing/invalid zone with UTC or the device's time zone.
 */
internal fun specialistCalendarZone(timeZoneId: String?): ZoneId? =
    timeZoneId?.takeIf { it.isNotBlank() }?.let { value ->
        runCatching { ZoneId.of(value) }.getOrNull()
    }
