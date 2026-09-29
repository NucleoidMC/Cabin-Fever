package io.github.haykam821.cabinfever.game.phase;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.predicates.BlockPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import io.github.haykam821.cabinfever.game.CabinFeverConfig;
import io.github.haykam821.cabinfever.game.map.CabinFeverMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import xyz.nucleoid.plasmid.api.game.GameActivity;
import xyz.nucleoid.plasmid.api.game.GameCloseReason;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptor;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptorResult;
import xyz.nucleoid.plasmid.api.game.player.JoinOffer;
import xyz.nucleoid.plasmid.api.game.rule.GameRuleType;
import xyz.nucleoid.plasmid.api.util.ItemStackBuilder;
import xyz.nucleoid.plasmid.api.util.PlayerRef;
import xyz.nucleoid.plasmid.api.util.PlayerUtil;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.block.BlockBreakEvent;
import xyz.nucleoid.stimuli.event.block.BlockUseEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

public class CabinFeverActivePhase {
	private final ServerLevel world;
	private final GameSpace gameSpace;
	private final CabinFeverMap map;
	private final CabinFeverConfig config;
	private final HolderAttachment guideText;
	private final Set<PlayerRef> players;
	private final Object2IntOpenHashMap<PlayerRef> coalAmounts = new Object2IntOpenHashMap<>();
	private boolean singleplayer;
	private int ticks = 0;
	private int ticksUntilClose = -1;

	public CabinFeverActivePhase(GameSpace gameSpace, ServerLevel world, CabinFeverMap map, CabinFeverConfig config, HolderAttachment guideText, Set<PlayerRef> players) {
		this.world = world;
		this.gameSpace = gameSpace;
		this.map = map;
		this.config = config;
		this.guideText = guideText;

		this.players = players;
		this.coalAmounts.defaultReturnValue(this.config.getMaxCoal());
	}

	public static void setRules(GameActivity activity) {
		activity.deny(GameRuleType.BLOCK_DROPS);
		activity.deny(GameRuleType.CRAFTING);
		activity.deny(GameRuleType.FALL_DAMAGE);
		activity.deny(GameRuleType.HUNGER);
		activity.deny(GameRuleType.PORTALS);
		activity.allow(GameRuleType.PVP);
		activity.deny(GameRuleType.SATURATED_REGENERATION);
		activity.deny(GameRuleType.THROW_ITEMS);
	}

	public static void open(GameSpace gameSpace, ServerLevel world, CabinFeverMap map, CabinFeverConfig config, HolderAttachment guide) {
		Set<PlayerRef> players = gameSpace.getPlayers().participants().stream().map(PlayerRef::of).collect(Collectors.toSet());
		CabinFeverActivePhase phase = new CabinFeverActivePhase(gameSpace, world, map, config, guide, players);

		gameSpace.setActivity(activity -> {
			CabinFeverActivePhase.setRules(activity);

			// Listeners
			activity.listen(BlockBreakEvent.EVENT, phase::onBreakBlock);
			activity.listen(GameActivityEvents.ENABLE, phase::enable);
			activity.listen(GameActivityEvents.TICK, phase::tick);
			activity.listen(GamePlayerEvents.ACCEPT, phase::onAcceptPlayers);
			activity.listen(GamePlayerEvents.OFFER, JoinOffer::acceptSpectators);
			activity.listen(GamePlayerEvents.REMOVE, phase::removePlayer);
			activity.listen(PlayerDeathEvent.EVENT, phase::onPlayerDeath);
			activity.listen(BlockUseEvent.EVENT, phase::onUseBlock);
		});
	}

	private void enable() {
		this.singleplayer = this.players.size() == 1;

		HolderGetter<Block> blocks = this.world.registryAccess().lookupOrThrow(Registries.BLOCK);

		ItemStack sword = ItemStackBuilder.of(Items.WOODEN_SWORD)
			.setUnbreakable()
			.build();

		ItemStack tool = ItemStackBuilder.of(Items.WOODEN_PICKAXE)
			.setUnbreakable()
			.build();

		BlockPredicate predicate = BlockPredicate.Builder.block()
			.of(blocks, Blocks.COAL_ORE, Blocks.COAL_BLOCK)
			.build();

		AdventureModePredicate checker = new AdventureModePredicate(List.of(predicate));
		tool.set(DataComponents.CAN_BREAK, checker);

 		for (PlayerRef playerRef : this.players) {
			playerRef.ifOnline(this.world, player -> {
				player.setGameMode(GameType.ADVENTURE);
				CabinFeverActivePhase.spawn(this.world, this.map, player);

				player.addItem(sword.copy());
				player.addItem(tool.copy());
			});
		}

		for (ServerPlayer player : this.gameSpace.getPlayers().spectators()) {
			CabinFeverActivePhase.spawn(this.world, this.map, player);
			this.setSpectator(player);
		}
	}

	private boolean updatePlayer(PlayerRef playerRef, ServerPlayer player) {
		int coalAmount = this.coalAmounts.getInt(playerRef);

		player.experienceProgress = Mth.clamp(coalAmount / (float) this.config.getMaxCoal(), 0, 1);
		player.setExperienceLevels(coalAmount);

		if (coalAmount <= 0) {
			this.eliminate(player, false);
			return true;
		}
		return false;
	}

