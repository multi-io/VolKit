package de.olafklischat.volkit.controller;

import java.awt.Component;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JOptionPane;
import javax.swing.ProgressMonitor;
import javax.swing.SwingWorker;
import javax.swing.undo.UndoManager;

import de.olafklischat.volkit.model.VolumeDataSet;
import de.olafklischat.volkit.view.SliceViewer;
import de.olafklischat.volkit.view.VolumeViewer;
import de.olafklischat.math.LinAlg;
import de.sofd.util.ProgressReportage;


public class TripleSliceViewerController {

    // TODO: make these parameterizable
    private static final int ROTATION_MOUSE_BUTTON = MouseEvent.BUTTON1;
    private static final int ROTATION_MOUSE_MASK = MouseEvent.BUTTON1_MASK;

    private static final int ZOOMPAN_MOUSE_BUTTON = MouseEvent.BUTTON2;
    private static final int ZOOMPAN_MOUSE_MASK = MouseEvent.BUTTON2_MASK;
    
    protected Component parentComponent;

    protected SliceViewer sv1;
    protected SliceViewer sv2;
    protected SliceViewer sv3;
    
    protected VolumeViewer vv;
    
    protected UndoManager vol2worldTransformUndoManager;
    
    public TripleSliceViewerController(Component parentComponent, SliceViewer sv1, SliceViewer sv2, SliceViewer sv3, VolumeViewer vv) {
        this(parentComponent, sv1, sv2, sv3, vv, null);
    }
    
    public TripleSliceViewerController(Component parentComponent, SliceViewer sv1, SliceViewer sv2, SliceViewer sv3, VolumeViewer vv, UndoManager undoMgr) {
        this.parentComponent = parentComponent;
        this.sv1 = sv1;
        this.sv2 = sv2;
        this.sv3 = sv3;
        this.vv = vv;
        
        if (undoMgr == null) {
            this.vol2worldTransformUndoManager = new UndoManager();
        } else {
            this.vol2worldTransformUndoManager = undoMgr;
        }

        float[] identity = new float[16];
        LinAlg.fillIdentity(identity);
        sv1.setVolumeToWorldTransform(identity);
        sv2.setVolumeToWorldTransform(identity);
        sv3.setVolumeToWorldTransform(identity);
        vv.setVolumeToWorldTransform(identity);

        sv1.setWorldToBaseSliceTransform(SliceViewer.BASE_SLICE_XZ);
        sv2.setWorldToBaseSliceTransform(SliceViewer.BASE_SLICE_YZ);
        sv3.setWorldToBaseSliceTransform(SliceViewer.BASE_SLICE_XY);

        VolumeRotatingMouseHandler rhxz = new VolumeRotatingMouseHandler(new float[]{1,0,0}, new float[]{0,1,0}, false, true);
        sv1.addCanvasMouseListener(rhxz);
        sv1.addCanvasMouseMotionListener(rhxz);
        
        VolumeRotatingMouseHandler rhyz = new VolumeRotatingMouseHandler(new float[]{0,1,0}, new float[]{0,0,1}, false, true);
        sv2.addCanvasMouseListener(rhyz);
        sv2.addCanvasMouseMotionListener(rhyz);

        VolumeRotatingMouseHandler rhxy = new VolumeRotatingMouseHandler(new float[]{1,0,0}, new float[]{0,1,0}, true, false);
        sv3.addCanvasMouseListener(rhxy);
        sv3.addCanvasMouseMotionListener(rhxy);
        
        ZoomPanMouseHandler zpmh = new ZoomPanMouseHandler();
        sv1.addCanvasMouseListener(zpmh);
        sv1.addCanvasMouseMotionListener(zpmh);
        sv1.addCanvasMouseWheelListener(zpmh);

        zpmh = new ZoomPanMouseHandler();
        sv2.addCanvasMouseListener(zpmh);
        sv2.addCanvasMouseMotionListener(zpmh);
        sv2.addCanvasMouseWheelListener(zpmh);

        zpmh = new ZoomPanMouseHandler();
        sv3.addCanvasMouseListener(zpmh);
        sv3.addCanvasMouseMotionListener(zpmh);
        sv3.addCanvasMouseWheelListener(zpmh);

        sv1.addTrackedViewer(sv2);
        sv1.addTrackedViewer(sv3);
        sv2.addTrackedViewer(sv1);
        sv2.addTrackedViewer(sv3);
        sv3.addTrackedViewer(sv1);
        sv3.addTrackedViewer(sv2);
        
        vv.addTrackedSliceViewer(sv1);
        vv.addTrackedSliceViewer(sv2);
        vv.addTrackedSliceViewer(sv3);
    }
    

