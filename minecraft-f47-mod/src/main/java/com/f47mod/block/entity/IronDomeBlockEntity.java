package com.f47mod.block.entity;

import com.f47mod.F47Config;
import com.f47mod.entity.projectile.MissileEntity;
import com.f47mod.registry.ModBlockEntities;
import com.f47mod.util.Iff;
import com.f47mod.util.Team;
import com.f47mod.util.TeamMember;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Feuerleitrechner der Iron-Dome-Stellung.
 *
 * <p>Der Ablauf entspricht dem echten Vorbild: Ziele erfassen, bewerten, ob sie
 * ueberhaupt gefaehrlich werden, und nur dann eine Abfangrakete starten. Auf
 * bereits bekaempfte Ziele wird kein zweites Mal geschossen.
 */
public class IronDomeBlockEntity extends BlockEntity implements TeamMember {
	public static final int MAX_AMMO = 32;
	/** Ticks zwischen zwei Starts. */
	private static final int RELOAD_TICKS = 25;
	private static final int SCAN_INTERVAL = 5;

	private int ammo;
	private int cooldown;
	private int scanTimer;
	private int interceptCount;
	private Team team = Team.BLUE;
	/** Ziele, auf die bereits eine Abfangrakete unterwegs ist. */
	private final Set<Integer> engaged = new HashSet<>();

	public IronDomeBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.IRON_DOME, pos, state);
	}

	public static void serverTick(World world, BlockPos pos, BlockState state, IronDomeBlockEntity dome) {
		if (dome.cooldown > 0) {
			dome.cooldown--;
		}
		if (++dome.scanTimer < SCAN_INTERVAL) {
			return;
		}
		dome.scanTimer = 0;
		dome.engaged.removeIf(id -> {
			Entity entity = world.getEntityById(id);
			return entity == null || !entity.isAlive();
		});

		if (dome.ammo <= 0 || dome.cooldown > 0) {
			return;
		}
		dome.engage(world, pos);
	}

	/** Sucht das gefaehrlichste Ziel und startet eine Abfangrakete. */
	private void engage(World world, BlockPos pos) {
		int range = F47Config.get().ironDomeRange;
		// Eine Radarstation in der Naehe vergroessert die Reichweite deutlich.
		RadarBlockEntity radar = RadarBlockEntity.findNearby(world, pos, 48);
		if (radar != null) {
			range = (int) (range * 1.6);
		}

		Vec3d origin = Vec3d.ofCenter(pos).add(0, 0.8, 0);
		Box box = new Box(pos).expand(range);
		List<Entity> threats = world.getOtherEntities(null, box, entity -> Iff.isAirThreat(team, entity));

		Entity best = null;
		int bestPriority = Integer.MIN_VALUE;
		double bestDistance = Double.MAX_VALUE;

		for (Entity threat : threats) {
			if (engaged.contains(threat.getId())) {
				continue;
			}
			if (!Iff.canDetect(threat, origin, range)) {
				continue;
			}
			// Ziele, die sich entfernen, sind keine Gefahr mehr.
			if (isMovingAway(threat, origin)) {
				continue;
			}
			int priority = Iff.threatPriority(threat);
			double distance = threat.squaredDistanceTo(origin);
			if (priority > bestPriority || (priority == bestPriority && distance < bestDistance)) {
				bestPriority = priority;
				bestDistance = distance;
				best = threat;
			}
		}

		if (best == null) {
			return;
		}
		launch(world, origin, best);
	}

	/** Entfernt sich das Ziel bereits wieder von der Stellung? */
	private boolean isMovingAway(Entity threat, Vec3d origin) {
		Vec3d velocity = threat.getVelocity();
		if (velocity.lengthSquared() < 0.01) {
			return false;
		}
		Vec3d toDome = origin.subtract(threat.getPos());
		return velocity.normalize().dotProduct(toDome.normalize()) < -0.35;
	}

	private void launch(World world, Vec3d origin, Entity target) {
		ammo--;
		cooldown = RELOAD_TICKS;
		interceptCount++;
		engaged.add(target.getId());
		markDirty();

		MissileEntity interceptor = new MissileEntity(world, null, MissileEntity.Kind.INTERCEPTOR);
		interceptor.setTeam(team);
		interceptor.setPosition(origin.x, origin.y + 0.6, origin.z);
		interceptor.setTarget(target);
		// Erst senkrecht heraus, dann dreht der Suchkopf auf das Ziel.
		Vec3d aim = target.getPos().subtract(origin).normalize().add(0, 1.4, 0);
		interceptor.launch(aim);
		world.spawnEntity(interceptor);

		world.playSound(null, getPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH,
				SoundCategory.BLOCKS, 2.0f, 0.6f);
		if (world instanceof ServerWorld server) {
			server.spawnParticles(ParticleTypes.CLOUD,
					origin.x, origin.y + 0.5, origin.z, 20, 0.4, 0.2, 0.4, 0.06);
		}
	}

	/** Munition einfuellen. Gibt zurueck, wie viel tatsaechlich passte. */
	public int load(int count) {
		int space = MAX_AMMO - ammo;
		int loaded = Math.min(space, count);
		if (loaded > 0) {
			ammo += loaded;
			markDirty();
			if (world != null) {
				world.playSound(null, getPos(), SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE,
						SoundCategory.BLOCKS, 0.8f, 1.3f);
			}
		}
		return loaded;
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

	public int getAmmo() {
		return ammo;
	}

	public int getInterceptCount() {
		return interceptCount;
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.putInt("Ammo", ammo);
		nbt.putInt("Cooldown", cooldown);
		nbt.putInt("Intercepts", interceptCount);
		nbt.putInt("Team", team.ordinal());
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		ammo = nbt.getInt("Ammo");
		cooldown = nbt.getInt("Cooldown");
		interceptCount = nbt.getInt("Intercepts");
		team = Team.byOrdinal(nbt.getInt("Team"));
	}
}
