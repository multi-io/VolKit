package de.olafklischat.volkit.math;

import java.awt.geom.AffineTransform;

/**
 *
 * @author olaf
 */
public class LinAlg {

    // float[]s representing matrices are column-major, as OpenGL functions expect it

    // this is a least-effort port straight from my C linalg.cc
    // Beware: Very ugly. No typedefs in Java...
    // TODO: define the same operations on FloatBuffers too. Or just use some 3rd party lib...

    public static float[] fillZeros(float[] arr) {
        if (null == arr) {
            arr = new float[16];
        }
        for (int i=0; i<arr.length; i++) {
            arr[i] = 0;
        }
        return arr;
    }

    public static float[] fillIdentity(float[] m) {
        m = fillZeros(m);
        m[0] = 1;
        m[5] = 1;
        m[10] = 1;
        m[15] = 1;
        return m;
    }


    /**
     * res := a * (rotation matrix defined by angle, x, y, z)
     */
    public static float[] fillRotation(float[] a,
                                       float  	angle,
                                       float  	x,
                                       float  	y,
                                       float  	z,
                                       float[] res) {
        // (straight from http://www.opengl.org/sdk/docs/man/xhtml/glRotate.xml)
        float aRad = angle * (float)Math.PI / 180;
        float c = (float) Math.cos(aRad);
        float s = (float) Math.sin(aRad);

        float[] rm = new float[16];

        rm[0]  = x*x*(1-c)+c;
        rm[1]  = y*x*(1-c)+z*s;
        rm[2]  = x*z*(1-c)-y*s;
        rm[3]  = 0;

        rm[4]  = x*y*(1-c)-z*s;
        rm[5]  = y*y*(1-c)+c;
        rm[6]  = y*z*(1-c)+x*s;
        rm[7]  = 0;

        rm[8]  = x*z*(1-c)+y*s;
        rm[9]  = y*z*(1-c)-x*s;
        rm[10] = z*z*(1-c)+c;
        rm[11] = 0;

        rm[12] = 0;
        rm[13] = 0;
        rm[14] = 0;
        rm[15] = 1;

        return fillMultiplication(a, rm, res);
    }


    public static float[] fillTranslation(float[] a,
                                          float   tx,
                                          float   ty,
                                          float   tz,
                                          float[] res) {
        float[] tm = new float[16];
        fillIdentity(tm);
        tm[12] = tx;
        tm[13] = ty;
        tm[14] = tz;
        return fillMultiplication(a, tm, res);
    }

    /**
     * fill*L operations multiply the new transformation with a from
     * the left, rather than from the right.
     * 
     * @param a
     * @param tx
     * @param ty
     * @param tz
     * @param res
     */
    public static float[] fillTranslationL(float[] a,
                                           float   tx,
                                           float   ty,
                                           float   tz,
                                           float[] res) {
        float[] tm = new float[16];
        fillIdentity(tm);
        tm[12] = tx;
        tm[13] = ty;
        tm[14] = tz;
        return fillMultiplication(tm, a, res);
    }
    
    public static float[] fillScale(float[] a,
                                    float   sx,
                                    float   sy,
                                    float   sz,
                                    float[] res) {
        float[] tm = new float[16];
        fillIdentity(tm);
        tm[0]  = sx;
        tm[5]  = sy;
        tm[10] = sz;
        return fillMultiplication(a, tm, res);
    }
    
    public static float[] fillMultiplication(float[] a, float[] b, float[] res) {
        if (res == null) {
            res = new float[16];
        }
        float[] a2 = a;
        if (a2 == res) {
            a2 = copyArr(a, null);
        }
        float[] b2 = b;
        if (b2 == res) {
            b2 = copyArr(b, null);
        }
        for (int rr = 0; rr < 4; rr++) {
            for (int rc = 0; rc < 4; rc++) {
                int ri = rc * 4 + rr;
                res[ri] = 0;
                for (int i = 0; i < 4; i++) {
                    res[ri] += a2[i * 4 + rr] * b2[rc * 4 + i];
                }
            }
        }
        return res;
    }