    private class VolumeRotatingMouseHandler extends MouseAdapter {

        /**
         * rot. axes
         */
        float[] horizAxis;
        float[] vertAxis;
        boolean horizRotEnabled;
        boolean vertRotEnabled;
        float[] preDragVol2WorldTr;
        
        public VolumeRotatingMouseHandler(float[] horizAxis, float[] vertAxis, boolean horizRotEnabled, boolean vertRotEnabled) {
            this.horizAxis = horizAxis;
            this.vertAxis = vertAxis;
            this.horizRotEnabled = horizRotEnabled;
            this.vertRotEnabled = vertRotEnabled;
        }
        
        private Point lastPos = null;
        
        @Override
        public void mousePressed(MouseEvent e) {
            if (e.getButton() == ROTATION_MOUSE_BUTTON || (e.getModifiers() & ROTATION_MOUSE_MASK) != 0) {
                lastPos = e.getPoint();
                preDragVol2WorldTr = sv1.getVolumeToWorldTransform();
                e.consume();
            }
        }
        @Override
        public void mouseDragged(MouseEvent e) {
            if (e.getButton() == ROTATION_MOUSE_BUTTON || (e.getModifiers() & ROTATION_MOUSE_MASK) != 0) {
                Point pos = e.getPoint();
                if (lastPos != null) {
                    float roty = ((float)pos.x - lastPos.x) / 400 * 180;
                    float rotx = ((float)pos.y - lastPos.y) / 400 * 180;
                    float[] volumeDeltaTransform = new float[16];
                    LinAlg.fillIdentity(volumeDeltaTransform);
                    float[] offset = getSlicesIntersectionPointInWorld();
                    LinAlg.fillTranslation(volumeDeltaTransform, offset[0], offset[1], offset[2], volumeDeltaTransform);
                    if (horizRotEnabled) {
                        LinAlg.fillRotation(volumeDeltaTransform, rotx, horizAxis[0], horizAxis[1], horizAxis[2], volumeDeltaTransform);
                    }
                    if (vertRotEnabled) {
                        LinAlg.fillRotation(volumeDeltaTransform, roty, vertAxis[0], vertAxis[1], vertAxis[2], volumeDeltaTransform);
                    }
                    LinAlg.fillTranslation(volumeDeltaTransform, - offset[0], - offset[1], - offset[2], volumeDeltaTransform);
                    float[] vol2worlTx = sv1.getVolumeToWorldTransform();
                    LinAlg.fillMultiplication(volumeDeltaTransform, vol2worlTx, vol2worlTx);
                    sv1.setVolumeToWorldTransform(vol2worlTx);
                    sv2.setVolumeToWorldTransform(vol2worlTx);
                    sv3.setVolumeToWorldTransform(vol2worlTx);
                    vv.setVolumeToWorldTransform(vol2worlTx);
                    e.consume();
                }
                lastPos = pos;
            }
        }
        
        @Override
        public void mouseReleased(MouseEvent e) {
            if (lastPos != null && (e.getButton() == ROTATION_MOUSE_BUTTON || (e.getModifiers() & ROTATION_MOUSE_MASK) != 0)) {
                vol2worldTransformUndoManager.addEdit(new UndoableVol2WorldTransformChange(preDragVol2WorldTr, sv1.getVolumeToWorldTransform(), vv, sv1, sv2, sv3));
                e.consume();
            }
        }
        
        private float[] getSlicesIntersectionPointInWorld() {
            // TODO: this is a somewhat less than general solution b/c it
            // only works for the specific worldToBaseSliceTransform settings
            // of the svs as set in the constructor
            float[] result = new float[3];
            result[0] = - sv2.getNavigationZ();
            result[1] = - sv1.getNavigationZ();
            result[2] =   sv3.getNavigationZ();
            return result;
        }

    }

    public UndoManager getVol2worldTransformUndoManager() {
        return vol2worldTransformUndoManager;
    }
    
    private class ZoomPanMouseHandler extends MouseAdapter {

        private Point lastPos = null;
        
