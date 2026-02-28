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

### Quaternius MegaKit Series (Primary Choice)
Both packs are confirmed to match stylistically — soft, rounded, Ghibli-inspired look.

| Pack | Contents | Link |
|------|----------|------|
| Stylized Nature MegaKit | 110+ models: 40 trees, 35 plants/flowers, 27 rocks, grass, bushes | https://quaternius.itch.io/stylized-nature-megakit |
| Medieval Village MegaKit | 300+ modular pieces: walls, roofs, stairs, props (grid-based) | https://quaternius.itch.io/medieval-village-megakit |

- **Formats:** glTF, FBX, OBJ (free) — Godot source version with custom shaders via Patreon ($20/month)
- **License:** Free for personal, educational, and commercial use
- **Godot shaders included:** Wind movement for grass/leaves, stylized shading

### Import Workflow (Pre-Made Packs)
1. Import glTF/glb into Godot
2. Apply `StandardMaterial3D` overrides if needed (roughness 0.7-0.9, metallic 0.0-0.1)
3. Post-processing stack (ACES, bloom, warm color grading) unifies look with custom assets

## Tools

| Tool | Purpose | Link |
|------|---------|------|
| Meshy AI | AI 3D model generation | https://www.meshy.ai/ |
| Tripo3D | AI 3D models + auto-rigging | https://www.tripo3d.ai/ |
| Blender | 3D cleanup, rigging, UV editing | https://www.blender.org/ |
| Mixamo | Character animations | https://www.mixamo.com/ |
| Godot 4 | Game engine, material setup | https://godotengine.org/ |
