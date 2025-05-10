package io.github.haykam821.cabinfever;

import io.github.haykam821.cabinfever.game.CabinFeverConfig;
import io.github.haykam821.cabinfever.game.phase.CabinFeverWaitingPhase;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import xyz.nucleoid.plasmid.api.game.GameType;

public class Main implements ModInitializer {
	private static final String MOD_ID = "cabinfever";

	private static final Identifier CABIN_FEVER_ID = Main.identifier("cabin_fever");
	public static final GameType<CabinFeverConfig> CABIN_FEVER_TYPE = GameType.register(CABIN_FEVER_ID, CabinFeverConfig.CODEC, CabinFeverWaitingPhase::open);

	@Override
	public void onInitialize() {
		return;
	}

	public static Identifier identifier(String path) {
		return Identifier.of(MOD_ID, path);
	}
}