        @Override
        public void mousePressed(MouseEvent e) {
            if (e.getButton() == ZOOMPAN_MOUSE_BUTTON || (e.getModifiers() & ZOOMPAN_MOUSE_MASK) != 0) {
                lastPos = e.getPoint();
                e.consume();
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (e.getButton() == ZOOMPAN_MOUSE_BUTTON || (e.getModifiers() & ZOOMPAN_MOUSE_MASK) != 0) {
                SliceViewer sv = (SliceViewer) e.getSource();
                Point pos = e.getPoint();
                if (lastPos != null) {
                    float[] posCnv = sv.convertAwtToCanvas(pos);
                    float[] lastPosCnv = sv.convertAwtToCanvas(lastPos);
                    float[] delta = LinAlg.fillIdentity(null);
                    LinAlg.fillTranslation(delta, posCnv[0] - lastPosCnv[0], posCnv[1] - lastPosCnv[1], 0, delta);
                    float[] slice2cnv = sv.getSliceToCanvasTransform();
                    LinAlg.fillMultiplication(delta, slice2cnv, slice2cnv);
                    sv.setSliceToCanvasTransform(slice2cnv);
                }
                lastPos = pos;
                e.consume();
            }
        }
        
        public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
            SliceViewer sv = (SliceViewer) e.getSource();
            float scaleChange = (e.getWheelRotation() < 0 ? 1.1f/1f : 1f/1.1f);
            float[] zoomCenterCnv = sv.convertAwtToCanvas(e.getPoint());
            float[] delta = LinAlg.fillIdentity(null);
            LinAlg.fillTranslation(delta, zoomCenterCnv[0], zoomCenterCnv[1], 0, delta);
            LinAlg.fillScale(delta, scaleChange, scaleChange, 1f, delta);
            LinAlg.fillTranslation(delta, - zoomCenterCnv[0], - zoomCenterCnv[1], 0, delta);
            float[] slice2cnv = sv.getSliceToCanvasTransform();
            LinAlg.fillMultiplication(delta, slice2cnv, slice2cnv);
            sv.setSliceToCanvasTransform(slice2cnv);
            e.consume();
        }

    };

    public void resetVolumeToWorldTransform() {
        float[] identity = new float[16];
        LinAlg.fillIdentity(identity);
        sv1.setVolumeToWorldTransform(identity);
        sv2.setVolumeToWorldTransform(identity);
        sv3.setVolumeToWorldTransform(identity);
        vv.setVolumeToWorldTransform(identity);
    }
    
    public void resetZNavigations() {
        sv1.setNavigationZ(0f);
        sv2.setNavigationZ(0f);
        sv3.setNavigationZ(0f);
    }
    
    public void resetSliceToCanvasTransformations() {
        float[] identity = LinAlg.fillIdentity(null);
        sv1.setSliceToCanvasTransform(identity);
        sv2.setSliceToCanvasTransform(identity);
        sv3.setSliceToCanvasTransform(identity);
    }
    
    public void setVolumeDataSet(VolumeDataSet vds) {
        sv1.setVolumeDataSet(vds);
        sv2.setVolumeDataSet(vds);
        sv3.setVolumeDataSet(vds);
        vv.setVolumeDataSet(vds);
    }
    
    public void loadVolumeDataSet(String pathName, int stride) throws Exception {
        long t0 = System.currentTimeMillis();
        VolumeDataSet vds = VolumeDataSet.readFromDirectory(pathName, stride);
        long t1 = System.currentTimeMillis();
        System.out.println("time for reading: " + (t1-t0) + " ms.");
        setVolumeDataSet(vds);
    }
    
    public void startLoadingVolumeDataSetInBackground(final String pathName, final int stride) {
        final SwingWorker<VolumeDataSet, Void> bkgTask = new SwingWorker<VolumeDataSet, Void>() {
            private void doSetProgress(int p) {  // make doSetProgress callable...
                super.setProgress(p);
            }
            @Override
            protected VolumeDataSet doInBackground() throws Exception {
                //runs in bkg thread
                setProgress(0);
                return VolumeDataSet.readFromDirectory(pathName, stride, new ProgressReportage() {
                    @Override
                    public void setProgress(int zeroTo100) {
                        doSetProgress(zeroTo100);
                    }
                });
            }
            @Override
            protected void done() {
                // runs in EDT
                if (!isCancelled()) {  // TODO: isCancelled() is set to true even if the bkg thread keeps running -- mechanism to react to "cancel request"?
                    VolumeDataSet vds = null;
                    try {
                        vds = get();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(parentComponent, "Error: "+ ex.getLocalizedMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    setVolumeDataSet(vds);
                }
            }
        };
        final ProgressMonitor progressMonitor = new ProgressMonitor(parentComponent, "Loading DICOM data...", "", 0, 100);
        bkgTask.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                // runs in EDT
                if ("progress" == evt.getPropertyName() ) {
                    int progress = (Integer) evt.getNewValue();
                    progressMonitor.setProgress(progress);
                    String message = String.format("Completed %d%%.\n", progress);
                    progressMonitor.setNote(message);
                    if (progressMonitor.isCanceled() || bkgTask.isDone()) {
                        if (progressMonitor.isCanceled()) {
                            bkgTask.cancel(true);
                        }
                    }
                }
            }
        });
        bkgTask.execute();
    }

}
