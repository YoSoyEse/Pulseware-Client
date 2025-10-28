package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.karhu

import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.QueuePacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.ModuleFly.modes
import net.ccbluex.liquidbounce.utils.client.PacketQueueManager
import net.ccbluex.liquidbounce.utils.client.handlePacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket

internal object FlyKarhu2 : Choice("Karhu2") {

    override val parent: ChoiceConfigurable<*>
        get() = modes

    private var active = false
    private var ticks = 0

    override fun enable() {
        active = false
        ticks = 0
    }

    private val tickHandler = tickHandler {
        if (ticks > 0) ticks--
    }

    @Suppress("unused")
    private val packetHandler = handler<QueuePacketEvent> { event ->
        val packet = event.packet

        // ---- Trigger cuando recibes daño, knockback o lagback ----
        if (event.origin == TransferOrigin.INCOMING) {
            event.action = when {
                packet is EntityDamageS2CPacket && packet.entityId == player.id -> {
                    active = true; ticks = 6
                    handlePacket(packet)
                    PacketQueueManager.Action.QUEUE
                }
                packet is EntityVelocityUpdateS2CPacket && packet.entityId == player.id -> {
                    active = true; ticks = 6
                    handlePacket(packet)
                    PacketQueueManager.Action.QUEUE
                }
                packet is PlayerPositionLookS2CPacket -> {
                    active = true; ticks = 6
                    handlePacket(packet)
                    PacketQueueManager.Action.QUEUE
                }
                else -> PacketQueueManager.Action.PASS
            }
        }

        // ---- Solo modificar outgoing movements ----
        if (active && ticks <= 0 && event.origin == TransferOrigin.OUTGOING) {
            if (packet is PlayerMoveC2SPacket) {
                // Solo spoof onGround
                packet.onGround = true
            }
        }
    }
}
