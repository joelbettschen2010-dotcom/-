package com.f47mod.registry;

import com.f47mod.F47Mod;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Eigene Schadensarten - sorgt fuer passende Todesmeldungen wie
 * "... wurde von einer Lenkwaffe getroffen" statt eines generischen Textes.
 */
public final class ModDamage {
	private ModDamage() {
	}

	public static final RegistryKey<DamageType> LASER =
			RegistryKey.of(RegistryKeys.DAMAGE_TYPE, F47Mod.id("laser"));
	public static final RegistryKey<DamageType> MISSILE =
			RegistryKey.of(RegistryKeys.DAMAGE_TYPE, F47Mod.id("missile"));
	public static final RegistryKey<DamageType> BULLET =
			RegistryKey.of(RegistryKeys.DAMAGE_TYPE, F47Mod.id("bullet"));

	public static DamageSource laser(World world, @Nullable Entity attacker) {
		return of(world, LASER, null, attacker);
	}

	public static DamageSource missile(World world, @Nullable Entity source, @Nullable Entity attacker) {
		return of(world, MISSILE, source, attacker);
	}

	public static DamageSource bullet(World world, @Nullable Entity source, @Nullable Entity attacker) {
		return of(world, BULLET, source, attacker);
	}

	private static DamageSource of(World world, RegistryKey<DamageType> key,
			@Nullable Entity source, @Nullable Entity attacker) {
		return new DamageSource(
				world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(key), source, attacker);
	}
}
