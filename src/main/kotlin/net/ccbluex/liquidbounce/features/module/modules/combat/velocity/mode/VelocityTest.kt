/*
 * Safe Velocity Mode with Ping Delay Simulation
 * Cancels knockback + simulates delayed ping/pong without desync
 */
package net.ccbluex.liquidbounce.features.module.modules.combat.velocity.mode

import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.ModuleFly
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket

internal object VelocityTest : VelocityMode("SafeTest") {
    private data class DelayedPing(val id: Int, var delay: Int)
    private val pingQueue = mutableListOf<DelayedPing>()

    private var enabled = false

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        val packet = event.packet

        if (ModuleFly.enabled) return@handler

        // Cancel all velocity packets (no knockback)
        if (packet is EntityVelocityUpdateS2CPacket && packet.entityId == player.id) {
            event.cancelEvent()
            enabled = true
            return@handler
        }

        when (packet) {
            is CommonPingS2CPacket -> {
                // Cancel server ping and delay our pong manually
                event.cancelEvent()
                val id = try {
                    val field = packet::class.java.getDeclaredField("id")
                    field.isAccessible = true
                    field.getInt(packet)
                } catch (_: Exception) { -1 }

                if (id != -1) {
                    pingQueue.add(DelayedPing(id, 20)) // 20 ticks delay
                }
            }

            is KeepAliveS2CPacket -> {
                // Cancel keepalive packet, respond manually
                event.cancelEvent()
                val id = try {
                    val field = packet::class.java.getDeclaredField("id")
                    field.isAccessible = true
                    field.getLong(packet).toInt()
                } catch (_: Exception) { -1 }

                if (id != -1) {
                    pingQueue.add(DelayedPing(id, 20))
                }
            }
        }
    }

    // Handle the delay tick queue
    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> {
        if (pingQueue.isEmpty()) return@handler

        val iterator = pingQueue.iterator()
        while (iterator.hasNext()) {
            val ping = iterator.next()
            ping.delay--

            if (ping.delay <= 0) {
                try {
                    // Send exact pong with correct ID (no desync)
                    network.sendPacket(CommonPongC2SPacket(ping.id))
                } catch (_: Exception) {
                    // failsafe, ignore broken send
                }
                iterator.remove()
            }
        }
    }
}
