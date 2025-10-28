package net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.karhu

import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.events.QueuePacketEvent
import net.ccbluex.liquidbounce.event.events.TransferOrigin
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.utils.entity.airTicks
import net.ccbluex.liquidbounce.utils.entity.direction
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import kotlin.math.*

/**
 * Karhu Underground Speed (Grim-compatible variant)
 *
 * - Evita offsets > threshold (~0.05) para no ser flaggeado por Grim Simulation.
 * - Aplica "underground spoof" solo justo tras teleports/updates server-side.
 * - Ajusta motionY para simular coherencia física (evita vertical advantage).
 */
class SpeedKarhuground(override val parent: ChoiceConfigurable<*>) : Choice("Karhu Garuda Safe") {

    private val boostSpeed by float("BoostSpeed", 0.02F, 0.001F..0.1F)
    private val undergroundOffset by float("UndergroundOffset", 0.3F, 0.05F..0.5F) // más suave
    private val undergroundTicks by int("UndergroundTicks", 8, 1..30)
    private val airBoostStrength by float("AirBoost", 0.018F, 0.0F..0.05F)

    private var moving = false
    private var allowUndergroundFor = 0
    private var lastServerTeleportY: Double? = null
    private var lastSpoofTick = 0L

    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> {
        moving = player.input.movementForward != 0f || player.input.movementSideways != 0f
        if (allowUndergroundFor > 0) allowUndergroundFor--

        val yaw = Math.toRadians(player.direction.toDouble())

        // ----------------- Karhu collide boost -----------------
        if (moving && player.isOnGround) {
            var collisions = 0
            val box = player.boundingBox.expand(1.0)
            for (entity in world.entities) {
                if (canCauseSpeed(entity) && box.intersects(entity.boundingBox)) collisions++
            }

            if (collisions > 0) {
                val boost = boostSpeed * collisions
                player.addVelocity(-sin(yaw) * boost, 0.0, cos(yaw) * boost)
            }
            player.jump()
        }

        // ----------------- Air boost "legit" -----------------
        if (!player.isOnGround && player.isSprinting && player.airTicks in 2..10) {
            val boost = airBoostStrength.toDouble()
            player.addVelocity(-sin(yaw) * boost, 0.0, cos(yaw) * boost)
        }
    }

    @Suppress("unused")
    private val incomingHandler = handler<QueuePacketEvent> { event ->
        if (event.origin != TransferOrigin.INCOMING) return@handler
        val packet = event.packet

        when (packet) {
            is PlayerPositionLookS2CPacket -> {
                lastServerTeleportY = packet.change().position.y
                allowUndergroundFor = undergroundTicks
            }
            is BlockUpdateS2CPacket -> {
                if (packet.pos == player.blockPos) {
                    lastServerTeleportY = player.y
                    allowUndergroundFor = undergroundTicks
                }
            }
        }
    }

    @Suppress("unused")
    private val outgoingHandler = handler<QueuePacketEvent> { event ->
        if (event.origin != TransferOrigin.OUTGOING) return@handler
        val packet = event.packet

        if (packet is PlayerMoveC2SPacket && moving && player.isOnGround) {
            val tickNow = System.currentTimeMillis()
            val timeSinceLast = tickNow - lastSpoofTick
            val canSpoof = allowUndergroundFor > 0 && timeSinceLast > 150L

            if (canSpoof && lastServerTeleportY != null) {
                val baseY = player.blockPos.y.toDouble()
                val serverY = lastServerTeleportY!!
                val rawTarget = maxOf(baseY, serverY - undergroundOffset)

                // Clamp del offset para evitar flag (>0.05 aprox)
                val safeTarget = if (abs(player.y - rawTarget) > 0.05)
                    player.y - sign(undergroundOffset) * 0.05
                else rawTarget

                try {
                    // reportamos una Y un poco más baja y coherente con motionY
                    packet.y = safeTarget
                    packet.onGround = true
                    player.velocity.y = -0.05 // simula caída ligera
                    lastSpoofTick = tickNow
                } catch (_: Throwable) {}
            } else {
                // mantener coherencia sin alterar Y
                try { packet.onGround = true } catch (_: Throwable) {}
            }
        }
    }

    private fun canCauseSpeed(entity: Entity) =
        entity != player && entity is LivingEntity && entity !is ArmorStandEntity
}
