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
 */
package net.ccbluex.liquidbounce.features.module.modules.render.nametags

import net.ccbluex.fastutil.mapToArray
import net.ccbluex.liquidbounce.render.RenderEnvironment
import net.ccbluex.liquidbounce.render.drawColoredQuad
import net.ccbluex.liquidbounce.render.drawColoredQuadOutlines
import net.ccbluex.liquidbounce.render.engine.font.processor.MinecraftTextProcessor
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.ccbluex.liquidbounce.render.engine.type.Rect
import net.ccbluex.liquidbounce.render.engine.type.Vec3
import net.ccbluex.liquidbounce.utils.item.getEnchantment
import net.ccbluex.liquidbounce.utils.item.getEnchantmentCount
import net.ccbluex.liquidbounce.utils.kotlin.LruCache
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.Formatting
import net.minecraft.client.resource.language.I18n
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.ccbluex.liquidbounce.utils.client.mc
import org.joml.Vector2f
import org.joml.component1
import org.joml.component2
import kotlin.math.hypot

private object EnchantmentDisplayHelper {
    private val enchantmentAbbreviationCache = LruCache<RegistryKey<Enchantment>, String>(128)

    private val knownCurses = setOf(
        Enchantments.BINDING_CURSE,
        Enchantments.VANISHING_CURSE
    )

    fun getEnchantmentInfo(enchantment: RegistryKey<Enchantment>): EnchantmentInfo {
        return EnchantmentInfo(
            displayName = getAbbreviation(enchantment),
            isCurse = isCurse(enchantment)
        )
    }

    private fun getEnchantmentName(enchantment: RegistryKey<Enchantment>): String {
        val idPath = enchantment.value.toString().substringAfter(':')
        val translationKey = "enchantment.minecraft.$idPath"
        return I18n.translate(translationKey)
    }

    private fun getSingleWordAbbreviation(word: String): String = word.take(3)

    private fun getInitialsAbbreviation(words: List<String>): String =
        words.joinToString("") { it.first().toString() }

    private fun getCompoundAbbreviation(words: List<String>): String {
        val firstWord = words[0]

        if (firstWord.length >= 3) {
            return firstWord.take(3)
        }

        val remainingChars = 3 - firstWord.length
        return firstWord + words.getOrNull(1)?.take(remainingChars).orEmpty()
    }

    private fun processMultiWordName(words: List<String>): String {
        val initials = getInitialsAbbreviation(words)

        return if (initials.length >= 3) {
            initials
        } else {
            getCompoundAbbreviation(words)
        }
    }

    private fun processName(name: String): String {
        if (name.length <= 3) {
            return name
        }

        val words = name.split(" ").filter { it.isNotEmpty() }

        return if (words.size >= 2) {
            processMultiWordName(words)
        } else {
            getSingleWordAbbreviation(words.getOrNull(0) ?: "")
        }
    }

    private fun getAbbreviation(enchantment: RegistryKey<Enchantment>): String {
        return enchantmentAbbreviationCache.getOrPut(enchantment) {
            val name = getEnchantmentName(enchantment)
            processName(name)
        }
    }

    private fun isCurse(enchantment: RegistryKey<Enchantment>): Boolean = enchantment in knownCurses
}

private data class EnchantmentInfo(
    val displayName: String,
    val isCurse: Boolean = false
)

object NametagEnchantmentRenderer {
    private const val MAX_ENCHANTMENTS_PER_ITEM = 10
    private const val FIXED_SCALE = 0.6f
    private const val LINE_HEIGHT = 14f
    private const val COLUMN_SPACING = 20f
    private const val PADDING = 3f
    private const val CELL_HEIGHT = LINE_HEIGHT + PADDING * 2
    private const val VERTICAL_SPACING = 4f
    private const val FRAME_MARGIN = 6f
    private val BG_COLOR_NORMAL = Color4b.BLACK.with(a = 200)
    private val BG_COLOR_CURSE = Color4b.RED.darker().with(a = 200)

