package de.olafklischat.volkit.controller;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;

import de.olafklischat.volkit.model.Measurement;
import de.olafklischat.volkit.model.MeasurementsDB;
import de.olafklischat.volkit.model.VolumeDataSet;
import de.olafklischat.volkit.view.SlicePaintEvent;
import de.olafklischat.volkit.view.SlicePaintListener;
import de.olafklischat.volkit.view.SliceViewer;
import de.sofd.viskit.image3D.jogl.util.LinAlg;

public class MeasurementsController {

    // TODO: make these parameterizable
    private static final int MOUSE_BUTTON = MouseEvent.BUTTON3;
    private static final int MOUSE_MASK = MouseEvent.BUTTON3_MASK;

    private MeasurementsDB mdb;
    private List<SliceViewer> sliceViewers = new ArrayList<SliceViewer>();
    private Measurement currentMeasurement;

    private Color nextDrawingColor = Color.red;

    public MeasurementsController(MeasurementsDB mdb, SliceViewer... svs) {
        this.mdb = mdb;
        for (SliceViewer sv: svs) {
            sv.addCanvasMouseListener(sliceViewersMouseHandler);
            sv.addCanvasMouseMotionListener(sliceViewersMouseHandler);
            sv.addSlicePaintListener(sliceViewersPaintHandler);
            sliceViewers.add(sv);
        }
        refreshViewers();
    }
    
    private MouseAdapter sliceViewersMouseHandler = new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
            if (e.isShiftDown() && (e.getButton() == MOUSE_BUTTON || (e.getModifiers() & MOUSE_MASK) != 0)) {
                SliceViewer sv = (SliceViewer) e.getSource();
                VolumeDataSet vds = sv.getVolumeDataSet();
                currentMeasurement = new Measurement();
                currentMeasurement.setDatasetName(vds.getDatasetName());
                currentMeasurement.setColor(nextDrawingColor);
                currentMeasurement.setVolumeToWorldTransformation(sv.getVolumeToWorldTransform());
                float [] navZs = new float[sliceViewers.size()];
                for (int i = 0; i < navZs.length; i++) {
                    navZs[i] = sliceViewers.get(i).getNavigationZ();
                }
                currentMeasurement.setNavigationZs(navZs);
                currentMeasurement.setPt0InVolume(canvasToVolume(e.getPoint(), sv));
                e.consume();
            }
        }
        
        @Override
        public void mouseDragged(MouseEvent e) {
            if (currentMeasurement != null &&
                    e.isShiftDown() && (e.getButton() == MOUSE_BUTTON || (e.getModifiers() & MOUSE_MASK) != 0)) {
                SliceViewer sv = (SliceViewer) e.getSource();
                currentMeasurement.setPt1InVolume(canvasToVolume(e.getPoint(), sv));
                sv.refresh();
                e.consume();
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (currentMeasurement != null && currentMeasurement.getPt1InVolume() != null) {
                mdb.addMeasurement(currentMeasurement);
                System.err.println("new measurement added: " + currentMeasurement);
                currentMeasurement = null;
                refreshViewers();
            }
        }
        
    };
    
    protected static float[] canvasToVolume(Point p, SliceViewer sv) {
        return LinAlg.mtimesv(sv.getBaseSliceToVolumeTransform(), sv.convertCanvasToBaseSlice(p), null);
    }
    
    private SlicePaintListener sliceViewersPaintHandler = new SlicePaintListener() {
        
        @Override
        public void glSharedContextDataInitialization(GL gl,
                Map<String, Object> sharedData) {
        }
        
        @Override
        public void glDrawableInitialized(GLAutoDrawable glAutoDrawable) {
        }
        
        @Override
        public void glDrawableDisposing(GLAutoDrawable glAutoDrawable) {
        }

        @Override
        public void onPaint(SlicePaintEvent e) {
        }
        
    };

    protected void refreshViewers() {
        for (SliceViewer sv: sliceViewers) {
            sv.refresh();
        }
    }
    
}
