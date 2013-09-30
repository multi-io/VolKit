package de.olafklischat.volkit.controller;

import java.io.File;


public class DatasetsController {

    private final String baseDir;
    private final TripleSliceViewerController tsvc;

    public DatasetsController(String baseDir, TripleSliceViewerController tsvc) {
        this.baseDir = baseDir;
        this.tsvc = tsvc;
    }
    
    public void loadDataset(String datasetName) {
        tsvc.startLoadingVolumeDataSetInBackground(baseDir + File.separator + datasetName, 1);
    }

}
