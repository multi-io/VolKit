//#version 130

uniform sampler3D tex;
uniform float scale;
uniform float offset;

void main() {
    gl_FragColor.rgb = scale * texture3D(tex, gl_TexCoord[0].xyz).rgb + offset;
    gl_FragColor.a = 1.0;
}