    public static float[] inverse(float[] src, float[] dest) {
        assert(src.length == 4);
        if (dest == null) {
            dest = new float[src.length];
        } else if (dest == src) {
            src = copyArr(src, null);
        }
        // adapted from http://download.intel.com/design/PentiumIII/sml/24504301.pdf (Cramer's rule)
        float[] tmp = new float[12]; /* temp array for pairs */
        float[] srct = new float[16]; /* array of transpose source matrix */
        float det; /* determinant */
        /* transpose matrix */
        for (int i = 0; i < 4; i++) {
            srct[i] = src[i*4];
            srct[i + 4] = src[i*4 + 1];
            srct[i + 8] = src[i*4 + 2];
            srct[i + 12] = src[i*4 + 3];
        }
        /* calculate pairs for first 8 elements (cofactors) */
        tmp[0] = srct[10] * srct[15];
        tmp[1] = srct[11] * srct[14];
        tmp[2] = srct[9] * srct[15];
        tmp[3] = srct[11] * srct[13];
        tmp[4] = srct[9] * srct[14];
        tmp[5] = srct[10] * srct[13];
        tmp[6] = srct[8] * srct[15];
        tmp[7] = srct[11] * srct[12];
        tmp[8] = srct[8] * srct[14];
        tmp[9] = srct[10] * srct[12];
        tmp[10] = srct[8] * srct[13];
        tmp[11] = srct[9] * srct[12];
        /* calculate first 8 elements (cofactors) */
        dest[0] = tmp[0]*srct[5] + tmp[3]*srct[6] + tmp[4]*srct[7];
        dest[0] -= tmp[1]*srct[5] + tmp[2]*srct[6] + tmp[5]*srct[7];
        dest[1] = tmp[1]*srct[4] + tmp[6]*srct[6] + tmp[9]*srct[7];
        dest[1] -= tmp[0]*srct[4] + tmp[7]*srct[6] + tmp[8]*srct[7];
        dest[2] = tmp[2]*srct[4] + tmp[7]*srct[5] + tmp[10]*srct[7];
        dest[2] -= tmp[3]*srct[4] + tmp[6]*srct[5] + tmp[11]*srct[7];
        dest[3] = tmp[5]*srct[4] + tmp[8]*srct[5] + tmp[11]*srct[6];
        dest[3] -= tmp[4]*srct[4] + tmp[9]*srct[5] + tmp[10]*srct[6];
        dest[4] = tmp[1]*srct[1] + tmp[2]*srct[2] + tmp[5]*srct[3];
        dest[4] -= tmp[0]*srct[1] + tmp[3]*srct[2] + tmp[4]*srct[3];
        dest[5] = tmp[0]*srct[0] + tmp[7]*srct[2] + tmp[8]*srct[3];
        dest[5] -= tmp[1]*srct[0] + tmp[6]*srct[2] + tmp[9]*srct[3];
        dest[6] = tmp[3]*srct[0] + tmp[6]*srct[1] + tmp[11]*srct[3];
        dest[6] -= tmp[2]*srct[0] + tmp[7]*srct[1] + tmp[10]*srct[3];
        dest[7] = tmp[4]*srct[0] + tmp[9]*srct[1] + tmp[10]*srct[2];
        dest[7] -= tmp[5]*srct[0] + tmp[8]*srct[1] + tmp[11]*srct[2];
        /* calculate pairs for second 8 elements (cofactors) */
        tmp[0] = srct[2]*srct[7];
        tmp[1] = srct[3]*srct[6];
        tmp[2] = srct[1]*srct[7];
        tmp[3] = srct[3]*srct[5];
        tmp[4] = srct[1]*srct[6];
        tmp[5] = srct[2]*srct[5];
        tmp[6] = srct[0]*srct[7];
        tmp[7] = srct[3]*srct[4];
        tmp[8] = srct[0]*srct[6];
        tmp[9] = srct[2]*srct[4];
        tmp[10] = srct[0]*srct[5];
        tmp[11] = srct[1]*srct[4];
        /* calculate second 8 elements (cofactors) */
        dest[8] = tmp[0]*srct[13] + tmp[3]*srct[14] + tmp[4]*srct[15];
        dest[8] -= tmp[1]*srct[13] + tmp[2]*srct[14] + tmp[5]*srct[15];
        dest[9] = tmp[1]*srct[12] + tmp[6]*srct[14] + tmp[9]*srct[15];
        dest[9] -= tmp[0]*srct[12] + tmp[7]*srct[14] + tmp[8]*srct[15];
        dest[10] = tmp[2]*srct[12] + tmp[7]*srct[13] + tmp[10]*srct[15];
        dest[10]-= tmp[3]*srct[12] + tmp[6]*srct[13] + tmp[11]*srct[15];
        dest[11] = tmp[5]*srct[12] + tmp[8]*srct[13] + tmp[11]*srct[14];
        dest[11]-= tmp[4]*srct[12] + tmp[9]*srct[13] + tmp[10]*srct[14];
        dest[12] = tmp[2]*srct[10] + tmp[5]*srct[11] + tmp[1]*srct[9];
        dest[12]-= tmp[4]*srct[11] + tmp[0]*srct[9] + tmp[3]*srct[10];
        dest[13] = tmp[8]*srct[11] + tmp[0]*srct[8] + tmp[7]*srct[10];
        dest[13]-= tmp[6]*srct[10] + tmp[9]*srct[11] + tmp[1]*srct[8];
        dest[14] = tmp[6]*srct[9] + tmp[11]*srct[11] + tmp[3]*srct[8];
        dest[14]-= tmp[10]*srct[11] + tmp[2]*srct[8] + tmp[7]*srct[9];
        dest[15] = tmp[10]*srct[10] + tmp[4]*srct[8] + tmp[9]*srct[9];
        dest[15]-= tmp[8]*srct[9] + tmp[11]*srct[10] + tmp[5]*srct[8];
        /* calculate determinant */
        det=srct[0]*dest[0]+srct[1]*dest[1]+srct[2]*dest[2]+srct[3]*dest[3];
        /* calculate matrix inverse */
        det = 1/det;
        for (int j = 0; j < 16; j++) {
            dest[j] *= det;
        }
        return dest;
    }

