// ============================================================================
// SpeakerBox Pro — Gehaeuse v2 "modern" (PETG, parametrisch, 3 Druckteile)
//
// Design nach Vorbild kommerzieller BT-Speaker (JBL Xtreme/Partybox-Klasse):
//   - rundum abgerundete Vertikalkanten (R12) + gefaste Deckkanten
//   - integrierter Tragegriff (Tunnel-Griff oben, konstantes Profil ->
//     druckt sich in Front-nach-unten-Orientierung ohne Stuetzen)
//   - abnehmbare Grill-Platte mit Hex-Perforation (Press-Fit-Zapfen)
//   - versenktes Bedienfeld (Taster/LED/OLED) auf der Oberseite
//   - Silikonfuss-Kanaele unten, Anschlussnische hinten
//
// Umgesetzte Bau-Regeln (Recherche diyAudio/Parts-Express/Prusa-Forum):
//   - Wandstaerke 4 mm + >=4 Perimeter + >=40% Infill gegen Wandresonanzen
//   - interne Verstrebungsrippen zwischen gegenueberliegenden Waenden
//   - Kammern luftdicht (innen versiegeln), Schraubdome statt Selbstschneider
//   - Treiber hinterschraubt mit M3-Einschmelzmuttern, nicht ueberdrehen
//
// Kammern wie Simulation (Dayton ND91-4 + TCP115-4, v2.1):
//   Sub ~2.1 L brutto (- Treiberverdraengung ~1.9 L netto, mit Wolle eff. 2.2 L)
//   Mids 0.52 L brutto (- ND91-Verdraengung ~0.38 L netto, eff. 0.44 L)
// Druck: Schale Front nach unten, Deckel+Grill flach.
//
// Rendern:
//   openscad -o shell.stl    -D 'part="shell"'    speakerbox.scad
//   openscad -o rear_lid.stl -D 'part="rear_lid"' speakerbox.scad
//   openscad -o grille.stl   -D 'part="grille"'   speakerbox.scad
// ============================================================================

part = "preview";   // "shell" | "rear_lid" | "grille" | "preview"
                    // Split-Teile fuer Drucker < 300mm (z.B. Creality K1):
                    // "center" | "cap_left" | "cap_right"
                    // "lid_center" | "lid_left" | "lid_right"
                    // "grille_center" | "grille_left" | "grille_right"

// Trennebenen liegen IN den Kammerteilern -> Sub-Kammer bleibt ungeschnitten
xs1 = 100;   // = wall + mid_w
xs2 = 228;   // = W - wall - mid_w
swall = 4;   // Seitenwandstaerke des Mittelteils (ersetzt pwall-Teiler)
// Schraubpositionen (y,z) auf dem Flanschring der Trennflaeche
bolt_pos = [[9, 20], [141, 20], [9, 146], [141, 146], [75, 9], [75, 157]];

// --- Grundmasse [mm] ---------------------------------------------------------
wall   = 4;
pwall  = 3;
W      = 328;
H      = 150;
D      = 177;      // inkl. Rueckdeckel
lid_t  = 4;
R      = 14;       // Radius der Vertikalkanten (kleiner: 93er-ND91-Flansch
                   // braucht plane Front bis nahe an die Ecken)
CH     = 6;        // 45-Grad-Fasen vorne/hinten (druckbar)

// --- Kammern -------------------------------------------------------------------
mid_w   = 96;      // 96x96x56 = 0.52 L brutto -> ~0.38 L netto (ND91-4)
mid_h   = 96;
mid_d   = 56;
front_d = 122;     // Sub: 122x142x122 = 2.11 L brutto -> ~1.9 L netto (TCP115-4)

