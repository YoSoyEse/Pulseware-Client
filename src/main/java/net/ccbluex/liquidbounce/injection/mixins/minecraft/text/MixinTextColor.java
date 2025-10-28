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
 *
 */

package net.ccbluex.liquidbounce.injection.mixins.minecraft.text;

import com.google.common.base.Objects;
import net.ccbluex.liquidbounce.interfaces.ClientTextColorAdditions;
import net.minecraft.text.TextColor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Why not Style? Because it is immutable and we would have to edit each and every new instance creation.
 */
@Mixin(TextColor.class)
public class MixinTextColor implements ClientTextColorAdditions {

    @Shadow
    @Final
    private @Nullable String name;
    @Shadow
    @Final
    private int rgb;
    @Unique
    private boolean bypassesNameProtect = false;

    @Override
    public boolean liquid_bounce$doesBypassingNameProtect() {
        return bypassesNameProtect;
    }

    @Override
    public TextColor liquid_bounce$withNameProtectionBypass() {
        var textColor = new TextColor(this.rgb, this.name);

        ((ClientTextColorAdditions) ((Object) textColor)).liquid_bounce$setBypassingNameProtection(true);

        return textColor;
    }

    @Override
    public void liquid_bounce$setBypassingNameProtection(boolean bypassesNameProtect) {
        this.bypassesNameProtect = bypassesNameProtect;
    }

    @Inject(method = "equals", at = @At("RETURN"), cancellable = true)
    private void equals(Object o, CallbackInfoReturnable<Boolean> cir) {
        if (o instanceof TextColor) {
            if (this.bypassesNameProtect != ((ClientTextColorAdditions) o).liquid_bounce$doesBypassingNameProtect()) {
                cir.setReturnValue(false);
            }
        }
    }
    /**
     * @author superblaubeere27
     * @reason Nobody will ever overwrite this method too fr.
     */
    @Overwrite
    public int hashCode() {
        return Objects.hashCode(this.name, this.rgb, this.bypassesNameProtect);
    }

}
