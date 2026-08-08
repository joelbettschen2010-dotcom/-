package com.f47mod.block.entity;

import com.f47mod.entity.mob.SoldierEntity;
import com.f47mod.entity.mob.SoldierRole;
import com.f47mod.entity.vehicle.AutonomousF47Entity;
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
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Ausbildungsbetrieb der Kaserne: verbraucht Nachschub und stellt in festen
 * Abstaenden neue Soldaten auf, solange der Trupp noch nicht voll ist.
 */
public class BarracksBlockEntity extends BlockEntity implements TeamMember {
	public static final int MAX_SUPPLIES = 64;
	public static final int MAX_SQUAD = 20;
	/** Ticks pro Ausbildung (etwa 30 Sekunden). */
	private static final int TRAINING_TICKS = 600;
	/** Eisenbarren pro Soldat. */
	private static final int COST = 4;
	/** Umkreis, in dem die Kaserne ihre eigenen Einheiten zaehlt. */
	private static final double RANGE = 64.0;

	/** Ticks, bis von selbst ein Barren Nachschub eintrifft. */
	private static final int SUPPLY_TICKS = 160;
	/** Sollstaerke der Staffel - darunter wird Ersatz gebaut. */
	private static final int SQUADRON_TARGET = 6;
	/** Eisenbarren fuer eine Ersatzmaschine. */
	private static final int JET_COST = 20;
	/** Ticks pro Ersatzmaschine (etwa 90 Sekunden). */
	private static final int JET_TICKS = 1800;

	private int supplies;
	private int progress;
	/** Fortschritt am Ersatzflugzeug, unabhaengig von der Ausbildung. */
	private int jetProgress;
	/** Uhr fuer den nachrueckenden Nachschub. */
	private int supplyTimer;
	private SoldierRole trainingRole = SoldierRole.RIFLEMAN;
	/**
	 * Legt der Spieler die Rolle von Hand fest, mischt sich die Kaserne nicht
	 * mehr ein - sonst waere jedes Umstellen am Kommandopult nach dreissig
	 * Sekunden wieder ueberschrieben.
	 */
	private boolean roleChosenByHand;
	@Nullable
	private UUID ownerUuid;
	/** Wo die Bahn liegt - dort werden Ersatzmaschinen abgestellt. */
	@Nullable
	private BlockPos homeBase;
	/** Richtung der Bahn in Grad, damit Ersatz richtig herum steht. */
	private float homeHeading;
	private Team team = Team.BLUE;

