package de.olafklischat.volkit.controller;

import java.io.File;
import java.util.Arrays;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;


public class DatasetsController {

    private final File baseDir;
    private final TripleSliceViewerController tsvc;
    private final JList datasetsList;

    public DatasetsController(File baseDir, JList datasetsList, TripleSliceViewerController tsvc) {
        this.baseDir = baseDir;
        this.tsvc = tsvc;
        this.datasetsList = datasetsList;
        File[] datasetDirs = baseDir.listFiles();
        Arrays.sort(datasetDirs);
        DefaultListModel lm = new DefaultListModel();
        for (File d : datasetDirs) {
            if (d.isDirectory()) {
                lm.addElement(d.getName());
            }
        }
        datasetsList.setModel(lm);
        datasetsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        datasetsList.addListSelectionListener(listSelectionChangeHandler);
    }

    private ListSelectionListener listSelectionChangeHandler = new ListSelectionListener() {
        @Override
        public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting()) {
                return;
            }
            String datasetName = (String) datasetsList.getSelectedValue();
            if (datasetName != null) {
                tsvc.startLoadingVolumeDataSetInBackground(baseDir + File.separator + datasetName, 1);
            }
        }
    };
    
    public void loadDataset(String datasetName) {
        tsvc.startLoadingVolumeDataSetInBackground(baseDir + File.separator + datasetName, 1);
    }

}
