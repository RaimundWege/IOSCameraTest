#ifdef GL_ES
precision highp float;
#endif

varying vec2 v_texCoord;
varying vec2 v_texCoordChroma;

uniform sampler2D y_texture;
uniform sampler2D uv_texture;

void main (void) {
    float r, g, b, y, u, v;

    // We had put the Y values of each pixel to the R, G, B components
    // by GL_LUMINANCE,
    // that's why we're pulling it from the R component, we could
    // also use G or B
    y = texture2D(y_texture, v_texCoord).r;

    // We had put the U and V values of each pixel to the A and
    // R, G, B components of the
    // texture respectively using GL_LUMINANCE_ALPHA. Since U,V
    // bytes are interspread
    // in the texture, this is probably the fastest way to use them
    // in the shader
    u = texture2D(uv_texture, v_texCoord).a - 0.5;
    v = texture2D(uv_texture, v_texCoord).r - 0.5;

    // The numbers are just YUV to RGB conversion constants
    r = y + 1.13983 * v;
    g = y - 0.39465 * u - 0.58060 * v;
    b = y + 2.03211 * u;

    // We finally set the RGB color of our pixel
    gl_FragColor = vec4(r, g, b, 1.0);
}