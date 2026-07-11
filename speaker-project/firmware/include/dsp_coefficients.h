// Automatisch generiert von dsp/generate_dsp_program.py — nicht editieren!
// Parameter-RAM-Adressen und 5.23-Startwerte fuer den ADAU1701.
#pragma once
#include <stdint.h>

#define DSP_FS            48000
#define DSP_XOVER_HZ      310.0f
#define DSP_EQ_NUM_BANDS  10
#define DSP_EQ_Q          1.41f

// Parameter-RAM-Adressen (muessen nach SigmaStudio-Compile
// einmalig gegen den Capture-Export abgeglichen werden!)
#define DSP_ADDR_MIXERINL_AUXGAIN             0
#define DSP_ADDR_MIXERINL_BTGAIN              1
#define DSP_ADDR_MIXERINR_AUXGAIN             2
#define DSP_ADDR_MIXERINR_BTGAIN              3
#define DSP_ADDR_MASTERVOLUME                 4
#define DSP_ADDR_XOVERHP_L_0                  5
#define DSP_ADDR_XOVERHP_L_1                  6
#define DSP_ADDR_XOVERHP_L_2                  7
#define DSP_ADDR_XOVERHP_L_3                  8
#define DSP_ADDR_XOVERHP_L_4                  9
#define DSP_ADDR_XOVERHP_R_0                  10
#define DSP_ADDR_XOVERHP_R_1                  11
#define DSP_ADDR_XOVERHP_R_2                  12
#define DSP_ADDR_XOVERHP_R_3                  13
#define DSP_ADDR_XOVERHP_R_4                  14
#define DSP_ADDR_XOVERLP_SUB_0                15
#define DSP_ADDR_XOVERLP_SUB_1                16
#define DSP_ADDR_XOVERLP_SUB_2                17
#define DSP_ADDR_XOVERLP_SUB_3                18
#define DSP_ADDR_XOVERLP_SUB_4                19
#define DSP_ADDR_SUBDELAY_SAMPLES             20
#define DSP_ADDR_SUBPOLARITY                  21
#define DSP_ADDR_PEQ_L_B0_0                   22
#define DSP_ADDR_PEQ_L_B0_1                   23
#define DSP_ADDR_PEQ_L_B0_2                   24
#define DSP_ADDR_PEQ_L_B0_3                   25
#define DSP_ADDR_PEQ_L_B0_4                   26
#define DSP_ADDR_PEQ_L_B1_0                   27
#define DSP_ADDR_PEQ_L_B1_1                   28
#define DSP_ADDR_PEQ_L_B1_2                   29
#define DSP_ADDR_PEQ_L_B1_3                   30
#define DSP_ADDR_PEQ_L_B1_4                   31
#define DSP_ADDR_PEQ_L_B2_0                   32
#define DSP_ADDR_PEQ_L_B2_1                   33
#define DSP_ADDR_PEQ_L_B2_2                   34
#define DSP_ADDR_PEQ_L_B2_3                   35
#define DSP_ADDR_PEQ_L_B2_4                   36
#define DSP_ADDR_PEQ_L_B3_0                   37
#define DSP_ADDR_PEQ_L_B3_1                   38
#define DSP_ADDR_PEQ_L_B3_2                   39
#define DSP_ADDR_PEQ_L_B3_3                   40
#define DSP_ADDR_PEQ_L_B3_4                   41
#define DSP_ADDR_PEQ_L_B4_0                   42
#define DSP_ADDR_PEQ_L_B4_1                   43
#define DSP_ADDR_PEQ_L_B4_2                   44
#define DSP_ADDR_PEQ_L_B4_3                   45
#define DSP_ADDR_PEQ_L_B4_4                   46
#define DSP_ADDR_PEQ_L_B5_0                   47
#define DSP_ADDR_PEQ_L_B5_1                   48
#define DSP_ADDR_PEQ_L_B5_2                   49
#define DSP_ADDR_PEQ_L_B5_3                   50
#define DSP_ADDR_PEQ_L_B5_4                   51
#define DSP_ADDR_PEQ_L_B6_0                   52
#define DSP_ADDR_PEQ_L_B6_1                   53
#define DSP_ADDR_PEQ_L_B6_2                   54
#define DSP_ADDR_PEQ_L_B6_3                   55
#define DSP_ADDR_PEQ_L_B6_4                   56
#define DSP_ADDR_PEQ_L_B7_0                   57
#define DSP_ADDR_PEQ_L_B7_1                   58
#define DSP_ADDR_PEQ_L_B7_2                   59
#define DSP_ADDR_PEQ_L_B7_3                   60
#define DSP_ADDR_PEQ_L_B7_4                   61
#define DSP_ADDR_PEQ_L_B8_0                   62
#define DSP_ADDR_PEQ_L_B8_1                   63
#define DSP_ADDR_PEQ_L_B8_2                   64
#define DSP_ADDR_PEQ_L_B8_3                   65
#define DSP_ADDR_PEQ_L_B8_4                   66
#define DSP_ADDR_PEQ_L_B9_0                   67
#define DSP_ADDR_PEQ_L_B9_1                   68
#define DSP_ADDR_PEQ_L_B9_2                   69
#define DSP_ADDR_PEQ_L_B9_3                   70
#define DSP_ADDR_PEQ_L_B9_4                   71
#define DSP_ADDR_PEQ_R_B0_0                   72
#define DSP_ADDR_PEQ_R_B0_1                   73
#define DSP_ADDR_PEQ_R_B0_2                   74
#define DSP_ADDR_PEQ_R_B0_3                   75
#define DSP_ADDR_PEQ_R_B0_4                   76
#define DSP_ADDR_PEQ_R_B1_0                   77
#define DSP_ADDR_PEQ_R_B1_1                   78
#define DSP_ADDR_PEQ_R_B1_2                   79
#define DSP_ADDR_PEQ_R_B1_3                   80
#define DSP_ADDR_PEQ_R_B1_4                   81
#define DSP_ADDR_PEQ_R_B2_0                   82
#define DSP_ADDR_PEQ_R_B2_1                   83
#define DSP_ADDR_PEQ_R_B2_2                   84
#define DSP_ADDR_PEQ_R_B2_3                   85
#define DSP_ADDR_PEQ_R_B2_4                   86
#define DSP_ADDR_PEQ_R_B3_0                   87
#define DSP_ADDR_PEQ_R_B3_1                   88
#define DSP_ADDR_PEQ_R_B3_2                   89
#define DSP_ADDR_PEQ_R_B3_3                   90
#define DSP_ADDR_PEQ_R_B3_4                   91
#define DSP_ADDR_PEQ_R_B4_0                   92
#define DSP_ADDR_PEQ_R_B4_1                   93
#define DSP_ADDR_PEQ_R_B4_2                   94
#define DSP_ADDR_PEQ_R_B4_3                   95
#define DSP_ADDR_PEQ_R_B4_4                   96
#define DSP_ADDR_PEQ_R_B5_0                   97
#define DSP_ADDR_PEQ_R_B5_1                   98
#define DSP_ADDR_PEQ_R_B5_2                   99
#define DSP_ADDR_PEQ_R_B5_3                   100
#define DSP_ADDR_PEQ_R_B5_4                   101
#define DSP_ADDR_PEQ_R_B6_0                   102
#define DSP_ADDR_PEQ_R_B6_1                   103
#define DSP_ADDR_PEQ_R_B6_2                   104
#define DSP_ADDR_PEQ_R_B6_3                   105
#define DSP_ADDR_PEQ_R_B6_4                   106
#define DSP_ADDR_PEQ_R_B7_0                   107
#define DSP_ADDR_PEQ_R_B7_1                   108
#define DSP_ADDR_PEQ_R_B7_2                   109
#define DSP_ADDR_PEQ_R_B7_3                   110
#define DSP_ADDR_PEQ_R_B7_4                   111
#define DSP_ADDR_PEQ_R_B8_0                   112
#define DSP_ADDR_PEQ_R_B8_1                   113
#define DSP_ADDR_PEQ_R_B8_2                   114
#define DSP_ADDR_PEQ_R_B8_3                   115
#define DSP_ADDR_PEQ_R_B8_4                   116
#define DSP_ADDR_PEQ_R_B9_0                   117
#define DSP_ADDR_PEQ_R_B9_1                   118
#define DSP_ADDR_PEQ_R_B9_2                   119
#define DSP_ADDR_PEQ_R_B9_3                   120
#define DSP_ADDR_PEQ_R_B9_4                   121
#define DSP_ADDR_PEQ_SUB_B0_0                 122
#define DSP_ADDR_PEQ_SUB_B0_1                 123
#define DSP_ADDR_PEQ_SUB_B0_2                 124
#define DSP_ADDR_PEQ_SUB_B0_3                 125
#define DSP_ADDR_PEQ_SUB_B0_4                 126
#define DSP_ADDR_PEQ_SUB_B1_0                 127
#define DSP_ADDR_PEQ_SUB_B1_1                 128
#define DSP_ADDR_PEQ_SUB_B1_2                 129
#define DSP_ADDR_PEQ_SUB_B1_3                 130
#define DSP_ADDR_PEQ_SUB_B1_4                 131
#define DSP_ADDR_PEQ_SUB_B2_0                 132
#define DSP_ADDR_PEQ_SUB_B2_1                 133
#define DSP_ADDR_PEQ_SUB_B2_2                 134
#define DSP_ADDR_PEQ_SUB_B2_3                 135
#define DSP_ADDR_PEQ_SUB_B2_4                 136
#define DSP_ADDR_PEQ_SUB_B3_0                 137
#define DSP_ADDR_PEQ_SUB_B3_1                 138
#define DSP_ADDR_PEQ_SUB_B3_2                 139
#define DSP_ADDR_PEQ_SUB_B3_3                 140
#define DSP_ADDR_PEQ_SUB_B3_4                 141
#define DSP_ADDR_PEQ_SUB_B4_0                 142
#define DSP_ADDR_PEQ_SUB_B4_1                 143
#define DSP_ADDR_PEQ_SUB_B4_2                 144
#define DSP_ADDR_PEQ_SUB_B4_3                 145
#define DSP_ADDR_PEQ_SUB_B4_4                 146
#define DSP_ADDR_PEQ_SUB_B5_0                 147
#define DSP_ADDR_PEQ_SUB_B5_1                 148
#define DSP_ADDR_PEQ_SUB_B5_2                 149
#define DSP_ADDR_PEQ_SUB_B5_3                 150
#define DSP_ADDR_PEQ_SUB_B5_4                 151
#define DSP_ADDR_PEQ_SUB_B6_0                 152
#define DSP_ADDR_PEQ_SUB_B6_1                 153
#define DSP_ADDR_PEQ_SUB_B6_2                 154
#define DSP_ADDR_PEQ_SUB_B6_3                 155
#define DSP_ADDR_PEQ_SUB_B6_4                 156
#define DSP_ADDR_PEQ_SUB_B7_0                 157
#define DSP_ADDR_PEQ_SUB_B7_1                 158
#define DSP_ADDR_PEQ_SUB_B7_2                 159
#define DSP_ADDR_PEQ_SUB_B7_3                 160
#define DSP_ADDR_PEQ_SUB_B7_4                 161
#define DSP_ADDR_PEQ_SUB_B8_0                 162
#define DSP_ADDR_PEQ_SUB_B8_1                 163
#define DSP_ADDR_PEQ_SUB_B8_2                 164
#define DSP_ADDR_PEQ_SUB_B8_3                 165
#define DSP_ADDR_PEQ_SUB_B8_4                 166
#define DSP_ADDR_PEQ_SUB_B9_0                 167
#define DSP_ADDR_PEQ_SUB_B9_1                 168
#define DSP_ADDR_PEQ_SUB_B9_2                 169
#define DSP_ADDR_PEQ_SUB_B9_3                 170
#define DSP_ADDR_PEQ_SUB_B9_4                 171
#define DSP_ADDR_SUBLEVEL                     172
#define DSP_ADDR_LIMITER_L_THRESHOLD          173
#define DSP_ADDR_LIMITER_L_ATTACK             174
#define DSP_ADDR_LIMITER_L_RELEASE            175
#define DSP_ADDR_LIMITER_R_THRESHOLD          176
#define DSP_ADDR_LIMITER_R_ATTACK             177
#define DSP_ADDR_LIMITER_R_RELEASE            178
#define DSP_ADDR_LIMITER_SUB_THRESHOLD        179
#define DSP_ADDR_LIMITER_SUB_ATTACK           180
#define DSP_ADDR_LIMITER_SUB_RELEASE          181

