package net.ccbluex.liquidbounce.features.module.modules.combat.velocity.mode

import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.PlayerTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.modules.movement.fly.ModuleFly
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.min

internal object VelocityDelayedPong : VelocityMode("delayedPong") {

    // Configurables
    private const val DELAY_TICKS = 20            // ticks to wait before sending the queued pongs
    private const val PER_TICK_SEND_LIMIT = 3     // cuántos pongs enviar por tick (evitar spam)
    private const val MAX_QUEUE_SIZE = 100        // to prevent memory explosion
    private const val ENTRY_TIMEOUT_TICKS = 1200  // si un entry no se envía en tanto tiempo lo descartamos

    // Internal tick counter (incrementado en tick handler)
    private var currentTick = 0L

    // Queue entries: Pair(id:Int, sendTick:Long, createdTick:Long)
    private data class PongEntry(val id: Int, val sendTick: Long, val createdTick: Long)

    // Thread-safe queue
    private val pongQueue = ConcurrentLinkedQueue<PongEntry>()

    // Flag para activar tras cancelar un velocity (ejemplo de trigger)
    private var testMode = false

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        val packet = event.packet

        // No interferir si Fly está encendido
        if (ModuleFly.enabled) return@handler

        // 1) Trigger: cancelar velocity entrante y activar modo test
        if (packet is EntityVelocityUpdateS2CPacket && packet.entityId == player.id) {
            testMode = true
            event.cancelEvent()
            return@handler
        }

        // 2) Intercept KeepAlive / Ping S2C: extraer id, cancelar, encolar (no reenviar el original)
        if ((packet is KeepAliveS2CPacket || packet is CommonPingS2CPacket) && testMode) {
            // intentar extraer id (int o long -> int)
            val idInt = extractIdAsInt(packet)
            if (idInt != null) {
                event.cancelEvent() // no procesar el packet S2C original por el cliente

                // Agregar a la cola solo si no supera tamaño máximo
                if (pongQueue.size < MAX_QUEUE_SIZE) {
                    pongQueue.add(PongEntry(idInt, currentTick + DELAY_TICKS, currentTick))
                }

                // opcional: desactivar testMode si quieres sólo una respuesta por trigger
                testMode = false
            }
        }
    }

    // tick handler: incrementar tick y despachar pongs programados
    @Suppress("unused")
    private val tickHandler = handler<PlayerTickEvent> {
        currentTick++

        if (pongQueue.isEmpty()) return@handler

        // Contador de envíos esta tick
        var sentThisTick = 0

        // Intentar enviar hasta PER_TICK_SEND_LIMIT entradas cuyo sendTick <= currentTick
        val queueSnapshot = ArrayList<PongEntry>() // snapshot para iterar sin bloquear
        queueSnapshot.addAll(pongQueue)

        for (entry in queueSnapshot) {
            if (sentThisTick >= PER_TICK_SEND_LIMIT) break

            // Timeout: si entry antiguo, quitarlo
            if (currentTick - entry.createdTick > ENTRY_TIMEOUT_TICKS) {
                pongQueue.remove(entry)
                continue
            }

            if (entry.sendTick <= currentTick) {
                // enviar pong construido con el id (int)
                try {
                    network.sendPacket(CommonPongC2SPacket(entry.id))
                } catch (_: Throwable) {
                    // ignore send errors
                }
                pongQueue.remove(entry)
                sentThisTick++
            }
        }
    }

    // Helper: intenta extraer id como Int (soporta Int o Long fields con nombres comunes)
    private fun extractIdAsInt(packet: Any): Int? {
        return try {
            val fld = packet::class.java.declaredFields.firstOrNull {
                val n = it.name.lowercase()
                n == "id" || n == "keepaliveid" || n == "pingid" || n == "randomid"
            } ?: packet::class.java.declaredFields.firstOrNull() // fallback: primer campo
            fld?.apply { isAccessible = true }?.get(packet)?.let { value ->
                when (value) {
                    is Int -> value
                    is Long -> value.toInt()
                    else -> null
                }
            }
        } catch (t: Throwable) {
            null
        }
    }
}
