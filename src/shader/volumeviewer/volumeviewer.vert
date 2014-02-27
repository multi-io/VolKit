//#version 130

varying vec4 fragCoordsInWorld;

void main() {
	gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
	fragCoordsInWorld = gl_ModelViewMatrix * gl_Vertex;
	gl_TexCoord[0] = gl_TextureMatrix[0] * gl_MultiTexCoord0;
}