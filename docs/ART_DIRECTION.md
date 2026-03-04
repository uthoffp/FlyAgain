# Art Direction: Stylized PBR

## Visual Identity

FlyAgain uses a **Stylized PBR** approach — Godot's built-in PBR rendering with stylized textures, warm lighting, and post-processing effects. The goal is a playful, inviting look inspired by games like Genshin Impact (anime characters) and Coral Island (warm, colorful world).

**Not** photorealistic. **Not** low-poly/retro. Think: painterly, saturated, warm, fantasy.

## Rendering Pipeline

### Post-Processing (WorldEnvironment)
| Effect | Setting | Purpose |
|--------|---------|---------|
| Tonemapping | ACES | Filmic, warm color response |
| Bloom/Glow | Low threshold | Fantasy glow on magic objects, portals, lights |
| SSAO | Light | Subtle depth without realism |
| Color Grading | Warm temperature, +saturation | Coral Island warmth |
| Volumetric Fog | Zone-dependent | Atmosphere and depth |

### Lighting Per Zone
| Zone | Sun Color | Ambient | Mood |
|------|-----------|---------|------|
| Green Plains | Warm yellow-white | Light sky blue | Sunny, inviting |
| Dark Forest | Cool blue-white | Deep blue-green | Moody, mysterious |
| Aerheim City | Golden-warm | Warm amber | Welcoming, safe |

### Sky
- Saturated, warm sky colors (not washed out)
- Zone-specific sky tinting via environment overrides

## Material Guidelines

All 3D materials use `StandardMaterial3D` with these conventions:

| Parameter | Range | Reason |
|-----------|-------|--------|
| Roughness | 0.7 - 0.9 | Matte, painterly surfaces |
| Metallic | 0.0 - 0.1 | No realistic metal sheen |
| Emission | Per object | Glow on magic/portal/light objects |

Textures should look **hand-painted** — saturated colors, soft detail, no photographic textures.

## Asset Pipeline

### Workflow: AI Generation to Godot
1. **Meshy AI / Tripo3D** — Generate base model from text prompt or reference image
2. **Blender** — Cleanup: reduce poly count, fix UV maps, retopology if needed
3. **Textures** — Stylized PBR: painterly, saturated, low fine detail
4. **Export** — glTF 2.0 (.glb) — Godot's preferred format
5. **Godot** — Apply StandardMaterial3D with guidelines above

### Character Models
- **Tripo3D** Auto-Rigging for skeleton setup
- **Mixamo** for standard animations (Walk, Run, Idle, Attack, Jump, Fly)
- Stylized proportions: slightly oversized head, expressive eyes (anime influence)

### Poly Budget (Target)
| Asset Type | Triangle Budget |
|------------|----------------|
| Player character | 5,000 - 10,000 |
| Monster | 3,000 - 8,000 |
| Building | 2,000 - 5,000 |
| Prop (tree, rock) | 500 - 2,000 |
| Terrain | Procedural shader |

## Terrain

Procedural shader-based (`terrain_noise.gdshader`) with:
- Warm, saturated color palettes per zone
- Texture splatting (grass, dirt, stone) for visual depth
- Stylized, not photorealistic textures

## Color Palette Reference

### Green Plains
- Base: warm greens with yellow undertone
- Accents: wildflower colors (purple, yellow, pink)
- Sky: bright blue, puffy white clouds

### Dark Forest
- Base: deep greens, mossy browns
- Accents: bioluminescent blues and purples
- Sky: muted, overcast, filtered light

### Aerheim City
- Base: warm stone, golden wood
- Accents: banners, market stalls, warm lantern light
- Sky: golden hour warmth

## Pre-Made Asset Packs (Environment)

For standard environment assets (trees, grass, rocks, buildings) that don't need to be custom, use cohesive pre-made packs. Custom/unique assets (characters, monsters, special props) are AI-generated per the pipeline above.

### Quaternius Stylized Nature MegaKit (Selected)

**Decision:** The Stylized Nature MegaKit by Quaternius is the primary environment asset pack for all nature/outdoor zones. Its soft, rounded, Ghibli-inspired aesthetic matches FlyAgain's Stylized PBR direction.

