package de.olafklischat.volkit.controller;

import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Set;

import de.olafklischat.volkit.view.SliceViewer;
import de.sofd.util.IdentityHashSet;
import de.sofd.viskit.image3D.jogl.util.LinAlg;

public class TrackedViewerDraggingController {
    
    // TODO: make these parameterizable
    private static final int MOUSE_BUTTON = MouseEvent.BUTTON3;
    private static final int MOUSE_MASK = MouseEvent.BUTTON3_MASK;

    protected final SliceViewer controlledViewer;

    /**
     * @invariant draggedViewers subset of controlledViewer.getTrackedViewers()
     */
    protected final Set<SliceViewer> draggedViewers = new IdentityHashSet<SliceViewer>();
    
    public TrackedViewerDraggingController(SliceViewer controlledViewer) {
        this.controlledViewer = controlledViewer;
        controlledViewer.addCanvasMouseListener(mouseHandler);
        controlledViewer.addCanvasMouseMotionListener(mouseHandler);
    }
    
    protected MouseAdapter mouseHandler = new MouseAdapter() {
        Point lastPos = null;
        
        @Override
        public void mousePressed(java.awt.event.MouseEvent e) {
            if ((e.getButton() == MOUSE_BUTTON || (e.getModifiers() & MOUSE_MASK) != 0)) {
                lastPos = new Point(e.getPoint());
                float[] ptInControlledSlice = controlledViewer.convertAwtToCanvas(e.getPoint());
                //printPt(ptInControlledSlice);
                float[] ptInVolume = LinAlg.mtimesv(controlledViewer.getSliceToVolumeTransform(), ptInControlledSlice, null);
                //printPt(ptInVolume);
                draggedViewers.clear();
                for (SliceViewer trackedSv : controlledViewer.getTrackedViewers()) {
                    float[] ptInTrackedSlice = LinAlg.mtimesv(trackedSv.getVolumeToSliceTransform(), ptInVolume, null);
                    if (Math.abs(ptInTrackedSlice[2]) < 1.0) {  //TODO: the 1.0 must depend on the controlledViewer's current canvas (viewport) extent
                        draggedViewers.add(trackedSv);
                        e.consume();
                    }
                }
            } else {
                lastPos = null;
            }
        }
        
        private void printPt(float[] p) {
            System.out.println(""+p[0] + " " + p[1] + " " + p[2]);
        }
        
        @Override
        public void mouseDragged(java.awt.event.MouseEvent e) {
            if (lastPos != null && (e.getButton() == MOUSE_BUTTON || (e.getModifiers() & MOUSE_MASK) != 0)) {
                if (draggedViewers.isEmpty()) {
                    return;
                }
                float[] ptInControlledSlice = controlledViewer.convertAwtToCanvas(e.getPoint());
                //printPt(ptInControlledSlice);
                float[] ptInVolume = LinAlg.mtimesv(controlledViewer.getSliceToVolumeTransform(), ptInControlledSlice, null);
                for (SliceViewer draggedSv : draggedViewers) {
                    //TODO
                }
            }
        }
        
        public void mouseReleased(MouseEvent e) {
            draggedViewers.clear();
            lastPos = null;
        }
    };
}
