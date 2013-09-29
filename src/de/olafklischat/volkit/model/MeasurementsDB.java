package de.olafklischat.volkit.model;

import java.util.ArrayList;
import java.util.List;

public class MeasurementsDB {

    private List<Measurement> measurements = new ArrayList<Measurement>();
    
    public List<Measurement> getMeasurements() {
        return measurements;
    }
    
    public void addMeasurement(Measurement m) {
        measurements.add(m);
    }

    public int size() {
        return measurements.size();
    }
}
