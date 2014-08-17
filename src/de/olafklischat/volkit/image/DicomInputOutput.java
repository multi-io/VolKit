package de.olafklischat.volkit.image;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.TreeMap;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.dcm4che2.data.BasicDicomObject;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.io.DicomInputStream;
import org.dcm4che2.io.StopTagInputHandler;

import de.olafklischat.volkit.image.DoubleDimension3D;
import de.olafklischat.volkit.util.ProgressReportage;
import de.olafklischat.volkit.util.properties.ExtendedProperties;
import de.olafklischat.volkit.image.VolumeBasicConfig;
import de.olafklischat.volkit.image.VolumeConfig;

/**
 * 
 * URL url= null; try { url = new
 * URL("file:///I:/DICOM/dcm4che-2.0.18-bin/dcm4che-2.0.18/bin/67010"); } catch
 * (MalformedURLException ex) {
 * Logger.getLogger(JListImageListTestApp.class.getName()).log(Level.SEVERE,
 * null, ex); } Dcm dcm = DcmInputOutput.read(url); DcmImageListViewModelElement
 * dcmImageListViewModelElement = new DcmImageListViewModelElement(dcm);
 * model.addElement(dcmImageListViewModelElement);
 */
public class DicomInputOutput {

	static final Logger log4jLogger = Logger.getLogger(DicomInputOutput.class);

	public static BasicDicomObject read(URL url) {
		return read(url, null);
	}

	/**
	 * TODO add implementation for reading from PACS, HTTP, ...
	 * 
	 * file:///C:/Dokumente und Einstellungen/fokko/Desktop/123.dcm
	 * http://pacs.sofd
	 * .local:8080/wado/?requestType=WADO&studyUID=1.2.840.113619
	 * .2.25.4.1207014.1228146104
	 * .835&seriesUID=1.2.840.113619.2.25.4.1207014.1228146105
	 * .98&objectUID=1.2.840.113619.2.25.4.1207014.1228146105.99
	 * 
	 * @param url
	 * @param stopTagInputHandler
	 *            if <code>null</code>, whole DICOM is loaded
	 * @return BasicDicomObject or <code>null</code>
	 */
	public static BasicDicomObject read(URL url,
			StopTagInputHandler stopTagInputHandler) {
		DicomInputStream dicomInputStream = null;
		try {
			BasicDicomObject basicDicomObject = new BasicDicomObject();
			dicomInputStream = new DicomInputStream(new File(System
					.getProperty("os.name").contains("Windows") ? StringUtils
					.replace(url.getFile(), "%20", " ") : url.getFile()));
			if (stopTagInputHandler != null) {
				dicomInputStream.setHandler(stopTagInputHandler);
			}
			dicomInputStream.readDicomObject(basicDicomObject, -1);
			dicomInputStream.close();
			// TODO isEmpty() OK? Additional check for null needed?
			if (!basicDicomObject.isEmpty()) {
				// Dcm dcm = new Dcm();
				// dcm.setUrl(url);
				// dcm.setDicomObject(basicDicomObject);
				return basicDicomObject;
			}
		} catch (IOException ex) {
			log4jLogger.error("read " + url, ex);
			ex.printStackTrace();
		} finally {
			if (dicomInputStream != null) {
				try {
					dicomInputStream.close();
				} catch (IOException ex) {
					log4jLogger.error("read " + url, ex);
					ex.printStackTrace();
				}
			}
		}
		return null;
	}

    public static ArrayList<DicomObject> readDir(String dirPath,
            String seriesInstanceUID) throws Exception {
        return readDir(dirPath, seriesInstanceUID, 1, null);
    }

    public static ArrayList<DicomObject> readDir(String dirPath,
			String seriesInstanceUID, ProgressReportage progressReport) throws Exception {
		return readDir(dirPath, seriesInstanceUID, 1, progressReport);
	}

    public static ArrayList<DicomObject> readDir(String dirPath,
            String seriesInstanceUID, int stride) throws Exception {
        return readDir(dirPath, seriesInstanceUID, 0, Integer.MAX_VALUE, stride, null);
    }

    public static ArrayList<DicomObject> readDir(String dirPath,
			String seriesInstanceUID, int stride, ProgressReportage progressReport) throws Exception {
		return readDir(dirPath, seriesInstanceUID, 0, Integer.MAX_VALUE, stride, progressReport);
	}

    public static ArrayList<DicomObject> readDir(String dirPath,
            String seriesInstanceUID, int firstSlice, int lastSlice, int stride)
            throws Exception {
        return readDir(dirPath, seriesInstanceUID, firstSlice, lastSlice, stride, null);
    }
    
