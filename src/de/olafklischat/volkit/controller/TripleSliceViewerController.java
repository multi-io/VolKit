package de.olafklischat.volkit.controller;

import de.olafklischat.volkit.view.SliceViewer;


public class TripleSliceViewerController {

    protected SliceViewer sv1;
    protected SliceViewer sv2;
    protected SliceViewer sv3;
    
    public TripleSliceViewerController(SliceViewer sv1, SliceViewer sv2, SliceViewer sv3) {
        this.sv1 = sv1;
        this.sv2 = sv2;
        this.sv3 = sv3;
        sv1.setWorldToBaseSliceTransform(SliceViewer.BASE_SLICE_XZ);
        sv2.setWorldToBaseSliceTransform(SliceViewer.BASE_SLICE_YZ);
        sv3.setWorldToBaseSliceTransform(SliceViewer.BASE_SLICE_XY);
        //sv1.addCellMouseListener(null);
    }
    
}
