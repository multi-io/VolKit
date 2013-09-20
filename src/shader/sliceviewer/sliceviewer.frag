//#version 130

//uniform sampler3D tex;
uniform sampler2D tex;

void main() {
    //gl_FragColor.rgb = 10*texture3D(tex, gl_TexCoord[0].str).rgb; // + vec3(0.5,0,0);
    //gl_FragColor.r = gl_TexCoord[0][1];
    gl_FragColor.rgb = texture2D(tex, gl_TexCoord[0].st).rgb;
    //gl_FragColor.r += gl_TexCoord[0][1];
    gl_FragColor.a = 1.0;
}
