package de.olafklischat.volkit.model;

import java.io.File;
import java.io.IOException;
import java.nio.Buffer;
import java.util.Arrays;
import java.util.List;

import javax.media.opengl.GL;

import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.io.DicomInputStream;

import com.sun.opengl.util.BufferUtil;

import de.olafklischat.volkit.view.SharedContextData;
import de.sofd.viskit.model.RawImage;

public class VolumeDataSet {

    protected int xCount, yCount, zCount;
    protected List<Buffer> xyPixelPlaneBuffers;  // invariant: depth == xyPixelPlaneBuffers.size()
    protected float xSpacing, ySpacing, zSpacing;
    protected int pixelFormat, pixelType;

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
    
    public static VolumeDataSet readFromDirectory(File dir) throws IOException {  // TODO move I/O into separate class
        VolumeDataSet result = new VolumeDataSet();
        File[] files = dir.listFiles();
        Arrays.sort(files);
        boolean metadataRead = false;
        boolean zSpacingRead = false;
        float firstSliceLocation = 0;
        result.zCount = files.length;
        int readCount = 0;
        for (File f : files) {
            if (!f.getName().toLowerCase().endsWith(".dcm")) {
                continue;
            }

            DicomObject dobj;
            DicomInputStream din = new DicomInputStream(f);
            try {
                dobj = din.readDicomObject();
            } finally {
                din.close();
            }

            if (!metadataRead) {
                int bitsAllocated = dobj.getInt(Tag.BitsAllocated);
                if (bitsAllocated <= 0) {
                    return null;
                }
                int bitsStored = dobj.getInt(Tag.BitsStored);
                if (bitsStored <= 0) {
                    return null;
                }
                boolean isSigned = (1 == dobj.getInt(Tag.PixelRepresentation));
                // TODO: fail if compressed
                // TODO: support for RGB? (at least don't misinterpret it as luminance)
                // TODO: account for endianness (Tag.HighBit)
                // TODO: maybe use static multidimensional tables instead of nested switch statements
                switch (bitsAllocated) {
                    case 8:
                        throw new IOException("8-bit DICOM images not supported for now");
                    case 16:
                        result.pixelFormat = RawImage.PIXEL_FORMAT_LUMINANCE;
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
                result.xCount = dobj.getInt(Tag.Columns);
                result.yCount = dobj.getInt(Tag.Rows);

                float[] rowCol;
                if (dobj.contains(Tag.PixelSpacing)) {
                    rowCol = dobj.getFloats(Tag.PixelSpacing);
                    if ((rowCol.length != 2) || (rowCol[0] <= 0) || (rowCol[1] <= 0)) {
                        throw new RuntimeException("Illegal PixelSpacing tag in DICOM metadata (2 positive real numbers expected)");
                    }
                } else if (dobj.contains(Tag.ImagerPixelSpacing)) {
                    rowCol = dobj.getFloats(Tag.ImagerPixelSpacing);
                    if ((rowCol.length != 2) || (rowCol[0] <= 0) || (rowCol[1] <= 0)) {
                        throw new RuntimeException("Illegal ImagerPixelSpacing tag in DICOM metadata (2 positive real numbers expected)");
                    }
                } else {
                    throw new IOException("DICOM metadata contained neither a PixelSpacing nor an ImagerPixelSpacing tag");
                }
                result.xSpacing = rowCol[1];
                result.ySpacing = rowCol[0];
                firstSliceLocation = dobj.getFloat(Tag.SliceLocation);

                metadataRead = true;
            } else if (!zSpacingRead) {
                result.zSpacing = dobj.getFloat(Tag.SliceLocation) - firstSliceLocation;
                zSpacingRead = true;
            }
            Buffer b = BufferUtil.newShortBuffer(dobj.getShorts(Tag.PixelData)); // type of buffer may later depend on image metadata
            result.xyPixelPlaneBuffers.add(b);
            System.out.println("read " + readCount + "/" + result.zCount + " (" + (100 * readCount/result.zCount) + "%)");
            readCount++;
        }
        return result;
    }

    public int bindTexture(GL gl, SharedContextData scd) {
        final String sharedTexIdKey = "VolumeDateSetTex" + hashCode();
        Integer result = (Integer) scd.getAttribute(sharedTexIdKey);
        if (result == null) {
            
        }
        return result;
    }
    
    
}
