package de.olafklischat.volkit.model;

public class Measurement {

    String datasetName;
    float[] pt0InVolume, pt1InVolume;
    float[] volumeToWorldTransformation;
    // {sv1,sv2,sv3}.worldToBaseSliceTransformation sind fix in TripleSliceViewerController...

    public Measurement() {
    }
    
    public Measurement(String datasetName) {
        setDatasetName(datasetName);
    }

    public Measurement(String datasetName, float[] pt0InVolume, float[] pt1InVolume, float[] volumeToWorldTransformation) {
        setDatasetName(datasetName);
        setPt0InVolume(pt0InVolume);
        setPt1InVolume(pt1InVolume);
        setVolumeToWorldTransformation(volumeToWorldTransformation);
    }

    public String getDatasetName() {
        return datasetName;
    }
    
    public void setDatasetName(String datasetName) {
        this.datasetName = datasetName;
    }
    
    public float[] getPt0InVolume() {
        return pt0InVolume;
    }
    
    public void setPt0InVolume(float[] pt0InVolume) {
        this.pt0InVolume = pt0InVolume;
    }
    
    public float[] getPt1InVolume() {
        return pt1InVolume;
    }
    
    public void setPt1InVolume(float[] pt1InVolume) {
        this.pt1InVolume = pt1InVolume;
    }
    
    public float[] getVolumeToWorldTransformation() {
        return volumeToWorldTransformation;
    }
    
    public void setVolumeToWorldTransformation(
            float[] volumeToWorldTransformation) {
        this.volumeToWorldTransformation = volumeToWorldTransformation;
    }
    
    public float getLengthInMm() {
        float dx = pt0InVolume[0] - pt1InVolume[0];
        float dy = pt0InVolume[1] - pt1InVolume[1];
        float dz = pt0InVolume[2] - pt1InVolume[2];
        return (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

}
