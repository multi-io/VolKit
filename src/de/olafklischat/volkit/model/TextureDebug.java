package de.olafklischat.volkit.model;

import java.nio.Buffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;

import de.olafklischat.volkit.view.SharedContextData;

public class TextureDebug {

    protected int xCount, yCount, zCount;
    protected List<Buffer> xyPixelPlaneBuffers = new ArrayList<Buffer>();  // invariant: depth == xyPixelPlaneBuffers.size()
    protected float xSpacingInMm, ySpacingInMm, zSpacingInMm;
    protected int pixelFormat, pixelType;

    public static class TextureRef {
        protected int texId;
        protected float preScale;
        protected float preOffset;

        public TextureRef() {
            
        }
        
        public TextureRef(int texId, float preScale, float preOffset) {
            this.texId = texId;
            this.preScale = preScale;
            this.preOffset = preOffset;
        }

        public int getTexId() {
            return texId;
        }

        /**
         * preScale/preOffset: linear transformation to be applied to texel
         * values by the shader to normalize to [0..1] range. preScale * (texel
         * value) + preOffset must transform all texel values to that range
         */
        public float getPreScale() {
            return preScale;
        }
        
        public float getPreOffset() {
            return preOffset;
        }
    }


    public TextureDebug() {
        xCount = 128;
        yCount = 128;
    }
    
    public TextureRef bindTexture(int texUnit, GL gl1, SharedContextData scd) {
        GL2 gl = gl1.getGL2();
        final String sharedTexIdKey = "DebugTex" + hashCode();
        TextureRef result = (TextureRef) scd.getAttribute(sharedTexIdKey);
        if (result == null) {
            ShortBuffer buf = ShortBuffer.allocate(xCount*yCount);
            for (int x=0; x<xCount; x++) {
                for (int y=0; y<yCount; y++) {
                    //short val = (short)(32000*x/xCount);
                    short xval = (short)(32000*((x/10)%2==0?0.1:0.4));
                    short yval = (short)(32000*((y/13)%2==1?0.1:0.4));
                    buf.put(y*xCount + x, (short)(xval+yval));
                }
            }
            
            result = new TextureRef();

            //Texture imageTexture = new Texture(imageTextureData);
            //result.texId = imageTexture.getTextureObject();

            int[] tmp = new int[1];
            gl.glGenTextures(1, tmp, 0);
            result.texId = tmp[0];
            gl.glBindTexture(GL2.GL_TEXTURE_2D, result.getTexId());

            gl.glTexImage2D(GL2.GL_TEXTURE_2D,    //target
                            0,                    //level
                            GL2.GL_LUMINANCE16,   //internalFormat
                            xCount,               //width
                            yCount,               //height
                            0,                    //border
                            GL.GL_LUMINANCE,      // int pixelFormat,
                            GL.GL_UNSIGNED_SHORT, // int pixelType,
                            null);                 //data

            gl.glTexSubImage2D(GL2.GL_TEXTURE_2D,    //target
                               0,                    //level
                               0,  //xoffset
                               0,  //yoffset
                               xCount,               //width
                               yCount,               //height
                               GL.GL_LUMINANCE,      // int pixelFormat,
                               GL.GL_UNSIGNED_SHORT, // int pixelType,
                               buf);                 //data
            gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
            
            scd.setAttribute(sharedTexIdKey, result);
        }
        gl.glEnable(GL2.GL_TEXTURE_2D);
        gl.glActiveTexture(texUnit);
        gl.glBindTexture(GL2.GL_TEXTURE_2D, result.getTexId());
        return result;
    }

    public TextureRef bindTexture1(int texUnit, GL gl1, SharedContextData scd) {
        GL2 gl = gl1.getGL2();
        final String sharedTexIdKey = "DebugTex" + hashCode();
        TextureRef result = (TextureRef) scd.getAttribute(sharedTexIdKey);
        if (result == null) {
            result = new TextureRef();
            int[] tmp = new int[1];
            gl.glGenTextures(1, tmp, 0);
            result.texId = tmp[0];
            scd.setAttribute(sharedTexIdKey, result);

            gl.glBindTexture(GL2.GL_TEXTURE_2D, result.getTexId());

            int glInternalFormat, glPixelFormat, glPixelType;

            glPixelFormat = GL.GL_LUMINANCE;
            glPixelType = GL.GL_UNSIGNED_SHORT;
            glInternalFormat = GL2.GL_LUMINANCE16; // NOT GL_LUMINANCE12 b/c pixelType is 16-bit and we'd thus lose precision
            result.preScale = (float) (1<<16) / (1<<12);
            result.preOffset = 0.0F;

            ShortBuffer buf = ShortBuffer.allocate(xCount*yCount);
            for (int x=0; x<xCount; x++) {
                for (int y=0; y<yCount; y++) {
                    //short val = (short)(32000*x/xCount);
                    short val = (short)(32000*((x/1000)%2==0?0.8:0.2));
                    buf.put(y*xCount + y, val);
                }
            }
            gl.glTexImage2D(GL2.GL_TEXTURE_2D,    //target
                            0,                    //level
                            glInternalFormat,     //internalFormat
                            xCount,               //width
                            yCount,               //height
                            0,                    //border
                            glPixelFormat,        //format
                            glPixelType,          //type
                            buf);                //data

            gl.glActiveTexture(texUnit);
        }
        gl.glEnable(GL2.GL_TEXTURE_2D);
        gl.glActiveTexture(texUnit);
        gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, gl.GL_REPLACE);
        gl.glBindTexture(GL2.GL_TEXTURE_2D, result.getTexId());
        gl.glTexParameteri( GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP );
        gl.glTexParameteri( GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP );
        return result;
    }
    
    public void unbindCurrentTexture(GL2 gl) {
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    }
    
}
