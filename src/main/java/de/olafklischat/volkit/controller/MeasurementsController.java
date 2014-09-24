package de.olafklischat.volkit.controller;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;

import org.lwjgl.opengl.GL11;

import de.olafklischat.volkit.math.LinAlg;
import de.olafklischat.volkit.model.Measurement;
import de.olafklischat.volkit.model.MeasurementsDB;
import de.olafklischat.volkit.model.VolumeDataSet;
import de.olafklischat.volkit.view.PaintEvent;
import de.olafklischat.volkit.view.PaintListener;
import de.olafklischat.volkit.view.SliceViewer;
import de.olafklischat.volkit.view.VolumeViewer;
import de.olafklischat.lwjgl.LWJGLTools;
import de.olafklischat.lwjgl.ShaderProgram;

/**
 * Measurements controller. Knows the measurements db, the measurements table view,
 * and all the volume and slice viewers. Displays the measurements in the table
 * (one per row) and draws them in the viewers. Allows the user to select a measurement
 * in the table -- it is then drawn in the viewers in a different color. Also
 * allows the user to add new measurements to the currently
 * loaded volume dataset by drawing the new measurements in any of the slice viewers
 * (using Shift + Right mouse button), or to delete measurements using a context menu
 * on the corresponding table row.
 * <p>
 * The viewers must all display the same volume dataset.
 * <p>
 * TODO: ability to select measurements by clicking them in the slice and maybe even
 * volume viewers.
 *
 * @author Olaf Klischat
 */
public class MeasurementsController {

    // TODO: make these parameterizable
    private static final int MOUSE_BUTTON = MouseEvent.BUTTON3;
    private static final int MOUSE_MASK = MouseEvent.BUTTON3_MASK;

    private MeasurementsDB mdb;
    private JTable measurementsTable;
    private VolumeViewer volumeViewer;
    private List<SliceViewer> sliceViewers = new ArrayList<SliceViewer>();
    private Measurement currentMeasurement;
    
    private boolean restrictMeasurementsTableToCurrDataset = false;  //TODO: impl

    private Color nextDrawingColor = Color.green;
    
    private float transparencyCoeff = 3f;
    
    private boolean foregroundMeasurementsVisible = false;
    private boolean backgroundMeasurementsVisible = false;

