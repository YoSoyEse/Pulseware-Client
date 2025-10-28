package net.ccbluex.liquidbounce.features.module.modules.movement.fly.modes.grim

import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.QueuePacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.sequenceHandler
import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.ModuleFly.modes
import net.ccbluex.liquidbounce.utils.client.PacketQueueManager
import net.ccbluex.liquidbounce.utils.client.handlePacket
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket

/**
 * GrimFall – spoof onGround con encapsulamiento estilo bread
 * Evita Timer/Velocity checks innecesarios al momento de caer.
 */
internal object GrimFall : Choice("GrimFall") {

    override val parent: ChoiceConfigurable<*>
        get() = modes

    private var active = false
    private var delay = false
    private var waitForPing = false
    private var waitForUpdate = false
    private var freezeTicks = 0

    private const val MAX_FREEZE_TICKS = 20

    override fun enable() {
        active = false
        delay = false
        waitForPing = false
        waitForUpdate = false
        freezeTicks = 0
    }

    override fun disable() {
        PacketQueueManager.flush(TransferOrigin.INCOMING)
    }

    private val Packet<*>.isSelfDamage
        get() = this is EntityDamageS2CPacket && this.entityId == player.id

    private val Packet<*>.isSelfVelocity
        get() = this is EntityVelocityUpdateS2CPacket && this.entityId == player.id || this is ExplosionS2CPacket

    @Suppress("unused")
    private val packetHandler = sequenceHandler<PacketEvent> { event ->
        val packet = event.packet

        when (packet) {
            // Cancela movimientos hasta que termine encapsulamiento
            is PlayerMoveC2SPacket if waitForUpdate -> event.cancelEvent()

            // Si llega ping → desbloquear
            is CommonPongC2SPacket if waitForPing -> {
                waitTicks(1)
                waitForUpdate = false
                waitForPing = false
            }
        }

        if (event.isCancelled) return@sequenceHandler

        // Trigger de daño / velocity / lagback
        if (event.origin == TransferOrigin.INCOMING) {
            when {
                packet.isSelfDamage -> {
                    active = true
                }
                packet.isSelfVelocity -> {
                    if (active) {
                        delay = true
                        active = false
                        event.cancelEvent() // cancelar el knockback real
                    }
                }
                packet is PlayerPositionLookS2CPacket -> {
                    active = true
                }
            }
        }
    }

    @Suppress("unused")
    private val queuePacketHandler = handler<QueuePacketEvent> { event ->
        if (waitForUpdate || !delay || event.origin != TransferOrigin.INCOMING) {
            return@handler
        }
        event.action = PacketQueueManager.Action.QUEUE
    }

    @Suppress("unused")
    private val playerTickHandler = handler<PlayerTickEvent> { event ->
        if (delay && !waitForUpdate) {
            delay = false

            // Encapsulamiento estilo bread → flush en bloque
            PacketQueueManager.flush(TransferOrigin.INCOMING)

            // Spoof movimiento en ground
            network.sendPacket(
                PlayerMoveC2SPacket.OnGroundOnly(true, player.horizontalCollision)
            )

            // Esperar al update del servidor
            waitForUpdate = true
            waitForPing = true
            freezeTicks = 0
        }

        if (waitForUpdate) {
            event.cancelEvent()
            freezeTicks++
            if (freezeTicks > MAX_FREEZE_TICKS) {
                waitForUpdate = false
                waitForPing = false
            }
        }
    }
}
