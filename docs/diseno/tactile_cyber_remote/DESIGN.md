---
name: Tactile Cyber-Remote
colors:
  surface: '#0f131c'
  surface-dim: '#0f131c'
  surface-bright: '#353942'
  surface-container-lowest: '#0a0e16'
  surface-container-low: '#181c24'
  surface-container: '#1c2028'
  surface-container-high: '#262a33'
  surface-container-highest: '#31353e'
  on-surface: '#dfe2ee'
  on-surface-variant: '#bac9cc'
  inverse-surface: '#dfe2ee'
  inverse-on-surface: '#2c3039'
  outline: '#849396'
  outline-variant: '#3b494c'
  surface-tint: '#00daf3'
  primary: '#c3f5ff'
  on-primary: '#00363d'
  primary-container: '#00e5ff'
  on-primary-container: '#00626e'
  inverse-primary: '#006875'
  secondary: '#d0bcff'
  on-secondary: '#3c0091'
  secondary-container: '#571bc1'
  on-secondary-container: '#c4abff'
  tertiary: '#a8ffd2'
  on-tertiary: '#003824'
  tertiary-container: '#5be9ad'
  on-tertiary-container: '#006645'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#9cf0ff'
  primary-fixed-dim: '#00daf3'
  on-primary-fixed: '#001f24'
  on-primary-fixed-variant: '#004f58'
  secondary-fixed: '#e9ddff'
  secondary-fixed-dim: '#d0bcff'
  on-secondary-fixed: '#23005c'
  on-secondary-fixed-variant: '#5516be'
  tertiary-fixed: '#6ffbbe'
  tertiary-fixed-dim: '#4edea3'
  on-tertiary-fixed: '#002113'
  on-tertiary-fixed-variant: '#005236'
  background: '#0f131c'
  on-background: '#dfe2ee'
  surface-variant: '#31353e'
typography:
  display-lg:
    fontFamily: Space Grotesk
    fontSize: 40px
    fontWeight: '700'
    lineHeight: 48px
    letterSpacing: -0.03em
  display-lg-mobile:
    fontFamily: Space Grotesk
    fontSize: 30px
    fontWeight: '700'
    lineHeight: 36px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Space Grotesk
    fontSize: 26px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.02em
  headline-sm:
    fontFamily: Space Grotesk
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: -0.01em
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: 0em
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0em
  body-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
    letterSpacing: 0.01em
  label-code-lg:
    fontFamily: JetBrains Mono
    fontSize: 13px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.04em
  label-code-sm:
    fontFamily: JetBrains Mono
    fontSize: 10px
    fontWeight: '600'
    lineHeight: 12px
    letterSpacing: 0.08em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-sm: 0.5rem
  margin: 1rem
  margin-tablet: 1.5rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 0.75rem
  space-lg: 1.25rem
  space-xl: 2rem
---

## Brand & Style

This design system is engineered for next-generation smart control hardware interfaces, home theater command centers, and industrial automation control. The personality is precise, surgical, ultra-responsive, and mechanical yet refined. It merges the ergonomics of luxury industrial hardware (machined anodized aluminum, physical micro-switches, chamfered edge bezels) with digital glassmorphism and luminous cybernetic telemetry.

The target audience encompasses AV enthusiasts, smart home power users, broadcast studio operators, and hardware tinkerers who demand low-latency tactile feedback, zero visual clutter, and instant situational awareness.

### Design Movement: Tactical Neomorphic Glassmorphism
The aesthetic synthesizes three specific idioms:
- **Matte Micro-Surfaces**: Low-specular dark slate textures layered with machined interior highlights.
- **Precision Optic Glass**: Frosted translucent layers (`backdrop-filter: blur(24px)`) bordered by directional 1px metallic rim-lighting (`linear-gradient(135deg, rgba(255,255,255,0.18), rgba(255,255,255,0.02))`).
- **Telemetry-Driven Luminescence**: Functional glowing indicators that visually articulate latency, protocol signals (IR, Wi-Fi, BLE), and physical haptic depth.

## Colors

The palette leverages a deep, non-reflective obsidian canvas layered with cold-slate containers and vivid functional neon accents. Every accent represents a hardware signal state or wireless transmission protocol.

