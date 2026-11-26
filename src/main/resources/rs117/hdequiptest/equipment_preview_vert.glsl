#version 330

// Target rectangle in normalized device coordinates: (minX, minY, maxX, maxY)
uniform vec4 targetRect;

out vec2 fUv;

void main() {
    // Triangle strip quad without a vertex buffer
    vec2 corner = vec2(gl_VertexID & 1, (gl_VertexID >> 1) & 1);
    fUv = corner;
    gl_Position = vec4(mix(targetRect.xy, targetRect.zw, corner), 0, 1);
}