    private val supportedEnchantments by lazy {
        mc.world?.registryManager?.getOrThrow(RegistryKeys.ENCHANTMENT)?.keys?.toList() ?: emptyList()
    }

    @JvmRecord
    private data class EnchantCell(
        val processedText: MinecraftTextProcessor.RecyclingProcessedText,
        val textWidth: Float,
        val isCurse: Boolean
    )

    @JvmRecord
    private data class EnchantColumn(
        val cells: List<EnchantCell>,
        val width: Float
    )

    fun drawEntityEnchantments(
        env: RenderEnvironment,
        entity: LivingEntity,
        worldX: Float,
        worldY: Float,
    ) {
        if (!NametagShowOptions.ENCHANTMENTS.isShowing()) return

        val itemsWithEnchantments = getEntityItemsWithEnchantments(entity)
        if (itemsWithEnchantments.isEmpty()) return

        if (isPositionOccluded(worldX, worldY)) {
            return
        }

        val columnData = itemsWithEnchantments.mapNotNull { item ->
            val cells = processItemEnchantments(item)
            if (cells.isEmpty()) return@mapNotNull null

            val maxWidth = cells.maxOfOrNull { it.textWidth } ?: 0f
            val columnWidth = maxWidth * FIXED_SCALE + PADDING * 2
            EnchantColumn(cells, columnWidth)
        }

        if (columnData.isNotEmpty()) {
            // Add this position to the drawn areas list
            ModuleNametags.drawnEnchantmentAreas.add(Vector2f(worldX, worldY))
            env.drawEnchantmentColumns(worldX, worldY, columnData)
        }
    }

    // Check if a position would be occluded by another enchantment panel
    private fun isPositionOccluded(x: Float, y: Float): Boolean {
        val OCCLUSION_THRESHOLD = 2f

        return ModuleNametags.drawnEnchantmentAreas.any { (existingX, existingY) ->
            hypot(existingX - x, existingY - y) < OCCLUSION_THRESHOLD
        }
    }

    private fun processItemEnchantments(itemStack: ItemStack): List<EnchantCell> {
        val enchantmentList = mutableListOf<Pair<EnchantmentInfo, Int>>()

        for (enchantmentKey in supportedEnchantments) {
            val level = itemStack.getEnchantment(enchantmentKey)
            if (level > 0) {
                enchantmentList.add(EnchantmentDisplayHelper.getEnchantmentInfo(enchantmentKey) to level)
            }
        }

        if (enchantmentList.isEmpty()) return emptyList()

        val sortedEnchantments = enchantmentList.sortedByDescending { it.second }
        val hasMoreEnchantments = sortedEnchantments.size > MAX_ENCHANTMENTS_PER_ITEM

        val cells = sortedEnchantments
            .take(MAX_ENCHANTMENTS_PER_ITEM)
            .mapToArray { (info, level) -> createCell(info, level) }

        if (hasMoreEnchantments && cells.isNotEmpty()) {
            cells[cells.lastIndex] = createCell(null, 0, true)
        }

        return cells.asList()
    }

    private fun getEntityItemsWithEnchantments(entity: LivingEntity): List<ItemStack> = listOf(
        entity.mainHandStack,
        entity.offHandStack,
        entity.getEquippedStack(EquipmentSlot.HEAD),
        entity.getEquippedStack(EquipmentSlot.CHEST),
        entity.getEquippedStack(EquipmentSlot.LEGS),
        entity.getEquippedStack(EquipmentSlot.FEET)
    ).filter { !it.isEmpty && it.getEnchantmentCount() > 0 }

