package io.github.haykam821.cabinfever.game.phase;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import io.github.haykam821.cabinfever.game.CabinFeverConfig;
import io.github.haykam821.cabinfever.game.map.CabinFeverMap;
import io.github.haykam821.cabinfever.game.map.CabinFeverMapBuilder;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.DisplayEntity.BillboardMode;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
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
	private static final Formatting GUIDE_FORMATTING = Formatting.GOLD;
	private static final Text GUIDE_TEXT = Text.empty()
		.append(Text.translatable("gameType.cabinfever.cabin_fever").formatted(Formatting.BOLD))
		.append(ScreenTexts.LINE_BREAK)
		.append(Text.translatable("text.cabinfever.guide"))
		.formatted(GUIDE_FORMATTING);

	private final GameSpace gameSpace;
	private final ServerWorld world;
	private final CabinFeverMap map;
	private final CabinFeverConfig config;
	private HolderAttachment guideText;

	public CabinFeverWaitingPhase(GameSpace gameSpace, ServerWorld world, CabinFeverMap map, CabinFeverConfig config) {
		this.gameSpace = gameSpace;
		this.world = world;
		this.map = map;
		this.config = config;
	}

	public static GameOpenProcedure open(GameOpenContext<CabinFeverConfig> context) {
		CabinFeverMapBuilder mapBuilder = new CabinFeverMapBuilder(context.config());
		CabinFeverMap map = mapBuilder.create();

		RuntimeWorldConfig worldConfig = new RuntimeWorldConfig()
			.setGenerator(map.createGenerator(context.server()))
			.setRaining(true)
			.setTimeOfDay(18000);

		return context.openWithWorld(worldConfig, (activity, world) -> {
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
			player.changeGameMode(GameMode.ADVENTURE);
		});
	}

	private GameResult requestStart() {
		CabinFeverActivePhase.open(this.gameSpace, this.world, this.map, this.config, this.guideText);
		return GameResult.ok();
	}

	private EventResult onPlayerDamage(ServerPlayerEntity player, DamageSource source, float amount) {
		return EventResult.DENY;
	}

	private EventResult onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
		CabinFeverActivePhase.spawn(this.world, this.map, player);
		return EventResult.DENY;
	}

	private void enable() {
		TextDisplayElement element = new TextDisplayElement(GUIDE_TEXT);

		element.setBillboardMode(BillboardMode.CENTER);
		element.setLineWidth(350);

		ElementHolder holder = new ElementHolder();
		holder.addElement(element);

		// Spawn guide text
		Vec3d center = Vec3d.of(this.map.getCenter()).add(0.5, 1, 0.5);
		this.guideText = ChunkAttachment.of(holder, world, center);
	}
}