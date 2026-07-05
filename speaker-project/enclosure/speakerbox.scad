// ============================================================================
// SpeakerBox Pro — 3D-druckbares Gehaeuse (PETG, parametrisch)
//
// Konzept (Front = XY-Ebene, Tiefe = +Z beim Modellieren; gedruckt wird die
// Schale mit der Front nach unten -> keine Stuetzen noetig):
//
//   +--------------------------------------------------+
//   | [Mid L 0.35L] [   SUB ~2.0L netto   ] [Mid R]    |  <- Frontkammern
//   |  80x80x62      126 x 142 x 115        0.35L      |
//   |--------------------------------------------------|
//   |        Elektronik-/Akkufach (hinten, 44 tief)    |  <- Rueckfach
//   +--------------------------------------------------+
//
// - Sub-Kammer netto ~1.95L; mit Daemmwolle (+15%) effektiv ~2.2L wie in
//   der Simulation (acoustics/) angesetzt.
// - Mid-Kammern 0.40L brutto -> ~0.35L netto nach Treiberverdraengung.
// - Die Restvolumina der Seitenspalten (unter/hinter den Mid-Kammern)
//   gehoeren zum Elektronik-Luftraum (Durchbrueche zum Rueckfach).
// - Rueckdeckel separat, mit Lueftungsschlitzen ueber der Amp-Zone und
//   Schraubdomen fuer M3-Einschmelzmuttern.
// - ALLE Treibermasse sind Typwerte fuer AliExpress 3"/4"-Chassis:
//   VOR DEM DRUCK an die realen Treiber anpassen (Messschieber)!
//
// Rendern:  openscad -o shell.stl    -D 'part="shell"'    speakerbox.scad
//           openscad -o rear_lid.stl -D 'part="rear_lid"' speakerbox.scad
// ============================================================================

part = "preview";   // "shell" | "rear_lid" | "preview"

// --- Grundmasse [mm] --------------------------------------------------------
wall      = 4;      // Aussenwand (PETG, 4 Perimeter, steif)
pwall     = 3;      // Innenwaende / Kammerteiler
W         = 300;    // aussen Breite
H         = 150;    // aussen Hoehe
D         = 170;    // aussen Tiefe (inkl. Rueckdeckel)
lid_t     = 4;      // Rueckdeckel-Staerke

// --- Kammeraufteilung -------------------------------------------------------
mid_w     = 80;     // Breite der Seitenspalten (Mid-Kammern)
mid_h     = 80;     // Hoehe der Mid-Kammern (oben in den Seitenspalten)
mid_d     = 62;     // Tiefe der Mid-Kammern
front_d   = 115;    // Innentiefe der Frontsektion (Sub-Kammer)

// --- Treiber (TYPWERTE — an reale Chassis anpassen!) -------------------------
fr_cutout    = 73;    // 3" Fullrange: Einbauoeffnung Durchmesser
fr_screw_bc  = 68;    // 3": Schraubenabstand (quadratisch, Kantenmass)
sub_cutout   = 94;    // 4" Sub: Einbauoeffnung Durchmesser
sub_screw_bc = 96;    // 4": Lochkreis Durchmesser (4 Schrauben)
screw_d      = 3.2;   // Treiberschrauben M3 (Einschmelzmuttern von innen)

// --- Button-Board (pcb/button-board) -----------------------------------------
btn_pcb_w  = 60; btn_pcb_h = 20;
btn_pitch  = 9;             // Tasterraster ab x=5 (design.py)
btn_first_x = 5;
btn_hole_d = 7.5;           // Stoessel 6x6 -> 7.5mm Durchgang
led_x = 50.5; led_y = 15;   // WS2812B
oled_w = 26; oled_h = 15;   // Sichtfenster 0.96" OLED
btn_mnt = [[3, 17.5], [57.5, 17.5]];  // M2.5

// --- Main-PCB-Befestigung (pcb/main-board, 100x80, M3) ------------------------
pcb_w = 100; pcb_h = 80;
pcb_holes = [[4, 43], [4, 58], [96.5, 28], [52, 5]];

