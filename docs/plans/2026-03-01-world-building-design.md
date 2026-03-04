# World Building System Design

**Date:** 2026-03-01
**Status:** Approved
**Scope:** MegaKit integration, heightmap terrain, scatter system (Client + Server)

## Summary

Replace the current flat placeholder terrain and primitive shape decorations with a proper world building system: real heightmap-based terrain, the Quaternius Stylized Nature MegaKit (116 models), and a hybrid scatter + manual placement pipeline. All three zones (Aerheim, Green Plains, Dark Forest) are affected. Server-side movement validation is updated to use heightmap data.

## Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Asset pack | Quaternius Stylized Nature MegaKit | CC0 license, Ghibli-inspired style matches art direction, Godot source version with shaders included |
| Terrain approach | Pre-computed heightmaps (start), Terrain3D plugin (evaluate later) | Eliminates cross-language floating-point divergence risk; Terrain3D as future upgrade path for faster iteration |
| Heightmap resolution | 512x512 per zone (~20m spacing over 10,200 units) | Good balance of detail and performance; sufficient for rolling hills |
| Asset placement | Hybrid: automated scatter + manual editor placement | Scatter for mass vegetation, manual for landmarks and POIs |
| Grass/flower rendering | MultiMeshInstance3D (GPU instancing) | Required for performance — thousands of instances at 1 draw call |
| Tree/rock rendering | Individual scene instances | Need collision, limited count (~500-1000 per visible area) |

## Architecture

### 1. Heightmap Terrain

#### Data Pipeline

```
Generate heightmap (noise script or external tool)
    → 512x512 raw float file per zone
    → stored in shared/heightmaps/
    → loaded by both Client (Godot) and Server (Kotlin)
```

#### Client

- Replace `PlaneMesh` + `WorldBoundaryShape3D` with heightmap-based terrain mesh
- Use `HeightMapShape3D` for physics collision (matches visual mesh)
- Existing `terrain_noise.gdshader` repurposed for color blending based on height/slope (grass on flat, rock on steep, snow on peaks)
- Per-zone noise parameters remain for visual texture variation on top of the heightmap geometry

#### Server

- Load the same heightmap binary files
- `HeightmapManager` provides `getHeightAt(zoneId, x, z) → Float` via bilinear interpolation
- Movement validation: `abs(clientY - heightAt(x,z)) < tolerance` (tolerance accounts for jumping, slopes, network latency)
- Flying players: skip ground-height check, enforce Y-max and speed limits only

#### Heightmap Parameters Per Zone

| Zone | Amplitude | Character | Notes |
|------|-----------|-----------|-------|
| Aerheim | 2-5m | Gentle, mostly flat | City area needs buildable ground |
| Green Plains | 5-10m | Rolling hills, wide valleys | Open, inviting landscape |
| Dark Forest | 8-15m | Steeper, more rugged | Dense, claustrophobic feel |

#### Generation Tool

A GDScript tool script (`tools/heightmap_generator.gd`) that:
- Takes zone parameters (amplitude, frequency, octaves, seed)
- Generates 512x512 noise-based heightmap
- Exports as raw float binary (`.heightmap`) to `shared/heightmaps/`
- Can be run from Godot editor or command line

#### Future: Terrain3D Plugin

If faster iteration is needed, Terrain3D can replace the custom heightmap mesh with:
- In-editor sculpt brushes
- Texture painting (splatmap)
- LOD built-in
- Export heightmap data for server consumption

The pre-computed approach is designed so that migrating to Terrain3D later only replaces the rendering/editing layer — the server heightmap format and scatter system remain unchanged.

### 2. MegaKit Integration

#### Asset Organization

```
client/assets/nature/
├── materials/          # .gdshader + .tres from MegaKit Godot zip
│   ├── M_Bark.gdshader
│   ├── M_BaseFoliage.gdshader
│   ├── M_Grass.gdshader
│   ├── M_Leaves.gdshader
│   ├── M_Leaves_Pine.gdshader
│   ├── M_Leaves_GiantPine.gdshader
│   └── MI_*.tres        # Material instances
├── textures/           # All .png files from MegaKit
│   ├── Bark_*.png
│   ├── Leaves_*.png
│   ├── Flowers.png
│   ├── Grass.png
│   ├── Mushrooms.png
│   ├── Rocks_Diffuse.png
│   └── Noise_*.png     # Wind/Perlin noise textures
└── models/             # .tscn mesh scenes from MegaKit (organized by type)
    ├── trees/          # 40 tree scenes
    ├── bushes/         # 6 bush scenes
    ├── plants/         # 10 plant scenes
    ├── flowers/        # 14 flower scenes
    ├── grass/          # 7 grass scenes
    ├── ground_cover/   # 12 scenes (clover, fern, mushroom, petal)
    └── rocks/          # 27 scenes (rocks, pebbles, rock paths)
```

#### Import Process

1. Extract `Stylized Nature MegaKit[Godot].zip`
2. Copy `Materials/` → `client/assets/nature/materials/`
3. Copy `assets/*.png` → `client/assets/nature/textures/`
4. Copy `Mesh Scenes/*.tscn` → `client/assets/nature/models/<category>/`
5. Fix resource paths in `.tres` and `.tscn` files to match new directory structure
6. Verify in Godot editor that models render correctly with shaders

