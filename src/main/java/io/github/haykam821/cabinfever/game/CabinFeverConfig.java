package io.github.haykam821.cabinfever.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.haykam821.cabinfever.game.map.CabinFeverMapConfig;
import net.minecraft.SharedConstants;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import net.minecraft.util.math.intprovider.IntProvider;
import xyz.nucleoid.plasmid.api.game.common.config.WaitingLobbyConfig;

public class CabinFeverConfig {
	public static final MapCodec<CabinFeverConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> {
		return instance.group(
			CabinFeverMapConfig.CODEC.fieldOf("map").forGetter(CabinFeverConfig::getMapConfig),
			WaitingLobbyConfig.CODEC.fieldOf("players").forGetter(CabinFeverConfig::getPlayerConfig),
			IntProvider.NON_NEGATIVE_CODEC.optionalFieldOf("ticks_until_close", ConstantIntProvider.create(SharedConstants.TICKS_PER_SECOND * 5)).forGetter(CabinFeverConfig::getTicksUntilClose),
			Codec.INT.optionalFieldOf("guide_ticks", 20 * 30).forGetter(CabinFeverConfig::getGuideTicks),
			Codec.INT.optionalFieldOf("max_coal", 32).forGetter(CabinFeverConfig::getMaxCoal),
			Codec.INT.optionalFieldOf("max_held_coal", 8).forGetter(CabinFeverConfig::getMaxHeldCoal),
			Codec.INT.optionalFieldOf("death_price", 12).forGetter(CabinFeverConfig::getDeathPrice)
		).apply(instance, CabinFeverConfig::new);
	});

	private final CabinFeverMapConfig mapConfig;
	private final WaitingLobbyConfig playerConfig;
	private final IntProvider ticksUntilClose;
	private final int guideTicks;
	private final int maxCoal;
	private final int maxHeldCoal;
	private final int deathPrice;

	public CabinFeverConfig(CabinFeverMapConfig mapConfig, WaitingLobbyConfig playerConfig, IntProvider ticksUntilClose, int guideTicks, int maxCoal, int maxHeldCoal, int deathPrice) {
		this.mapConfig = mapConfig;
		this.playerConfig = playerConfig;
		this.ticksUntilClose = ticksUntilClose;
		this.guideTicks = guideTicks;
		this.maxCoal = maxCoal;
		this.maxHeldCoal = maxHeldCoal;
		this.deathPrice = deathPrice;
	}

	public CabinFeverMapConfig getMapConfig() {
		return this.mapConfig;
	}

	public WaitingLobbyConfig getPlayerConfig() {
		return this.playerConfig;
	}

	public IntProvider getTicksUntilClose() {
		return this.ticksUntilClose;
	}

	public int getGuideTicks() {
		return this.guideTicks;
	}

	public int getMaxCoal() {
		return this.maxCoal;
	}

	public int getMaxHeldCoal() {
		return this.maxHeldCoal;
	}

	public int getDeathPrice() {
		return this.deathPrice;
	}
}