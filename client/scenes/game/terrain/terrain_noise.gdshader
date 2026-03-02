shader_type spatial;

// Terrain heightmap shader — shared by all zone terrains.
// Displaces vertices on the Y axis by sampling a heightmap texture so that
// the visual mesh exactly matches the HeightMapShape3D collision surface.
// Colors are blended based on the sampled height for natural variation.

uniform sampler2D heightmap_texture : filter_linear, repeat_disable;
uniform float world_size = 10200.0;
uniform float amplitude : hint_range(0.0, 50.0) = 8.0;
uniform vec3 base_color : source_color = vec3(0.28, 0.52, 0.18);
uniform vec3 valley_color : source_color = vec3(0.22, 0.42, 0.14);
uniform vec3 hill_color : source_color = vec3(0.34, 0.58, 0.22);
uniform float roughness : hint_range(0.0, 1.0) = 0.9;

void vertex() {
	// PlaneMesh vertices range from -size/2 to +size/2.
	// Map to 0..1 UV for the heightmap texture.
	vec2 uv = (VERTEX.xz / world_size) + 0.5;
	float height = texture(heightmap_texture, uv).r;
	VERTEX.y += height;

	// Recalculate normal from neighboring heightmap samples for correct lighting.
	float texel = 1.0 / float(textureSize(heightmap_texture, 0).x);
	float hL = texture(heightmap_texture, uv + vec2(-texel, 0.0)).r;
	float hR = texture(heightmap_texture, uv + vec2(texel, 0.0)).r;
	float hD = texture(heightmap_texture, uv + vec2(0.0, -texel)).r;
	float hU = texture(heightmap_texture, uv + vec2(0.0, texel)).r;
	// Scale normal calculation by the ratio of world_size to heightmap resolution
	// so that the slope is correct in world space.
	float cell_size = world_size / float(textureSize(heightmap_texture, 0).x);
	NORMAL = normalize(vec3(hL - hR, 2.0 * cell_size, hD - hU));
}

void fragment() {
	// Blend color based on vertex height for natural terrain variation.
	float h = VERTEX.y / max(amplitude, 0.001);
	float t = clamp(h, 0.0, 1.0);
	vec3 color = mix(valley_color, hill_color, t);
	color = mix(base_color, color, 0.5);
	ALBEDO = color;
	ROUGHNESS = roughness;
}
