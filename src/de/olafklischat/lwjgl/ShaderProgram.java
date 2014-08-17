package de.olafklischat.lwjgl;

import static org.lwjgl.opengl.GL11.GL_TRUE;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import org.lwjgl.opengl.GL20;

public class ShaderProgram {
    
    private static enum State {
        NOT_CREATED, CREATED   //we try to rely on the GL's state for the rest
    }

    private State state = State.NOT_CREATED;
    private int programHandle;

    private Map<String, Integer> uniformLocationsByName = new HashMap<String, Integer>();
    
    private static Map<String, Integer> shaderTypesByExtension;
    static {
        shaderTypesByExtension = new HashMap<String, Integer>();
        shaderTypesByExtension.put(".vert", GL20.GL_VERTEX_SHADER);
        shaderTypesByExtension.put(".frag", GL20.GL_FRAGMENT_SHADER);
    }

    protected static int fileNameToShaderType(String fileName) {
        for(Map.Entry<String, Integer> e : shaderTypesByExtension.entrySet()) {
            if (fileName.endsWith(e.getKey())) {
                return e.getValue();
            }
        }
        throw new IllegalArgumentException("can't determine shader type for file name: " + fileName);
    }
    
    public ShaderProgram() {
    }
    
    public void create() {
        if (state != State.NOT_CREATED) {
            throw new IllegalStateException("program already created");
        }
        programHandle = GL20.glCreateProgram();
        state = State.CREATED;
    }
    
    protected void ensureCreated() {
        if (state == State.NOT_CREATED) {
            throw new IllegalStateException("program not created yet");
        }
    }
    

    public int getProgramHandle() {
        ensureCreated();
        return programHandle;
    }
    
    public void attachShader(String source, int shaderType) {
        ensureCreated();
        int shader = GL20.glCreateShader(shaderType);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL_TRUE != GL20.glGetShader( shader, GL20.GL_COMPILE_STATUS)) {
            int logLen = GL20.glGetShader( shader, GL20.GL_INFO_LOG_LENGTH );
            String log = GL20.glGetShaderInfoLog(shader, logLen);
            throw new IllegalStateException("Shader compilation error: " + log);
        }
        GL20.glAttachShader(programHandle, shader);
    }
    
    public void attachShaderFromReader(Reader r, int shaderType) throws IOException {
        StringBuffer source = new StringBuffer(1024);
        char[] buf = new char[1024];
        int n;
        while (-1 != (n = r.read(buf))) {
            source.append(buf, 0, n);
        }
        attachShader(source.toString(), shaderType);
    }
    
    public void attachShaderFromFile(String fileName, int shaderType) throws IOException {
        attachShaderFromReader(new InputStreamReader(new FileInputStream(fileName), "utf-8"), shaderType);
    }
    
    public void attachShaderFromFile(String fileName) throws IOException {
        attachShaderFromFile(fileName, fileNameToShaderType(fileName));
    }
    
    public void attachShaderFromResource(String resourceName) throws IOException {
        ClassLoader cl = getClass().getClassLoader();
        attachShaderFromReader(new InputStreamReader(cl.getResourceAsStream(resourceName), "utf-8"), fileNameToShaderType(resourceName));
    }
    
    public void link() {
        ensureCreated();
        GL20.glLinkProgram(programHandle);
        if (GL_TRUE != GL20.glGetProgram( programHandle, GL20.GL_LINK_STATUS)) {
            int logLen = GL20.glGetProgram(programHandle, GL20.GL_INFO_LOG_LENGTH);
            String log = GL20.glGetProgramInfoLog(programHandle, logLen);
            throw new IllegalStateException("Shader program linker error: " + log);
        }
    }
    
    public void use() {
        ensureCreated();
        GL20.glUseProgram(programHandle);
    }
    
    public void unuse() {
        GL20.glUseProgram(0);
    }
    
    public int getUniformLocation(String varName) {
        ensureCreated();
        Integer result = uniformLocationsByName.get(varName);
        if (null != result) {
            return result;
        }
        result = GL20.glGetUniformLocation(programHandle, varName);
        if (-1 == result) {
            throw new IllegalArgumentException("undefined uniform shader variable: " + varName);
        }
        uniformLocationsByName.put(varName, result);
        return result;
    }

    
    public void setUniform(String varName, float value) {
        setUniform(getUniformLocation(varName), value);
    }

    public void setUniform(int location, float value) {
        GL20.glUniform1f(location, value);
    }

    public void setUniform(String varName, int value) {
        setUniform(getUniformLocation(varName), value);
    }
    
    public void setUniform(int location, int value) {
        GL20.glUniform1i(location, value);
    }

    public void setUniform(String varName, float[] value) {
        setUniform(getUniformLocation(varName), value);
    }
    
    public void setUniform(int location, float[] value) {
        switch (value.length) {
        case 1:
            GL20.glUniform1f(location, value[0]);
            break;
        case 2:
            GL20.glUniform2f(location, value[0], value[1]);
            break;
        case 3:
            GL20.glUniform3f(location, value[0], value[1], value[2]);
            break;
        case 4:
            GL20.glUniform4f(location, value[0], value[1], value[2], value[3]);
            break;
        default:
            throw new IllegalArgumentException("unsupported vector length in variable (location): " + location);
        }
    }
    
    public void delete() {
        ensureCreated();
        //TODO delete the shaders too (unless they've been shared w/ other programs, which we don't support atm.)
        GL20.glDeleteProgram(programHandle);
        uniformLocationsByName.clear();
        state = State.NOT_CREATED;
    }

}
