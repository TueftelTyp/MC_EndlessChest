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

    public record TargetListRequest(int ignored) implements CustomPacketPayload {
        public static final Type<TargetListRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath(InfiniteChestMod.ID, "target_list_request"));
        public static final StreamCodec<FriendlyByteBuf, TargetListRequest> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, TargetListRequest::ignored, TargetListRequest::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Targets(String ownName, String jsonNames) implements CustomPacketPayload {
        public static final Type<Targets> TYPE = new Type<>(Identifier.fromNamespaceAndPath(InfiniteChestMod.ID, "targets"));
        public static final StreamCodec<FriendlyByteBuf, Targets> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(24), Targets::ownName,
            ByteBufCodecs.stringUtf8(16384), Targets::jsonNames, Targets::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Rename(String name) implements CustomPacketPayload {
        public static final Type<Rename> TYPE = new Type<>(Identifier.fromNamespaceAndPath(InfiniteChestMod.ID, "rename"));
        public static final StreamCodec<FriendlyByteBuf, Rename> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), Rename::name, Rename::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record NamedTransfer(int slot, String targetName, int amount) implements CustomPacketPayload {
        public static final Type<NamedTransfer> TYPE = new Type<>(Identifier.fromNamespaceAndPath(InfiniteChestMod.ID, "named_transfer"));
        public static final StreamCodec<FriendlyByteBuf, NamedTransfer> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, NamedTransfer::slot,
            ByteBufCodecs.stringUtf8(24), NamedTransfer::targetName,
            ByteBufCodecs.INT, NamedTransfer::amount, NamedTransfer::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record RecipientChange(String name, boolean add) implements CustomPacketPayload {
        public static final Type<RecipientChange> TYPE = new Type<>(Identifier.fromNamespaceAndPath(InfiniteChestMod.ID, "recipient_change"));
        public static final StreamCodec<FriendlyByteBuf, RecipientChange> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(24), RecipientChange::name,
            ByteBufCodecs.BOOL, RecipientChange::add, RecipientChange::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(TargetListRequest.TYPE, TargetListRequest.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RecipientChange.TYPE, RecipientChange.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Rename.TYPE, Rename.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(NamedTransfer.TYPE, NamedTransfer.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Targets.TYPE, Targets.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Search.TYPE, Search.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Transfer.TYPE, Transfer.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Withdraw.TYPE, Withdraw.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RecipientChange.TYPE, (payload, context) -> {
            if (context.player().containerMenu instanceof PagedChestMenu menu)
                menu.changeRecipient(context.player(), payload.name(), payload.add());
        });
        ServerPlayNetworking.registerGlobalReceiver(TargetListRequest.TYPE, (payload, context) -> {
            if (context.player().containerMenu instanceof PagedChestMenu menu)
                menu.sendTargets(context.player());
        });
        ServerPlayNetworking.registerGlobalReceiver(Rename.TYPE, (payload, context) -> {
            if (context.player().containerMenu instanceof PagedChestMenu menu)
                menu.rename(context.player(), payload.name());
        });
        ServerPlayNetworking.registerGlobalReceiver(NamedTransfer.TYPE, (payload, context) -> {
            if (context.player().containerMenu instanceof PagedChestMenu menu)
                menu.transferNamed(context.player(), payload.slot(), payload.targetName(), payload.amount());
        });
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
