package com.bontelempi.radialmenu.model

import kotlinx.serialization.Serializable

/**
 * @param headTexture  Optional base64 skin value for a custom player head icon.
 *                     When set, overrides [iconItem] for rendering purposes.
 */
@Serializable
data class MenuItem(
    val id: String,
    val label: String,
    val iconItem: String = "minecraft:barrier",
    val command: String = "",
    val children: MutableList<MenuItem> = mutableListOf(),
    val headTexture: String? = null
) {
    val isFolder: Boolean get() = children.isNotEmpty()
    val hasCustomHead: Boolean get() = !headTexture.isNullOrBlank()
}
