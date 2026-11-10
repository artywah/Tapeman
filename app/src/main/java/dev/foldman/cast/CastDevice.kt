package dev.foldman.cast

data class CastDevice(
    val id: String,
    val name: String,
    val model: String? = null,
    val address: String,
    val port: Int = 8009,
    val isCurrent: Boolean = false,
)
