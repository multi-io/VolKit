package de.olafklischat.lwjgl;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;


public class LWJGLTools {
    public static final int FB_MAX_LENGTH = 16;
    private static final FloatBuffer fb = BufferUtils.createFloatBuffer(FB_MAX_LENGTH);

    /**
     * Not thread-safe (the returned FloatBuffer is shared).
     * 
     * @param arr
     * @return
     */
    public static FloatBuffer toFB(float[] arr) {
        return toFB(arr, arr.length);
    }

    /**
     * Not thread-safe (the returned FloatBuffer is shared).
     * 
     * @param arr
     * @return
     */
    public static FloatBuffer toFB(float[] arr, int n) {
        if (n > FB_MAX_LENGTH) {
            throw new IllegalArgumentException("count (" + n + ") > " + FB_MAX_LENGTH);
        }
        fb.clear();
        fb.put(arr, 0, n);
        fb.rewind();
        return fb;
    }

    public static ShortBuffer newShortBuffer(short[] arr) {
        ShortBuffer b = BufferUtils.createShortBuffer(arr.length);
        b.put(arr, 0, arr.length);
        b.rewind();
        return b;
    }

    //23:33 < multi_io> how would you port gl*3fv(arr) calls to lwjgl?
    //23:34 < multi_io> gl*3f(arr[0],arr[1],arr[2]) ?
    //23:34 < MatthiasM2> multi_io: yes

    public static void glVertex2fv(float[] arr) {
        GL11.glVertex2f(arr[0], arr[1]);
    }

    public static void glVertex3fv(float[] arr) {
        GL11.glVertex3f(arr[0], arr[1], arr[2]);
    }
    
    public static void glTexCoord3fv(float[] arr) {
        GL11.glTexCoord3f(arr[0], arr[1], arr[2]);
    }

}
