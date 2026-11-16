#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform vec2 GlassSize;
uniform float GlassRadius;
uniform vec4 SampleUv;

in vec2 texCoord0;

out vec4 fragColor;

float roundedDistance(vec2 pixel, vec2 size, float radius) {
    vec2 halfSize = size * 0.5;
    float clampedRadius = min(radius, min(halfSize.x, halfSize.y));
    vec2 point = pixel - halfSize;
    vec2 corner = abs(point) - (halfSize - vec2(clampedRadius));
    return length(max(corner, 0.0)) - clampedRadius;
}

void main() {
    vec2 pixel = texCoord0 * GlassSize;
    float distance = roundedDistance(pixel, GlassSize, GlassRadius);
    float alpha = 1.0 - smoothstep(0.0, 1.25, distance);
    if (alpha <= 0.001) {
        discard;
    }
    vec2 sampleUv = vec2(mix(SampleUv.x, SampleUv.z, texCoord0.x), mix(SampleUv.y, SampleUv.w, texCoord0.y));
    vec4 color = texture(Sampler0, sampleUv);
    fragColor = vec4(color.rgb, color.a * alpha) * ColorModulator;
}
