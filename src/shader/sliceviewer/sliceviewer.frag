//#version 130

uniform sampler3D tex;
uniform float scale;
uniform float offset;

void main() {
    gl_FragColor.rgb = scale * texture3D(tex, gl_TexCoord[0].str).rgb + offset;
    gl_FragColor.r = gl_TexCoord[0][2];
    gl_FragColor.a = 1.0;
}
