shader_type spatial;

// Terrain noise shader — shared by GreenPlains and DarkForest.
// Displaces vertices on the Y axis using procedural noise for a
// natural landscape look while keeping physics collision flat at Y=0.

uniform vec3 base_color : source_color = vec3(0.28, 0.52, 0.18);
uniform vec3 valley_color : source_color = vec3(0.22, 0.42, 0.14);
uniform vec3 hill_color : source_color = vec3(0.34, 0.58, 0.22);
uniform float amplitude : hint_range(0.0, 0.5) = 0.4;
uniform float frequency : hint_range(0.0001, 0.01) = 0.002;
uniform float roughness : hint_range(0.0, 1.0) = 0.9;

// Simple hash-based noise (no texture dependency).
vec2 hash22(vec2 p) {
	vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
	p3 += dot(p3, p3.yzx + 33.33);
	return fract((p3.xx + p3.yz) * p3.zy);
}

float value_noise(vec2 p) {
	vec2 i = floor(p);
	vec2 f = fract(p);
	f = f * f * (3.0 - 2.0 * f); // smoothstep
	float a = dot(hash22(i), vec2(1.0));
	float b = dot(hash22(i + vec2(1.0, 0.0)), vec2(1.0));
	float c = dot(hash22(i + vec2(0.0, 1.0)), vec2(1.0));
	float d = dot(hash22(i + vec2(1.0, 1.0)), vec2(1.0));
	return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p, int octaves) {
	float value = 0.0;
	float amp = 0.5;
	float freq = 1.0;
	for (int i = 0; i < octaves; i++) {
		value += amp * value_noise(p * freq);
		freq *= 2.0;
		amp *= 0.5;
	}
	return value;
}

void vertex() {
	vec2 world_uv = VERTEX.xz * frequency;
	float height = fbm(world_uv, 4) * amplitude;
	VERTEX.y += height;
	// Recalculate normal from neighboring samples for correct lighting.
	float eps = 1.0;
	float hL = fbm((VERTEX.xz + vec2(-eps, 0.0)) * frequency, 4) * amplitude;
	float hR = fbm((VERTEX.xz + vec2(eps, 0.0)) * frequency, 4) * amplitude;
	float hD = fbm((VERTEX.xz + vec2(0.0, -eps)) * frequency, 4) * amplitude;
	float hU = fbm((VERTEX.xz + vec2(0.0, eps)) * frequency, 4) * amplitude;
	NORMAL = normalize(vec3(hL - hR, 2.0 * eps, hD - hU));
}

void fragment() {
	// Blend color based on vertex height for subtle variation.
	float h = VERTEX.y / max(amplitude, 0.001);
	float t = clamp(h, 0.0, 1.0);
	vec3 color = mix(valley_color, hill_color, t);
	color = mix(base_color, color, 0.5);
	ALBEDO = color;
	ROUGHNESS = roughness;
}
