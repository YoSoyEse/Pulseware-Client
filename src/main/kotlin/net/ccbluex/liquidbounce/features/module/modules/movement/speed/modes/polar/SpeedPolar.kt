package net.ccbluex.liquidbounce.features.module.modules.movement.speed.modes.polar

import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.HealthUpdateEvent
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleAimbot
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura
import kotlin.math.*

class SpeedPolar(override val parent: ChoiceConfigurable<*>) : Choice("Polar Rotational 3.0") {

    private val strafeStrength by float("MoveStrength", 0.05f, 0.001f..1.0f)
    private val directionChangeDelayMs by int("DirectionChangeDelay", 200, 50..1000)
    private val floatHopPower by float("FloatHopPower", 0.05f, 0.0f..0.3f)
    private val maxHurtCooldownTicks by int("HurtCooldownTicks", 40, 1..80)
    private val hurtSpeedFactor by float("HurtSpeedFactor", 0.02f, 0.0f..1.0f)

    private var direction = 1
    private var lastHitTime = 0L
    private var hurtCooldownTicks = 0
    private var lastDirectionChangeTime = 0L
    private var currentYawOffset = 0.0

    /**
     * Detecta daño
     */
    @Suppress("unused")
    private val healthHandler = handler<HealthUpdateEvent> { event ->
        if (event.health < event.previousHealth) {
            lastHitTime = System.currentTimeMillis()
            hurtCooldownTicks = maxHurtCooldownTicks
        }
    }

    /**
     * Movimiento rotacional flotante
     */
    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (hurtCooldownTicks > 0) hurtCooldownTicks--

        if (player.hurtTime > 0 || hurtCooldownTicks > 0) return@tickHandler
        if (!player.isOnGround) return@tickHandler

        val now = System.currentTimeMillis()

        if (now - lastDirectionChangeTime >= directionChangeDelayMs) {
            direction = if (player.input.movementForward > 0) 1 else -1
            currentYawOffset += direction * 15.0 * (Math.random() - 0.5)
            lastDirectionChangeTime = now
        }

        val yawRad = Math.toRadians(player.yaw.toDouble() + currentYawOffset)
        val moveX = -sin(yawRad) * strafeStrength
        val moveZ = cos(yawRad) * strafeStrength

        player.velocity = player.velocity.add(moveX, 0.0, moveZ)

        if (player.isOnGround && player.velocity.y <= 0.0) {
            player.velocity = player.velocity.add(0.0, floatHopPower.toDouble(), 0.0)
        }

        val target = ModuleKillAura.targetTracker.target ?: ModuleAimbot.targetTracker.target
        if (target != null) {
            val dx = target.x - player.x
            val dz = target.z - player.z
            val targetYaw = Math.toDegrees(atan2(dz, dx)) - 90.0
            player.yaw = player.yaw + ((targetYaw - player.yaw) * 0.05f).toFloat()
        }
    }
}
