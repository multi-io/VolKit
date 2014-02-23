package de.olafklischat.volkit.model;

import java.io.File;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

import org.dcm4che2.data.DicomElement;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.util.ByteUtils;
import org.lwjgl.opengl.ARBTextureFloat;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

import com.sun.opengl.util.BufferUtil;

import de.olafklischat.volkit.view.SharedContextData;
import de.sofd.util.ProgressReportage;
import de.sofd.viskit.image.DicomInputOutput;

public class VolumeDataSet {

    protected String datasetName;
    protected int xCount, yCount, zCount;
    protected List<Buffer> xyPixelPlaneBuffers = new ArrayList<Buffer>();  // invariant: zCount == xyPixelPlaneBuffers.size()
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

    /**
     * Each Buffer in xyPixelPlaneBuffers consists of one
     * element of type getPixelType(), containing the luminance value
     */
    public static final int PIXEL_FORMAT_LUMINANCE = 1;

    /**
     * Each Buffer in xyPixelPlaneBuffers consists consists of three
     * elements of type getPixelType(), containing the R, G and B values
     */
    public static final int PIXEL_FORMAT_RGB = 2;

    /**
     * stored in bytes. buffers instanceof ByteBuffer
     */
    public static final int PIXEL_TYPE_UNSIGNED_BYTE = 1;

    /**
     * stored in shorts. buffers instanceof ShortBuffer
     */
    public static final int PIXEL_TYPE_SIGNED_12BIT = 2;

    /**
     * stored in shorts. buffers instanceof ShortBuffer
     */
    public static final int PIXEL_TYPE_UNSIGNED_12BIT = 3;

    /**
     * stored in shorts. buffers instanceof ShortBuffer
     */
    public static final int PIXEL_TYPE_SIGNED_16BIT = 4;

    /**
     * stored in shorts. buffers instanceof ShortBuffer
     */
    public static final int PIXEL_TYPE_UNSIGNED_16BIT = 5;

    protected VolumeDataSet() {
    }
    
    public static VolumeDataSet readFromDirectory(String dirName, int stride) throws Exception {
        return readFromDirectory(dirName, stride, null);
    }
    
    public static VolumeDataSet readFromDirectory(String dirName, int stride, ProgressReportage progressReport) throws Exception {  // TODO move I/O into separate class
        return createFromDicoms(new File(dirName).getName(), DicomInputOutput.readDir(dirName, null, stride, progressReport));
    }
    
