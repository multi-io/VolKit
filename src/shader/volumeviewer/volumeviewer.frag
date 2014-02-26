//#version 130

uniform sampler3D tex;
uniform float scale;
uniform float offset;

uniform vec3 debugColor;
uniform float debugZ;
uniform float sliceCountFactor;

uniform vec4 cutterPlane;  // = x,y,z,lambda in dot(vec3(x,y,z), point_on_plane) == lambda

void main() {
    if (dot(gl_FragCoord.xyz, cutterPlane.xyz) > cutterPlane.w) {
        float intensity = scale * texture3D(tex, gl_TexCoord[0].xyz).r + offset;
        gl_FragColor.rgba = intensity;
        //TODO: handle sliceCountFactor correctly (how?)
        //gl_FragColor.a = 1 - (1 - intensity) * sliceCountFactor;
        
        //gl_FragColor.r = intensity * (debugZ / 2 + 0.5);  //debugging (visualize back-to-front direction)
        //gl_FragColor.rgb = debugColor; gl_FragColor.a = 1;  //debugging (visualize z buffer accuracy)
    }
}
