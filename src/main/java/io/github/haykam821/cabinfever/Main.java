package io.github.haykam821.cabinfever;

import io.github.haykam821.cabinfever.game.CabinFeverConfig;
import io.github.haykam821.cabinfever.game.phase.CabinFeverWaitingPhase;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import xyz.nucleoid.plasmid.api.game.GameType;
import xyz.nucleoid.plasmid.api.game.GameTypes;

public class Main implements ModInitializer {
	private static final String MOD_ID = "cabinfever";

	private static final Identifier CABIN_FEVER_ID = Main.identifier("cabin_fever");
	public static final GameType<CabinFeverConfig> CABIN_FEVER_TYPE = GameTypes.register(CABIN_FEVER_ID, CabinFeverConfig.CODEC, CabinFeverWaitingPhase::open);

	@Override
	public void onInitialize() {
		return;
	}

	public static Identifier identifier(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}