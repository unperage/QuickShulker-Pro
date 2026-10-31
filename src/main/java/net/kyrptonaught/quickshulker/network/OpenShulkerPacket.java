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
import net.kyrptonaught.quickshulker.api.Util;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

/**
 * @param invSlot the menu slot to open, negative for a slot that lives in another container
 * @param nest    whether the client wants this open to be stacked, so closing it returns to the previous screen.
 *                The setting lives on the client, so it has to travel with the request - the server cannot read it.
 */
public record OpenShulkerPacket(int invSlot, boolean nest) implements CustomPacketPayload {

    //#if MC <= 1.20.6
    //$$ public static final ResourceLocation OPEN_SHULKER_PACKET = ResourceLocation.tryBuild(QuickShulkerMod.MOD_ID, "open_shulker_packet_v2");
    //#elseif MC >= 1.21.11
    public static final Identifier OPEN_SHULKER_PACKET = Identifier.fromNamespaceAndPath(QuickShulkerMod.MOD_ID, "open_shulker_packet_v2");
    //#else
    //$$ public static final ResourceLocation OPEN_SHULKER_PACKET = ResourceLocation.fromNamespaceAndPath(QuickShulkerMod.MOD_ID, "open_shulker_packet_v2");
    //#endif

    public static final Type<OpenShulkerPacket> OPEN_SHULKER_PACKET_ID = new Type<>(OPEN_SHULKER_PACKET);

    public static final StreamCodec<FriendlyByteBuf, OpenShulkerPacket> CODEC = StreamCodec.ofMember(
            (value, buf) -> {
                buf.writeInt(value.invSlot);
                buf.writeBoolean(value.nest);
            },
            buf -> new OpenShulkerPacket(buf.readInt(), buf.readBoolean()));

    public static void registerReceivePacket() {
        PayloadTypeRegistry.playC2S().register(OpenShulkerPacket.OPEN_SHULKER_PACKET_ID, OpenShulkerPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenShulkerPacket.OPEN_SHULKER_PACKET_ID, OpenShulkerPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(OpenShulkerPacket.OPEN_SHULKER_PACKET_ID, (payload, context) -> context.server().execute(() -> Util.openItem(context.player(), payload.invSlot(), payload.nest())));
    }

    @Environment(EnvType.CLIENT)
    public static void sendOpenPacket(int invSlot, boolean nest) {
        ClientPlayNetworking.send(new OpenShulkerPacket(invSlot, nest));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return OPEN_SHULKER_PACKET_ID;
    }
}