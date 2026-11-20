# Spec Delta

## Purpose

Defines how the app's launch window and Android 12+ splash screen look before the first frame draws, so startup matches Dewfall's light, dark, and dynamic colors.

## ADDED Requirements

### Requirement: Launch window matches Dewfall's surface on Android 10 and 11
On Android 10 and 11, where dynamic color is not available, the launch window background before the first frame SHALL match Dewfall's `surface` color token: `#F5FBF2` in light mode and `#101411` in dark mode.

#### Scenario: Opening in light mode on Android 10 or 11
- **WHEN** the user launches the app in light mode on Android 10 or 11
- **THEN** the launch window before the first frame uses `#F5FBF2`

#### Scenario: Opening in dark mode on Android 10 or 11
- **WHEN** the user launches the app in dark mode on Android 10 or 11
- **THEN** the launch window before the first frame uses `#101411`

### Requirement: Launch window follows dynamic color on Android 12 and later
On Android 12 and later, the launch window background before the first frame SHALL follow the device's dynamic color palette in light and dark mode. On Android 14 and later it SHALL match the system dynamic `surface` color exactly; on Android 12 and 13 it SHALL use the closest platform dynamic neutral-variant tone.

#### Scenario: Opening in light mode on Android 14 or later
- **WHEN** the user launches the app in light mode on Android 14 or later
- **THEN** the launch window background uses the device's light dynamic surface color

#### Scenario: Opening in dark mode on Android 14 or later
- **WHEN** the user launches the app in dark mode on Android 14 or later
- **THEN** the launch window background uses the device's dark dynamic surface color

#### Scenario: Opening on Android 12 or 13
- **WHEN** the user launches the app on Android 12 or 13 in light or dark mode
- **THEN** the launch window background follows the wallpaper's dynamic neutral-variant palette rather than platform grey or Dewfall's static palette

#### Scenario: Changing the wallpaper
- **WHEN** the user changes the device wallpaper on Android 12 or later and cold-starts the app
- **THEN** the launch window background reflects the new wallpaper's palette

### Requirement: Android 12+ splash screen background matches the launch window
On Android 12 and later, the system splash screen background SHALL match the launch window's dynamic surface color in light and dark mode. Until Dewfall has its own app icon, the splash screen SHALL keep the platform's default launcher icon behavior without adding a branding image or artificial launch delay.

#### Scenario: Cold start in light mode on Android 12 or later
- **WHEN** the user cold-starts the app in light mode on Android 12 or later
- **THEN** the system splash screen fills its background with the light dynamic surface color and transitions into the first screen without flashing platform grey

#### Scenario: Cold start in dark mode on Android 12 or later
- **WHEN** the user cold-starts the app in dark mode on Android 12 or later
- **THEN** the system splash screen fills its background with the dark dynamic surface color and transitions into the first screen without flashing platform grey

### Requirement: Launch window status bar and system bar icons match the theme
Before the app draws its first frame, the launch window SHALL request dark status bar and navigation bar icons in light mode and light icons in dark mode. On Android 10 and 11, the launch window's status bar background SHALL match Dewfall's `surface` color instead of the platform's default grey or black.

#### Scenario: Launch window bar contrast in light mode
- **WHEN** the user launches the app in light mode on Android 10 or 11
- **THEN** the launch window shows dark status bar icons on `#F5FBF2` instead of light icons on grey

#### Scenario: Launch window bar contrast in dark mode
- **WHEN** the user launches the app in dark mode on Android 10 or 11
- **THEN** the launch window shows light status bar icons on `#101411`

### Requirement: Window fallback and task background match the launch surface
The application window theme's background color SHALL match the launch surface color across all supported Android versions and light, dark, and dynamic themes, so multi-window resizing, Recents, and every activity inheriting the application theme share the same surface color.

#### Scenario: Resizing in multi-window
- **WHEN** the user resizes the app window in split screen or freeform mode
- **THEN** any unfilled window area behind the resizing surface shows the theme's surface color instead of platform grey

#### Scenario: Launching another activity that inherits the application theme
- **WHEN** a debug user launches the YouTube debug screen
- **THEN** its launch window uses the same light, dark, or dynamic surface color as the main app