// abgeleitete Masse
iw = W - 2*wall;                 // Innenbreite
ih = H - 2*wall;                 // Innenhoehe
sub_w = iw - 2*mid_w - 2*pwall;  // Breite Sub-Spalte
rear_d = D - wall - lid_t - front_d - pwall;  // Innentiefe Rueckfach

// Volumen-Report (erscheint in der Konsole)
echo(str("Sub-Kammer brutto: ", sub_w*ih*front_d/1e6, " L"));
echo(str("Mid-Kammer brutto: ", mid_w*mid_h*mid_d/1e6, " L"));
echo(str("Rueckfach: ", iw*ih*rear_d/1e6, " L (Elektronik+Akku)"));

$fn = 64;

// ============================================================================
// Hauptschale
// ============================================================================
module shell() {
  difference() {
    // Aussenkoerper (Front bei z=0, offen hinten)
    cube([W, H, D - lid_t]);

    // Innenraum ausraeumen
    translate([wall, wall, wall])
      cube([iw, ih, D]);

    driver_holes();
    button_pocket_cut();
  }

  // Innenwaende wieder einsetzen
  partitions();
  pcb_mounts();
  lid_bosses();
  driver_inserts();
}

module partitions() {
  eps = 0.05;
  // Vertikale Teiler beidseits der Sub-Spalte (volle Hoehe, Tiefe front_d)
  for (x = [wall + mid_w, wall + mid_w + pwall + sub_w])
    translate([x, wall - eps, wall - eps])
      cube([pwall, ih + 2*eps, front_d + eps]);

  // Querschott zwischen Frontsektion und Rueckfach — nur hinter der
  // Sub-Spalte durchgehend (Sub-Kammer luftdicht); die Seitenspalten
  // bleiben zum Rueckfach offen (Elektronik-Luftraum)
  translate([wall + mid_w, wall - eps, wall + front_d])
    cube([pwall + sub_w + pwall, ih + 2*eps, pwall]);

  // Mid-Kammern: Boden (horizontal) + Rueckwand
  for (x = [wall, wall + mid_w + pwall + sub_w + pwall]) {
    translate([x - eps, wall + ih - mid_h - pwall, wall - eps])
      cube([mid_w + 2*eps, pwall, mid_d + eps]);       // Kammerboden
    translate([x - eps, wall + ih - mid_h - pwall, wall + mid_d])
      cube([mid_w + 2*eps, mid_h + pwall + eps, pwall]); // Kammerrueckwand
  }
}

module driver_holes() {
  // Mid-Treiber: mittig in den oberen Seitenfeldern
  for (cx = [wall + mid_w/2, W - wall - mid_w/2])
    translate([cx, wall + ih - mid_h/2 - pwall/2, -1])
      cylinder(d = fr_cutout, h = wall + 2);

  // Sub: mittig in der Sub-Spalte
  translate([W/2, H/2, -1])
    cylinder(d = sub_cutout, h = wall + 2);
}

module driver_inserts() {
  // Schraubdome fuer Treiber (M3-Einschmelzmuttern), von innen an die Front
  module boss() {
    difference() {
      cylinder(d = 9, h = 8);
      translate([0, 0, -1]) cylinder(d = 4.2, h = 10);  // Einschmelzmutter M3
    }
  }
  for (cx = [wall + mid_w/2, W - wall - mid_w/2])
    for (dx = [-1, 1], dy = [-1, 1])
      translate([cx + dx*fr_screw_bc/2,
                 wall + ih - mid_h/2 - pwall/2 + dy*fr_screw_bc/2, wall - 0.05])
        boss();
  for (a = [45, 135, 225, 315])
    translate([W/2 + sub_screw_bc/2*cos(a), H/2 + sub_screw_bc/2*sin(a), wall - 0.05])
      boss();
}

