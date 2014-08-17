package de.olafklischat.volkit.controller;

import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import de.olafklischat.volkit.view.VolumeViewer;
import de.olafklischat.volkit.math.LinAlg;

/**
 * VolumeCameraController. Is associated with a {@link VolumeViewer},
 * allows the user to rotate the viewer's camera around the origin
 * (always pointing to the origin) and zoom in or out, all using
 * mouse (button1) and mouse wheel interactions.
 *
 * @author Olaf Klischat
 */
public class VolumeCameraController {
    
    // TODO: make these parameterizable
    private static final int MOUSE_BUTTON = MouseEvent.BUTTON1;
    private static final int MOUSE_MASK = MouseEvent.BUTTON1_MASK;

    protected final VolumeViewer controlledViewer;

    public VolumeCameraController(VolumeViewer controlledViewer) {
        this.controlledViewer = controlledViewer;
        controlledViewer.addCanvasMouseListener(mouseHandler);
        controlledViewer.addCanvasMouseMotionListener(mouseHandler);
        controlledViewer.addCanvasMouseWheelListener(mouseHandler);
    }
    
    protected MouseAdapter mouseHandler = new MouseAdapter() {
        Point lastPos = null;
        
        @Override
        public void mousePressed(java.awt.event.MouseEvent e) {
            if ((e.getButton() == MOUSE_BUTTON || (e.getModifiers() & MOUSE_MASK) != 0)) {
                lastPos = new Point(e.getPoint());
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
                Point pos = e.getPoint();
                float roty = ((float)pos.x - lastPos.x) / 400 * 180;
                float rotx = ((float)pos.y - lastPos.y) / 400 * 180;
                float[] deltaTransform = new float[16];
                float[] w2e = controlledViewer.getWorldToEyeTransform();
                float[] eyeToOriginVec = new float[]{w2e[12], w2e[13], w2e[14]};  //first 2 components always 0... b/c the eye points to origin??
                float eyeToOriginDist = LinAlg.length(eyeToOriginVec);  // == w2e[12], thus

                LinAlg.fillIdentity(deltaTransform);
                LinAlg.fillTranslation(deltaTransform, 0, 0, -eyeToOriginDist, deltaTransform);
                LinAlg.fillRotation(deltaTransform, rotx, 1, 0, 0, deltaTransform);
                LinAlg.fillRotation(deltaTransform, roty, 0, 1, 0, deltaTransform);
                LinAlg.fillTranslation(deltaTransform, 0, 0, eyeToOriginDist, deltaTransform);
                LinAlg.fillMultiplication(deltaTransform, w2e, w2e);
                controlledViewer.setWorldToEyeTransform(w2e);
                controlledViewer.refresh();

                lastPos = new Point(e.getPoint());
                e.consume();
            } else {
                lastPos = null;
            }
        }
        
        public void mouseReleased(MouseEvent e) {
            lastPos = null;
        }

        float vpWidthMin = 0.01f, vpWidthMax = 2.0f;
        
        @Override
        public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
            float scaleChange = (e.getWheelRotation() > 0 ? 1.1f/1f : 1f/1.1f);
            float vpWidth = controlledViewer.getVpWidthInRadiants();
            vpWidth *= scaleChange;
            if (vpWidth < vpWidthMin) { vpWidth = vpWidthMin; }
            if (vpWidth > vpWidthMax) { vpWidth = vpWidthMax; }
            controlledViewer.setVpWidthInRadiants(vpWidth);
            e.consume();
        }
    };
}
