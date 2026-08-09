package ch.joel.westernmod.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

/**
 * Kleiner Bau-Helfer, der in lokalen Stadtkoordinaten rechnet.
 *
 * <p>Alle Bauteile werden in {@code (f, u, r)} beschrieben: {@code f} läuft die Hauptstrasse
 * entlang, {@code u} nach oben und {@code r} quer zur Strasse nach rechts. Der Builder rechnet
 * das je nach Blickrichtung des Spielers in Weltkoordinaten um — so steht die Stadt immer
 * richtig herum, egal aus welcher Richmung man sie gründet.
 */
public class TownBuilder {
	private final ServerWorld world;
	private final BlockPos origin;
	private final Direction forward;
	private final Direction right;
	private final Random random;

	private final BlockPos.Mutable scratch = new BlockPos.Mutable();

	public TownBuilder(ServerWorld world, BlockPos origin, Direction forward, Random random) {
		this.world = world;
		this.origin = origin;
		this.forward = forward;
		this.right = forward.rotateYClockwise();
		this.random = random;
	}

	public ServerWorld world() {
		return this.world;
	}

	public Random random() {
		return this.random;
	}

	/** Richtung, in die die Hauptstrasse zeigt. */
	public Direction forward() {
		return this.forward;
	}

	/** Quer zur Strasse, nach rechts. */
	public Direction right() {
		return this.right;
	}

	/** Rechnet lokale Stadtkoordinaten in eine Weltposition um. */
	public BlockPos at(int f, int u, int r) {
		return this.origin
				.offset(this.forward, f)
				.offset(this.right, r)
				.up(u);
	}

	// --- Setzen ----------------------------------------------------------------------------

	/** Setzt einen Block inklusive Nachbar-Updates — für Zäune, Scheiben, Türen und Treppen. */
	public void set(int f, int u, int r, BlockState state) {
		this.world.setBlockState(this.at(f, u, r), state, Block.NOTIFY_ALL);
	}

	/** Setzt einen Block ohne Nachbar-Updates — schnell, für grosse Flächen aus Vollblöcken. */
	public void setFast(int f, int u, int r, BlockState state) {
		this.world.setBlockState(this.at(f, u, r), state, Block.NOTIFY_LISTENERS);
	}

	public BlockState get(int f, int u, int r) {
		return this.world.getBlockState(this.at(f, u, r));
	}

	public boolean isAir(int f, int u, int r) {
		return this.world.getBlockState(this.at(f, u, r)).isAir();
	}

	/** Füllt einen Quader (Grenzen inklusive). */
	public void fill(int f1, int u1, int r1, int f2, int u2, int r2, BlockState state) {
		for (int f = Math.min(f1, f2); f <= Math.max(f1, f2); f++) {
			for (int u = Math.min(u1, u2); u <= Math.max(u1, u2); u++) {
				for (int r = Math.min(r1, r2); r <= Math.max(r1, r2); r++) {
					this.setFast(f, u, r, state);
				}
			}
		}
	}

	/** Füllt einen Quader und mischt dabei zwei Blöcke — gegen zu glatte Flächen. */
	public void fillMixed(int f1, int u1, int r1, int f2, int u2, int r2, BlockState main, BlockState accent,
			float accentChance) {
		for (int f = Math.min(f1, f2); f <= Math.max(f1, f2); f++) {
			for (int u = Math.min(u1, u2); u <= Math.max(u1, u2); u++) {
				for (int r = Math.min(r1, r2); r <= Math.max(r1, r2); r++) {
					this.setFast(f, u, r, this.random.nextFloat() < accentChance ? accent : main);
				}
			}
		}
	}

	/** Zieht nur die vier Wände eines Quaders hoch, Boden und Decke bleiben frei. */
	public void walls(int f1, int u1, int r1, int f2, int u2, int r2, BlockState state) {
		int fMin = Math.min(f1, f2);
		int fMax = Math.max(f1, f2);
		int rMin = Math.min(r1, r2);
		int rMax = Math.max(r1, r2);

		for (int u = Math.min(u1, u2); u <= Math.max(u1, u2); u++) {
			for (int f = fMin; f <= fMax; f++) {
				this.setFast(f, u, rMin, state);
				this.setFast(f, u, rMax, state);
			}
			for (int r = rMin; r <= rMax; r++) {
				this.setFast(fMin, u, r, state);
				this.setFast(fMax, u, r, state);
			}
		}
	}

	/** Setzt einen Block nur, wenn dort bisher Luft ist. */
	public void setIfAir(int f, int u, int r, BlockState state) {
		if (this.isAir(f, u, r)) {
			this.set(f, u, r, state);
		}
	}

	// --- Gelände ---------------------------------------------------------------------------

	/**
	 * Planiert das Baufeld: räumt alles über der Strassenhöhe ab und füllt Löcher darunter auf,
	 * damit die Stadt weder in der Luft schwebt noch im Hügel steckt.
	 */
	public void levelGround(int fMin, int fMax, int rMin, int rMax, int clearHeight, int fillDepth) {
		BlockState ground = Blocks.DIRT.getDefaultState();

		for (int f = fMin; f <= fMax; f++) {
			for (int r = rMin; r <= rMax; r++) {
				for (int u = 1; u <= clearHeight; u++) {
					BlockPos pos = this.at(f, u, r);
					if (!this.world.getBlockState(pos).isAir()) {
						this.world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				}

				for (int u = 0; u >= -fillDepth; u--) {
					BlockPos pos = this.at(f, u, r);
					BlockState state = this.world.getBlockState(pos);
					if (state.isAir() || !state.getFluidState().isEmpty()) {
						this.world.setBlockState(pos, ground, Block.NOTIFY_LISTENERS);
					}
				}
			}
		}
	}

	/** Der Mutable-Puffer steht Aufrufern für eigene Schleifen zur Verfügung. */
	public BlockPos.Mutable scratch() {
		return this.scratch;
	}
}
