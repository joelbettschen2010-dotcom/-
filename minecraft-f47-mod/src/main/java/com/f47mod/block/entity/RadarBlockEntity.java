package com.f47mod.block.entity;

import com.f47mod.F47Config;
import com.f47mod.block.RadarBlock;
import com.f47mod.net.RadarContact;
import com.f47mod.registry.ModBlockEntities;
import com.f47mod.util.Iff;
import com.f47mod.util.Team;
import com.f47mod.util.TeamMember;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Rechenteil der Radarstation. Taste den Luftraum in festen Abstaenden ab,
 * haelt die aktuellen Kontakte vor und stellt sie anderen Systemen bereit.
 */
public class RadarBlockEntity extends BlockEntity implements TeamMember {
	/** Abstand zwischen zwei Radarumlaeufen in Ticks. */
	private static final int SCAN_INTERVAL = 20;

	private final List<RadarContact> contacts = new ArrayList<>();
	private int scanTimer;
	private Team team = Team.BLUE;
	/** Winkel der Antenne fuer die Darstellung. */
	private float sweepAngle;
	private float previousSweepAngle;

	public RadarBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.RADAR, pos, state);
	}

	public static void serverTick(World world, BlockPos pos, BlockState state, RadarBlockEntity radar) {
		if (!state.get(RadarBlock.ACTIVE)) {
			radar.contacts.clear();
			return;
		}
		if (++radar.scanTimer < SCAN_INTERVAL) {
			return;
		}
		radar.scanTimer = 0;
		radar.scan(world, pos);
	}

	public static void clientTick(World world, BlockPos pos, BlockState state, RadarBlockEntity radar) {
		radar.previousSweepAngle = radar.sweepAngle;
		if (state.get(RadarBlock.ACTIVE)) {
			radar.sweepAngle += 3.0f;
			if (radar.sweepAngle >= 360.0f) {
				radar.sweepAngle -= 360.0f;
				radar.previousSweepAngle -= 360.0f;
			}
		}
	}

	/** Ein Radarumlauf: alle Luftziele im Umkreis erfassen. */
	private void scan(World world, BlockPos pos) {
		int range = F47Config.get().radarBlockRange;
		Vec3d center = Vec3d.ofCenter(pos);
		Box box = new Box(pos).expand(range);

		List<RadarContact> found = new ArrayList<>();
		boolean newThreat = false;

		for (Entity entity : world.getOtherEntities(null, box, entity -> !(entity instanceof PlayerEntity))) {
			boolean threat = Iff.isEnemy(team, entity);
			boolean friendly = Iff.isAlly(team, entity);
			if (!threat && !friendly) {
				continue;
			}
			// Getarnte Jets tauchen erst sehr spaet auf dem Schirm auf.
			if (!Iff.canDetect(entity, center, range)) {
				continue;
			}
			// Bodenmobs ohne Flugbezug interessieren das Luftraumradar nicht.
			if (threat && !Iff.isAirThreat(team, entity) && entity.squaredDistanceTo(center) > 90.0 * 90.0) {
				continue;
			}
			found.add(RadarContact.of(entity, team));
			if (threat && !containsId(entity.getId())) {
				newThreat = true;
			}
		}

		contacts.clear();
		contacts.addAll(found);
		markDirty();

		if (newThreat && world instanceof ServerWorld server) {
			// Alarm: kurzer Warnton und ein Signal ueber der Antenne.
			world.playSound(null, pos, SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 1.4f, 0.8f);
			server.spawnParticles(ParticleTypes.END_ROD,
					pos.getX() + 0.5, pos.getY() + 1.6, pos.getZ() + 0.5, 10, 0.2, 0.2, 0.2, 0.03);
		}
	}

	private boolean containsId(int entityId) {
		for (RadarContact contact : contacts) {
			if (contact.entityId() == entityId) {
				return true;
			}
		}
		return false;
	}

	@Override
	public Team getTeam() {
		return team;
	}

	@Override
	public void setTeam(Team team) {
		this.team = team;
		markDirty();
	}

	public List<RadarContact> getContacts() {
		return Collections.unmodifiableList(contacts);
	}

	public int getContactCount() {
		return contacts.size();
	}

	public int getThreatCount() {
		return (int) contacts.stream().filter(RadarContact::hostile).count();
	}

	public float getSweepAngle(float tickDelta) {
		return previousSweepAngle + (sweepAngle - previousSweepAngle) * tickDelta;
	}

	public boolean isActive() {
		return getCachedState().get(RadarBlock.ACTIVE);
	}

	/**
	 * Naechstgelegene aktive Radarstation zu einem Punkt. Iron-Dome-Stellungen
	 * nutzen das, um ihre eigene Reichweite zu erweitern.
	 */
	public static RadarBlockEntity findNearby(World world, BlockPos pos, int searchRadius) {
		return RadarRegistry.findNearby(world, pos, searchRadius);
	}

	@Override
	public void setWorld(World world) {
		super.setWorld(world);
		RadarRegistry.add(world, getPos());
	}

	@Override
	public void markRemoved() {
		super.markRemoved();
		if (world != null) {
			RadarRegistry.remove(world, getPos());
		}
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.putInt("ScanTimer", scanTimer);
		nbt.putInt("Team", team.ordinal());
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		scanTimer = nbt.getInt("ScanTimer");
		team = Team.byOrdinal(nbt.getInt("Team"));
	}
}
