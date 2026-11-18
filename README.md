# Visuals

A client-side Fabric mod for Minecraft Java **1.21.11** (Java 21). It is designed for visual and ambient customization only: it does not add blocks, items, entities with gameplay behavior, or server-side mechanics.

## Current implementation status

| Area | Status |
|---|---|
| Central option registry and typed configuration | Implemented |
| Versioned JSON config, delayed atomic save, corrupt-file backup | Implemented |
| Fullscreen settings screen, tab navigation and option widgets | Implemented |
| Sliders, dropdowns, color picker, text editing and reset/context actions | Implemented |
| Search, presets, import/export and undo/redo UI | Present in the current code; needs runtime verification |
| Preview scene and preview-only time/season/weather/wind controls | Implemented as a stylized GUI preview |
| Settings catalogue for all 16 planned categories | Registered |
| English and German labels for the settings catalogue | Added |
| In-world particle effects | Partially implemented: fireflies, falling leaves, wildlife-style particles and touch feedback |
| Plant bending / footprints / water rings | Particle approximations only; no model deformation, persistent decals or reflective water shader |
| Remaining renderer, weather, lighting and audio categories | Not yet implemented |
| Spotify account/API integration | Not implemented; requires a separate integration design |

**Important:** A registered setting is not proof that its corresponding in-world effect is active. The implemented particle effects are limited to the areas listed above. Wildlife is represented by vanilla particles, not animated animal entities. Plant response, water rings and footprints are also visual particle approximations. Other categories still need their renderer/audio hooks. The preview is a GUI illustration, not a replacement for the Minecraft world renderer.

## Categories

General, Sky, Glint, Seasons, Weather & Fog, Light, Foliage & Particles, Fireflies, Puddles, Wildlife, Biomes, World Events, Touch, Sound, AFK/Cinema and Spotify.

## Build

```bash
./gradlew build
./gradlew runClient
```

The resulting mod JAR is in `build/libs/`.

## Runtime requirements

- Minecraft Java 1.21.11
- Java 21
- Fabric Loader and Fabric API versions configured in `gradle.properties`

## Configuration

The configuration is stored at `config/visuals/config.json`. Custom presets are stored below `config/visuals/presets/`. The registry is the source of truth for option IDs, types, defaults, limits and dependencies.

## Development notes

- Keep this mod client-only.
- Do not introduce gameplay-affecting blocks, items, entities or server logic.
- Keep optional integrations optional and check compatibility before using renderer-specific APIs.
- Do not claim an effect is implemented until its runtime behavior has been verified in a Minecraft client.