// --- Treiber: Dayton ND91-4 (Mid) + TCP115-4 (Sub) -----------------------------
// !!! MASSE VOR DEM DRUCK MIT MESSSCHIEBER AM ECHTEN CHASSIS PRUEFEN !!!
// Quellen widersprechen sich teils (ND91: Flansch 93 vs 103.5 mm, Tiefe 46 vs
// 63 mm). Hier: Parts-Express-Datenblattwerte. Falls die Tiefe >52 mm ist,
// mid_d auf 70 erhoehen und mid_h auf 78 senken (Volumen bleibt ~gleich).
fr_cutout    = 76;   // ND91-4 Einbauoeffnung (PE: 2.95" = 75 -> +1 Spiel)
fr_screw_bc  = 83;   // ND91-4 Schraubenkreis, 4 Loecher (Flansch-OD 93)
sub_cutout   = 96;   // TCP115-4 Einbauoeffnung (PE: 3.7" = 94 -> +2 Spiel)
sub_screw_bc = 106;  // TCP115-4 Schraubenkreis, 4 Loecher (Flansch-OD ~117)

// --- Griff ----------------------------------------------------------------------
grip_base = 124;   // Griff-Fussbreite — liegt komplett im Mittelteil (Split!)
grip_top  = 96;    // Breite des flachen Griffruecken
grip_h    = 38;    // Hoehe ueber Oberkante
grip_slot_top = 68;
grip_slot_bot = 82;
grip_z0   = 55;    // Tiefenlage (z) Beginn
grip_z1   = 115;   // Tiefenlage Ende

// --- Bedienfeld / Button-Board (pcb/button-board, Stoessel bei x+2.25) ---------
btn_pcb_w = 60;
btn_pitch = 9;
btn_stem_x0 = 7.25;          // erster Stoessel (Board-x)
btn_hole_d = 7.5;
led_bx = 53.25; led_by = 12.5;   // WS2812B-Position auf dem Board (x+2.25 wg rot90? LED zentriert)
oled_w = 27; oled_h = 16;

// --- Main-PCB (100x80, M3) -------------------------------------------------------
pcb_w = 100; pcb_h = 80;
pcb_holes = [[4, 43], [4, 58], [96.5, 28], [52, 5]];

// abgeleitet
iw = W - 2*wall;
ih = H - 2*wall;
sub_w = iw - 2*mid_w - 2*pwall;
rear_d = D - wall - lid_t - front_d - pwall;

echo(str("Sub-Kammer brutto: ", sub_w*ih*front_d/1e6, " L"));
echo(str("Mid-Kammer brutto: ", mid_w*mid_h*mid_d/1e6, " L"));
echo(str("Rueckfach: ", iw*ih*rear_d/1e6, " L"));

$fn = 72;
eps = 0.05;

// ============================================================================
// Aussenform: abgerundeter Kasten (Vertikalkanten R, Deckkanten gefast)
// ============================================================================
module rounded_body(w, h, d, r) {
  hull()
    for (x = [r, w - r], y = [r, h - r])
      translate([x, y, 0]) cylinder(r = r, h = d);
}

module outer_body() {
  hull() {
    // Mittelteil voll, vorne und hinten 45-Grad-Fasen (stuetzenfrei)
    translate([0, 0, CH]) rounded_body(W, H, D - lid_t - 2*CH, R);
    translate([CH, CH, 0])
      rounded_body(W - 2*CH, H - 2*CH, D - lid_t, R - CH*0.7);
  }
}

// ============================================================================
// Griff: konstantes 2D-Profil, entlang z extrudiert -> stuetzenfrei druckbar
// ============================================================================
module handle() {
  translate([0, 0, grip_z0])
    linear_extrude(grip_z1 - grip_z0)
      difference() {
        // Trapezbuegel (flacher Ruecken, schraege Beine) — Motion-Boom-Stil
        offset(r = 7) offset(r = -7)
          polygon([[W/2 - grip_base/2, H - 18], [W/2 + grip_base/2, H - 18],
                   [W/2 + grip_top/2,  H + grip_h],
                   [W/2 - grip_top/2,  H + grip_h]]);
        // Griffloch (Trapez, gerundet)
        offset(r = 8) offset(r = -8)
          polygon([[W/2 - grip_slot_bot/2, H + 4], [W/2 + grip_slot_bot/2, H + 4],
                   [W/2 + grip_slot_top/2, H + grip_h - 13],
                   [W/2 - grip_slot_top/2, H + grip_h - 13]]);
      }
}

