package io.github.haykam821.cabinfever.game.phase;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import io.github.haykam821.cabinfever.game.CabinFeverConfig;
import io.github.haykam821.cabinfever.game.map.CabinFeverMap;
import io.github.haykam821.cabinfever.game.map.CabinFeverMapBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Display.BillboardConstraints;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeLevelConfig;
import xyz.nucleoid.plasmid.api.game.GameOpenContext;
import xyz.nucleoid.plasmid.api.game.GameOpenProcedure;
import xyz.nucleoid.plasmid.api.game.GameResult;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.common.GameWaitingLobby;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptor;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptorResult;
import xyz.nucleoid.plasmid.api.game.player.JoinOffer;
import xyz.nucleoid.plasmid.api.game.rule.GameRuleType;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerDamageEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

public class CabinFeverWaitingPhase {
	private static final ChatFormatting GUIDE_FORMATTING = ChatFormatting.GOLD;
	private static final Component GUIDE_TEXT = Component.empty()
		.append(Component.translatable("gameType.cabinfever.cabin_fever").withStyle(ChatFormatting.BOLD))
		.append(CommonComponents.NEW_LINE)
		.append(Component.translatable("text.cabinfever.guide"))
		.withStyle(GUIDE_FORMATTING);

	private final GameSpace gameSpace;
	private final ServerLevel world;
	private final CabinFeverMap map;
	private final CabinFeverConfig config;
	private HolderAttachment guideText;

	public CabinFeverWaitingPhase(GameSpace gameSpace, ServerLevel world, CabinFeverMap map, CabinFeverConfig config) {
		this.gameSpace = gameSpace;
		this.world = world;
		this.map = map;
		this.config = config;
	}

	public static GameOpenProcedure open(GameOpenContext<CabinFeverConfig> context) {
		CabinFeverMapBuilder mapBuilder = new CabinFeverMapBuilder(context.config());
		CabinFeverMap map = mapBuilder.create();

		RuntimeLevelConfig worldConfig = new RuntimeLevelConfig()
				.setDimensionType(Fantasy.DEFAULT_OVERWORLD_DIM_TYPE)
			.setGenerator(map.createGenerator(context.server()))
			.setClockTime(WorldClocks.OVERWORLD, 18000);

		return context.openWithLevel(worldConfig, (activity, world) -> {
			world.setRainLevel(1);
			world.getWeatherData().setRaining(true);
			world.getWeatherData().setClearWeatherTime(Integer.MAX_VALUE);
			CabinFeverWaitingPhase phase = new CabinFeverWaitingPhase(activity.getGameSpace(), world, map, context.config());
			GameWaitingLobby.addTo(activity, context.config().getPlayerConfig());

			CabinFeverActivePhase.setRules(activity);
			activity.deny(GameRuleType.PVP);

			// Listeners
			activity.listen(PlayerDamageEvent.EVENT, phase::onPlayerDamage);
			activity.listen(PlayerDeathEvent.EVENT, phase::onPlayerDeath);
			activity.listen(GameActivityEvents.ENABLE, phase::enable);
			activity.listen(GamePlayerEvents.ACCEPT, phase::onAcceptPlayers);
			activity.listen(GamePlayerEvents.OFFER, JoinOffer::accept);
			activity.listen(GameActivityEvents.REQUEST_START, phase::requestStart);
		});
	}

	private JoinAcceptorResult onAcceptPlayers(JoinAcceptor acceptor) {
		return acceptor.teleport(this.world, this.map.getSpawn()).thenRunForEach(player -> {
			player.setGameMode(GameType.ADVENTURE);
		});
	}

	private GameResult requestStart() {
		CabinFeverActivePhase.open(this.gameSpace, this.world, this.map, this.config, this.guideText);
		return GameResult.ok();
	}

	private EventResult onPlayerDamage(ServerPlayer player, DamageSource source, float amount) {
		return EventResult.DENY;
	}

	private EventResult onPlayerDeath(ServerPlayer player, DamageSource source) {
		CabinFeverActivePhase.spawn(this.world, this.map, player);
		return EventResult.DENY;
	}

	private void enable() {
		TextDisplayElement element = new TextDisplayElement(GUIDE_TEXT);

		element.setBillboardMode(BillboardConstraints.CENTER);
		element.setLineWidth(350);

		ElementHolder holder = new ElementHolder();
		holder.addElement(element);

		// Spawn guide text
		Vec3 center = Vec3.atLowerCornerOf(this.map.getCenter()).add(0.5, 1, 0.5);
		this.guideText = ChunkAttachment.of(holder, world, center);
	}
}