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
import javax.swing.JTable;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;

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
    private JTable measurementsTable;
    private List<SliceViewer> sliceViewers = new ArrayList<SliceViewer>();
    private Measurement currentMeasurement;
    
    private boolean restrictMeasurementsTableToCurrDataset = false;  //TODO: impl

    private Color nextDrawingColor = Color.green;
    
    private boolean foregroundMeasurementsVisible = false;
    private boolean backgroundMeasurementsVisible = false;

    /**
     * 
     * @param mdb MUST NOT be changed outside this controller
     * @param measurementsTable
     * @param svs
     */
    public MeasurementsController(MeasurementsDB mdb, JTable measurementsTable, SliceViewer... svs) {
        this.mdb = mdb;
        this.measurementsTable = measurementsTable;
        measurementsTable.setModel(measurementsTableModel);
        for (SliceViewer sv: svs) {
            sv.addCanvasMouseListener(sliceViewersMouseHandler);
            sv.addCanvasMouseMotionListener(sliceViewersMouseHandler);
            sv.addSlicePaintListener(sliceViewersPaintHandler);
            sliceViewers.add(sv);
        }
        measurementsTable.getSelectionModel().addListSelectionListener(measurementsSelectionHandler);
        refreshViewers();
    }

    public boolean isForegroundMeasurementsVisible() {
        return foregroundMeasurementsVisible;
    }
    
    public void setForegroundMeasurementsVisible(boolean foregroundMeasurementsVisible) {
        this.foregroundMeasurementsVisible = foregroundMeasurementsVisible;
    }
    
    public boolean isBackgroundMeasurementsVisible() {
        return backgroundMeasurementsVisible;
    }
    
    public void setBackgroundMeasurementsVisible(boolean backgroundMeasurementsVisible) {
        this.backgroundMeasurementsVisible = backgroundMeasurementsVisible;
    }
    
    private MeasurementsTableModel measurementsTableModel = new MeasurementsTableModel();
    
    private class MeasurementsTableModel extends AbstractTableModel {
        private String[] colNames = new String[]{
                "nr.", "dataset", "length (mm)"
        };

        @Override
        public String getColumnName(int column) {
            return colNames[column];
        }
        
        @Override
        public int getRowCount() {
            return mdb.size();
        }
        
        @Override
        public int getColumnCount() {
            return colNames.length;
        }
        
        public List<Measurement> getAllDisplayedMeasurements() {
            return mdb.getMeasurements();
        }

        public Measurement getMeasurementAt(int row) {
            return getAllDisplayedMeasurements().get(row);
        }
        
        protected int getCurrentRowNumberOf(Measurement m) {
            //TODO: optimize
            List<Measurement> ms = getAllDisplayedMeasurements();
            int i = 0;
            for (Measurement msm : ms) {
                if (m.equals(msm)) {
                    return i;
                }
                i++;
            }
            return -1;
        }
        
        @Override
        public Object getValueAt(int row, int col) {
            Measurement m = getMeasurementAt(row);
            switch (col) {
            case 0:
                return m.getNumber();
            case 1:
                return m.getDatasetName();
            case 2:
                return m.getLengthInMm();
            default:
                throw new RuntimeException("SHOULD NEVER HAPPEN: col=" + col);
            }
        }
        
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
        
    };

    /**
     * Utility method
     */
    protected void runIgnoringTableSelectionEvents(Runnable r) {
        boolean prevValue = ignoreTableSelectionEvents;
        ignoreTableSelectionEvents = true;
        try {
            r.run();
        } finally {
            ignoreTableSelectionEvents = prevValue;
        }
    }
    
    private boolean ignoreTableSelectionEvents = false;
    
    private ListSelectionListener measurementsSelectionHandler = new ListSelectionListener() {
        @Override
        public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting()) {
                return;
            }
            if (ignoreTableSelectionEvents) {
                return;
            }
            /*
            System.out.println("sel. rows:");
            for (int i: measurementsTable.getSelectedRows()) {
                System.out.println(" "+i);
            }
            System.out.println("lead sel.idx: " + measurementsTable.getSelectionModel().getLeadSelectionIndex());
            */
            int lsi = measurementsTable.getSelectionModel().getLeadSelectionIndex();
            if (lsi != -1) {
                selectMeasurement(measurementsTableModel.getMeasurementAt(lsi));
            }
        }
    };
    
    protected void selectMeasurementInTable(final Measurement m) {
        runIgnoringTableSelectionEvents(new Runnable() {
            @Override
            public void run() {
                int r = measurementsTableModel.getCurrentRowNumberOf(m);
                if (r != -1) {
                    selectMeasurementInTable(r);
                }
            }
        });
    }
    
    protected void selectMeasurementInTable(final int rowInCurrTable) {
        runIgnoringTableSelectionEvents(new Runnable() {
            @Override
            public void run() {
                measurementsTable.getSelectionModel().setSelectionInterval(rowInCurrTable, rowInCurrTable);
            }
        });
    }

    public void selectMeasurement(Measurement m) {
        selectMeasurementInTable(m);
        if (!m.getDatasetName().equals(sliceViewers.get(0).getVolumeDataSet().getDatasetName())) {
            return;
        }
        for (int i=0; i < sliceViewers.size(); i++) {
            sliceViewers.get(i).setVolumeToWorldTransform(m.getVolumeToWorldTransformation());
            sliceViewers.get(i).setNavigationZ(m.getNavigationZs()[i]);
        }
    }

    protected void refreshTable() {
        runIgnoringTableSelectionEvents(new Runnable() {
            @Override
            public void run() {
                measurementsTableModel.fireTableDataChanged();
            }
        });
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
                refreshTable();
                selectMeasurementInTable(currentMeasurement);
                currentMeasurement = null;
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
                    if (m.getDatasetName().equals(sv.getVolumeDataSet().getDatasetName())) {
                        paintMeasurement(gl, m);
                    }
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