    public static float[] mtimesv(float[] m, float[] v, float[] dest) {
        if (dest == null) {
            dest = new float[4];
        }
        if (v.length == 3) {
            v = new float[]{v[0], v[1], v[2], 1};
        }
        for (int r=0; r < dest.length; r++) {
            dest[r] = 0;
            for (int c=0; c<4; c++) {
                dest[r] += m[c*4+r] * v[c];
            }
        }
        return dest;
    }
    
    public static float[] copyArr(float[] src, float[] dest) {
        if (dest == null) {
            dest = new float[src.length];
        }
        System.arraycopy(src, 0, dest, 0, src.length);
        return dest;
    }


    public static float[] cross(float[] a, float[] b, float[] dest) {
        if (dest == null) {
            dest = new float[a.length];
        }
        dest[0] = -a[2] * b[1] + a[1] * b[2];
        dest[1] = a[2] * b[0] - a[0] * b[2];
        dest[2] = -a[1] * b[0] + a[0] * b[1];
        return dest;
    }

    
    public static float[] cross(float[] a, int ai, float[] b, int bi, float[] dest) {
        if (dest == null) {
            dest = new float[3];
        }
        dest[0] = -a[ai+2] * b[bi+1] + a[ai+1] * b[bi+2];
        dest[1] = a[ai+2] * b[bi+0] - a[ai+0] * b[bi+2];
        dest[2] = -a[ai+1] * b[bi+0] + a[ai+0] * b[bi+1];
        return dest;
    }

    
    public static float dot(float[] a, float[] b) {
        float result = 0;
        for (int i = 0; i < a.length; i++) {
            result += a[i] * b[i];
        }
        return result;
    }
    
    
    public static float[] stimesv(float s, float[] v, float[] dest) {
        if (dest == null) {
            dest = new float[v.length];
        }
        dest[0] = s * v[0];
        dest[1] = s * v[1];
        dest[2] = s * v[2];
        return dest;
    }

    public static float[] vplusv(float[] v1, float[] v2, float[] dest) {
        if (dest == null) {
            dest = new float[4];
        }
        dest[0] = v1[0] + v2[0];
        dest[1] = v1[1] + v2[1];
        dest[2] = v1[2] + v2[2];
        return dest;
    }

    public static float[] vminusv(float[] v1, float[] v2, float[] dest) {
        if (dest == null) {
            dest = new float[4];
        }
        dest[0] = v1[0] - v2[0];
        dest[1] = v1[1] - v2[1];
        dest[2] = v1[2] - v2[2];
        return dest;
    }

    public static float length(float[] v) {
        return (float) Math.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2]);
    }


    public static float[] norm(float[] v, float[] dest) {
        if (dest == null) {
            dest = new float[v.length];
        }
        float l = length(v);
        stimesv(1.0F/l, v, dest);
        return dest;
    }

    /**
     * Like {@link #fillMultiplication(float[], float[], float[])}, but for R^1
     * (i.e. 2x2 matrices describing affine transformations in R^1 ("y=mx+n"),
     * useful for combining (concatening) things like rescale/slope (windowing)
     * transformations of grey values. Contrary to fillMultiplication, a float[]
     * representing a 2x2 matrix will contain only the first row of the matrix
     * (i.e., two elements). The second row is implicitly assumed to be [0,1]
     * (similar to how Java2D's AffineTransformation class also doesn't store
     * the last row of the matrix ans assumes it to be [0,0,0,1]).
     * 
     * @param a
     * @param b
     * @param res
     * @return
     */
    public static float[] matrMult1D(float[] a, float[] b, float[] res) {
        float[] a2 = a;
        if (a2 == res) {
            a2 = copyArr(a, null);
        }
        float[] b2 = b;
        if (b2 == res) {
            b2 = copyArr(b, null);
        }
        if (null == res) {
            res = new float[2];
        }
        res[0] = a2[0] * b2[0];
        res[1] = a2[0] * b2[1] + a2[1];
        return res;
    }

    public static float[] matrixJ2DtoJOGL(AffineTransform at) {
        double[] values = new double[6];
        at.getMatrix(values);

        float[] rm = new float[16];

        rm[0]  = (float) values[0];
        rm[1]  = (float) values[1];
        rm[2]  = 0;
        rm[3]  = 0;

        rm[4]  = (float) values[2];
        rm[5]  = (float) values[3];
        rm[6]  = 0;
        rm[7]  = 0;

        rm[8]  = 0;
        rm[9]  = 0;
        rm[10] = 1;
        rm[11] = 0;

        rm[12]  = (float) values[4];
        rm[13]  = (float) values[5];
        rm[14] = 0;
        rm[15] = 1;

        return rm;
    }

    public static AffineTransform matrixJOGLtoJ2D(float[] m) {
        return new AffineTransform(m[0], m[1], m[4], m[5], m[8], m[9]);
    }

}
