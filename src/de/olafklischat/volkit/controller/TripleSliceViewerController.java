package de.olafklischat.volkit.controller;

import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import de.olafklischat.volkit.view.SliceViewer;
import de.sofd.viskit.image3D.jogl.util.LinAlg;


public class TripleSliceViewerController {

    protected SliceViewer sv1;
    protected SliceViewer sv2;
    protected SliceViewer sv3;
    
    public TripleSliceViewerController(SliceViewer sv1, SliceViewer sv2, SliceViewer sv3) {
        this.sv1 = sv1;
        this.sv2 = sv2;
        this.sv3 = sv3;

        float[] identity = new float[16];
        LinAlg.fillIdentity(identity);
        sv1.setVolumeToWorldTransform(identity);
        sv2.setVolumeToWorldTransform(identity);
        sv3.setVolumeToWorldTransform(identity);

        sv1.setWorldToBaseSliceTransform(SliceViewer.BASE_SLICE_XZ);
        sv2.setWorldToBaseSliceTransform(SliceViewer.BASE_SLICE_YZ);
        sv3.setWorldToBaseSliceTransform(SliceViewer.BASE_SLICE_XY);

        VolumeRotatingMouseHandler rhxz = new VolumeRotatingMouseHandler(new float[]{1,0,0}, new float[]{0,0,1});
        sv1.addCanvasMouseListener(rhxz);
        sv1.addCanvasMouseMotionListener(rhxz);
        
        VolumeRotatingMouseHandler rhyz = new VolumeRotatingMouseHandler(new float[]{0,-1,0}, new float[]{0,0,1});
        sv2.addCanvasMouseListener(rhyz);
        sv2.addCanvasMouseMotionListener(rhyz);

        VolumeRotatingMouseHandler rhxy = new VolumeRotatingMouseHandler(new float[]{1,0,0}, new float[]{0,1,0});
        sv3.addCanvasMouseListener(rhxy);
        sv3.addCanvasMouseMotionListener(rhxy);
    }
    

    private class VolumeRotatingMouseHandler extends MouseAdapter {

        /**
         * rot. axes
         */
        float[] horizAxis;
        float[] vertAxis;
        
        public VolumeRotatingMouseHandler(float[] horizAxis, float[] vertAxis) {
            this.horizAxis = horizAxis;
            this.vertAxis = vertAxis;
        }
        
        private Point lastPos = null;
        
        @Override
        public void mousePressed(MouseEvent e) {
            lastPos = e.getPoint();
        }
        @Override
        public void mouseDragged(MouseEvent e) {
            Point pos = e.getPoint();
            if (lastPos != null) {
                float roty = - ((float)pos.x - lastPos.x) / 400 * 180;
                float rotx = - ((float)pos.y - lastPos.y) / 400 * 180;
                float[] volumeDeltaTransform = new float[16];
                LinAlg.fillIdentity(volumeDeltaTransform);
                LinAlg.fillRotation(volumeDeltaTransform, rotx, horizAxis[0], horizAxis[1], horizAxis[2], volumeDeltaTransform);
                LinAlg.fillRotation(volumeDeltaTransform, roty, vertAxis[0], vertAxis[1], vertAxis[2], volumeDeltaTransform);
                float[] vol2worlTx = sv1.getVolumeToWorldTransform();
                LinAlg.fillMultiplication(volumeDeltaTransform, vol2worlTx, vol2worlTx);
                sv1.setVolumeToWorldTransform(vol2worlTx);
                sv2.setVolumeToWorldTransform(vol2worlTx);
                sv3.setVolumeToWorldTransform(vol2worlTx);
            }
            lastPos = pos;
        }

    }
    
    public void resetVolumeToWorldTransform() {
        float[] identity = new float[16];
        LinAlg.fillIdentity(identity);
        sv1.setVolumeToWorldTransform(identity);
        sv2.setVolumeToWorldTransform(identity);
        sv3.setVolumeToWorldTransform(identity);
    }
    
    public void resetZNavigations() {
        sv1.setNavigationZ(0f);
        sv2.setNavigationZ(0f);
        sv3.setNavigationZ(0f);
    }
    
}
