package de.olafklischat.volkit.controller;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;

import de.olafklischat.volkit.model.Measurement;
import de.olafklischat.volkit.model.MeasurementsDB;
import de.olafklischat.volkit.model.VolumeDataSet;
import de.olafklischat.volkit.view.SlicePaintEvent;
import de.olafklischat.volkit.view.SlicePaintListener;
import de.olafklischat.volkit.view.SliceViewer;
import de.sofd.viskit.image3D.jogl.util.GLShader;
import de.sofd.viskit.image3D.jogl.util.LinAlg;
import de.sofd.viskit.image3D.jogl.util.ShaderManager;

public class MeasurementsController {

    // TODO: make these parameterizable
    private static final int MOUSE_BUTTON = MouseEvent.BUTTON3;
    private static final int MOUSE_MASK = MouseEvent.BUTTON3_MASK;

    private MeasurementsDB mdb;
    private List<SliceViewer> sliceViewers = new ArrayList<SliceViewer>();
    private Measurement currentMeasurement;

    private Color nextDrawingColor = Color.green;

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

        Map<SliceViewer, GLShader> measShaderBySV = new IdentityHashMap<SliceViewer, GLShader>();

        @Override
        public void glSharedContextDataInitialization(SliceViewer sv, GL gl1, Map<String, Object> sharedData) {
            /*
            // TODO: this is how we should initialize measShader (just one, not one per SliceViewer), but it doesn't work for now
            // because measShader stored the GL object, which won't be usable in other contexts
            GLShader measShader = (GLShader) sharedData.get("measurementsShader");
            if (null == measShader) {
                GL2 gl = gl1.getGL2();
                try {
                    ShaderManager.read(gl, "measurements");
                    measShader = ShaderManager.get("measurements");
                    //measShader.addProgramUniform("tex");
                    sharedData.put("measurementsShader", measShader);
                } catch (Exception e) {
                    throw new RuntimeException("couldn't initialize GL shader: " + e.getLocalizedMessage(), e);
                }
            }
            */
        }
        
        @Override
        public void glDrawableInitialized(SliceViewer sv, GLAutoDrawable glAutoDrawable, Map<String, Object> sharedData) {
        }
        
        @Override
        public void glDrawableDisposing(SliceViewer sv, GLAutoDrawable glAutoDrawable, Map<String, Object> sharedData) {
        }

        @Override
        public void onPaint(SlicePaintEvent e) {
            SliceViewer sv = e.getSource();
            GL2 gl = e.getGl().getGL2();
            GLShader measShader = measShaderBySV.get(sv);
            if (null == measShader) {
                try {
                    // TODO: this is the wrong place to initialize measShader -- see above
                    ShaderManager.read(gl, "measurements");
                    measShader = ShaderManager.get("measurements");
                    //measShader.addProgramUniform("tex");
                    measShaderBySV.put(sv, measShader);
                } catch (Exception ex) {
                    throw new RuntimeException("couldn't initialize GL shader: " + ex.getLocalizedMessage(), ex);
                }
            }
            
            measShader.bind();
            //measShader.bindUniform("tex", 0);
            gl.glMatrixMode(GL2.GL_MODELVIEW);
            gl.glPushMatrix();
            gl.glPushAttrib(GL.GL_COLOR_BUFFER_BIT);  // b/c we set up alpha blending
            try {
                gl.glEnable(GL.GL_BLEND);
                gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
                gl.glLoadIdentity();
                gl.glMultMatrixf(sv.getVolumeToSliceTransform(), 0);  // TODO: sv.getNavigationZ() is missing, and restrict visible depth to just the slice
                if (currentMeasurement != null && currentMeasurement.getPt1InVolume() != null) {
                    paintMeasurement(gl, currentMeasurement);
                }
                for (Measurement m : mdb.getMeasurements()) {
                    paintMeasurement(gl, m);
                }
            } finally {
                gl.glPopAttrib();
                gl.glPopMatrix();
                measShader.unbind();
            }
        }
        
        private void paintMeasurement(GL2 gl, Measurement m) {
            gl.glColor3f((float) m.getColor().getRed() / 255F,
                    (float) m.getColor().getGreen() / 255F,
                    (float) m.getColor().getBlue() / 255F);
            gl.glBegin(GL.GL_LINE_STRIP);
            gl.glVertex3fv(m.getPt0InVolume(), 0);
            gl.glVertex3fv(m.getPt1InVolume(), 0);
            gl.glEnd();
        }
        
    };

    protected void refreshViewers() {
        for (SliceViewer sv: sliceViewers) {
            sv.refresh();
        }
    }
    
}
