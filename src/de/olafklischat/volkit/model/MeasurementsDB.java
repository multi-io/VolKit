package de.olafklischat.volkit.model;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
    }

    public int size() {
        return measurements.size();
    }
    
    public void persist() throws IOException {
        File dest = new File(baseDir, dbFilename);
        File newDest = new File(baseDir, dbFilename + ".new");
        PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(newDest), "utf-8"));
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
    
    private void writeFloats(float[] fs, PrintWriter w) {
        for (float f: fs) {
            w.println(f);
        }
    }
    
}