### Palette Architecture
- **Canvas Base (`#0B0F17`)**: Pitch-black canvas minimizing OLED display power draw and maximizing contrast.
- **Surface Elevation (`#131B26`)**: Subdued gunmetal container background for recessed wells, card backdrops, and mechanical housings.
- **Primary — Cyan Beam (`#00E5FF`)**: High-throughput LAN, Wi-Fi connectivity, primary action rings, and directional tracking.
- **Secondary — Electric Violet (`#8B5CF6`)**: Bluetooth LE protocols, pairing flows, audio profile routing, and specialized macro controls.
- **Tertiary — Active Emerald (`#10B981`)**: Online link states, Wake-on-LAN verification, low jitter metrics, and active streaming.
- **Critical & Infrared — Precision Amber-Red (`#FF453A`)**: Hardware power cycles, Infrared (IR) beam emissions, master disconnect switches, and fatal packet drop alerts.

### Color Rules
- Background surfaces must never use pure `#000000` to prevent smearing during low-light interaction; `#0B0F17` ensures visible micro-borders.
- Glowing accents must use concentrated box-shadow blooms (`0 0 12px <color>40`) rather than wide, unfocused drop shadows to preserve optical precision.

## Typography

The typographic hierarchy distinguishes operational telemetry from human-readable labels:

1. **Brand & Hardware Displays (`Space Grotesk`)**: Geometric, sharp, and confident. Used on current input modes, active room designations, and large digital volume displays.
2. **Interface Body (`Inter`)**: High-legibility grotesque designed to stay neutral and crisp on dense mobile interfaces. Used for peripheral labels, settings, modal titles, and navigation bar options.
3. **Telemetry & Technical Layer (`JetBrains Mono`)**: Strict tabular monospace numbers and compact glyphs for IP addresses, latency benchmarks (`14ms`), port configurations (`UDP:8080`), hex packet visualizers, and protocol states. All numerical counters must render with tabular figures (`font-variant-numeric: tabular-nums`).

## Layout & Spacing

This design system uses a strict mobile-first ergonomics model tailored for single-handed thumb navigation and high tap accuracy. 

### Rhythm & Grid
- **Vertical Canvas Rhythm**: The layout follows a fixed thumb-zone hierarchy. Telemetry and connection states are locked in the upper third (visual glance area). The central navigation wheel / D-pad occupies the direct thumb sweep arc. Primary secondary controls (play/pause, volume rocker, input selector) flank the bottom interactive perimeter.
- **Fluid Grid**: Mobile viewports utilize a 4-column fluid layout with `1rem` margins and `0.5rem` gutters between adjacent tactical buttons. Tablet/expanded layouts scale to a 6-column interface anchored to a maximum width of `680px` to maintain ergonomic touch boundaries.
- **Touch Targets**: All physical press targets (keypad cells, directional arrows, switches) must adhere to a minimum bounding box of `48px x 48px` regardless of their visual footprint, preserving a minimum spacing gap of `space-sm` (8px) to prevent accidental neighboring triggers.

## Elevation & Depth

Depth is tactile, physical, and realistic—conveying mechanical travel and optical refraction rather than arbitrary drop shadows.

### Elevation Hierarchy

1. **Well / Recessed Layer (Inset Depth)**:
   - Used for the D-pad enclosure, trackpad regions, and housing chambers.
   - Built using inset shadows: `box-shadow: inset 0 2px 4px rgba(0, 0, 0, 0.7), inset 0 0 1px rgba(255, 255, 255, 0.05);` over `#0D131C`.

2. **Base Tactile Surface (Resting Mechanical Elevation)**:
   - Extruded mechanical buttons and primary toggles.
   - Exterior shadow: `0 4px 10px rgba(0, 0, 0, 0.6), 0 1px 2px rgba(0, 0, 0, 0.4)`.
   - Rim highlight: Top edge `border-top: 1px solid rgba(255, 255, 255, 0.12)`, bottom edge `border-bottom: 1px solid rgba(0, 0, 0, 0.8)`.

3. **Active Depressed State (Travel)**:
   - When a user touches or holds a button, elevation collapses immediately: `transform: scale(0.97) translateY(1.5px)`.
   - Shadow shifts from outward projection to an inner contact shadow: `box-shadow: inset 0 2px 6px rgba(0,0,0,0.8)`.

