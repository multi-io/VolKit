package de.olafklischat.volkit.model;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class MeasurementsDB {

    private List<Measurement> measurements = new ArrayList<Measurement>();
    private final File baseDir;
    private final String dbFilename = "measurements.db";
    
    public MeasurementsDB(String baseDirName) {
        this.baseDir = new File(baseDirName);
        if (!baseDir.isDirectory()) {
            throw new IllegalStateException("not a directory: " + baseDirName);
        }
    }
    
    public List<Measurement> getMeasurements() {
        return measurements;
    }
    
    public void addMeasurement(Measurement m) {
        m.setNumber(measurements.size());
        measurements.add(m);
        try {
            persist();
        } catch (IOException e) {
            throw new RuntimeException("I/O error: " + e.getLocalizedMessage(), e);
        }
    }
    
    public void removeMeasurement(Measurement m) {
        measurements.remove(m);
        try {
            persist();
        } catch (IOException e) {
            throw new RuntimeException("I/O error: " + e.getLocalizedMessage(), e);
        }
    }

    public int size() {
        return measurements.size();
    }
    
    public void persist() throws IOException {
        File dest = new File(baseDir, dbFilename);
        File newDest = new File(baseDir, dbFilename + ".new");
        PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(newDest), "utf-8"));
        w.println("1");   //version number
        w.println(getMeasurements().size());
        for (Measurement m : getMeasurements()) {
            w.println(m.getNumber());
            w.println(m.getDatasetName());
            writeFloats(m.getPt0InVolume(), w);
            writeFloats(m.getPt1InVolume(), w);
            w.println(m.getColor().getRed());
            w.println(m.getColor().getGreen());
            w.println(m.getColor().getBlue());
            writeFloats(m.getVolumeToWorldTransformation(), w);
            writeFloats(m.getNavigationZs(), w);
        }
        w.close();
        if (!newDest.renameTo(dest)) { // TODO: this won't work on Windows
            throw new IOException("file rename during DB persisting failed");
        }
    }
    
    private static void writeFloats(float[] fs, PrintWriter w) {
        for (float f: fs) {
            w.println(f);
        }
    }
    
    public void load() throws IOException {
        File src = new File(baseDir, dbFilename);
    	if (!src.exists()) {
    		return;
    	}
    	BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(src), "utf-8"));
    	int vnum = readInt(r);
    	switch (vnum) {
    	case 1:
    		loadV1(r);
    		break;
    	default:
    		throw new IllegalStateException("unsupported measurements DB version: " + vnum);
    	}
    }
    
    private void loadV1(BufferedReader r) throws IOException {
    	int msCount = readInt(r);
    	List<Measurement> newMs = new ArrayList<Measurement>(msCount);
    	for (int i=0; i<msCount; i++) {
    		Measurement m = new Measurement();
    		m.setNumber(readInt(r));
    		m.setDatasetName(readString(r));
    		m.setPt0InVolume(readFloatArr(4, r));
            m.setPt1InVolume(readFloatArr(4, r));
            m.setColor(new Color(readInt(r), readInt(r), readInt(r)));
            m.setVolumeToWorldTransformation(readFloatArr(16, r));
            m.setNavigationZs(readFloatArr(3, r));
            newMs.add(m);
    	}
    	this.measurements = newMs;
    }

    private static int readInt(BufferedReader r) throws IOException {
    	return Integer.parseInt(r.readLine());
    }
    
    private static float readFloat(BufferedReader r) throws IOException {
    	return Float.parseFloat(r.readLine());
    }
    
    private static float[] readFloatArr(int count, BufferedReader r) throws IOException {
    	float[] result = new float[count];
    	for (int i=0; i<count; i++) {
    		result[i] = readFloat(r);
    	}
    	return result;
    }
    
    private static String readString(BufferedReader r) throws IOException {
        return r.readLine();
    }

}
