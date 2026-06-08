#version 300 es

// CRT post-processing pass used in place of the plain blit. It does NOT try to be a pixel accurate CRT emulator
// (that looks bad on high res games like Undertale's 640x480 upscaled to a phone), it just evokes the CRT feeling
precision highp float;

in vec2 vTexCoord;
uniform sampler2D uTexture;
uniform vec2 uResolution;
out vec4 fragColor;

// Gentle barrel distortion so the image bows out like the curved glass of a CRT
vec2 curveRemap(vec2 uv) {
    uv = uv * 2.0 - 1.0;
    vec2 offset = abs(uv.yx) / vec2(7.0, 5.0);
    uv += uv * offset * offset;
    return uv * 0.5 + 0.5;
}

void main() {
    vec2 uv = curveRemap(vTexCoord);

    // The curvature pushes the corners past the panel edge, paint that area as the black bezel
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    // Subtle horizontal chromatic aberration, stronger toward the edges where a CRT lens fringes the most
    float edge = distance(uv, vec2(0.5));
    float aberration = edge / uResolution.x * 4.0;
    vec3 color;
    color.r = texture(uTexture, uv + vec2(aberration, 0.0)).r;
    color.g = texture(uTexture, uv).g;
    color.b = texture(uTexture, uv - vec2(aberration, 0.0)).b;

    // The sampled texels are sRGB encoded, but the beam/scanline/mask math models actual light intensity, so do it in linear space
    // Multiplying scanlines straight onto sRGB values crushes the midtones and reads muddy, working in linear keeps it looking lit from behind
    color = pow(color, vec3(2.2));

    // Linear luminance, used to bloom the scanlines: a real beam fattens in bright areas so the lines nearly vanish in highlights
    float lum = dot(color, vec3(0.2126, 0.7152, 0.0722));

    // Scanlines derived from the CURVED uv so they bow with the glass, with the count tied to resolution so density is consistent across devices
    // fract keeps the sin argument tiny, dodging mobile GPU large argument precision loss on tall screens
    // A sharpened (pow) dark band keeps the lines visible on dense phone panels instead of averaging out
    float lines = uResolution.y / 5.0;
    float scanPhase = fract(uv.y * lines);
    float scan = pow(0.5 + 0.5 * sin(scanPhase * 6.2831853), 2.0);
    float scanDepth = mix(0.35, 0.08, lum);
    color *= 1.0 - scanDepth * scan;

    // Faint aperture grille columns for a hint of phosphor structure
    float maskPhase = fract(gl_FragCoord.x / 3.0);
    float mask = 0.5 + 0.5 * sin(maskPhase * 6.2831853);
    color *= 1.0 - 0.05 * mask;

    // Vignette so the brightness falls off toward the corners
    float vignette = uv.x * (1.0 - uv.x) * uv.y * (1.0 - uv.y);
    vignette = clamp(pow(vignette * 16.0, 0.20), 0.0, 1.0);
    color *= vignette;

    // Compensate for the darkening the scanlines, mask and vignette introduce
    color *= 1.18;

    // Back to sRGB for display
    color = pow(color, vec3(1.0 / 2.2));

    fragColor = vec4(color, 1.0);
}
