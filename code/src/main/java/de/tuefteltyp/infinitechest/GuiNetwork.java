package de.tuefteltyp.infinitechest;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Only requests are transmitted. Storage and permission decisions remain server-owned. */
public final class GuiNetwork {
    private GuiNetwork() {}

    public record Search(String query) implements CustomPacketPayload {
        public static final Type<Search> TYPE = new Type<>(Identifier.fromNamespaceAndPath(InfiniteChestMod.ID, "search"));
        public static final StreamCodec<FriendlyByteBuf, Search> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), Search::query, Search::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Action(int action) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(Identifier.fromNamespaceAndPath(InfiniteChestMod.ID, "page"));
        public static final StreamCodec<FriendlyByteBuf, Action> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, Action::action, Action::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Withdraw(int slot, int amount) implements CustomPacketPayload {
        public static final Type<Withdraw> TYPE = new Type<>(Identifier.fromNamespaceAndPath(InfiniteChestMod.ID, "withdraw"));
        public static final StreamCodec<FriendlyByteBuf, Withdraw> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, Withdraw::slot, ByteBufCodecs.INT, Withdraw::amount, Withdraw::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Transfer(int slot, BlockPos target, int amount) implements CustomPacketPayload {
        public static final Type<Transfer> TYPE = new Type<>(Identifier.fromNamespaceAndPath(InfiniteChestMod.ID, "transfer"));
        public static final StreamCodec<FriendlyByteBuf, Transfer> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, Transfer::slot,
            BlockPos.STREAM_CODEC, Transfer::target,
            ByteBufCodecs.INT, Transfer::amount,
            Transfer::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Search.TYPE, Search.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Transfer.TYPE, Transfer.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Withdraw.TYPE, Withdraw.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Search.TYPE, (payload, context) -> {
            if (context.player().containerMenu instanceof PagedChestMenu menu) menu.applySearch(context.player(), payload.query());
        });
        ServerPlayNetworking.registerGlobalReceiver(Action.TYPE, (payload, context) -> {
            if (context.player().containerMenu instanceof PagedChestMenu menu) menu.navigate(context.player(), payload.action());
        });
        ServerPlayNetworking.registerGlobalReceiver(Withdraw.TYPE, (payload, context) -> {
            if (context.player().containerMenu instanceof PagedChestMenu menu)
                menu.withdraw(context.player(), payload.slot(), payload.amount());
        });
        ServerPlayNetworking.registerGlobalReceiver(Transfer.TYPE, (payload, context) -> {
            if (context.player().containerMenu instanceof PagedChestMenu menu)
                menu.transfer(context.player(), payload.slot(), payload.target(), payload.amount());
        });
    }
}
