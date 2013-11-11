package de.olafklischat.volkit.controller;

import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import de.olafklischat.volkit.view.VolumeViewer;

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
                System.out.println("drag " + (e.getX() - lastPos.getX()));
                lastPos = new Point(e.getPoint());
                e.consume();
            } else {
                lastPos = null;
            }
        }
        
        public void mouseReleased(MouseEvent e) {
            lastPos = null;
        }
    };
}
