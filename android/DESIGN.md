# Design System: Material 3 Expressive

<!-- impeccable:design-schema 1 -->

## Platform & Theme Architecture

Android Native (Material Design 3 Expressive).
Dual-mode: First-class Light Theme and Dark Theme (`Theme.Material3.DayNight.NoActionBar`).

## Color System

### Primary: Electric Cyan
- Light: Primary `#00677E`, OnPrimary `#FFFFFF`, PrimaryContainer `#B3EBFF`, OnPrimaryContainer `#001F28`
- Dark: Primary `#00E5FF`, OnPrimary `#00363D`, PrimaryContainer `#004F58`, OnPrimaryContainer `#A6F4FF`

### Secondary: Security Slate
- Light: Secondary `#4A626A`, OnSecondary `#FFFFFF`, SecondaryContainer `#CCE8F1`, OnSecondaryContainer `#051F25`
- Dark: Secondary `#B1CBD4`, OnSecondary `#1B343B`, SecondaryContainer `#324B52`, OnSecondaryContainer `#CCE8F1`

### Tertiary: Amber Pulse (Attentive Biometric Accent)
- Light: Tertiary `#6D5E0F`, OnTertiary `#FFFFFF`, TertiaryContainer `#F7E287`, OnTertiaryContainer `#211B00`
- Dark: Tertiary `#DAC66E`, OnTertiary `#393000`, TertiaryContainer `#534600`, OnTertiaryContainer `#F7E287`

### Threat Actions & Statuses
- ALLOW: Text `#2E7D32` (Light) / `#78DB9F` (Dark), Container `15% opacity`
- WARN: Text `#E65100` (Light) / `#FFB74D` (Dark), Container `15% opacity`
- STEP_UP_AUTH: Text `#C2185B` (Light) / `#FF80AB` (Dark), Container `15% opacity`
- BLOCK_SESSION: Text `#B3261E` (Light) / `#F2B8B5` (Dark), Container `18% opacity`

### Surfaces & Backgrounds
- Light: Background `#F6FAFC`, Surface `#FBFDFE`, SurfaceContainerLow `#F0F4F7`, SurfaceContainer `#EAEEF2`, SurfaceContainerHigh `#E4E9EC`
- Dark: Background `#0E1416`, Surface `#12181A`, SurfaceContainerLow `#171E20`, SurfaceContainer `#1B2225`, SurfaceContainerHigh `#22292C`

## Shape & Geometry (Material 3 Expressive)

- **Expressive Hero & Feature Cards**: `28dp` corner radius with tonal surface container.
- **Standard Action Cards**: `24dp` corner radius.
- **Pill Controls & Interactive Chips**: `24dp` to `28dp` corner radius (fully rounded pill buttons).
- **Interactive Inputs**: `16dp` corner radius with prominent active state stroke (`2dp`).
- **Progress Trackers**: Expressive segmented/stepped horizontal indicators (`10dp` thickness) with rounded pill ends.

## Typography

- **Display & Headline**: `headlineLarge` (32sp), `headlineMedium` (28sp), `titleLarge` (22sp) with `letterSpacing="-0.02"` and medium font weight.
- **Body**: `bodyLarge` (16sp), `bodyMedium` (14sp) for readability and accessible text flow.
- **Biometric Numbers**: Monospace and light/medium weight tabular figures for mathematical score clarity (`56sp` primary score, `15sp` vector stats).

## Components & Interaction Patterns

- **Touch Targets**: Strictly `≥ 48dp` touch target height and width across all interactive components with at least `8dp` inter-element spacing.
- **Navigation**: Clean BottomNavigationView with expressive icons and labeled states.
- **Haptic & Visual Feedback**: Tactile button responses, animated progress transitions, and live state chips.
