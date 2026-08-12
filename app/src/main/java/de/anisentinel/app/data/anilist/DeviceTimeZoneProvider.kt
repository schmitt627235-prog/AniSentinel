package de.anisentinel.app.data.anilist

import java.time.ZoneId

fun interface DeviceTimeZoneProvider {
    fun currentZoneId(): ZoneId
}

object SystemDeviceTimeZoneProvider : DeviceTimeZoneProvider {
    override fun currentZoneId(): ZoneId = ZoneId.systemDefault()
}
