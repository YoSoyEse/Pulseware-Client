package net.ccbluex.liquidbounce.features.module.modules.combat.velocity.mode

import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.ModuleFly
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket
//import net.minecraft.network.packet.c2s.play.ConfirmTransactionC2SPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket

/**
 * SafeLagVelocity — cancela velocity y retrasa responses para simular lag estable.
 */
internal object VelocityTest2 : VelocityMode("SafeLagVelocity") {

    private data class StoredPacket(val type: PacketType, val id: Long, var ticksLeft: Int)
    private enum class PacketType { KEEP_ALIVE, COMMON_PONG, TRANSACTION }

    private val delayedPackets = mutableListOf<StoredPacket>()
    private const val DELAY_TICKS = 400 // ~20 segundos

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        val packet = event.packet

        // Cancelar knockback
        if (packet is EntityVelocityUpdateS2CPacket && packet.entityId == player.id) {
            event.cancelEvent()
            return@handler
        }

        if (ModuleFly.enabled) return@handler

        when (packet) {
            is KeepAliveC2SPacket -> {
                // intercept KeepAlive out
                val id = getPacketId(packet)
                if (id != null) {
                    event.cancelEvent()
                    delayedPackets.add(StoredPacket(PacketType.KEEP_ALIVE, id, DELAY_TICKS))
                }
            }

            is CommonPongC2SPacket -> {
                val id = getPacketId(packet)
                if (id != null) {
                    event.cancelEvent()
                    delayedPackets.add(StoredPacket(PacketType.COMMON_PONG, id, DELAY_TICKS))
                }
            }

            /*is ConfirmTransactionC2SPacket -> {
                val id = ((packet.windowId.toLong() shl 32) or (packet.uid.toLong() and 0xFFFFFFFF))
                event.cancelEvent()
                delayedPackets.add(StoredPacket(PacketType.TRANSACTION, id, DELAY_TICKS))
            }*/
        }
    }

    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> {
        if (delayedPackets.isEmpty()) return@handler

        val iterator = delayedPackets.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            entry.ticksLeft--

            if (entry.ticksLeft <= 0) {
                try {
                    when (entry.type) {
                        PacketType.KEEP_ALIVE ->
                            network.sendPacket(KeepAliveC2SPacket(entry.id))

                        PacketType.COMMON_PONG ->
                            network.sendPacket(CommonPongC2SPacket(entry.id.toInt()))

                        PacketType.TRANSACTION -> {
                            val windowId = (entry.id shr 32).toInt()
                            val uid = (entry.id and 0xFFFFFFFF).toShort()
                            // enviar duplicado simple
                            //network.sendPacket(ConfirmTransactionC2SPacket(windowId, uid, true))
                            //etwork.sendPacket(ConfirmTransactionC2SPacket(windowId, uid, false))
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    iterator.remove()
                }
            }
        }
    }

    /**
     * Helper para obtener ID usando reflexión, ya que Mojang cambia nombres a menudo.
     */
    private fun getPacketId(packet: Packet<*>): Long? = try {
        val field = packet::class.java.declaredFields.firstOrNull {
            it.name.equals("id", true) || it.name.equals("keepAliveId", true)
        } ?: return null
        field.isAccessible = true
        when (val value = field.get(packet)) {
            is Long -> value
            is Int -> value.toLong()
            else -> null
        }
    } catch (_: Exception) { null }
}
