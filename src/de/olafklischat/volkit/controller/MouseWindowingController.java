package de.olafklischat.volkit.controller;

import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import de.olafklischat.volkit.view.SliceViewer;
import de.olafklischat.volkit.view.VolumeViewer;

public class MouseWindowingController {

    // TODO: make these parameterizable
    private static final int MOUSE_BUTTON = MouseEvent.BUTTON3;
    private static final int MOUSE_MASK = MouseEvent.BUTTON3_MASK;

    // TODO: we really need a common base class for VolumeViewer and SliceViewer
    private VolumeViewer volumeViewer;
    private List<SliceViewer> sliceViewers = new ArrayList<SliceViewer>();

    /**
     * 
     * @param mdb MUST NOT be changed outside this controller
     * @param measurementsTable
     * @param svs
     */
    public MouseWindowingController(VolumeViewer vv, SliceViewer... svs) {
        volumeViewer = vv;
        vv.addCanvasMouseListener(sliceViewersMouseHandler);
        vv.addCanvasMouseMotionListener(sliceViewersMouseHandler);
        for (SliceViewer sv: svs) {
            sv.addCanvasMouseListener(sliceViewersMouseHandler);
            sv.addCanvasMouseMotionListener(sliceViewersMouseHandler);
            sliceViewers.add(sv);
        }
        refreshViewers();
    }

    
    private MouseAdapter sliceViewersMouseHandler = new MouseAdapter() {
        private Object currentViewer;
        private Point lastPosition;
        @Override
        public void mousePressed(MouseEvent e) {
            if ((!e.isShiftDown() && !e.isAltDown() && !e.isControlDown()) && ((e.getButton() == MOUSE_BUTTON || (e.getModifiers() & MOUSE_MASK) != 0))) {
                currentViewer = e.getSource();
                lastPosition = e.getPoint();
                e.consume();
            }
        }
        
        @Override
        public void mouseDragged(MouseEvent e) {
            if ((!e.isShiftDown() && !e.isAltDown() && !e.isControlDown()) && ((e.getButton() == MOUSE_BUTTON || (e.getModifiers() & MOUSE_MASK) != 0))) {
                Object sourceViewer = e.getSource();
                if (sourceViewer != null && sourceViewer == currentViewer) {
                    float scale, offset;
                    if (sourceViewer instanceof SliceViewer) {
                        SliceViewer v = (SliceViewer) sourceViewer;
                        scale = v.getShadingPostScale();
                        offset = v.getShadingPostOffset();
                    } else {
                        VolumeViewer v = (VolumeViewer) sourceViewer;
                        scale = v.getShadingPostScale();
                        offset = v.getShadingPostOffset();
                    }

                    final Point sourcePosition = e.getPoint();

                    /*
                    // modification algorithm 1 -- change of scale/offset proportional to mouse movement
                    float brightnessDelta = 0.003f * (sourcePosition.y - lastPosition.y);
                    float contrastDelta = 0.003f * (sourcePosition.x - lastPosition.x);
                    
                    float newscale = scale + contrastDelta;
                    offset = offset + 0.5f * scale - 0.5f * newscale + brightnessDelta;
                    scale = newscale;
                    */
                    
                    // modification algorithm 2 -- compute ww/wl, change those proportionally to mouse movement
                    float ww = 1f/scale;
                    float wl = (0.5f - offset) / scale;
                    ww += 1.0/300 * (sourcePosition.y - lastPosition.y);
                    if (ww < 0.001f) {
                        ww = 0.001f;
                    }
                    wl += 1.0/300 * (sourcePosition.x - lastPosition.x);

                    scale = 1F/ww;
                    offset = (ww/2-wl)*scale;
                    
                    for (SliceViewer sv: sliceViewers) {
                        sv.setShadingPostOffset(offset);
                        sv.setShadingPostScale(scale);
                    }
                    volumeViewer.setShadingPostOffset(offset);
                    volumeViewer.setShadingPostScale(scale);
                    
                    lastPosition = sourcePosition;
                    e.consume();
                }
            }
        }
        
    };
    

    protected void refreshViewers() {
        for (SliceViewer sv: sliceViewers) {
            sv.refresh();
        }
    }

}