    /**
     * 
     * @param mdb MUST NOT be changed outside this controller
     * @param measurementsTable
     * @param svs
     */
    public MeasurementsController(final MeasurementsDB mdb, final JTable measurementsTable, VolumeViewer vv, SliceViewer... svs) {
        this.mdb = mdb;
        this.measurementsTable = measurementsTable;
        measurementsTable.setModel(measurementsTableModel);
        volumeViewer = vv;
        for (SliceViewer sv: svs) {
            sv.addCanvasMouseListener(sliceViewersMouseHandler);
            sv.addCanvasMouseMotionListener(sliceViewersMouseHandler);
            sv.addSlicePaintListener(sliceViewersPaintHandler);
            sliceViewers.add(sv);
        }
        vv.addPaintListener(VolumeViewer.ZORDER_BEFORE_VOLUME + 1, volumeViewerPaintHandler);
        measurementsTable.getSelectionModel().addListSelectionListener(measurementsSelectionHandler);
        final JPopupMenu ctxMenu = new JPopupMenu();
        final int[] popupClickedRow = new int[1];
        ctxMenu.add(new JMenuItem(new AbstractAction("Delete this Measurement") {
            @Override
            public void actionPerformed(ActionEvent e) {
                mdb.removeMeasurement(measurementsTableModel.getMeasurementAt(popupClickedRow[0]));
                refreshTable();
                refreshViewers();
            }
        }));
        measurementsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }
            private void maybeShowPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = measurementsTable.rowAtPoint(e.getPoint());
                    if (row != -1) {
                        popupClickedRow[0] = row;
                        ctxMenu.show(e.getComponent(),
                                     e.getX(), e.getY());
                    }
                }
            }            
        });
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
            return mdb.getMeasurements().get(row);
        }
        
        protected int getCurrentRowNumberOf(Measurement m) {
            return mdb.getIndexOf(m);
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
        refreshTable();
        selectMeasurementInTable(m);
        if (!m.getDatasetName().equals(sliceViewers.get(0).getVolumeDataSet().getDatasetName())) {
            refreshViewers();
            return;
        }
        for (int i=0; i < sliceViewers.size(); i++) {
            sliceViewers.get(i).setVolumeToWorldTransform(m.getVolumeToWorldTransformation());
            sliceViewers.get(i).setNavigationZ(m.getNavigationZs()[i]);
        }
        volumeViewer.setVolumeToWorldTransform(m.getVolumeToWorldTransformation());
    }
    
    public boolean isSelected(Measurement m) {
        int row = measurementsTableModel.getCurrentRowNumberOf(m);
        return measurementsTable.getSelectionModel().isSelectedIndex(row);
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
                currentMeasurement.setPt0InVolume(awtToVolume(e.getPoint(), sv));
                e.consume();
            }
        }
        
        @Override
        public void mouseDragged(MouseEvent e) {
            if (currentMeasurement != null &&
                    e.isShiftDown() && (e.getButton() == MOUSE_BUTTON || (e.getModifiers() & MOUSE_MASK) != 0)) {
                SliceViewer sv = (SliceViewer) e.getSource();
                currentMeasurement.setPt1InVolume(awtToVolume(e.getPoint(), sv));
                sv.refresh();
                e.consume();
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (currentMeasurement != null && currentMeasurement.getPt1InVolume() != null) {
                mdb.addMeasurement(currentMeasurement);
                System.err.println("new measurement added: " + currentMeasurement);
                //refreshTable();
                selectMeasurement(currentMeasurement);
                currentMeasurement = null;
            }
        }
        
    };
    
    protected static float[] awtToVolume(Point p, SliceViewer sv) {
        float[] ptInSlice = LinAlg.mtimesv(sv.getCanvasToSliceTransform(), sv.convertAwtToCanvas(p), null);
        return LinAlg.mtimesv(sv.getSliceToVolumeTransform(), ptInSlice, null);
    }
    
    public float getTransparencyCoeff() {
        return transparencyCoeff;
    }
    
    public void setTransparencyCoeff(float transparencyCoeff) {
        this.transparencyCoeff = transparencyCoeff;
        refreshViewers();
    }
    
    private PaintListener<SliceViewer> sliceViewersPaintHandler = new PaintListener<SliceViewer>() {

        ShaderProgram measShaderProg;

        @Override
        public void glSharedContextDataInitialization(SliceViewer sv, Map<String, Object> sharedData) {
        }
        
        @Override
        public void glDrawableInitialized(SliceViewer sv, Map<String, Object> sharedData) {
        }
        
        @Override
        public void glDrawableDisposing(SliceViewer sv, Map<String, Object> sharedData) {
        }

        @Override
        public void onPaint(PaintEvent<SliceViewer> e) {
            SliceViewer sv = e.getSource();
            if (null == measShaderProg) {
                try {
                    measShaderProg = new ShaderProgram();
                    measShaderProg.create();
                    measShaderProg.attachShaderFromResource("shader/measurements/measurements.vert");
                    measShaderProg.attachShaderFromResource("shader/measurements/measurements.frag");
                    measShaderProg.link();
                } catch (Exception ex) {
                    throw new RuntimeException("couldn't initialize GL shader: " + ex.getLocalizedMessage(), ex);
                }
            }
            
            measShaderProg.use();
            measShaderProg.setUniform("transpCoeff", getTransparencyCoeff());
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT);  // b/c we set up alpha blending
            try {
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glLoadIdentity();
                GL11.glMultMatrix(LWJGLTools.toFB(sv.getSliceToCanvasTransform()));
                GL11.glMultMatrix(LWJGLTools.toFB(sv.getVolumeToSliceTransform()));
                if (currentMeasurement != null && currentMeasurement.getPt1InVolume() != null) {
                    paintMeasurement(currentMeasurement);
                }
                for (Measurement m : mdb.getMeasurements()) {
                    if (m.getDatasetName().equals(sv.getVolumeDataSet().getDatasetName())) {
                        paintMeasurement(m);
                    }
                }
            } finally {
                GL11.glPopAttrib();
                GL11.glPopMatrix();
                measShaderProg.unuse();
            }
        }
        
        private void paintMeasurement(Measurement m) {
            if (isSelected(m)) {
                GL11.glColor3f(1f, 1f, 0f);
            } else {
                GL11.glColor3f((float) m.getColor().getRed() / 255F,
                               (float) m.getColor().getGreen() / 255F,
                               (float) m.getColor().getBlue() / 255F);
            }
            GL11.glBegin(GL11.GL_LINE_STRIP);
            LWJGLTools.glVertex3fv(m.getPt0InVolume());
            LWJGLTools.glVertex3fv(m.getPt1InVolume());
            GL11.glEnd();
        }
        
    };


    private PaintListener<VolumeViewer> volumeViewerPaintHandler = new PaintListener<VolumeViewer>() {

        @Override
        public void glSharedContextDataInitialization(VolumeViewer sv, Map<String, Object> sharedData) {
        }
        
        @Override
        public void glDrawableInitialized(VolumeViewer sv, Map<String, Object> sharedData) {
        }
        
        @Override
        public void glDrawableDisposing(VolumeViewer sv, Map<String, Object> sharedData) {
        }

        @Override
        public void onPaint(PaintEvent<VolumeViewer> e) {
            VolumeViewer vv = e.getSource();

            //GL11.glPushAttrib(GL11.GL_CURRENT_BIT|GL11.GL_LIGHTING_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_POLYGON_BIT|GL11.GL_ENABLE_BIT|GL11.GL_VIEWPORT_BIT|GL11.GL_TRANSFORM_BIT);
            GL11.glPushAttrib(GL11.GL_CURRENT_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_ENABLE_BIT|GL11.GL_TRANSFORM_BIT);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glMultMatrix(LWJGLTools.toFB(vv.getVolumeToWorldTransform()));
            if (currentMeasurement != null && currentMeasurement.getPt1InVolume() != null) {
                paintMeasurement(currentMeasurement);
            }
            for (Measurement m : mdb.getMeasurements()) {
                if (m.getDatasetName().equals(vv.getVolumeDataSet().getDatasetName())) {
                    paintMeasurement(m);
                }
            }
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
        
        private void paintMeasurement(Measurement m) {
            if (isSelected(m)) {
                GL11.glColor3f(1f, 1f, 0f);
            } else {
                GL11.glColor3f((float) m.getColor().getRed() / 255F,
                               (float) m.getColor().getGreen() / 255F,
                               (float) m.getColor().getBlue() / 255F);
            }
            GL11.glBegin(GL11.GL_LINE_STRIP);
            LWJGLTools.glVertex3fv(m.getPt0InVolume());
            LWJGLTools.glVertex3fv(m.getPt1InVolume());
            GL11.glEnd();
        }
        
    };

    
    protected void refreshViewers() {
        for (SliceViewer sv: sliceViewers) {
            sv.refresh();
        }
    }

    public void exportAllMeasurements(File f) throws IOException {
        PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f), "utf-8"));
        try {
            w.println("nr,dataset,length");
            for (Measurement m : measurementsTableModel.getAllDisplayedMeasurements()) {
                w.println("" + m.getNumber() + "," + m.getDatasetName() + "," + m.getLengthInMm());
            }
        } finally {
            w.close();
        }
    }
    
}
