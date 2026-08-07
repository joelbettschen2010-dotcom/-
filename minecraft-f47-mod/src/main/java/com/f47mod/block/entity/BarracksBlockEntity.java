package com.f47mod.block.entity;

import com.f47mod.entity.mob.SoldierEntity;
import com.f47mod.entity.mob.SoldierRole;
import com.f47mod.registry.ModBlockEntities;
import com.f47mod.registry.ModEntities;
import com.f47mod.util.Team;
import com.f47mod.util.TeamMember;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Ausbildungsbetrieb der Kaserne: verbraucht Nachschub und stellt in festen
 * Abstaenden neue Soldaten auf, solange der Trupp noch nicht voll ist.
 */
public class BarracksBlockEntity extends BlockEntity implements TeamMember {
	public static final int MAX_SUPPLIES = 64;
	public static final int MAX_SQUAD = 12;
	/** Ticks pro Ausbildung (etwa 30 Sekunden). */
	private static final int TRAINING_TICKS = 600;
	/** Eisenbarren pro Soldat. */
	private static final int COST = 4;

	private int supplies;
	private int progress;
	private SoldierRole trainingRole = SoldierRole.RIFLEMAN;
	@Nullable
	private UUID ownerUuid;
	private Team team = Team.BLUE;

	public BarracksBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.BARRACKS, pos, state);
	}

	public static void serverTick(World world, BlockPos pos, BlockState state, BarracksBlockEntity barracks) {
		if (barracks.supplies < COST) {
			return;
		}
		if (barracks.countSquad(world, pos) >= MAX_SQUAD) {
			// Trupp ist vollzaehlig - Ausbildung pausiert.
			barracks.progress = 0;
			return;
		}
		if (++barracks.progress < TRAINING_TICKS) {
			if (barracks.progress % 40 == 0 && world instanceof ServerWorld server) {
				server.spawnParticles(ParticleTypes.SMOKE,
						pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 2, 0.3, 0.1, 0.3, 0.01);
			}
			return;
		}
		barracks.progress = 0;
		barracks.supplies -= COST;
		barracks.deploy(world, pos);
		barracks.markDirty();
	}

	/** Stellt einen fertig ausgebildeten Soldaten vor der Kaserne auf. */
	private void deploy(World world, BlockPos pos) {
		SoldierEntity soldier = ModEntities.SOLDIER.create(world);
		if (soldier == null) {
			return;
		}
		BlockPos spawn = pos.up();
		soldier.refreshPositionAndAngles(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
				world.getRandom().nextFloat() * 360.0f, 0.0f);
		soldier.setTeam(team);
		soldier.setRole(trainingRole);
		soldier.setStance(SoldierEntity.Stance.PATROL);
		soldier.setGuardPost(pos);

		PlayerEntity owner = ownerUuid == null ? null : world.getPlayerByUuid(ownerUuid);
		if (owner != null) {
			soldier.setOwner(owner);
			owner.sendMessage(Text.translatable("message.f47.soldier_ready",
					Text.translatable(trainingRole.translationKey())), false);
		}
		if (soldier.getWorld() instanceof ServerWorld server) {
			soldier.initialize(server, world.getLocalDifficulty(spawn), SpawnReason.TRIGGERED, null);
		}
		world.spawnEntity(soldier);
		world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.6f, 1.2f);
	}

	/** Wie viele eigene Soldaten stehen schon im Umkreis? */
	private int countSquad(World world, BlockPos pos) {
		Box box = new Box(pos).expand(48.0);
		return world.getEntitiesByClass(SoldierEntity.class, box, soldier -> soldier.getTeam() == team).size();
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

	public int addSupplies(int count) {
		int accepted = Math.min(MAX_SUPPLIES - supplies, count);
		if (accepted > 0) {
			supplies += accepted;
			markDirty();
		}
		return accepted;
	}

	public int getSupplies() {
		return supplies;
	}

	public int getSquadSize() {
		return world == null ? 0 : countSquad(world, getPos());
	}

	public SoldierRole getTrainingRole() {
		return trainingRole;
	}

	public SoldierRole cycleRole() {
		SoldierRole[] values = SoldierRole.values();
		trainingRole = values[(trainingRole.ordinal() + 1) % values.length];
		progress = 0;
		markDirty();
		return trainingRole;
	}

	public void setOwner(PlayerEntity player) {
		ownerUuid = player.getUuid();
		markDirty();
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.putInt("Supplies", supplies);
		nbt.putInt("Progress", progress);
		nbt.putInt("Role", trainingRole.ordinal());
		nbt.putInt("Team", team.ordinal());
		if (ownerUuid != null) {
			nbt.putUuid("Owner", ownerUuid);
		}
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		supplies = nbt.getInt("Supplies");
		progress = nbt.getInt("Progress");
		trainingRole = SoldierRole.byOrdinal(nbt.getInt("Role"));
		team = Team.byOrdinal(nbt.getInt("Team"));
		if (nbt.containsUuid("Owner")) {
			ownerUuid = nbt.getUuid("Owner");
		}
	}
}