	public BarracksBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.BARRACKS, pos, state);
	}

	public static void serverTick(World world, BlockPos pos, BlockState state, BarracksBlockEntity barracks) {
		barracks.replenish();
		barracks.rebuildSquadron(world, pos);
		barracks.trainSoldier(world, pos);
	}

	/**
	 * Nachschub rueckt von selbst nach.
	 *
	 * <p>Ohne das ist die Basis nach sechzehn Ausbildungen erschoepft und
	 * erholt sich nie wieder von einem Angriff - genau das, was sie eigentlich
	 * leisten soll. Der Zulauf ist langsam genug, dass Verluste weh tun.
	 */
	private void replenish() {
		if (supplies >= MAX_SUPPLIES) {
			return;
		}
		if (++supplyTimer >= SUPPLY_TICKS) {
			supplyTimer = 0;
			supplies++;
			markDirty();
		}
	}

	/** Bildet Ersatz aus - und zwar in der Rolle, die der Basis gerade fehlt. */
	private void trainSoldier(World world, BlockPos pos) {
		if (supplies < COST) {
			return;
		}
		if (countSquad(world, pos) >= MAX_SQUAD) {
			// Trupp ist vollzaehlig - Ausbildung pausiert.
			progress = 0;
			return;
		}
		if (!roleChosenByHand) {
			trainingRole = neededRole(world, pos);
		}
		if (++progress < TRAINING_TICKS) {
			if (progress % 40 == 0 && world instanceof ServerWorld server) {
				server.spawnParticles(ParticleTypes.SMOKE,
						pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 2, 0.3, 0.1, 0.3, 0.01);
			}
			return;
		}
		progress = 0;
		supplies -= COST;
		deploy(world, pos);
		markDirty();
	}

	/**
	 * Welche Rolle fehlt am dringendsten?
	 *
	 * <p>Der Reihe nach: Erst muessen leere Maschinen einen Piloten bekommen -
	 * ein Jet ohne Besatzung steht nur herum. Danach Technik und Sanitaet,
	 * damit Beschaedigtes wieder flott wird, dann die Panzerabwehr, und was
	 * uebrig bleibt, wird Schuetze.
	 */
	private SoldierRole neededRole(World world, BlockPos pos) {
		Box box = new Box(pos).expand(RANGE);
		long emptyJets = world.getEntitiesByClass(AutonomousF47Entity.class, box,
				jet -> jet.getTeam() == team && !jet.hasPilot()).size();
		if (emptyJets > countRole(world, pos, SoldierRole.PILOT)) {
			return SoldierRole.PILOT;
		}
		if (countRole(world, pos, SoldierRole.ENGINEER) < 3) {
			return SoldierRole.ENGINEER;
		}
		if (countRole(world, pos, SoldierRole.MEDIC) < 2) {
			return SoldierRole.MEDIC;
		}
		if (countRole(world, pos, SoldierRole.HEAVY) < 3) {
			return SoldierRole.HEAVY;
		}
		return SoldierRole.RIFLEMAN;
	}

	private int countRole(World world, BlockPos pos, SoldierRole role) {
		Box box = new Box(pos).expand(RANGE);
		return world.getEntitiesByClass(SoldierEntity.class, box,
				soldier -> soldier.getTeam() == team && soldier.getRole() == role).size();
	}

	/**
	 * Baut abgeschossene Maschinen nach, bis die Staffel wieder vollzaehlig
	 * ist. Die neue steht unbemannt auf dem Vorfeld - einen Piloten holt sie
	 * sich selbst, sobald die Kaserne einen ausgebildet hat.
	 */
	private void rebuildSquadron(World world, BlockPos pos) {
		if (supplies < JET_COST) {
			return;
		}
		if (countSquadron(world, pos) >= SQUADRON_TARGET) {
			jetProgress = 0;
			return;
		}
		if (++jetProgress < JET_TICKS) {
			return;
		}
		jetProgress = 0;
		supplies -= JET_COST;
		deployJet(world, pos);
		markDirty();
	}

	/** Stellt eine fertige Ersatzmaschine auf dem Vorfeld ab. */
	private void deployJet(World world, BlockPos pos) {
		AutonomousF47Entity jet = ModEntities.AUTONOMOUS_F47.create(world);
		if (jet == null) {
			return;
		}
		BlockPos field = homeBase != null ? homeBase : pos;
		Vec3d spot = findParkingSpot(world, field);
		if (spot == null) {
			// Alles voll - dann eben beim naechsten Anlauf.
			jetProgress = JET_TICKS - 100;
			return;
		}
		// Nase in Bahnrichtung, damit sie ohne Wendemanoever losrollen kann.
		jet.refreshPositionAndAngles(spot.x, spot.y, spot.z, homeHeading, 0.0f);
		jet.setTeam(team);
		jet.setHomeBase(field, homeHeading);
		jet.randomiseCallsign();

		PlayerEntity owner = ownerUuid == null ? null : world.getPlayerByUuid(ownerUuid);
		if (owner != null) {
			jet.setOwner(owner);
			owner.sendMessage(Text.translatable("message.f47.jet_ready", jet.getCallsign()), false);
		}
		world.spawnEntity(jet);
		world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.8f, 0.7f);
	}

	/**
	 * Sucht einen Stellplatz, auf dem die Maschine wirklich frei steht.
	 *
	 * <p>Notwendig, weil sie vier Bloecke breit ist: Ein fester Punkt neben
	 * dem Heimatflugplatz landet frueher oder spaeter in einer Hangarwand oder
	 * in einer schon abgestellten Maschine. Wer eingeklemmt startet, loest bei
	 * jedem Tick eine Kollision aus und kommt nie auf Fahrt.
	 *
	 * @return Mittelpunkt eines freien Platzes oder {@code null}
	 */
	@Nullable
	private Vec3d findParkingSpot(World world, BlockPos field) {
		// Vom Flugplatz aus in beide Richtungen der Bahn entlang tasten.
		for (int step = 0; step <= 84; step += 6) {
			for (int sign = 1; sign >= -1; sign -= 2) {
				for (Direction along : new Direction[] {Direction.NORTH, Direction.EAST}) {
					BlockPos candidate = field.offset(along, step * sign).up();
					Vec3d centre = new Vec3d(candidate.getX() + 0.5, candidate.getY(),
							candidate.getZ() + 0.5);
					Box box = ModEntities.AUTONOMOUS_F47.getSpawnBox(centre.x, centre.y, centre.z);
					if (world.isSpaceEmpty(box)
							&& world.getEntitiesByClass(AutonomousF47Entity.class, box, jet -> true).isEmpty()) {
						return centre;
					}
				}
				if (step == 0) {
					// Der Ausgangspunkt selbst muss nur einmal geprueft werden.
					break;
				}
			}
		}
		return null;
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

	/**
	 * Wie viele Maschinen gehoeren zu dieser Basis?
	 *
	 * <p>Gezaehlt wird nach Heimatflugplatz, nicht nach Entfernung: Eine
	 * patrouillierende Maschine ist fuenfzig Bloecke weit weg und eine im
	 * Angriff noch weiter. Wer nur die Umgebung absucht, haelt die halbe
	 * Staffel fuer abgeschossen und baut immer weiter nach - im Versuch
	 * standen so nach fuenf Minuten neunzehn statt zwoelf Maschinen da.
	 */
	private int countSquadron(World world, BlockPos pos) {
		BlockPos field = homeBase != null ? homeBase : pos;
		// Grosszuegiger Umkreis, damit auch ein Angriffsflug mitzaehlt.
		Box box = new Box(field).expand(220.0);
		return world.getEntitiesByClass(AutonomousF47Entity.class, box, jet -> {
			if (jet.getTeam() != team) {
				return false;
			}
			BlockPos home = jet.getHomeBase();
			// Grosszuegig, weil Bahnmitte und Abstellstreifen weit auseinander
			// liegen koennen: Bei einer hundertsechzig Bloecke langen Bahn sind
			// es siebzig. Mit einem engen Radius zaehlt die Kaserne die eigene
			// Staffel nicht mehr mit und baut endlos Ersatz.
			return home == null || home.isWithinDistance(field, 128.0);
		}).size();
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
		// Ab jetzt bestimmt der Spieler, nicht mehr der Bedarf.
		roleChosenByHand = true;
		progress = 0;
		markDirty();
		return trainingRole;
	}

	public void setOwner(PlayerEntity player) {
		ownerUuid = player.getUuid();
		markDirty();
	}

	/** Wo die Bahn liegt - dorthin kommen Ersatzmaschinen. */
	public void setHomeBase(BlockPos pos, float heading) {
		homeBase = pos.toImmutable();
		homeHeading = heading;
		markDirty();
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.putInt("Supplies", supplies);
		nbt.putInt("Progress", progress);
		nbt.putInt("JetProgress", jetProgress);
		nbt.putInt("SupplyTimer", supplyTimer);
		nbt.putInt("Role", trainingRole.ordinal());
		nbt.putBoolean("RoleByHand", roleChosenByHand);
		nbt.putInt("Team", team.ordinal());
		if (ownerUuid != null) {
			nbt.putUuid("Owner", ownerUuid);
		}
		if (homeBase != null) {
			nbt.putLong("HomeBase", homeBase.asLong());
			nbt.putFloat("HomeHeading", homeHeading);
		}
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		supplies = nbt.getInt("Supplies");
		progress = nbt.getInt("Progress");
		jetProgress = nbt.getInt("JetProgress");
		supplyTimer = nbt.getInt("SupplyTimer");
		trainingRole = SoldierRole.byOrdinal(nbt.getInt("Role"));
		roleChosenByHand = nbt.getBoolean("RoleByHand");
		team = Team.byOrdinal(nbt.getInt("Team"));
		if (nbt.containsUuid("Owner")) {
			ownerUuid = nbt.getUuid("Owner");
		}
		if (nbt.contains("HomeBase")) {
			homeBase = BlockPos.fromLong(nbt.getLong("HomeBase"));
			homeHeading = nbt.getFloat("HomeHeading");
		}
	}
}
