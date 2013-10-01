package de.olafklischat.volkit.model;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
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
    }
    
    public void removeMeasurement(Measurement m) {
        measurements.remove(m);
    }

    public int size() {
        return measurements.size();
    }
    
    public void persist() throws IOException {
        Writer w = new OutputStreamWriter(new FileOutputStream(new File(baseDir, dbFilename + ".new")), "utf-8");
        w.write("" + getMeasurements().size() + "\n");
    }
    
}