	public static ArrayList<DicomObject> readDir(String dirPath,
			String seriesInstanceUID, int firstSlice, int lastSlice, int stride, ProgressReportage progressReport)
			throws Exception {
		if (stride <= 0)
			throw new Exception("stride have to be a positive number!");

		TreeMap<Integer, DicomObject> dicomSeries = new TreeMap<Integer, DicomObject>();

		File dir = new File(dirPath);

		if (!dir.isDirectory())
			throw new IOException("no directory : " + dirPath);

		int count = dir.listFiles().length;
		System.out.println("files : " + count);
		int iFile = 0;
		for (File file : dir.listFiles()) {
			if (file.isDirectory() || !file.getPath().toLowerCase().endsWith(".dcm"))
				continue;

			if (null != progressReport) {
			    progressReport.setProgress(100*iFile/count);
			}
			++iFile;
			
			DicomInputStream dis = new DicomInputStream(file);

			dis.setHandler(new StopTagInputHandler(Tag.PixelData));
			DicomObject header = new BasicDicomObject();
			dis.readDicomObject(header, -1);
			String headerUID = header.getString(Tag.SeriesInstanceUID);
			int imageNr = header.getInt(Tag.InstanceNumber);
			dis.close();
			dis = null;

			//System.out.println("file " + file.getAbsolutePath());

			if (imageNr < firstSlice || imageNr > lastSlice
					|| (imageNr - firstSlice) % stride != 0)
			{
				System.out.println("imageNr " + imageNr );
				continue;
			}

			if (seriesInstanceUID == null
					|| seriesInstanceUID.equals(headerUID)) {
				System.out.println("file to read: " + file.getAbsolutePath());

				dis = new DicomInputStream(file);
				DicomObject dicomObject = dis.readDicomObject();
				dicomSeries.put(imageNr, dicomObject);
				dis.close();
			} else {
				System.out.println("seriesInstanceUID " + seriesInstanceUID);
			}
		}

		ArrayList<DicomObject> dicomList = new ArrayList<DicomObject>(
				dicomSeries.values());

		if (dicomList.isEmpty()) {
            throw new IOException("no dicom images");
		}
		
		System.out.println("files read : " + dicomList.size());

		return dicomList;
	}

	public static VolumeConfig readVolumeConfig()
			throws Exception {
		VolumeConfig volumeConfig = new VolumeConfig(new ExtendedProperties("volume-config.properties"));

		String dirPath = volumeConfig.getBasicConfig().getImageDirectory();
		
		File dir = new File(dirPath);

		if (!dir.isDirectory())
			throw new IOException("no directory : " + dirPath);

		System.out.println("files : " + dir.listFiles().length);
		int imageStart = Integer.MAX_VALUE;
		int nrOfImages = 0;
		double thickness = 0;
		double[] ps = {1.0, 1.0};

		VolumeBasicConfig basicConfig = volumeConfig.getBasicConfig();
		
		for (File file : dir.listFiles()) {
			if (file.isDirectory() || !file.getPath().toLowerCase().endsWith(".dcm"))
				continue;


			//System.out.println(file.getPath());
			
			DicomInputStream dis = new DicomInputStream(file);

			dis.setHandler(new StopTagInputHandler(Tag.PixelData));
			DicomObject header = new BasicDicomObject();
			dis.readDicomObject(header, -1);
			int imageNr = header.getInt(Tag.InstanceNumber);
			dis.close();
			dis = null;

			nrOfImages++;
			
			imageStart = Math.min(imageStart, imageNr);
			
			if (imageNr == 1) {
				dis = new DicomInputStream(file);
				DicomObject dicomObject = dis.readDicomObject();

				basicConfig.setSeriesName(dicomObject
						.getString(Tag.SeriesDescription));
				basicConfig.setPixelWidth(dicomObject.getInt(Tag.Columns));
				basicConfig.setPixelHeight(dicomObject.getInt(Tag.Rows));

				ps = dicomObject.getDoubles(Tag.PixelSpacing);
				thickness = dicomObject.getDouble(Tag.SliceThickness);

				basicConfig.setPixelFormatBits(dicomObject.getInt(Tag.BitsAllocated));
				basicConfig.setInternalPixelFormatBits(basicConfig.getPixelFormatBits());
				volumeConfig.getWindowingConfig().setTargetPixelFormat(basicConfig.getPixelFormatBits());
				
				short windowCenter = (short)dicomObject.getFloat(Tag.WindowCenter);
				short windowWidth = (short)dicomObject.getFloat(Tag.WindowWidth);
				basicConfig.setOriginalWindowingExists(windowCenter != 0 || windowWidth != 0);
				
				dis.close();
			}
		}

		basicConfig.setSlices(nrOfImages);
		basicConfig.setImageStart(imageStart);
		basicConfig.setImageEnd(imageStart+nrOfImages-1);
		
		basicConfig.setSpacing(new DoubleDimension3D(ps[0], ps[1], thickness));

		return volumeConfig;
	}
}
