package net.ccbluex.liquidbounce.features.module.modules.player.nofall.modes

import net.ccbluex.liquidbounce.config.types.nesting.Choice
import net.ccbluex.liquidbounce.config.types.nesting.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.player.nofall.ModuleNoFall
import net.ccbluex.liquidbounce.utils.block.isBlockedByEntities
import net.ccbluex.liquidbounce.utils.block.toBlockPos
import net.minecraft.util.Hand

internal object NoFallGrimTest: Choice("Grim-test") {

    override val parent: ChoiceConfigurable<*>
        get() = ModuleNoFall.modes

    private data class HitEntry(val time: Long, val blockPos: net.minecraft.util.math.BlockPos)

    private val hitQueue = mutableListOf<HitEntry>()
    private const val STALL_DURATION_MS = 10000L

    @Suppress("unused")
    private val tickHandler = tickHandler {
        if (player.isOnGround) {
            hitQueue.removeAll { true }
        }

        if (player.fallDistance > 3f) {
            val posBelow = player.blockPos.down()
            if (posBelow.isBlockedByEntities()) {
                hitQueue.add(HitEntry(System.currentTimeMillis(), posBelow))
            }
        }

        val iterator = hitQueue.iterator()
        while (iterator.hasNext()) {
            val hitEntry = iterator.next()
            if (System.currentTimeMillis() - hitEntry.time >= STALL_DURATION_MS) {
                interaction.interactBlock(player, Hand.MAIN_HAND, null)

                iterator.remove()
            }
        }
    }
}
