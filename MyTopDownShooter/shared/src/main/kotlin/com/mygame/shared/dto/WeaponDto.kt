package com.mygame.shared.dto

data class WeaponDto(
    val id: String,
    val damage: Int,
    val fireRate: Float,
    val spread: Float,
    val projectileSize: Float,
    val ammo: Int
)
