package de.olafklischat.volkit.controller;

import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Set;

import de.olafklischat.volkit.view.SliceViewer;
import de.olafklischat.math.LinAlg;
import de.sofd.util.IdentityHashSet;

public class TrackedViewerDraggingController {
    
    // TODO: make these parameterizable
    private static final int MOUSE_BUTTON = MouseEvent.BUTTON1;
    private static final int MOUSE_MASK = MouseEvent.BUTTON1_MASK;

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
                float[] ptInControlledViewerCanvas = controlledViewer.convertAwtToCanvas(e.getPoint());
                float[] ptInControlledSlice = LinAlg.mtimesv(controlledViewer.getCanvasToSliceTransform(), ptInControlledViewerCanvas, null);
                //printPt(ptInControlledSlice);
                float[] ptInVolume = LinAlg.mtimesv(controlledViewer.getSliceToVolumeTransform(), ptInControlledSlice, null);
                //printPt(ptInVolume);
                draggedViewers.clear();
                for (SliceViewer trackedSv : controlledViewer.getTrackedViewers()) {
                    float[] ptInTrackedSliceSystem = LinAlg.mtimesv(trackedSv.getVolumeToSliceTransform(), ptInVolume, null);
                    printPt(ptInTrackedSliceSystem);
                    float pixelScale = LinAlg.mtimesv(trackedSv.getSliceToCanvasTransform(), new float[]{2,0,0}, null)[0];
                    if (Math.abs(ptInTrackedSliceSystem[2]) < pixelScale) {
                        draggedViewers.add(trackedSv);
                        System.err.println("start dragging viewer " + trackedSv);
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
                float[] ptInControlledViewerCanvas = controlledViewer.convertAwtToCanvas(e.getPoint());
                float[] ptInControlledSlice = LinAlg.mtimesv(controlledViewer.getCanvasToSliceTransform(), ptInControlledViewerCanvas, null);
                float[] ptInVolume = LinAlg.mtimesv(controlledViewer.getSliceToVolumeTransform(), ptInControlledSlice, null);

                for (SliceViewer draggedViewer : draggedViewers) {
                    float[] ptInDraggedBaseSliceSystem = LinAlg.mtimesv(draggedViewer.getVolumeToBaseSliceTransform(), ptInVolume, null);
                    float newNavZ = ptInDraggedBaseSliceSystem[2];
                    if (Math.abs(newNavZ) < draggedViewer.getNavigationCubeLength()/2) {
                        draggedViewer.setNavigationZ(ptInDraggedBaseSliceSystem[2]);
                        System.err.println("dragged viewer " + draggedViewer);
                    }
                }
                e.consume();
            }
        }
        
        public void mouseReleased(MouseEvent e) {
            draggedViewers.clear();
            lastPos = null;
        }
    };
}
