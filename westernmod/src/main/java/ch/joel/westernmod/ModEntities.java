package ch.joel.westernmod;

import ch.joel.westernmod.entity.BanditEntity;
import ch.joel.westernmod.entity.BanditLeaderEntity;
import ch.joel.westernmod.entity.BulletEntity;
import ch.joel.westernmod.entity.CowboyEntity;
import ch.joel.westernmod.entity.DynamiteEntity;
import ch.joel.westernmod.entity.GunslingerEntity;
import ch.joel.westernmod.entity.SheriffEntity;
import ch.joel.westernmod.entity.TumbleweedEntity;
import ch.joel.westernmod.entity.WagonEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.world.Heightmap;

public final class ModEntities {

	public static final EntityType<CowboyEntity> COWBOY = Registry.register(Registries.ENTITY_TYPE,
			WesternMod.id("cowboy"),
			FabricEntityType.Builder.createMob(CowboyEntity::new, SpawnGroup.CREATURE,
							mob -> mob.defaultAttributes(GunslingerEntity::createGunslingerAttributes))
					.dimensions(0.6f, 1.95f)
					.maxTrackingRange(10)
					.build("cowboy"));

	public static final EntityType<SheriffEntity> SHERIFF = Registry.register(Registries.ENTITY_TYPE,
			WesternMod.id("sheriff"),
			FabricEntityType.Builder.createMob(SheriffEntity::new, SpawnGroup.CREATURE,
							mob -> mob.defaultAttributes(SheriffEntity::createSheriffAttributes))
					.dimensions(0.6f, 1.95f)
					.maxTrackingRange(10)
					.build("sheriff"));

	public static final EntityType<BanditEntity> BANDIT = Registry.register(Registries.ENTITY_TYPE,
			WesternMod.id("bandit"),
			FabricEntityType.Builder.createMob(BanditEntity::new, SpawnGroup.MONSTER,
							mob -> mob.defaultAttributes(BanditEntity::createBanditAttributes)
									.spawnRestriction(SpawnLocationTypes.ON_GROUND,
											Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
											BanditEntity::canSpawnBandit))
					.dimensions(0.6f, 1.95f)
					.maxTrackingRange(10)
					.build("bandit"));

	public static final EntityType<BanditLeaderEntity> BANDIT_LEADER = Registry.register(Registries.ENTITY_TYPE,
			WesternMod.id("bandit_leader"),
			FabricEntityType.Builder.createMob(BanditLeaderEntity::new, SpawnGroup.MONSTER,
							mob -> mob.defaultAttributes(BanditLeaderEntity::createLeaderAttributes))
					.dimensions(0.6f, 2.1f)
					.maxTrackingRange(10)
					.build("bandit_leader"));

	public static final EntityType<WagonEntity> WAGON = Registry.register(Registries.ENTITY_TYPE,
			WesternMod.id("wagon"),
			FabricEntityType.Builder.createMob(WagonEntity::new, SpawnGroup.CREATURE,
							mob -> mob.defaultAttributes(WagonEntity::createWagonAttributes))
					.dimensions(1.8f, 1.9f)
					.maxTrackingRange(10)
					.build("wagon"));

	public static final EntityType<TumbleweedEntity> TUMBLEWEED = Registry.register(Registries.ENTITY_TYPE,
			WesternMod.id("tumbleweed"),
			FabricEntityType.Builder.createMob(TumbleweedEntity::new, SpawnGroup.AMBIENT,
							mob -> mob.defaultAttributes(TumbleweedEntity::createTumbleweedAttributes)
									.spawnRestriction(SpawnLocationTypes.ON_GROUND,
											Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
											TumbleweedEntity::canSpawn))
					.dimensions(0.8f, 0.8f)
					.maxTrackingRange(8)
					.build("tumbleweed"));

	public static final EntityType<BulletEntity> BULLET = Registry.register(Registries.ENTITY_TYPE,
			WesternMod.id("bullet"),
			EntityType.Builder.<BulletEntity>create(BulletEntity::new, SpawnGroup.MISC)
					.dimensions(0.2f, 0.2f)
					.maxTrackingRange(8)
					.build("bullet"));

	public static final EntityType<DynamiteEntity> DYNAMITE = Registry.register(Registries.ENTITY_TYPE,
			WesternMod.id("dynamite"),
			EntityType.Builder.<DynamiteEntity>create(DynamiteEntity::new, SpawnGroup.MISC)
					.dimensions(0.25f, 0.25f)
					.maxTrackingRange(8)
					.build("dynamite"));

	private ModEntities() {
	}

	/** Löst die statische Initialisierung aus. */
	public static void register() {
	}
}
