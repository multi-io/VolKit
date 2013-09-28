//#version 130

varying vec4 mycolor;
varying float depth;

void main() {
	gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
	depth = 3 * gl_Position.z;
	gl_TexCoord[0] = gl_TextureMatrix[0] * gl_MultiTexCoord0;
	mycolor = gl_Color;
}