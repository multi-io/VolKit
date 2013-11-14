//#version 130

uniform sampler3D tex;
uniform float scale;
uniform float offset;
uniform vec3 debugColor;
uniform float debugZ;

void main() {
    float intensity = scale * texture3D(tex, gl_TexCoord[0].xyz).r + offset;
    gl_FragColor.rgba = intensity;
    gl_FragColor.r = intensity * (debugZ / 2 + 0.5);
    //gl_FragColor.rgb = debugColor; gl_FragColor.a = 1;  //debugging (visualize z buffer accuracy)
}
