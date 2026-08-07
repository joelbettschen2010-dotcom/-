package com.f47mod.entity.projectile;

import com.f47mod.util.Iff;
import com.f47mod.util.Team;
import com.f47mod.util.TeamMember;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Gemeinsame Grundlage aller Geschosse des Mods. Jedes Geschoss traegt die
 * Partei seines Schuetzen mit sich - so trifft eine Rakete nie die eigene
 * Seite, selbst wenn der Schuetze inzwischen abgeschossen wurde.
 */
public abstract class TeamProjectileEntity extends ProjectileEntity implements TeamMember {
	private static final TrackedData<Integer> TEAM =
			DataTracker.registerData(TeamProjectileEntity.class, TrackedDataHandlerRegistry.INTEGER);

	protected TeamProjectileEntity(EntityType<? extends ProjectileEntity> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(TEAM, Team.BLUE.ordinal());
	}

	@Override
	public Team getTeam() {
		return Team.byOrdinal(dataTracker.get(TEAM));
	}

	@Override
	public void setTeam(Team team) {
		dataTracker.set(TEAM, team.ordinal());
	}

	/** Uebernimmt die Partei des Schuetzen. */
	public void inheritTeam(@Nullable Entity shooter) {
		Team team = Iff.teamOf(shooter);
		if (team != null) {
			setTeam(team);
		}
	}

	@Override
	public void setOwner(@Nullable Entity owner) {
		super.setOwner(owner);
		inheritTeam(owner);
	}

	/** Darf dieses Geschoss die Entity treffen? Eigene Einheiten nie. */
	protected boolean isValidHit(Entity entity) {
		return entity != getOwner() && Iff.isEnemy(getTeam(), entity);
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		setTeam(Team.byOrdinal(nbt.getInt("Team")));
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("Team", getTeam().ordinal());
	}

	@Override
	public boolean isFireImmune() {
		return true;
	}
}
