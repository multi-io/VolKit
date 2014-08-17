//#version 130

uniform sampler3D tex;
uniform float scale;
uniform float offset;

uniform vec3 debugColor;
uniform float debugZ;
uniform float sliceCountFactor;

void main() {
    float intensity = scale * texture3D(tex, gl_TexCoord[0].xyz).r + offset;
    gl_FragColor.rgba = vec4(intensity,intensity,intensity,intensity);
    //TODO: handle sliceCountFactor correctly (how?)
    //gl_FragColor.a = 1 - (1 - intensity) * sliceCountFactor;
    
    //gl_FragColor.r = intensity * (debugZ / 2 + 0.5);  //debugging (visualize back-to-front direction)
    //gl_FragColor.rgb = debugColor; gl_FragColor.a = 1;  //debugging (visualize z buffer accuracy)
}
