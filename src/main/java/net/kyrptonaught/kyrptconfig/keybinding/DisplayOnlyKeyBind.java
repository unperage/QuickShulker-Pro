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

package net.kyrptonaught.kyrptconfig.keybinding;

import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class DisplayOnlyKeyBind extends KeyMapping {
    private final Consumer<InputConstants.Key> keySet;
    private CustomKeyBinding customKeyBinding;

    /**
     * Minecraft walks every registered key mapping on startup and calls setKey(getDefaultKey()). At that moment
     * options.txt has no entry for these mappings - they keep their value in the mod config instead - so the key
     * read back is UNKNOWN, and writing it through would erase whatever the player had bound. Only a real change
     * from the player, which happens after registration, is allowed to persist.
     */
    private boolean readyToPersist;

    /**
     * Set for the duration of {@link net.minecraft.client.KeyMapping#resetMapping()}. That call seeds every mapping
     * with its default key, which for these mappings is the value the config held at construction time - the exact
     * same code path a player edit takes, so without this the default was written back over the player's choice
     * on every launch.
     */
    public static boolean SUPPRESS_PERSIST;

    //#if MC >= 1.21.10
    public DisplayOnlyKeyBind(String translationKey, InputConstants.Type type, int code, KeyMapping.Category category) {
    //#else
    //$$ public DisplayOnlyKeyBind(String translationKey, InputConstants.Type type, int code, String category) {
    //#endif
        super(translationKey, type, code, category);
        keySet = (boundKey) -> {
        };
    }

    //#if MC >= 1.21.10
    public DisplayOnlyKeyBind(String translationKey, KeyMapping.Category category, CustomKeyBinding customKeyBinding, Consumer<InputConstants.Key> keySet) {
    //#else
    //$$ public DisplayOnlyKeyBind(String translationKey, String category, CustomKeyBinding customKeyBinding, Consumer<InputConstants.Key> keySet) {
    //#endif
        super(translationKey, customKeyBinding.getDefaultKey().getType(), customKeyBinding.getDefaultKey().getValue(), category);
        this.customKeyBinding = customKeyBinding;
        this.customKeyBinding.setConsumer(key -> {
            if(!this.key.equals(customKeyBinding.getKeybinding().orElse(null)))
                this.updateSetKey();
        });
        this.keySet = keySet;
        updateSetKey();
        this.readyToPersist = true;
    }

    public void setKey(InputConstants.@NotNull Key boundKey) {
        super.setKey(boundKey);
        // Deliberately not writing the value back here. Minecraft seeds every mapping with its default key during
        // startup, and that call is indistinguishable from a real edit at this level - whichever guard is used,
        // the default ends up overwriting the player's choice. Persistence belongs to the config screen, which
        // knows a player edited it; this mapping only mirrors the config (see syncFromConfig).
        if (!SUPPRESS_PERSIST)
            keySet.accept(boundKey);
    }

    public void updateSetKey() {
        super.setKey(customKeyBinding.getKeybinding().orElse(InputConstants.UNKNOWN));
    }

    /**
     * Brings the mapping back in line with the config, but only when it drifted. Called repeatedly, so it must
     * not write anything: {@code super.setKey} bypasses the persisting override on purpose.
     */
    public void syncFromConfig() {
        if (customKeyBinding == null) return;
        InputConstants.Key wanted = customKeyBinding.getKeybinding().orElse(InputConstants.UNKNOWN);
        if (!wanted.equals(this.key)) {
            super.setKey(wanted);
        }
    }

    @Override
    //#if MC >= 1.21.10
    public KeyMapping.@NotNull Category getCategory() {
    //#else
    //$$ public @NotNull String getCategory() {
    //#endif
        return super.getCategory();
    }

    @Override
    public @NotNull String getName() {
        return super.getName();
    }

    @Override
    public InputConstants.@NotNull Key getDefaultKey() {
        return super.getDefaultKey();
    }
}
