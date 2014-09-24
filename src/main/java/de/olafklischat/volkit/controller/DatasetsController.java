package de.olafklischat.volkit.controller;

import java.io.File;
import java.util.Arrays;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;


/**
 * DatasetsController. Knows a base directory, a JList and a {@link TripleSliceViewerController},
 * displays all volume datasets below the base directory in the JList, and
 * if the user clicks on one, loads it asynchronously into the viewers associated
 * with the TripleSliceViewerController.
 *
 * @author Olaf Klischat
 */
public class DatasetsController {

    private final File baseDir;
    private final TripleSliceViewerController tsvc;
    private final JList datasetsList;

    public DatasetsController(File baseDir, JList datasetsList, TripleSliceViewerController tsvc) {
        this.baseDir = baseDir;
        this.tsvc = tsvc;
        this.datasetsList = datasetsList;
        File[] datasetDirs = baseDir.listFiles();
        if (null == datasetDirs) {
            throw new IllegalArgumentException("No dataset directories found under " + baseDir);
        }
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
