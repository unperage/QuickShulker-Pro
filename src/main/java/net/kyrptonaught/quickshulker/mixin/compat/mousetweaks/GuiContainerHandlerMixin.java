/*
 * MIT License
 *
 * Copyright (c) 2019 kyrptonaught
 * Copyright (c) 2024 Haocen2004
 * Copyright (c) 2025 MoRanpcy
 * Copyright (c) 2025 EnderPhantomWing
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.kyrptonaught.quickshulker.mixin.compat.mousetweaks;

import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mouse Tweaks keeps its own drag handling and installs it on {@code AbstractContainerScreen#slotClicked}. In a
 * shulker box screen that collides with QuickShulker: the drag moves a stack into the shulker, Mouse Tweaks then
 * fires one more click on the slot the drag started on, and by that point the slot is empty - so the click reads
 * as "take it back out". The symptom is that the slot the drag started on always comes back out, while the rest
 * of the row goes in.
 * <p>
 * Its own entry point to switch off per screen is {@code IGuiScreenHandler#isMouseTweaksDisabled}, which it also
 * feeds from the {@code @MouseTweaksIgnore} annotation. QuickShulker has no screen class of its own to annotate -
 * it opens vanilla's shulker box screen - so the answer is forced here instead, for that screen only.
 * <p>
 * The targets string keeps Mouse Tweaks out of the compile classpath; {@code @Restriction} keeps this mixin from
 * being applied at all when the mod is absent.
 */
@Restriction(require = @Condition("mousetweaks"))
@Mixin(targets = "yalter.mousetweaks.handlers.GuiContainerHandler")
public abstract class GuiContainerHandlerMixin {

    @Inject(method = "isMouseTweaksDisabled", at = @At("HEAD"), cancellable = true)
    private void quickshulker$disableInShulkerScreens(CallbackInfoReturnable<Boolean> cir) {
        // The root stonecutter node is 1.21.11 and compiles this file as-is, so the unmarked block
        // has to be the OLD api; //$$ marks what the newer nodes get.
        //#if MC >= 26.2
        //$$ if (Minecraft.getInstance().gui.screen() instanceof ShulkerBoxScreen) {
        //$$     cir.setReturnValue(true);
        //$$ }
        //#else
        if (Minecraft.getInstance().screen instanceof ShulkerBoxScreen) {
            cir.setReturnValue(true);
        }
        //#endif
    }
}