// ============================================================================
// Hauptschale
// ============================================================================
module shell(split = false) {
  difference() {
    union() {
      outer_body();
      handle();
    }

    // Innenraum (Ecken mitgerundet -> konstante Wandstaerke)
    translate([wall, wall, wall])
      rounded_body(iw, ih, D, R - wall);

    driver_holes();
    grille_sockets();
    button_deck_cut();
    foot_channels();
  }

  partitions(split);
  braces();
  pcb_mounts();
  lid_bosses();
  driver_inserts();
}

// ============================================================================
// SPLIT-MODUS (Creality K1 & Co.): 3 Schalen-Teile mit Dichtflanschen
// ============================================================================
module slab(x0, x1) {
  translate([x0, -60, -10]) cube([x1 - x0, H + grip_h + 120, D + 40]);
}

// Flanschring auf der Trennflaeche: folgt der Aussenkontur, 10 mm breit
module joint_ring(xcut, into_left) {
  t = 4.05;   // Flanschdicke (+0.05 Ueberlapp gegen koplanare Flaechen)
  x0 = into_left ? xcut - t + 0.05 : xcut - 0.05;
  intersection() {
    outer_body();
    difference() {
      slab(x0, x0 + t);
      // Innenfenster: 10 mm Rand stehen lassen
      translate([x0 - 1, wall + 10, wall + 10])
        cube([t + 2, ih - 20, D - lid_t - 2*wall - 16]);
    }
  }
}

// Mittelteil: Sub-Kammer + Griff + Bedienfeld, geschlossene Seitenwaende
module center_piece() {
  difference() {
    union() {
      intersection() { shell(split = true); slab(xs1, xs2); }
      // Seitenwaende (ersetzen die Teiler, volle Tiefe)
      for (x = [xs1, xs2 - swall])
        difference() {
          intersection() { outer_body(); slab(x, x + swall); }
          // Kabeldurchlass im Elektronikfach (Mid-Treiber-Litzen)
          translate([x - 1, wall + 20, wall + front_d + pwall + 8])
            cube([swall + 2, 24, 14]);
        }
      // Einschmelzmutter-Bosse auf den Seitenwand-Innenflaechen
      for (p = bolt_pos) {
        translate([xs1 + swall - eps, p[0], p[1]])
          rotate([0, 90, 0]) boss_insert();
        translate([xs2 - swall + eps, p[0], p[1]])
          rotate([0, -90, 0]) boss_insert();
      }
    }
    // Gewindeloecher fuer die Flansch-Schrauben
    for (p = bolt_pos) {
      translate([xs1 - 6, p[0], p[1]]) rotate([0, 90, 0]) cylinder(d = 4.2, h = 14);
      translate([xs2 + 6, p[0], p[1]]) rotate([0, -90, 0]) cylinder(d = 4.2, h = 14);
    }
  }
}

module boss_insert() {
  difference() {
    cylinder(d = 9, h = 8);
    translate([0, 0, -1]) cylinder(d = 4.2, h = 10);
  }
}

// Seitenkappe: Mid-Kammer-Schale mit Flanschring + Durchgangsschrauben
module cap(left = true) {
  xcut = left ? xs1 : xs2;
  difference() {
    union() {
      intersection() {
        shell(split = true);
        if (left) slab(-10, xcut); else slab(xcut, W + 10);
      }
      joint_ring(xcut, into_left = left);
    }
    // M3-Durchgangsloecher im Flansch (Zugang durch die Treiberoeffnung)
    for (p = bolt_pos)
      translate([xcut - 8, p[0], p[1]]) rotate([0, 90, 0]) cylinder(d = 3.4, h = 16);
  }
}

// ---------------------------------------------------------------------------
module partitions(split = false) {
  // Vertikale Teiler der Sub-Spalte (im Split-Modus ersetzen die
  // Mittelteil-Seitenwaende diese Teiler)
  if (!split)
    for (x = [wall + mid_w, wall + mid_w + pwall + sub_w])
      translate([x, wall - eps, wall - eps])
        cube([pwall, ih + 2*eps, front_d + eps]);

