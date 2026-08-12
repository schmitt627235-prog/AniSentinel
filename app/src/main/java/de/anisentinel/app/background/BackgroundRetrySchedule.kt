package de.anisentinel.app.background

import kotlin.math.min

fun backgroundRetryDelaySeconds(runAttemptCount: Int): Long {
    val exponent = runAttemptCount.coerceIn(0, 6)
    return min(30L * 60L * (1L shl exponent), 24L * 60L * 60L)
}