    public static VolumeDataSet createFromDicoms(String datasetName, Collection<DicomObject> dicoms) throws Exception {  // TODO move I/O into separate class
        VolumeDataSet result = new VolumeDataSet();
        result.datasetName = datasetName;
        NavigableSet<DicomObject> dobjs = new TreeSet<DicomObject>(new Comparator<DicomObject>() {
            @Override
            public int compare(DicomObject o1, DicomObject o2) {
                if (o1.contains(Tag.SliceLocation) && o2.contains(Tag.SliceLocation)) {
                    return Float.compare(o1.getFloat(Tag.SliceLocation), o2.getFloat(Tag.SliceLocation));
                } else {
                    return ((Integer)o1.getInt(Tag.InstanceNumber)).compareTo(o2.getInt(Tag.InstanceNumber));
                }
            }
        });
        
        dobjs.addAll(dicoms);
        
        // read metadata
        
        result.zCount = dobjs.size();
        if (result.zCount < 2) {
            throw new IOException("Only " + result.zCount + " files found in there. That's not gonna work, dude.");
        }

        DicomObject firstDobj = dobjs.first();
        DicomObject lastDobj = dobjs.last();
        
        result.zSpacingInMm = (lastDobj.getFloat(Tag.SliceLocation) - firstDobj.getFloat(Tag.SliceLocation)) / (result.zCount - 1);

        int bitsAllocated = firstDobj.getInt(Tag.BitsAllocated);
        if (bitsAllocated <= 0) {
            return null;
        }
        int bitsStored = firstDobj.getInt(Tag.BitsStored);
        if (bitsStored <= 0) {
            return null;
        }
        boolean isSigned = (1 == firstDobj.getInt(Tag.PixelRepresentation));
        // TODO: fail if compressed
        // TODO: support for RGB? (at least don't misinterpret it as luminance)
        // TODO: account for endianness (Tag.HighBit)
        // TODO: maybe use static multidimensional tables instead of nested switch statements
        switch (bitsAllocated) {
            case 8:
                throw new IOException("8-bit DICOM images not supported for now");
            case 16:
                result.pixelFormat = PIXEL_FORMAT_LUMINANCE;
                switch (bitsStored) {
                    case 12:
                        result.pixelType = (isSigned ? PIXEL_TYPE_SIGNED_12BIT : PIXEL_TYPE_UNSIGNED_12BIT);
                        break;
                    case 16:
                        result.pixelType = (isSigned ? PIXEL_TYPE_SIGNED_16BIT : PIXEL_TYPE_UNSIGNED_16BIT);
                        break;
                    default:
                        throw new IOException("unsupported DICOM stored bit count: " + bitsStored);
                }
                break;
            default:
                throw new IOException("unsupported DICOM allocated bit count: " + bitsAllocated);
        }
        result.xCount = firstDobj.getInt(Tag.Columns);
        result.yCount = firstDobj.getInt(Tag.Rows);

        float[] rowCol;
        if (firstDobj.contains(Tag.PixelSpacing)) {
            rowCol = firstDobj.getFloats(Tag.PixelSpacing);
            if ((rowCol.length != 2) || (rowCol[0] <= 0) || (rowCol[1] <= 0)) {
                throw new RuntimeException("Illegal PixelSpacing tag in DICOM metadata (2 positive real numbers expected)");
            }
        } else if (firstDobj.contains(Tag.ImagerPixelSpacing)) {
            rowCol = firstDobj.getFloats(Tag.ImagerPixelSpacing);
            if ((rowCol.length != 2) || (rowCol[0] <= 0) || (rowCol[1] <= 0)) {
                throw new RuntimeException("Illegal ImagerPixelSpacing tag in DICOM metadata (2 positive real numbers expected)");
            }
        } else {
            throw new IOException("DICOM metadata contained neither a PixelSpacing nor an ImagerPixelSpacing tag");
        }
        result.xSpacingInMm = rowCol[1];
        result.ySpacingInMm = rowCol[0];

        // read pixel data
        int readCount = 0;
        for (DicomObject dobj: dobjs) {
            DicomElement elt = dobj.get(Tag.PixelData);
            Buffer b = null;
            //System.err.println("count=" + elt.countItems());
            if (elt.countItems() == -1) {
                b = BufferUtil.newShortBuffer(elt.getShorts(true)); // type of buffer may later depend on image metadata
            } else {
                // if it's a sequence, get the 2nd item. Seems to be incorrect. What
                // does it mean for this tag to be a sequence?
                byte[] bytes = elt.getFragment(1);
                boolean bigEndian = (0 == dobj.getInt(Tag.HighBit));
                b = BufferUtil.newShortBuffer(bigEndian  ? ByteUtils.bytesBE2shorts(bytes) : ByteUtils.bytesLE2shorts(bytes));
            }
            //Buffer b = BufferUtil.newShortBuffer(dobj.getShorts(Tag.PixelData)); // type of buffer may later depend on image metadata
            result.xyPixelPlaneBuffers.add(b);
            System.out.println("read " + readCount + "/" + result.zCount + " (" + (100 * readCount/result.zCount) + "%). SL=" + dobj.getFloat(Tag.SliceLocation));
            readCount++;
        }
        
        return result;
    }

    public int getXCount() {
        return xCount;
    }
    
    public int getYCount() {
        return yCount;
    }
    
    public int getZCount() {
        return zCount;
    }
    
    public float getXSpacingInMm() {
        return xSpacingInMm;
    }
    
    public float getYSpacingInMm() {
        return ySpacingInMm;
    }
    
    public float getZSpacingInMm() {
        return zSpacingInMm;
    }
    
    public float getWidthInMm() {
        return xSpacingInMm * xCount;
    }
    
    public float getHeightInMm() {
        return ySpacingInMm * yCount;
    }
    
    public float getDepthInMm() {
        return zSpacingInMm * zCount;
    }

    public String getDatasetName() {
        return datasetName;
    }

