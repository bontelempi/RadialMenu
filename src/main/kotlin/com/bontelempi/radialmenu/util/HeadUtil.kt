package com.bontelempi.radialmenu.util

import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.ProfileComponent
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtOps
import java.util.Base64

object HeadUtil {

    private const val URL_PREFIX = "textures.minecraft.net"

    fun normalise(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        return when {
            trimmed.contains(URL_PREFIX) -> {
                val url = if (trimmed.startsWith("http")) trimmed else "https://$trimmed"
                val json = """{"textures":{"SKIN":{"url":"$url"}}}"""
                Base64.getEncoder().encodeToString(json.toByteArray())
            }
            else -> runCatching {
                val decoded = String(Base64.getDecoder().decode(trimmed))
                if (decoded.contains("textures")) trimmed else null
            }.getOrNull()
        }
    }

    /**
     * Builds a player_head ItemStack using the ProfileComponent CODEC.
     *
     * The expected NBT structure for ProfileComponent is:
     * {
     *   "name": "",
     *   "properties": [
     *     { "name": "textures", "value": "<base64>" }
     *   ]
     * }
     */
    fun buildStack(base64Value: String): ItemStack {
        val stack = ItemStack(Items.PLAYER_HEAD)
        runCatching {
            // Build the property entry
            val prop = NbtCompound()
            prop.putString("name", "textures")
            prop.putString("value", base64Value)

            val propList = NbtList()
            propList.add(prop)

            // Build the profile NBT
            val profileNbt = NbtCompound()
            profileNbt.putString("name", "")
            profileNbt.put("properties", propList)

            val result = ProfileComponent.CODEC.parse(NbtOps.INSTANCE, profileNbt)
            result.result().ifPresent { profile ->
                stack.set(DataComponentTypes.PROFILE, profile)
            }
        }.onFailure {
            println("[RadialMenu] Failed to build head stack: $it")
        }
        return stack
    }
}