  // Querschott hinter der Sub-Kammer (luftdicht); Seitenspalten offen
  translate([wall + mid_w, wall - eps, wall + front_d])
    cube([pwall + sub_w + pwall, ih + 2*eps, pwall]);

  // Mid-Kammern (oben in den Seitenspalten): Boden + Rueckwand
  for (x = [wall, wall + mid_w + pwall + sub_w + pwall]) {
    translate([x - eps, wall + ih - mid_h - pwall, wall - eps])
      cube([mid_w + 2*eps, pwall, mid_d + eps]);
    translate([x - eps, wall + ih - mid_h - pwall, wall + mid_d])
      cube([mid_w + 2*eps, mid_h + pwall + eps, pwall]);
  }
}

// Verstrebungsrippen (Regel: gegenueberliegende Waende koppeln)
module braces() {
  // Sub-Kammer: 2 vertikale Rippen an den Teilern + 1 Querrippe Boden/Decke
  for (x = [wall + mid_w + pwall, wall + mid_w + pwall + sub_w - 3])
    translate([x, wall - eps, wall + 55])
      cube([3, ih + 2*eps, 10]);
  // Boden-Decke-Steg mittig in der Sub-Kammer (bricht grosse Wandflaeche)
  translate([W/2 - 5, wall - eps, wall + 80])
    cube([10, ih + 2*eps, 6]);
  // Seitenwaende Rueckfach: kurze horizontale Rippen
  for (y = [wall + 30, wall + ih - 30])
    translate([wall - eps, y, wall + front_d + pwall + 5])
      cube([iw + 2*eps, 3, 8]);
}

// ---------------------------------------------------------------------------
module driver_holes() {
  for (cx = [wall + mid_w/2, W - wall - mid_w/2])
    translate([cx, wall + ih - mid_h/2 - pwall/2, -1])
      cylinder(d = fr_cutout, h = wall + 2);
  translate([W/2, H/2, -1])
    cylinder(d = sub_cutout, h = wall + 2);
}

module driver_inserts() {
  module boss() {
    difference() {
      cylinder(d = 9, h = 8);
      translate([0, 0, -1]) cylinder(d = 4.2, h = 10);
    }
  }
  // ND91-4: 4 Schrauben auf 83er-KREIS (45-Grad-Lagen, nicht Quadrat-Diagonale)
  for (cx = [wall + mid_w/2, W - wall - mid_w/2])
    for (a = [45, 135, 225, 315])
      translate([cx + fr_screw_bc/2*cos(a),
                 wall + ih - mid_h/2 - pwall/2 + fr_screw_bc/2*sin(a), wall - eps])
        boss();
  for (a = [45, 135, 225, 315])
    translate([W/2 + sub_screw_bc/2*cos(a), H/2 + sub_screw_bc/2*sin(a), wall - eps])
      boss();
}

// Press-Fit-Buchsen fuer die Grill-Platte (blind, in Verstaerkungsdomen)
grille_pegs = [[20, 20], [W - 20, 20], [20, H - 20], [W - 20, H - 20],
               [W/2, 16], [W/2, H - 16]];
module grille_sockets() {
  for (p = grille_pegs)
    translate([p[0], p[1], -1]) cylinder(d = 8.4, h = 5);   // 5.4mm tief ab Front
}
module grille_socket_bosses() {
  for (p = grille_pegs)
    translate([p[0], p[1], wall - eps])
      difference() {
        cylinder(d = 12, h = 4);
        translate([0, 0, -wall - 1]) cylinder(d = 8.4, h = wall + 5.5);
      }
}

