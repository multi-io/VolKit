package de.olafklischat.volkit;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.undo.UndoManager;

import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.AWTGLCanvas;
import org.lwjgl.opengl.GL11;

import de.matthiasmann.twl.Alignment;
import de.matthiasmann.twl.BoxLayout;
import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.GUI;
import de.matthiasmann.twl.Label;
import de.matthiasmann.twl.Scrollbar;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.BoxLayout.Direction;
import de.matthiasmann.twl.renderer.lwjgl.LWJGLRenderer;
import de.matthiasmann.twl.theme.ThemeManager;
import de.olafklischat.volkit.controller.DatasetsController;
import de.olafklischat.volkit.controller.MeasurementsController;
import de.olafklischat.volkit.controller.TrackedViewerDraggingController;
import de.olafklischat.volkit.controller.TripleSliceViewerController;
import de.olafklischat.volkit.controller.VolumeCameraController;
import de.olafklischat.volkit.model.MeasurementsDB;
import de.olafklischat.volkit.view.SharedContextData;
import de.olafklischat.volkit.view.SliceViewer;
import de.olafklischat.volkit.view.VolumeViewer;
import de.olafklischat.twlawt.TwlAwtEventUtil;

public class App {

    JFrame f;
    protected GUI gui;
    
    /**
     * @param args
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    new App();
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(null, e.getLocalizedMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    throw new RuntimeException(e);
                }
            }
        });
    }
    
    public App() throws Exception {
        Properties appProps = new Properties();
        appProps.load(new InputStreamReader(new FileInputStream("app.properties"), "utf-8"));
        
        f = new JFrame("SliceView");
        
        f.getContentPane().setBackground(Color.GRAY);
        f.getContentPane().setLayout(new BorderLayout());
        
        final MainFrameCanvas canvas = new MainFrameCanvas();
        f.getContentPane().add(canvas, BorderLayout.CENTER);
        canvas.setVisible(true);
        
        f.setSize(1200,900);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setVisible(true);
    }

    /**
     * TODO factor this out into a generic TwlAwtGLCanvas or something, maybe even
     * in a separate project.
     * 
     * @author Olaf Klischat
     */
    private class MainFrameCanvas extends AWTGLCanvas {

        protected LWJGLRenderer renderer;
        protected ThemeManager theme;
        
        protected Timer guiUpdateTimer;
        protected boolean resizePending = true;

