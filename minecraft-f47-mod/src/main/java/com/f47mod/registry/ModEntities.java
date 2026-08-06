package com.f47mod.registry;

import com.f47mod.F47Mod;
import com.f47mod.entity.mob.EnemyDroneEntity;
import com.f47mod.entity.mob.SoldierEntity;
import com.f47mod.entity.projectile.BombEntity;
import com.f47mod.entity.projectile.BulletEntity;
import com.f47mod.entity.projectile.MissileEntity;
import com.f47mod.entity.projectile.PlasmaBoltEntity;
import com.f47mod.entity.vehicle.AutonomousF47Entity;
import com.f47mod.entity.vehicle.F47Entity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** Registrierung aller Entities: Flugzeuge, Truppen und Geschosse. */
public final class ModEntities {
	private ModEntities() {
	}

	public static final EntityType<F47Entity> F47 = register("f47",
			EntityType.Builder.<F47Entity>create(F47Entity::new, SpawnGroup.MISC)
					.dimensions(2.6f, 1.4f)
					// Jets sind schnell - deshalb grosser Sichtbereich und Update jeden Tick.
					.maxTrackingRange(16)
					.trackingTickInterval(1));

	public static final EntityType<AutonomousF47Entity> AUTONOMOUS_F47 = register("autonomous_f47",
			EntityType.Builder.<AutonomousF47Entity>create(AutonomousF47Entity::new, SpawnGroup.MISC)
					.dimensions(2.6f, 1.4f)
					.maxTrackingRange(16)
					.trackingTickInterval(1));

	public static final EntityType<SoldierEntity> SOLDIER = register("soldier",
			EntityType.Builder.create(SoldierEntity::new, SpawnGroup.CREATURE)
					.dimensions(0.6f, 1.95f)
					.maxTrackingRange(10));

	public static final EntityType<EnemyDroneEntity> ENEMY_DRONE = register("enemy_drone",
			EntityType.Builder.create(EnemyDroneEntity::new, SpawnGroup.MONSTER)
					.dimensions(1.4f, 0.7f)
					.maxTrackingRange(12));

	public static final EntityType<MissileEntity> MISSILE = register("missile",
			EntityType.Builder.<MissileEntity>create(MissileEntity::new, SpawnGroup.MISC)
					.dimensions(0.4f, 0.4f)
					.maxTrackingRange(12)
					.trackingTickInterval(1));

	public static final EntityType<BulletEntity> BULLET = register("bullet",
			EntityType.Builder.<BulletEntity>create(BulletEntity::new, SpawnGroup.MISC)
					.dimensions(0.2f, 0.2f)
					.maxTrackingRange(8)
					.trackingTickInterval(1));

	public static final EntityType<BombEntity> BOMB = register("bomb",
			EntityType.Builder.<BombEntity>create(BombEntity::new, SpawnGroup.MISC)
					.dimensions(0.5f, 0.5f)
					.maxTrackingRange(10)
					.trackingTickInterval(2));

	public static final EntityType<PlasmaBoltEntity> PLASMA_BOLT = register("plasma_bolt",
			EntityType.Builder.<PlasmaBoltEntity>create(PlasmaBoltEntity::new, SpawnGroup.MISC)
					.dimensions(0.3f, 0.3f)
					.maxTrackingRange(8)
					.trackingTickInterval(1));

	private static <T extends net.minecraft.entity.Entity> EntityType<T> register(
			String name, EntityType.Builder<T> builder) {
		return Registry.register(Registries.ENTITY_TYPE, F47Mod.id(name), builder.build(name));
	}

	/** Attribute der lebenden Entities anmelden. */
	public static void registerAttributes() {
		FabricDefaultAttributeRegistry.register(SOLDIER, SoldierEntity.createSoldierAttributes());
		FabricDefaultAttributeRegistry.register(ENEMY_DRONE, EnemyDroneEntity.createDroneAttributes());
	}

	public static void init() {
	}
}
