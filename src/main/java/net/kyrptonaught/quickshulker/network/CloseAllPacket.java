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

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kyrptonaught.quickshulker.QuickShulkerMod;
import net.kyrptonaught.quickshulker.util.NestedMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

/**
 * Sent by the client right before it closes a container screen while the configurable extra key is held, so the
 * server drops the whole nested screen chain instead of stepping back one level.
 */
public record CloseAllPacket() implements CustomPacketPayload {

    //#if MC <= 1.20.6
    //$$ public static final ResourceLocation CLOSE_ALL_PACKET = ResourceLocation.tryBuild(QuickShulkerMod.MOD_ID, "close_all_packet");
    //#elseif MC >= 1.21.11
    public static final Identifier CLOSE_ALL_PACKET = Identifier.fromNamespaceAndPath(QuickShulkerMod.MOD_ID, "close_all_packet");
    //#else
    //$$ public static final ResourceLocation CLOSE_ALL_PACKET = ResourceLocation.fromNamespaceAndPath(QuickShulkerMod.MOD_ID, "close_all_packet");
    //#endif

    public static final Type<CloseAllPacket> CLOSE_ALL_PACKET_ID = new Type<>(CLOSE_ALL_PACKET);

    public static final StreamCodec<FriendlyByteBuf, CloseAllPacket> CODEC =
            StreamCodec.ofMember((value, buf) -> {
            }, buf -> new CloseAllPacket());

    public static void registerReceivePacket() {
        PayloadTypeRegistry.playC2S().register(CloseAllPacket.CLOSE_ALL_PACKET_ID, CloseAllPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CloseAllPacket.CLOSE_ALL_PACKET_ID, (payload, context) ->
                context.server().execute(() -> NestedMenus.clear(context.player())));
    }

    @Environment(EnvType.CLIENT)
    public static void send() {
        ClientPlayNetworking.send(new CloseAllPacket());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return CLOSE_ALL_PACKET_ID;
    }
}