    private fun createCell(
        info: EnchantmentInfo? = null,
        level: Int = 0,
        isEllipsis: Boolean = false
    ): EnchantCell {
        val text = if (isEllipsis) {
            "${Formatting.GRAY}..."
        } else {
            val textColor = when {
                info?.isCurse == true -> Formatting.RED
                level >= 4 -> Formatting.GOLD
                level == 3 -> Formatting.YELLOW
                level == 2 -> Formatting.GREEN
                else -> Formatting.WHITE
            }
            "${textColor}${info?.displayName} $level"
        }

        val processedText = ModuleNametags.fontRenderer.process(text)
        val textWidth = ModuleNametags.fontRenderer.getStringWidth(processedText, false)
        return EnchantCell(
            processedText,
            textWidth,
            !isEllipsis && info?.isCurse == true
        )
    }

    private fun RenderEnvironment.renderEnchantmentColumn(
        cells: List<EnchantCell>,
        x: Float,
        y: Float,
    ) {
        val maxWidth = cells.maxOfOrNull { it.textWidth } ?: 0f
        val cellWidth = maxWidth * FIXED_SCALE + PADDING * 2

        cells.forEachIndexed { index, cell ->
            val cellX = x - cellWidth / 2
            val cellY = y + index * (CELL_HEIGHT + VERTICAL_SPACING)

            val rect = Rect(
                cellX,
                cellY,
                cellX + cellWidth,
                cellY + CELL_HEIGHT
            )
            val bgColor = if (cell.isCurse) BG_COLOR_CURSE else BG_COLOR_NORMAL

            drawCellBackground(rect, bgColor)

            val textX = cellX + (cellWidth - cell.textWidth * FIXED_SCALE) / 2
            val textY = cellY + PADDING + (LINE_HEIGHT - (ModuleNametags.fontRenderer.height * FIXED_SCALE)) / 2

            ModuleNametags.fontRenderer.draw(
                cell.processedText,
                textX,
                textY,
                shadow = true,
                z = 0.001f,
                scale = FIXED_SCALE
            )
        }
    }

    private fun RenderEnvironment.drawCellBackground(
        rect: Rect,
        color: Color4b
    ) {
        val leftTop = Vec3(rect.x1, rect.y1, 0F)
        val rightBottom = Vec3(rect.x2, rect.y2, 0F)
        drawColoredQuad(leftTop, rightBottom, color.toARGB())
    }

    private fun RenderEnvironment.drawEnchantmentColumns(
        x: Float,
        y: Float,
        columnData: List<EnchantColumn>
    ) {
        val columnsWidth = columnData.sumOf { it.width.toDouble() }.toFloat()
        val spacingWidth = (columnData.size - 1) * COLUMN_SPACING
        val totalWidth = columnsWidth + spacingWidth
        val halfTotalWidth = totalWidth / 2

        val maxColumnHeight = columnData.maxOfOrNull { column ->
            column.cells.size * (CELL_HEIGHT + VERTICAL_SPACING) - VERTICAL_SPACING
        } ?: 0f

        val groupRect = Rect(
            x - halfTotalWidth - FRAME_MARGIN,
            y - FRAME_MARGIN,
            x + halfTotalWidth + FRAME_MARGIN,
            y + maxColumnHeight + FRAME_MARGIN
        )

        drawGroupBorder(groupRect)

        var columnX = x - halfTotalWidth
        columnData.forEach { column ->
            val columnCenterX = columnX + column.width / 2
            renderEnchantmentColumn(column.cells, columnCenterX, y)
            columnX += column.width + COLUMN_SPACING
        }
    }

    private fun RenderEnvironment.drawGroupBorder(rect: Rect) {
        // Drawing a semi-transparent background instead of just lines for better visibility
        val leftTop = Vec3(rect.x1, rect.y1, 0F)
        val rightBottom = Vec3(rect.x2, rect.y2, 0F)
        drawColoredQuad(
            leftTop, rightBottom,
            Color4b.BLACK.with(a = 100).toARGB(),
        )

        drawColoredQuadOutlines(
            leftTop, rightBottom,
            Color4b.RED.toARGB(),
        )
    }
}
