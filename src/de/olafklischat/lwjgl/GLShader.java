package de.olafklischat.lwjgl;

import static org.lwjgl.opengl.EXTGeometryShader4.GL_GEOMETRY_INPUT_TYPE_EXT;
import static org.lwjgl.opengl.EXTGeometryShader4.GL_GEOMETRY_OUTPUT_TYPE_EXT;
import static org.lwjgl.opengl.EXTGeometryShader4.GL_GEOMETRY_SHADER_EXT;
import static org.lwjgl.opengl.EXTGeometryShader4.GL_GEOMETRY_VERTICES_OUT_EXT;
import static org.lwjgl.opengl.GL11.GL_TRUE;
import static org.lwjgl.opengl.GL20.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_INFO_LOG_LENGTH;
import static org.lwjgl.opengl.GL20.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;

import java.util.HashMap;

import org.lwjgl.opengl.EXTGeometryShader4;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/**
 * 
 * @author oliver
 * @author olaf -- minimum-effort port to LWJGL (w/o ARB shaders)
 */
public class GLShader extends Shader
{

	protected boolean useGeomShader;
	protected int inputGeom;
	protected int outputGeom;
	protected int vertOut;
	
	protected int shader, program;
    
    protected int compiled, linked;
    
    protected HashMap<String, Integer> uniformMap = new HashMap<String, Integer>();

	public GLShader(String fname) throws Exception
	{
		this(fname, false, -1, -1, -1 );
	}

	public GLShader(String shaderName, boolean useGeomShader,
			int inputGeom, int outputGeom, int vertOut) throws Exception
	{
		super(shaderName);

		this.useGeomShader = useGeomShader;
		this.inputGeom = inputGeom;
		this.outputGeom = outputGeom;
		this.vertOut = vertOut;
		
		this.shader = -1;
		this.program = -1;
		this.compiled = GL11.GL_FALSE;
		
		this.linked = GL11.GL_FALSE;
		
		setupShader();
		
	}
	
	public void addProgramUniform(String name)
	{
		int loc = GL20.glGetUniformLocation(getProgram(), name);
		uniformMap.put(name, loc);
	}
    
	@Override
	public void bind()
	{
		GL20.glUseProgram(this.program);
	}
	
	public void bindUniform(String name, boolean value)
	{
	    GL20.glUniform1i(uniformMap.get(name), ( value ? 1 : 0 ));
	}
	
	public void bindUniform(String name, float value)
	{
	    GL20.glUniform1f(uniformMap.get(name), value);
	}
	
	public void bindUniform(String name, float[] value) {
		switch ( value.length )
		{
			case 1 :
			    GL20.glUniform1f(uniformMap.get(name), value[0]);
				break;
			case 2 :
			    GL20.glUniform2f(uniformMap.get(name), value[0], value[1]);
				break;
			case 3 :
			    GL20.glUniform3f(uniformMap.get(name), value[0], value[1], value[2]);
				break;
			case 4 :
			    GL20.glUniform4f(uniformMap.get(name), value[0], value[1], value[2], value[3]);
				break;
		}
		
	}
	
	public void bindUniform(String name, int value)
	{
	    GL20.glUniform1i(uniformMap.get(name), value);
	}

	public int getProgram()
	{
		return program;
	}
	
	public void setProgram(int program)
	{
		this.program = program;
	}
	
	@Override
	protected void setupShader() throws Exception
	{
	    
	    String[] shString = new String[1];
	    
	    this.program = GL20.glCreateProgram();
	    
	    String shExt[] = { "vert", "frag", "geom" };
	    int shType[] = { GL_VERTEX_SHADER, GL_FRAGMENT_SHADER, GL_GEOMETRY_SHADER_EXT };
	    
	    for ( int i = 0; i < ( useGeomShader ? 3 : 2 ); ++i )
	    {
	                    
	        //read in shader program
	    	String fname = shaderName + "." + shExt[i];
	        shString[0] = readShader( fname );
	        shader = GL20.glCreateShader( shType[i] );
	        GL20.glShaderSource( shader, shString[0] );
	        GL20.glCompileShader( shader );
	        compiled = GL20.glGetShader( shader, GL_COMPILE_STATUS );
	        if ( compiled != GL_TRUE )
	        {
	            String shlog = shaderLog( shader );
	            throw new Exception(shExt[i] + " " + shlog);
	        }
	        
	        GL20.glAttachShader( program, shader );
	        
	        if ( i == 2 )
	        {
	            EXTGeometryShader4.glProgramParameteriEXT( program, GL_GEOMETRY_INPUT_TYPE_EXT, inputGeom );
	            EXTGeometryShader4.glProgramParameteriEXT( program, GL_GEOMETRY_OUTPUT_TYPE_EXT, outputGeom );
	            EXTGeometryShader4.glProgramParameteriEXT( program, GL_GEOMETRY_VERTICES_OUT_EXT, vertOut );
	            
	            //GLUtil.logi( gl, "GL_MAX_GEOMETRY_OUTPUT_VERTICES", GL_MAX_GEOMETRY_OUTPUT_VERTICES );
	            //GLUtil.logi( gl, "GL_MAX_GEOMETRY_TOTAL_OUTPUT_COMPONENTS", GL_MAX_GEOMETRY_TOTAL_OUTPUT_COMPONENTS );
	        }
	    }    
	           
	    GL20.glLinkProgram( program );
	    
	    linked = GL20.glGetProgram( program, GL_LINK_STATUS );
	    
	    if ( linked != GL_TRUE )
	    {
	        String shlog = shaderPrLog( program );
	        throw new Exception("linking : " +shlog);
	    }
	}
	
	protected String shaderLog( int shader )
	{
	    int length = GL20.glGetShader( shader, GL_INFO_LOG_LENGTH );
	    //length.rewind();
	    return GL20.glGetShaderInfoLog( shader, length );
	}
	
	protected String shaderPrLog( int shader )
	{
	    int length = GL20.glGetProgram( shader, GL_INFO_LOG_LENGTH );
	    //length.rewind();
	    return GL20.glGetProgramInfoLog( shader, length );
	}

	@Override
	public void unbind() {
		GL20.glUseProgram(0);

	}

	@Override
	public void cleanUp( ) {
		GL20.glDeleteShader(shader);
		GL20.glDeleteProgram(program);
	}
	
}