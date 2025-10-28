/*
 * GrimAC AntiVelocity Bypass + AntiRocket/Launchpad
 *
 * Nombre: Grim2371-2
 */

package net.ccbluex.liquidbounce.features.module.modules.combat.velocity.mode

import net.ccbluex.liquidbounce.event.waitTicks
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.events.QueuePacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.sequenceHandler
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.utils.raycast
import net.ccbluex.liquidbounce.utils.client.PacketQueueManager
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerAbilitiesS2CPacket
import net.minecraft.network.packet.s2c.play.EntityS2CPacket
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult

internal object VelocityGrim2371_2 : VelocityMode("Grim2371-2") {

    private var cancelNextVelocity = false
    private var delay = false
    private var needClick = false

    private var waitForPing = false
    private var waitForUpdate = false

    private var hitResult: BlockHitResult? = null
    private var shouldSkip = false

    private var freezeTicks = 0
    private const val MAX_FREEZE_TICKS = 12 // failsafe: menos ticks = menos flags

    override fun enable() {
        cancelNextVelocity = false
        delay = false
        needClick = false
        waitForUpdate = false
        hitResult = null
        shouldSkip = false
        freezeTicks = 0
    }

    override fun disable() {
        PacketQueueManager.flush(TransferOrigin.INCOMING)
    }

    private val Packet<*>.isSelfDamage
        get() = this is EntityDamageS2CPacket && this.entityId == player.id

    private val Packet<*>.isSelfVelocity
        get() = (this is EntityVelocityUpdateS2CPacket && this.entityId == player.id)
            || this is ExplosionS2CPacket

    @Suppress("unused")
    private val packetHandler = sequenceHandler<PacketEvent> { event ->
        val packet = event.packet

        when (packet) {
            is PlayerInteractEntityC2SPacket, is PlayerInteractBlockC2SPacket ->
                shouldSkip = true

            is PlayerMoveC2SPacket if packet.changesPosition() && waitForUpdate ->
                event.cancelEvent()

            is CommonPongC2SPacket if waitForPing -> {
                waitTicks(1)
                waitForUpdate = false
                waitForPing = false
            }

            // 🚀 Cancelar abilities (ej. rockets, trampolines, jump pads)
            is PlayerAbilitiesS2CPacket -> {
                if (packet.allowFlying() || packet.flySpeed > 0.05f) {
                    event.cancelEvent()
                }
            }

            // 🚀 Cancelar impulses forzados vía EntityS2CPacket
            is EntityS2CPacket -> {
                val entity = packet.getEntity(world)
                if (entity != null && entity.id == player.id) {
                    if (packet.getDeltaY() > 400) { // OJO: deltaY es short (1/4096 block)
                        event.cancelEvent()
                    }
                }
            }
        }

        if (event.isCancelled) return@sequenceHandler

        if (packet is BlockUpdateS2CPacket && waitForUpdate && packet.pos == player.blockPos) {
            waitTicks(1)
            waitForPing = true
            needClick = false
            return@sequenceHandler
        }

        if (waitForUpdate || delay) return@sequenceHandler

        if (packet.isSelfDamage) {
            cancelNextVelocity = true
        } else if (cancelNextVelocity && packet.isSelfVelocity) {
            event.cancelEvent()
            delay = true
            cancelNextVelocity = false
            needClick = true
        }
    }

    @Suppress("unused")
    private val queuePacketHandler = handler<QueuePacketEvent> { event ->
        if (waitForUpdate || !delay || event.origin != TransferOrigin.INCOMING) return@handler
        event.action = PacketQueueManager.Action.QUEUE
    }

    @Suppress("unused")
    private val playerTickHandler = handler<PlayerTickEvent> { event ->
        // 🚧 failsafe: flush si estás en bloques problemáticos
        if (isOnSpecialBlock()) {
            PacketQueueManager.flush(TransferOrigin.INCOMING)
            delay = false
            waitForUpdate = false
            return@handler
        }

        if (needClick && !shouldSkip && !player.isUsingItem) {
            hitResult = raycast(
                rotation = RotationManager.serverRotation.copy(pitch = 90F)
            ).takeIf {
                it.blockPos.offset(it.side) == player.blockPos
            }
        }

        if (hitResult != null) {
            delay = false
            PacketQueueManager.flush(TransferOrigin.INCOMING)

            if (interaction.interactBlock(player, Hand.MAIN_HAND, hitResult).isAccepted) {
                player.swingHand(Hand.MAIN_HAND)
            }

            if (RotationManager.serverRotation.pitch != 90f) {
                network.sendPacket(
                    PlayerMoveC2SPacket.LookAndOnGround(
                        player.yaw,
                        90f,
                        player.isOnGround,
                        player.horizontalCollision
                    )
                )
            } else {
                network.sendPacket(
                    PlayerMoveC2SPacket.OnGroundOnly(
                        player.isOnGround,
                        player.horizontalCollision
                    )
                )
            }

            freezeTicks = 0
            waitForUpdate = true
            hitResult = null
            needClick = false
        }

        if (waitForUpdate) {
            event.cancelEvent()
            freezeTicks++
            if (freezeTicks > MAX_FREEZE_TICKS) {
                // failsafe: liberar todo para evitar flags AntiKB
                PacketQueueManager.flush(TransferOrigin.INCOMING)
                waitForUpdate = false
                waitForPing = false
                needClick = false
            }
        }

        shouldSkip = false
    }

    private fun isOnSpecialBlock(): Boolean {
        val block = world.getBlockState(player.blockPos).block
        return block.translationKey.contains("carpet")
            || block.translationKey.contains("slab")
            || block.translationKey.contains("stair")
            || block.translationKey.contains("wool")
    }
}