4. **Translucent Telemetry HUD (Floating Glass)**:
   - Overlays, volume slideouts, and status dropdowns float over the controls.
   - Background: `rgba(19, 27, 38, 0.75)` with `backdrop-filter: blur(20px)`.
   - Border: Continuous 1px stroke using `rgba(0, 229, 255, 0.2)` or `rgba(255, 255, 255, 0.08)`.
   - Ambient Glow: Soft radial ambient glow colored to the current connection mode (e.g., `#00E5FF` at 8% opacity).

## Shapes

The geometric architecture balances mechanical precision with human palm ergonomics:

- **Buttons & Control Tiles**: Standardized to `rounded-base` (`0.5rem` / `8px`) and `rounded-lg` (`1rem` / `16px`) for larger macro clusters, resembling injection-molded physical buttons.
- **Circular Hardware Modules**: The primary D-Pad, rotational scrubbers, and Volume dials use absolute circle geometry (`rounded-full`) embedded inside softly chamfered square recesses.
- **Status Pills**: Telemetry badges, protocol tags, and hardware latency markers adopt tight pill shapes (`rounded-full`) with dense internal padding (`2px 8px`).
- **Beveling**: Edges use high-precision micro-chamfers achieved via subtle 1px multi-stop gradient strokes rather than thick visible frames.

## Components

### 1. Tactile Remote D-Pad
- **Structure**: A large circular assembly housed inside an inset gunmetal housing. Divided into four cardinal quadrants (`Up`, `Down`, `Left`, `Right`) surrounding an elevated center `OK` button.
- **Surface**: Radial brushed metal gradient (`linear-gradient(145deg, #1A2332, #101621)`).
- **Center Button**: Distinct circular nub elevated `2px` higher than cardinal rings, bounded by a 1px metallic cyan halo on touch focus.
- **States**: Cardinal sectors illuminate with directional cyan edge streaks upon press.

### 2. Signal & Telemetry Badges
- **Structure**: Monospaced micro-chips placed within the navigation header.
- **Styling**: `JetBrains Mono` at `10px`. Background in dark translucent charcoal with an active indicator LED dot (3px diameter).
- **Variants**:
  - *IR Active*: Red pulse (`#FF453A`) with subtle outward ripple animation on signal transmit.
  - *Wi-Fi / LAN*: Cyan (`#00E5FF`) displaying live ping (e.g., `LAN 12ms`).
  - *BLE*: Electric violet (`#8B5CF6`) with signal RSSI bars.
  - *WoL*: Emerald (`#10B981`) outline with instant state toggle.

### 3. Tactile Mechanical Buttons
- **Primary Action (Power/Standby)**: Dark matte finish with an embossed glyph (`#FF453A`). On active state, the power glyph glows with an inner neon filament effect.
- **Secondary Actions (Volume, Channel, Mute)**: Rocker-style dual switches split horizontally or vertically with a recessed center groove.
- **Haptic Feedback**: Accompanied by tactile CSS compression (`transform: translateY(1px)`) and a momentary brightness increase of the perimeter border highlight.

### 4. Precision Input & Parameter Sliders
- **Volume & Scrubbing Tracks**: Recessed groove tracks featuring a luminous fill bar (`#00E5FF` gradient to `#8B5CF6`) with a knurled metal thumb knob.
- **Knob Design**: Etched vertical grip lines with an internal neon micro-indicator showing precise percentages.

### 5. Control Cards & Macro Trays
- **Structure**: Low-elevation `#131B26` containers with 1px hairline borders (`rgba(255, 255, 255, 0.07)`).
- **Header**: Space Grotesk section headline paired with a right-aligned monospace device identifier (e.g., `APPLE_TV_4K // LIVING_ROOM`).
- **Interactive Rows**: Tactile list rows with directional chevron micro-icons and active state highlight sweeps.

### 6. Interactive Switches & Toggles
- **Track**: Inset pill well (`#0B0F17`) with an interior shadow.
- **Thumb**: Metallic circular cap featuring a dual-gradient finish. When switched ON, the thumb's center cavity lights up in active connection emerald (`#10B981`).