// ---------------------------------------------------------------------------
// Bedienfeld: versenkte Mulde + Durchbrueche (Oberseite Rueckfach)
// ---------------------------------------------------------------------------
module button_deck_cut() {
  // WICHTIG: alles bleibt in der flachen Top-Zone (z < D-lid_t-2*CH = 154),
  // sonst frisst sich der Ausschnitt in die hintere 45-Grad-Fase.
  px0 = W/2 - btn_pcb_w/2;      // Button-Board beginnt hier (x)
  pz0 = wall + front_d + pwall + 6;   // Board-Vorderkante (z=128)
  // flache Design-Mulde ueber Tasten + OLED-Bereich (1.6 mm tief)
  hull()
    for (x = [px0 - 34, px0 + btn_pcb_w + 4], z = [pz0 - 2, pz0 + 24])
      translate([x, H - 1.6, z])
        rotate([-90, 0, 0]) cylinder(r = 5, h = 10);
  // Taster-Stoessel (Zentren x+2.25 wg. THT-Footprint-Ursprung)
  for (i = [0:5])
    translate([px0 + btn_stem_x0 + i*btn_pitch, H - wall - 1, pz0 + 6.25])
      rotate([-90, 0, 0]) cylinder(d = btn_hole_d, h = wall + 3);
  // LED-Lichtleiter
  translate([px0 + led_bx, H - wall - 1, pz0 + led_by])
    rotate([-90, 0, 0]) cylinder(d = 5, h = wall + 3);
  // OLED-Fenster LINKS neben dem Tastenfeld (Modul liegt flach im Fach,
  // per 4-adrigem Jumper an J2 des Button-Boards)
  translate([px0 - 32, H - wall - 1, pz0 + 3])
    cube([oled_w, wall + 3, oled_h]);
}

// Silikonfuss-Kanaele (Regel: rutschfeste Basis, entkoppelt Vibration)
module foot_channels() {
  for (z = [28, D - lid_t - 45])
    translate([W/2 - 110, -1, z])
      cube([220, 1.5 + 1, 10]);
}

// ---------------------------------------------------------------------------
module pcb_mounts() {
  // Main-PCB steht AUFRECHT, parallel zum Rueckdeckel, auf 5-mm-Domen am
  // Querschott (Board-Flaeche 100x80 passt nur so ins 44-mm-Fach).
  // Mapping: Board-x -> global x, Board-y -> global y.
  px0 = W/2 - pcb_w/2;
  py0 = wall + 8;
  for (h = pcb_holes)
    translate([px0 + h[0], py0 + h[1], wall + front_d + pwall - eps])
      boss5();
}
module boss5() {
  difference() {
    cylinder(d = 9, h = 5);
    translate([0, 0, -1]) cylinder(d = 4.2, h = 7);
  }
}

module lid_bosses() {
  for (p = lid_screw_positions()) {
    translate([p[0], p[1], D - lid_t - 12])
      difference() {
        cylinder(d = 10, h = 12);
        translate([0, 0, 2]) cylinder(d = 4.2, h = 12);
      }
    if (p[1] < H/2)
      translate([p[0] - 4, wall - eps, D - lid_t - 12])
        cube([8, p[1] - wall + eps, 12]);
    else
      translate([p[0] - 4, p[1], D - lid_t - 12])
        cube([8, H - wall - p[1] + eps, 12]);
  }
}
function lid_screw_positions() = [
  [wall + 4.5, wall + 4.5], [W - wall - 4.5, wall + 4.5],
  [wall + 4.5, H - wall - 4.5], [W - wall - 4.5, H - wall - 4.5],
  [W/2, wall + 4.5], [W/2, H - wall - 4.5]
];