	private void tick() {
		// Decrease ticks until game end to zero
		if (this.isGameEnding()) {
			if (this.ticksUntilClose == 0) {
				this.gameSpace.close(GameCloseReason.FINISHED);
			}

			this.ticksUntilClose -= 1;
			return;
		}

		this.ticks += 1;
		if (this.guideText != null && ticks == this.config.getGuideTicks()) {
			this.guideText.destroy();
		}

		// Eliminate players that do not have enough coal
		boolean decrementCoal = this.ticks % 60 == 0;
		Iterator<PlayerRef> playerIterator = this.players.iterator();
		while (playerIterator.hasNext()) {
			PlayerRef playerRef = playerIterator.next();
			if (decrementCoal) {
				this.coalAmounts.addTo(playerRef, -1);
			}

			playerRef.ifOnline(this.world, player -> {
				if (this.updatePlayer(playerRef, player)) {
					playerIterator.remove();
				}
			});
		}

		// Attempt to determine a winner
		if (this.players.size() < 2) {
			if (this.players.size() == 1 && this.singleplayer) return;
			this.gameSpace.getPlayers().sendMessage(this.getEndingMessage());
			this.ticksUntilClose = this.config.getTicksUntilClose().sample(this.world.getRandom());
		}
	}

	private Component getEndingMessage() {
		if (this.players.size() == 1) {
			PlayerRef winnerRef = this.players.iterator().next();
			if (winnerRef.isOnline(this.world)) {
				Player winner = winnerRef.getEntity(this.world);
				return Component.translatable("text.cabinfever.win", winner.getDisplayName()).withStyle(ChatFormatting.GOLD);
			}
		}
		return Component.translatable("text.cabinfever.no_winners").withStyle(ChatFormatting.GOLD);
	}

	private boolean isGameEnding() {
		return this.ticksUntilClose >= 0;
	}

	private void setSpectator(ServerPlayer player) {
		player.setGameMode(GameType.SPECTATOR);
	}

	private JoinAcceptorResult onAcceptPlayers(JoinAcceptor acceptor) {
		return acceptor.teleport(this.world, this.map.getSpawn()).thenRunForEach(player -> {
			player.setGameMode(GameType.SPECTATOR);
		});
	}

	private void eliminate(ServerPlayer eliminatedPlayer, boolean remove) {
		if (this.isGameEnding()) return;

		PlayerRef eliminatedRef = PlayerRef.of(eliminatedPlayer);
		if (!this.players.contains(eliminatedRef)) return;

		this.gameSpace.getPlayers().sendMessage(Component.translatable("text.cabinfever.eliminated", eliminatedPlayer.getDisplayName()).withStyle(ChatFormatting.RED));

		if (remove) {
			this.players.remove(eliminatedRef);
		}
		this.setSpectator(eliminatedPlayer);
	}

	private void removePlayer(ServerPlayer player) {
		this.eliminate(player, true);
	}

	private void clearCoal(ServerPlayer player) {
		player.getInventory().clearOrCountMatchingItems(CabinFeverActivePhase::isCoal,false, -1, player.inventoryMenu.getCraftSlots());
		player.containerMenu.broadcastChanges();
		player.inventoryMenu.slotsChanged(player.getInventory());
	}

	private EventResult onPlayerDeath(ServerPlayer player, DamageSource source) {
		if (!this.isGameEnding()) {
			PlayerRef playerRef = PlayerRef.of(player);
			if (this.players.contains(playerRef)) {
				this.coalAmounts.addTo(playerRef, -this.config.getDeathPrice());
				this.clearCoal(player);

				Component deathMessage = source.getLocalizedDeathMessage(player).copy().withStyle(ChatFormatting.RED);
				this.gameSpace.getPlayers().sendMessage(deathMessage);
			}
		}

		CabinFeverActivePhase.spawn(this.world, this.map, player);
		return EventResult.DENY;
	}

	private EventResult onBreakBlock(ServerPlayer player, ServerLevel world, BlockPos pos) {
		if (!this.isGameEnding()) {
			BlockState state = world.getBlockState(pos);

			int coalAmount = CabinFeverActivePhase.getBlockCoalAmount(state);
			if (coalAmount > 0) {
				PlayerRef playerRef = PlayerRef.of(player);
				if (this.players.contains(playerRef)) {
					int coalUntilMax = this.config.getMaxHeldCoal() - player.getInventory().countItem(Items.COAL);
					player.addItem(new ItemStack(Items.COAL, Math.min(coalAmount, coalUntilMax)));
				}
			}
		}

		return EventResult.DENY;
	}

	private InteractionResult onUseBlock(ServerPlayer player, InteractionHand hand, BlockHitResult hitResult) {
		if (!this.isGameEnding()) {
			PlayerRef playerRef = PlayerRef.of(player);
			if (this.players.contains(playerRef)) {
				BlockState state = this.world.getBlockState(hitResult.getBlockPos());
				if (state.is(BlockTags.CAMPFIRES)) {
					int heldCoal = player.getInventory().countItem(Items.COAL);
					int coalUntilMax = this.config.getMaxCoal() - this.coalAmounts.getInt(playerRef);

					this.coalAmounts.addTo(playerRef, Math.min(heldCoal, coalUntilMax));
					this.clearCoal(player);

					PlayerUtil.playSoundToPlayer(player, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1, 1);
				}
			}
		}

		return InteractionResult.FAIL;
	}

	private static boolean isCoal(ItemStack stack) {
		return stack.is(ItemTags.COALS);
	}

	private static int getBlockCoalAmount(BlockState state) {
		if (state.is(Blocks.COAL_ORE)) return 1;
		if (state.is(Blocks.COAL_BLOCK)) return 3;
		return 0;
	}

	public static void spawn(ServerLevel world, CabinFeverMap map, ServerPlayer player) {
		Vec3 spawn = map.getSpawn();
		player.teleportTo(world, spawn.x(), spawn.y(), spawn.z(), Set.of(), 0, 0, true);
	}
}