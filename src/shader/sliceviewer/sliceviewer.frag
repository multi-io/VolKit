//#version 130

uniform sampler3D tex;
uniform float scale;
uniform float offset;

void main() {
    if (all(greaterThanEqual(gl_TexCoord[0].xyz, vec3(0,0,0))) && all(lessThanEqual(gl_TexCoord[0].xyz, vec3(1,1,1)))) { //clamp to [0,1] tex coords
        gl_FragColor.rgb = scale * texture3D(tex, gl_TexCoord[0].xyz).rgb + offset;
        gl_FragColor.a = 1.0;
    }
}
