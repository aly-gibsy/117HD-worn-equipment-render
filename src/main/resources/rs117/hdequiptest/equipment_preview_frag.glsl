#version 330

uniform sampler2D previewTexture;

in vec2 fUv;

out vec4 FragColor;

void main() {
    // The preview is rendered with premultiplied alpha
    FragColor = texture(previewTexture, fUv);
}