        public MainFrameCanvas() throws LWJGLException {
            super();
            enableEvents(AWTEvent.MOUSE_EVENT_MASK|AWTEvent.MOUSE_MOTION_EVENT_MASK|AWTEvent.MOUSE_WHEEL_EVENT_MASK);
            guiUpdateTimer = new Timer(100, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (gui == null) {
                        return;
                    }
                    MainFrameCanvas.this.repaint();
                }
            });
            guiUpdateTimer.start();
        }
        
        @Override
        protected void processMouseEvent(MouseEvent e) {
            super.processMouseEvent(e);
            processAnyMouseEvent(e);
        }
        
        @Override
        protected void processMouseMotionEvent(MouseEvent e) {
            super.processMouseMotionEvent(e);
            processAnyMouseEvent(e);
        }
        
        @Override
        protected void processMouseWheelEvent(MouseWheelEvent e) {
            super.processMouseWheelEvent(e);
            processAnyMouseEvent(e);
        }
        
        
        protected void processAnyMouseEvent(MouseEvent e) {
            if (e.isConsumed()) {
                return;
            }
            if (gui == null) {
                return;
            }
            //TODO: in some cases, TWL-internal mouse handlers require a valid
            // current GL context -- see doc/twl-lwjgl.txt for examples. Those
            // will fail here, of course. Maybe only queue up the mouse events
            // in here and dispatch them in paintGL? Would that incur undesired
            // semantical changes
            boolean wasHandled = false;
            int id = e.getID();
            switch(id) {
              case MouseEvent.MOUSE_PRESSED:
                  wasHandled = gui.handleMouse(e.getX(), e.getY(), TwlAwtEventUtil.mouseButtonAwtToTwl(e.getButton()), true);
                  break;
              case MouseEvent.MOUSE_RELEASED:
                  wasHandled = gui.handleMouse(e.getX(), e.getY(), TwlAwtEventUtil.mouseButtonAwtToTwl(e.getButton()), false);
                  break;
              case MouseEvent.MOUSE_DRAGGED:
                  wasHandled = gui.handleMouse(e.getX(), e.getY(), -1, false);
                  break;
              case MouseEvent.MOUSE_MOVED:
                  wasHandled = gui.handleMouse(e.getX(), e.getY(), -1, false);
                  break;
              case MouseEvent.MOUSE_WHEEL:
                  wasHandled = gui.handleMouseWheel(((MouseWheelEvent)e).getWheelRotation() < 0 ? 1 : -1);
                  break;
              // not forwarded / generated by TWL internally: mouseClicked, mouseEntered, mouseExited
            }
            if (wasHandled) {
                e.consume();
                MainFrameCanvas.this.repaint();
            }
        }

        protected void initGL() {
            super.initGL();
            try {
                createUI(false);
            } catch (Exception e) {
                throw new RuntimeException(e.getLocalizedMessage(), e);
            }
        }

        private void loadTheme() throws IOException {
            renderer.syncViewportSize();
            System.out.println("width="+renderer.getWidth()+" height="+renderer.getHeight());

            long startTime = System.nanoTime();
            // NOTE: this code destroys the old theme manager (including it's cache context)
            // after loading the new theme with a new cache context.
            // This allows easy reloading of a theme for development.
            // If you want fast theme switching without reloading then use the existing
            // cache context for loading the new theme and don't destroy the old theme.
            ThemeManager newTheme = ThemeManager.createThemeManager(App.class.getResource("simple.xml"), renderer);
            long duration = System.nanoTime() - startTime;
            System.out.println("Loaded theme in " + (duration/1000) + " us");

            if(theme != null) {
                theme.destroy();
            }
            theme = newTheme;
            
            gui.setSize();
            gui.applyTheme(theme);
            gui.setBackground(theme.getImageNoWarning("gui.background"));
            
        }

        BoxLayout toolbar;
        
        private void addToolbarAction(final Action a) {
            Button b = new Button((String) a.getValue(Action.NAME));
            b.setTooltipContent(a.getValue(Action.SHORT_DESCRIPTION));
            b.addCallback(new Runnable() {
                @Override
                public void run() {
                    a.actionPerformed(null);
                }
            });
            toolbar.add(b);
        }
        
        public void createUI(boolean isApplet) throws Exception, LWJGLException, IOException {
            toolbar = new BoxLayout(Direction.HORIZONTAL);
            toolbar.setTheme("");
            toolbar.setAlignment(Alignment.CENTER);
            
            final de.olafklischat.twl.GridLayout mainPane = new de.olafklischat.twl.GridLayout(2, 2);
            mainPane.setTheme(""); //"buttonBox");
            
            Widget root = new Widget() {
                @Override
                protected void layout() {
                    int h = 10 + toolbar.getPreferredHeight();
                    toolbar.setPosition(getInnerX(), getInnerY());
                    toolbar.setSize(getInnerWidth(), h);
                    mainPane.setPosition(getInnerX(), getInnerY() + h);
                    mainPane.setSize(getInnerWidth(), getInnerHeight() - h);
                }
                
                @Override
                protected void paint(GUI gui) {
                    super.paint(gui);
                }
            };
            root.setTheme("");
            root.add(toolbar);
            root.add(mainPane);
            
            renderer = new LWJGLRenderer();
            renderer.setUseSWMouseCursors(true);
            gui = new GUI(root, renderer);
            
            Properties appProps = new Properties();
            appProps.load(new InputStreamReader(new FileInputStream("app.properties"), "utf-8"));
            
            final UndoManager undoMgr = new UndoManager();
            
            SharedContextData scd = new SharedContextData();
            
            SliceViewer sv1 = new SliceViewer(scd);
            mainPane.add(sv1);

            SliceViewer sv2 = new SliceViewer(scd);
            mainPane.add(sv2);
            //mainPane.add(new Button(":-|"));

            SliceViewer sv3 = new SliceViewer(scd);
            mainPane.add(sv3);
            //mainPane.add(new Button(":-("));

            if (isDebugMode()) {
                new TrackedViewerDraggingController(sv1);
                new TrackedViewerDraggingController(sv2);
                new TrackedViewerDraggingController(sv3);
            }
            
            VolumeViewer vv = new VolumeViewer(scd);
            mainPane.add(vv);
            
            new VolumeCameraController(vv);

            final TripleSliceViewerController slicesController = new TripleSliceViewerController(this, sv1, sv2, sv3, vv, undoMgr);
            
            MeasurementsDB mdb = new MeasurementsDB(appProps.getProperty("mdb.basedir"));
            mdb.load();
            JTable measurementsTable = new JTable();
            final MeasurementsController measurementsController = new MeasurementsController(mdb, measurementsTable, vv, sv1, sv2, sv3);


            addToolbarAction(new AbstractAction("Tx RST") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    slicesController.resetVolumeToWorldTransform();
                }
            });
            addToolbarAction(new AbstractAction("Nav RST") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    slicesController.resetZNavigations();
                }
            });
            if (isDebugMode()) {
            addToolbarAction(new AbstractAction("Undo") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (undoMgr.canUndo()) {
                        undoMgr.undo();
                    }
                }
            });
            addToolbarAction(new AbstractAction("Redo") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (undoMgr.canRedo()) {
                        undoMgr.redo();
                    }
                }
            });
            }
            // TODO: UndoManager doesn't have PropertyChangeEvents for its
            // canUndo/canRedo properties, so we can't easily enable/disable the above buttons
            // at the right time. Would have to make all the involved components
            // (TripleSliceViewerController in this case) fire UndoableEditEvents, which, frankly, sucks.

            addToolbarAction(new AbstractAction("Zoom RST") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    slicesController.resetSliceToCanvasTransformations();
                }
            });
            if (isDebugMode()) {
                addToolbarAction(new AbstractAction("Load Volume") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        final JFileChooser fc = new JFileChooser();
                        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                        int returnVal = fc.showOpenDialog(f);
                        if (returnVal == JFileChooser.APPROVE_OPTION) {
                            File dir = fc.getSelectedFile();
                            if (dir.isDirectory()) {
                                slicesController.startLoadingVolumeDataSetInBackground(dir.getAbsolutePath(), 1);
                            } else {
                                JOptionPane.showMessageDialog(f, "not a directory: " + dir, "Error", JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    }
                });
                addToolbarAction(new AbstractAction("incisx") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        slicesController.startLoadingVolumeDataSetInBackground("/home/olaf/oliverdicom/INCISIX", 1);
                    }
                });
                addToolbarAction(new AbstractAction("brainix") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        //slicesController.startLoadingVolumeDataSetInBackground("/home/olaf/oliverdicom/BRAINIX/BRAINIX/IRM/T1-3D-FFE-C - 801", 1);
                    }
                });
            }
            toolbar.add(new Label("transparency"));
            final Scrollbar transpSlider = new Scrollbar(Scrollbar.Orientation.HORIZONTAL);
            transpSlider.setTheme("hslider");
            transpSlider.setMinMaxValue(0, 10000);
            //transpSlider.setMinSize(300, 1);
            toolbar.add(transpSlider);
            transpSlider.addCallback(new Runnable() {
                @Override
                public void run() {
                    measurementsController.setTransparencyCoeff((float)Math.exp((float)transpSlider.getValue()/1500f));
                }
            });
            transpSlider.setValue(1650);
            
            // measurements frame

            {
                final JFrame measurementsFrame = new JFrame("Measurements");
                JScrollPane scrollpane = new JScrollPane(measurementsTable);
                measurementsFrame.getContentPane().add(scrollpane, BorderLayout.CENTER);

                JToolBar measurementsToolbar = new JToolBar();
                measurementsFrame.getContentPane().add(measurementsToolbar, BorderLayout.SOUTH);
                measurementsToolbar.add(new AbstractAction("Export") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        JFileChooser fc = new JFileChooser();
                        if (JFileChooser.APPROVE_OPTION == fc.showSaveDialog(measurementsFrame)) {
                            try {
                                measurementsController.exportAllMeasurements(fc.getSelectedFile());
                            } catch (Exception ex) {
                                JOptionPane.showMessageDialog(measurementsFrame, ex.getLocalizedMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                                ex.printStackTrace();
                            }
                        }
                    }
                });
                
                measurementsFrame.setSize(500,1000);
                measurementsFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                measurementsFrame.setVisible(true);
            }
            
            // datasets frame

            {
                JFrame datasetsFrame = new JFrame("Datasets");
                JList datasetsList = new JList();
                datasetsList.setLayoutOrientation(JList.VERTICAL);
                JScrollPane scrollpane = new JScrollPane(datasetsList);
                datasetsFrame.getContentPane().add(scrollpane, BorderLayout.CENTER);
                
                datasetsFrame.setSize(300,1000);
                datasetsFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                datasetsFrame.setVisible(true);
                
                DatasetsController dsc = new DatasetsController(new File(appProps.getProperty("dataset.basedir")), datasetsList, slicesController);
            }
            
            slicesController.loadVolumeDataSet("/home/olaf/oliverdicom/INCISIX", 1);
            //slicesController.loadVolumeDataSet("/home/olaf/gi/resources/DICOM-Testbilder/00001578", 4);

            loadTheme();
        }
        
        @Override
        protected void paintGL() {
            try {
                if (resizePending) {
                    GL11.glViewport(0, 0, MainFrameCanvas.this.getWidth(), MainFrameCanvas.this.getHeight());
                    renderer.syncViewportSize();
                    gui.setSize();
                    resizePending = false;
                }
                
                GL11.glClearColor(0, 0, 0, 1);
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

                gui.setSize();
                gui.updateTime();
                gui.updateTimers();
                gui.invokeRunables();
                gui.validateLayout();
                gui.draw();
                gui.handleTooltips();  // TODO: better do this after dispatching mouse events in here?
                //gui.setCursor();
                swapBuffers();
            } catch (LWJGLException ex) {
                throw new RuntimeException(ex);
            }
        }
        
        
        @Override
        public void componentResized(ComponentEvent e) {
            super.componentResized(e);
            resizePending = true;
        }
    }

    public static boolean isDebugMode() {
        return (null != System.getProperty("VolKit.debug"));
    }
}