// EQ-Mittenfrequenzen [Hz]
static const float DSP_EQ_FREQS[10] = {31.5f, 63.0f, 125.0f, 250.0f, 500.0f, 1000.0f, 2000.0f, 4000.0f, 8000.0f, 16000.0f};

// Preset-Gains [dB] je Band + Sub-Level
static const float DSP_PRESET_FLAT[11] = {0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
static const float DSP_PRESET_MUSIC[11] = {2.0f, 3.0f, 2.0f, 0.0f, -1.0f, 0.0f, 0.0f, 1.0f, 2.0f, 2.0f, 1.5f};
static const float DSP_PRESET_OUTDOOR[11] = {3.0f, 5.0f, 4.0f, 1.0f, 0.0f, 1.0f, 2.0f, 3.0f, 3.0f, 2.0f, 3.0f};

// Master-Volume-Tabelle: 51 Stufen (0=mute), logarithmisch -60..0 dB
static const float DSP_VOLUME_TABLE[51] = {
    0.000000f, 0.001148f, 0.001318f, 0.001514f, 0.001738f, 0.001995f, 0.002291f, 0.002630f, 0.003020f, 0.003467f, 0.003981f, 0.004571f, 0.005248f, 0.006026f, 0.006918f, 0.007943f, 0.009120f, 0.010471f, 0.012023f, 0.013804f, 0.015849f, 0.018197f, 0.020893f, 0.023988f, 0.027542f, 0.031623f, 0.036308f, 0.041687f, 0.047863f, 0.054954f, 0.063096f, 0.072444f, 0.083176f, 0.095499f, 0.109648f, 0.125893f, 0.144544f, 0.165959f, 0.190546f, 0.218776f, 0.251189f, 0.288403f, 0.331131f, 0.380189f, 0.436516f, 0.501187f, 0.575440f, 0.660693f, 0.758578f, 0.870964f, 1.000000f
};