module button_pocket_cut() {
  // Bedienfeld oben auf dem Rueckfach. Das Button-Board (60x20) liegt unter
  // der Oberwand; Mapping Board-Koordinaten -> global:
  //   Board-x -> global x (Ursprung px0), Board-y -> global z (Ursprung pz0)
  px0 = W/2 - btn_pcb_w/2;              // 120
  pz0 = wall + front_d + pwall + 10;    // Vorderkante Board unter der Decke
  // 6 Taster-Stoessel (Board-y = 6)
  for (i = [0:5])
    translate([px0 + btn_first_x + i*btn_pitch, H - wall - 1, pz0 + 6])
      rotate([-90, 0, 0]) cylinder(d = btn_hole_d, h = wall + 2);
  // WS2812B-Lichtleiter (Board 50.5 / 15)
  translate([px0 + led_x, H - wall - 1, pz0 + led_y])
    rotate([-90, 0, 0]) cylinder(d = 5, h = wall + 2);
  // OLED-Sichtfenster: hinter dem Board (Steckmodul zeigt nach hinten)
  translate([px0 + 17, H - wall - 1, pz0 + btn_pcb_h + 4])
    cube([oled_w, wall + 2, oled_h]);
}

module pcb_mounts() {
  // Main-PCB liegt flach auf dem Boden des Rueckfachs (Board in X/Z-Ebene,
  // Bauteilseite nach oben = +y). 5-mm-Dome mit M3-Einschmelzmuttern.
  // Mapping: Board-x -> global x, Board-y -> global z (Boardkante an der
  // Querschott-Seite).
  px0 = W/2 - pcb_w/2;
  pz0 = wall + front_d + pwall + 2;
  for (h = pcb_holes)
    translate([px0 + h[0], wall - 0.05, pz0 + h[1]])
      rotate([-90, 0, 0]) boss5();
}

module boss5() {
  difference() {
    cylinder(d = 9, h = 5);
    translate([0, 0, -1]) cylinder(d = 4.2, h = 7);
  }
}

module lid_bosses() {
  // 6 Schraubdome fuer den Rueckdeckel (M3), mit Rippe an die Wand angebunden
  for (p = lid_screw_positions()) {
    translate([p[0], p[1], D - lid_t - 12])
      difference() {
        cylinder(d = 10, h = 12);
        translate([0, 0, 2]) cylinder(d = 4.2, h = 12);
      }
    // Anbindungsrippe zur naechstgelegenen y-Wand (alle Dome sitzen wandnah)
    if (p[1] < H/2)
      translate([p[0] - 4, wall - 0.05, D - lid_t - 12])
        cube([8, p[1] - wall + 0.05, 12]);
    else
      translate([p[0] - 4, p[1], D - lid_t - 12])
        cube([8, H - wall - p[1] + 0.05, 12]);
  }
}

function lid_screw_positions() = [
  [wall + 4.5, wall + 4.5], [W - wall - 4.5, wall + 4.5],
  [wall + 4.5, H - wall - 4.5], [W - wall - 4.5, H - wall - 4.5],
  [W/2, wall + 4.5], [W/2, H - wall - 4.5]
];

// ============================================================================
// Rueckdeckel
// ============================================================================
module rear_lid() {
  difference() {
    union() {
      cube([W, H, lid_t]);
      // Zentrierrand
      translate([wall + 0.3, wall + 0.3, -3])
        difference() {
          cube([iw - 0.6, ih - 0.6, 3]);
          translate([2, 2, -1]) cube([iw - 4.6, ih - 4.6, 5]);
        }
    }
    // Deckelschrauben (M3, gesenkt)
    for (p = lid_screw_positions()) {
      translate([p[0], p[1], -1]) cylinder(d = 3.4, h = lid_t + 2);
      translate([p[0], p[1], lid_t - 1.8]) cylinder(d1 = 3.4, d2 = 6.5, h = 1.9);
    }
    // Lueftungsschlitze (ueber der Verstaerkerzone, mittig)
    for (i = [0:7])
      translate([W/2 - 56 + i*16, H/2 - 30, -1])
        hull() {
          cylinder(d = 4, h = lid_t + 2);
          translate([0, 60, 0]) cylinder(d = 4, h = lid_t + 2);
        }
    // Anschluss-Ausschnitt: USB-C + Aux + XT30/Laden (Position beim
    // Einbau nach realer Kabellage nacharbeiten — bewusst grosszuegig)
    translate([W/2 - 30, wall + 8, -1]) cube([60, 16, lid_t + 2]);
  }
}

// ============================================================================
if (part == "shell") shell();
else if (part == "rear_lid") rear_lid();
else {
  shell();
  translate([0, 0, D + 15]) rear_lid();
}
