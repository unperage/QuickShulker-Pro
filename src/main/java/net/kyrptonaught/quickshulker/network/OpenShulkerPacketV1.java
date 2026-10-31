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

package net.kyrptonaught.quickshulker.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kyrptonaught.quickshulker.QuickShulkerMod;
import net.kyrptonaught.quickshulker.api.Util;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

/**
 * The original, one-field shape of the open request, kept registered under the original channel id.
 * <p>
 * A newer client sends {@link OpenShulkerPacket}, which carries an extra boolean, under its own channel id. The
 * two must not share an id: a client still running an older build would send four bytes, the newer codec would
 * read a boolean past the end of the buffer, and the connection dies with a DecoderException - which is what
 * kicked every player who was not on the matching build.
 * <p>
 * Requests arriving here are handled exactly as before, with stacking off.
 */
public record OpenShulkerPacketV1(int invSlot) implements CustomPacketPayload {

    //#if MC <= 1.20.6
    //$$ public static final ResourceLocation OPEN_SHULKER_PACKET_V1 = ResourceLocation.tryBuild(QuickShulkerMod.MOD_ID, "open_shulker_packet");
    //#elseif MC >= 1.21.11
    public static final Identifier OPEN_SHULKER_PACKET_V1 = Identifier.fromNamespaceAndPath(QuickShulkerMod.MOD_ID, "open_shulker_packet");
    //#else
    //$$ public static final ResourceLocation OPEN_SHULKER_PACKET_V1 = ResourceLocation.fromNamespaceAndPath(QuickShulkerMod.MOD_ID, "open_shulker_packet");
    //#endif

    public static final Type<OpenShulkerPacketV1> OPEN_SHULKER_PACKET_V1_ID = new Type<>(OPEN_SHULKER_PACKET_V1);

    public static final StreamCodec<FriendlyByteBuf, OpenShulkerPacketV1> CODEC = StreamCodec.ofMember(
            (value, buf) -> buf.writeInt(value.invSlot),
            buf -> new OpenShulkerPacketV1(buf.readInt()));

    public static void registerReceivePacket() {
        PayloadTypeRegistry.playC2S().register(OpenShulkerPacketV1.OPEN_SHULKER_PACKET_V1_ID, OpenShulkerPacketV1.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(OpenShulkerPacketV1.OPEN_SHULKER_PACKET_V1_ID,
                (payload, context) -> context.server().execute(() -> Util.openItem(context.player(), payload.invSlot(), false)));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return OPEN_SHULKER_PACKET_V1_ID;
    }
}
