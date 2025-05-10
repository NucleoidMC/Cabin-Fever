package io.github.haykam821.cabinfever.game.map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.plasmid.api.game.world.generator.TemplateChunkGenerator;

public class CabinFeverMap {
	private final MapTemplate template;
	private final BlockPos center;
	private final Vec3d spawn;

	public CabinFeverMap(MapTemplate template, BlockPos center) {
		this.template = template;
		this.center = center;
		this.spawn = new Vec3d(this.center.getX() + 0.5, 1, this.center.getZ() + 1.5);
	}

	public BlockPos getCenter() {
		return this.center;
	}

	public Vec3d getSpawn() {
		return this.spawn;
	}

	public ChunkGenerator createGenerator(MinecraftServer server) {
		return new TemplateChunkGenerator(server, this.template);
	}
}