#ifdef GL_ES
precision highp float;
#endif

varying vec2 v_texCoord;

uniform sampler2D u_texture;

void main (void) {
    vec4 color = vec4(texture2D(u_texture, v_texCoord));
    gl_FragColor = color;
}