    public TextureRef bindTexture(int texUnit, SharedContextData scd) {
        final String sharedTexIdKey = "VolumeDataSetTex" + hashCode();
        TextureRef result = (TextureRef) scd.getAttribute(sharedTexIdKey);
        if (result == null) {
            result = new TextureRef();
            result.texId = GL11.glGenTextures();
            scd.setAttribute(sharedTexIdKey, result);

            GL13.glActiveTexture(texUnit);
            
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);

            GL11.glBindTexture(GL12.GL_TEXTURE_3D, result.getTexId());

            int glInternalFormat, glPixelFormat, glPixelType;

            // TODO: store the GL IDs in the VolumeDataSet directly
            if (pixelFormat == PIXEL_FORMAT_LUMINANCE && pixelType == PIXEL_TYPE_SIGNED_16BIT) {
                glPixelFormat = GL11.GL_LUMINANCE;
                glPixelType = GL11.GL_SHORT;
                glInternalFormat = ARBTextureFloat.GL_LUMINANCE16F_ARB;
                result.preScale = 0.5F;
                result.preOffset = 0.5F;
                
                result.preScale = 0.7F;
                result.preOffset = 0.1F;
            } else if (pixelFormat == PIXEL_FORMAT_LUMINANCE && pixelType == PIXEL_TYPE_UNSIGNED_16BIT) {
                glPixelFormat = GL11.GL_LUMINANCE;
                glPixelType = GL11.GL_UNSIGNED_SHORT;
                glInternalFormat = ARBTextureFloat.GL_LUMINANCE16F_ARB; // GL_*_SNORM result in GL_INVALID_ENUM and all-white texels on tack (GeForce 8600 GT/nvidia 190.42)
                result.preScale = 1.0F;
                result.preOffset = 0.0F;
            } else if (pixelFormat == PIXEL_FORMAT_LUMINANCE && pixelType == PIXEL_TYPE_UNSIGNED_12BIT) {
                glPixelFormat = GL11.GL_LUMINANCE;
                glPixelType = GL11.GL_UNSIGNED_SHORT;
                glInternalFormat = GL11.GL_LUMINANCE16; // NOT GL_LUMINANCE12 b/c pixelType is 16-bit and we'd thus lose precision
                result.preScale = (float) (1<<16) / (1<<12);
                result.preOffset = 0.0F;
            } else {
                throw new RuntimeException("this DICOM image format is not supported for now");
            }

            System.err.println("creating 3D texture of " + xCount + "x" + yCount + "x" + zCount + " texels");
            //glTexImage3D(int target, int level, int internalFormat, int width, int height, int depth, int border, int format, int type, ByteBuffer pixels) {
            GL12.glTexImage3D(GL12.GL_TEXTURE_3D,    //target
                            0,                    //level
                            glInternalFormat,     //internalFormat
                            xCount,               //width
                            yCount,               //height
                            zCount,               //depth
                            0,                    //border
                            glPixelFormat,        //format
                            glPixelType,          //type
                            (ShortBuffer)null);                //data

            for (int z = 0; z < zCount; z++) {
                Buffer planeBuffer = xyPixelPlaneBuffers.get(z);
                GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, //target
                                     0,  //level
                                     0,  //xoffset
                                     0,  //yoffset
                                     z,  //zoffset
                                     xCount,
                                     yCount,
                                     1,
                                     glPixelFormat,
                                     glPixelType,
                                     (ShortBuffer)planeBuffer);  // type of buffer may later depend on image metadata
            }
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP );
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL11.GL_CLAMP );
        }
        GL11.glEnable(GL12.GL_TEXTURE_3D);
        GL13.glActiveTexture(texUnit);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, result.getTexId());
        return result;
    }
    
    public void unbindCurrentTexture() {
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
    }
    
    public void dispose(SharedContextData scd) {
        final String sharedTexIdKey = "VolumeDataSetTex" + hashCode();
        TextureRef texRef = (TextureRef) scd.getAttribute(sharedTexIdKey);
        if (texRef != null) {
            GL11.glDeleteTextures(texRef.getTexId());
            scd.removeAttribute(sharedTexIdKey);
        }
        // TODO: reference count to detect when we can actually free the texture?
    }
    
}