// ============================================================================
// Grill-Platte: Hex-Perforation, Press-Fit-Zapfen, abgerundet wie die Front
// ============================================================================
module grille() {
  gt = 3;   // Plattenstaerke
  difference() {
    // Grundplatte mit 45-Grad-Rand, Kontur folgt dem gefassten Koerper
    hull() {
      linear_extrude(0.6)
        translate([CH + 1, CH + 1])
          offset(r = R - CH) offset(r = -(R - CH))
            square([W - 2*CH - 2, H - 2*CH - 2]);
      translate([0, 0, gt - 0.01])
        linear_extrude(0.01)
          translate([CH + 4, CH + 4])
            offset(r = R - CH) offset(r = -(R - CH))
              square([W - 2*CH - 8, H - 2*CH - 8]);
    }
    // Hex-Perforation ueber der GANZEN Flaeche (Rand 14 mm bleibt zu)
    intersection() {
      hex_field(W/2 - 1, H/2 - 1, 200);
      translate([14, 14, -2]) cube([W - 28, H - 28, 10]);
    }
  }
  // Press-Fit-Zapfen (0.2 Untermass zur 8.4er-Buchse)
  for (p = grille_pegs)
    translate([p[0] - 1, p[1] - 1, gt - eps]) {
      cylinder(d = 8.0, h = 5);
      translate([0, 0, 5 - 1]) cylinder(d1 = 8.0, d2 = 7.2, h = 1); // Einfuehrfase
    }
}

module hex_field(cx, cy, radius, hex_d = 8, pitch = 10.5) {
  rows = ceil(2*radius / (pitch * 0.866)) + 1;
  for (r = [-rows:rows])
    for (q = [-rows:rows]) {
      x = (q + (r % 2 == 0 ? 0 : 0.5)) * pitch;
      y = r * pitch * 0.866;
      if (sqrt(x*x + y*y) < radius)
        translate([cx + x, cy + y, -1])
          rotate([0, 0, 30]) cylinder(d = hex_d, h = 6, $fn = 6);
    }
}

// ============================================================================
// Rueckdeckel: abgerundet, Senkschrauben, Lueftung, Anschlussnische
// ============================================================================
module rear_lid() {
  difference() {
    union() {
      linear_extrude(lid_t)
        offset(r = R) offset(r = -R) square([W, H]);
      translate([wall + 0.3, wall + 0.3, -3])
        difference() {
          cube([iw - 0.6, ih - 0.6, 3]);
          translate([2, 2, -1]) cube([iw - 4.6, ih - 4.6, 5]);
        }
    }
    for (p = lid_screw_positions()) {
      translate([p[0], p[1], -1]) cylinder(d = 3.4, h = lid_t + 2);
      translate([p[0], p[1], lid_t - 1.8]) cylinder(d1 = 3.4, d2 = 6.5, h = 1.9);
    }
    // Lueftungsschlitze ueber der Amp-Zone
    for (i = [0:7])
      translate([W/2 - 56 + i*16, H/2 - 30, -1])
        hull() {
          cylinder(d = 4, h = lid_t + 2);
          translate([0, 60, 0]) cylinder(d = 4, h = lid_t + 2);
        }
    // Anschlussnische (USB-C/Aux/Laden — nach realer Kabellage nacharbeiten)
    translate([W/2 - 30, wall + 8, -1]) cube([60, 16, lid_t + 2]);
  }
}

// ============================================================================
if (part == "shell") { shell(); grille_socket_bosses(); }
else if (part == "rear_lid") rear_lid();
else if (part == "grille") grille();
else if (part == "center") { intersection() { union() { shell(split=true); grille_socket_bosses(); } slab(xs1, xs2); } center_piece(); }
else if (part == "cap_left") { intersection() { grille_socket_bosses(); slab(-10, xs1); } cap(left = true); }
else if (part == "cap_right") { intersection() { grille_socket_bosses(); slab(xs2, W + 10); } cap(left = false); }
else if (part == "lid_center") intersection() { rear_lid(); slab(xs1, xs2 - 0.3); }
else if (part == "lid_left") intersection() { rear_lid(); slab(-10, xs1 - 0.3); }
else if (part == "lid_right") intersection() { rear_lid(); slab(xs2, W + 10); }
else if (part == "grille_center") intersection() { grille(); slab(xs1, xs2 - 0.3); }
else if (part == "grille_left") intersection() { grille(); slab(-10, xs1 - 0.3); }
else if (part == "grille_right") intersection() { grille(); slab(xs2, W + 10); }
else {
  shell();
  grille_socket_bosses();
  translate([0, 0, -22]) grille();
  translate([0, 0, D + 12]) rear_lid();
}
