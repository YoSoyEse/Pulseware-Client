/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2025 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.karhu

import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.event.events.QueuePacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.ModuleFly
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.ModuleFly.modes
import net.ccbluex.liquidbounce.utils.client.PacketQueueManager
import net.ccbluex.liquidbounce.utils.client.handlePacket
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket

/**
 * @anticheat Karhu
 * @anticheat Version 2.7.14 (265B)
 * @testedOn test.karhu.cc:25570
 */
internal object FlyKarhuDamage : Choice("KarhuDamage") {

    override val parent: ChoiceConfigurable<*>
        get() = modes

    private var damageTaken = false
    private var release = false
    private var ticks = 0

    override fun enable() {
        ticks = 0
        damageTaken = false
        release = false
    }

    @Suppress("unused")
    private val tickHandler = tickHandler {
        waitTicks(1)

        if (ticks > 0) {
            ticks--
        }
    }

    @Suppress("unused")
    private val packetHandler = handler<QueuePacketEvent> { event ->
        val packet = event.packet

        if (event.origin != TransferOrigin.INCOMING) {
            return@handler
        }

        event.action = when {
            packet is ExplosionS2CPacket || packet is EntityDamageS2CPacket  && packet.entityId == player.id && ticks <= 0 -> {
                damageTaken = true
                ticks = 120
                handlePacket(packet)
                PacketQueueManager.Action.QUEUE
            }

            packet is EntityVelocityUpdateS2CPacket && packet.entityId == player.id && damageTaken -> {
                damageTaken = false
                release = true
                handlePacket(packet)
                PacketQueueManager.Action.QUEUE
            }

            packet is CommonPingS2CPacket -> {
                if (ticks <= 0) {
                    if (release) {
                        mc.player!!.velocity.horizontal.y = 0.98
                        //ModuleFly.enabled = false
                    }
                    return@handler
                }

                ticks--
                PacketQueueManager.Action.QUEUE
            }

            // Prevent [PacketQueueManager] from flushing queued packets
            else -> PacketQueueManager.Action.PASS
        }

    }

}