- **Source:** https://quaternius.itch.io/stylized-nature-megakit
- **License:** CC0 1.0 Universal (Public Domain) — free for all use
- **Version used:** Godot Source version (includes stylized shaders)
- **Godot compatibility:** 4.2.2+ (confirmed working with Godot 4.6)

#### Asset Inventory (116 models)

| Category | Count | Models |
|----------|-------|--------|
| **Trees** | 40 | Birch (5), CherryBlossom (5), CommonTree (5), DeadTree (5), GiantPine (5), Pine (5), TallThick (5), TwistedTree (5) |
| **Bushes** | 6 | Bush_Common, Bush_Common_Flowers, Bush_Large, Bush_Large_Flowers, Bush_Long_1, Bush_Long_2 |
| **Plants** | 10 | Plant_1–7 (incl. _Big variants) |
| **Flowers** | 14 | Flower_1–4 (Group + Single), Flower_6, Flower_6_2, Flower_7 (Group + Single) |
| **Grass** | 7 | Common (Short/Tall), Wide (Short/Tall), Wispy (Short/Tall), Wheat |
| **Ground Cover** | 12 | Clover (2), Fern (2), Mushroom (4), Petal (6) |
| **Rocks** | 17 | Rock_Big (2), Rock_Medium (4), Pebble_Round (5), Pebble_Square (6) |
| **Rock Paths** | 10 | RockPath_Round (5), RockPath_Square (5) |

#### Included Shaders

The Godot source version includes custom stylized shaders:

| Shader | Purpose |
|--------|---------|
| `M_Bark.gdshader` | Stylized bark rendering for tree trunks |
| `M_BaseFoliage.gdshader` | Base shader for bush/plant foliage |
| `M_Grass.gdshader` | Grass with wind animation |
| `M_Leaves.gdshader` | Tree leaves with wind sway |
| `M_Leaves_Pine.gdshader` | Pine-specific leaf shader |
| `M_Leaves_GiantPine.gdshader` | Giant pine leaf shader variant |

#### Textures (36 files)

Bark textures (diffuse + normal) for each tree type, leaf textures (diffuse + color variants), flower/grass/mushroom/rock atlas textures, and utility textures (Perlin noise, wind noise).

#### Zone-to-Asset Mapping

| Zone | Primary Trees | Ground Cover | Accents |
|------|--------------|--------------|---------|
| **Green Plains** | CommonTree, Birch, CherryBlossom | Grass (all), Clover, Fern | Flowers, Bushes with flowers |
| **Dark Forest** | TwistedTree, TallThick, DeadTree, GiantPine | Fern, Mushrooms | Petals, Plants (dark variants) |
| **Aerheim Outskirts** | CommonTree, Pine, Birch | Grass (short), Clover | Bushes, Rock paths |

### Future: Medieval Village MegaKit

For Aerheim City buildings and structures, the **Medieval Village MegaKit** (300+ modular pieces) from Quaternius is a planned addition. Same stylistic family.

- **Source:** https://quaternius.itch.io/medieval-village-megakit

### Import Workflow (Stylized Nature MegaKit)

The Godot source version provides ready-to-use `.tscn` mesh scenes with materials pre-applied:

1. Extract Godot project zip to `client/assets/nature/`
2. Copy `Materials/` (shaders + material instances) and `Mesh Scenes/` (per-model `.tscn`)
3. Copy `assets/` (textures with `.import` metadata)
4. Scenes are ready to instantiate — shaders, wind animation, and materials are pre-configured
5. Post-processing stack (ACES, bloom, warm color grading) further unifies the look

## Tools

| Tool | Purpose | Link |
|------|---------|------|
| Meshy AI | AI 3D model generation | https://www.meshy.ai/ |
| Tripo3D | AI 3D models + auto-rigging | https://www.tripo3d.ai/ |
| Blender | 3D cleanup, rigging, UV editing | https://www.blender.org/ |
| Mixamo | Character animations | https://www.mixamo.com/ |
| Godot 4 | Game engine, material setup | https://godotengine.org/ |
