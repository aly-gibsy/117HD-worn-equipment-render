#version 330

uniform sampler2D previewTexture;
// Area left uncovered so interface elements drawn there stay visible, in texture coordinates: (minU, minV, maxU, maxV)
uniform vec4 maskRect;

in vec2 fUv;

out vec4 FragColor;

void main() {
    if (all(greaterThanEqual(fUv, maskRect.xy)) && all(lessThan(fUv, maskRect.zw)))
        discard;
    // The preview is rendered with premultiplied alpha
    FragColor = texture(previewTexture, fUv);
}