### 3. Scatter + Manual Placement System

#### Scatter System

A `ZoneScatter` resource (`.tres`) per zone defines placement rules:

```gdscript
# Example structure
class_name ZoneScatterConfig extends Resource

@export var seed: int
@export var tree_rules: Array[ScatterRule]
@export var grass_rules: Array[ScatterRule]
@export var rock_rules: Array[ScatterRule]
@export var ground_cover_rules: Array[ScatterRule]
@export var exclusion_zones: Array[ExclusionZone]  # spawn points, portals, paths
```

Each `ScatterRule` defines:
- **scenes**: Array of PackedScene to pick from (weighted random)
- **density**: instances per 100m²
- **height_range**: min/max terrain height for placement
- **slope_range**: min/max terrain slope (degrees)
- **scale_variation**: ±percentage for random scaling
- **rotation_random**: Y-axis random rotation (0-360°)
- **collision_enabled**: whether instances get collision shapes

#### Rendering Strategy

| Category | Rendering | Collision | Typical Count/Zone |
|----------|-----------|-----------|-------------------|
| Trees | Individual scene instances | Yes (trunk) | 200-500 |
| Bushes | Individual scene instances | Optional | 100-300 |
| Large rocks | Individual scene instances | Yes | 50-150 |
| Grass | MultiMeshInstance3D | No | 5,000-20,000 |
| Flowers | MultiMeshInstance3D | No | 1,000-5,000 |
| Clover/Fern/Petal | MultiMeshInstance3D | No | 2,000-10,000 |
| Mushrooms | Individual scene instances | No | 20-50 |
| Pebbles | MultiMeshInstance3D | No | 500-2,000 |
| Rock paths | Individual scene instances | Yes | 10-30 |

#### Manual Placement

Landmarks and POIs are placed manually in the Godot editor as child nodes of each zone terrain scene. Examples:
- Feature tree group near quest giver
- Rock formation at dungeon entrance
- Flower meadow at zone transition
- Cleared area around spawn points

#### Zone-to-Asset Mapping

| Zone | Trees | Bushes | Ground Cover | Flowers | Rocks |
|------|-------|--------|-------------|---------|-------|
| **Green Plains** | CommonTree, Birch, CherryBlossom | All with flowers | Grass (all), Clover, Fern | All flower types | Medium rocks, Pebbles |
| **Dark Forest** | TwistedTree, TallThick, DeadTree, GiantPine | Bush_Common (no flowers), Bush_Long | Fern, Mushrooms | Petals only | Big rocks, Medium rocks |
| **Aerheim Outskirts** | CommonTree, Pine, Birch | Bush_Common, Bush_Large | Grass (short only), Clover | Sparse flowers | Rock paths, Pebbles |

## Server Changes

### HeightmapManager

New component in `world-service`:
- Loads `.heightmap` binary files on startup (one per zone)
- Provides `getHeightAt(zoneId: Int, x: Float, z: Float): Float`
- Bilinear interpolation between grid samples
- O(1) lookup, ~12 MB total memory for 3 zones at 512x512

### Movement Validation Update

Current validation in the world service checks `position.y` against fixed bounds. Updated logic:

```
groundHeight = heightmapManager.getHeightAt(zoneId, position.x, position.z)
if (isFlying):
    validate: position.y >= groundHeight && position.y <= MAX_Y
else:
    validate: abs(position.y - groundHeight) <= GROUND_TOLERANCE
```

`GROUND_TOLERANCE` = 2.0 units (accounts for jumping, slopes, and network jitter).

### WorldConstants Update

- Remove fixed `GROUND_Y = 0` and `MAX_GROUND_Y = 1.0`
- Add `GROUND_TOLERANCE = 2.0`
- Zone spawn positions updated with correct Y from heightmap

## Performance Budget

| Metric | Target | Notes |
|--------|--------|-------|
| Terrain draw calls | 1-4 per zone | Chunked mesh or single large mesh |
| Vegetation draw calls | 10-20 per zone | MultiMesh batches + individual trees |
| Total triangles (visible) | < 500K | LOD for distant objects |
| Server height lookup | < 1ms per 1000 lookups | Bilinear interpolation on cached array |
| Heightmap memory (server) | ~12 MB total | 3 zones × 512×512 × 4 bytes |
| Heightmap memory (client) | ~4 MB per zone | Only active zone loaded |

## Risk Mitigation

| Risk | Mitigation |
|------|-----------|
| Cross-language height divergence | Pre-computed heightmaps loaded by both sides (no runtime generation) |
| Terrain rendering performance | Start with 512x512, chunk if needed, Terrain3D as upgrade path |
| Grass performance | MultiMeshInstance3D mandatory, frustum culling, distance fade |
| MegaKit shader compatibility | Godot source version tested for 4.2.2+, verify on 4.6 early |
| Scatter placement on slopes | Height/slope sampling from heightmap before placing, skip invalid spots |

## Out of Scope

- Water/rivers (future feature)
- Day/night cycle (future feature)
- Weather effects (future feature)
- Terrain destruction/modification at runtime
- LOD system for vegetation (optimize later if needed)
