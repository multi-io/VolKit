//#version 130

uniform float transpCoeff;

varying vec4 mycolor;
varying float depth;

void main() {
    //gl_FragColor.rgb = vec3(0,1,1);
    gl_FragColor = mycolor;
    gl_FragColor.a = 1.0 - abs(transpCoeff * depth);
}
