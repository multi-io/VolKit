package de.olafklischat.volkit;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.GraphicsDevice;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.undo.UndoManager;

import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Bindings;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.AWTGLCanvas;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.Drawable;
import org.lwjgl.opengl.PixelFormat;

import de.matthiasmann.twl.BoxLayout;
import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.DesktopArea;
import de.matthiasmann.twl.Event;
import de.matthiasmann.twl.GUI;
import de.matthiasmann.twl.Label;
import de.matthiasmann.twl.ToggleButton;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.model.PersistentIntegerModel;
import de.matthiasmann.twl.model.SimpleBooleanModel;
import de.matthiasmann.twl.renderer.lwjgl.LWJGLRenderer;
import de.matthiasmann.twl.theme.ThemeManager;
import de.olafklischat.volkit.controller.DatasetsController;
import de.olafklischat.volkit.controller.MeasurementsController;
import de.olafklischat.volkit.controller.TripleSliceViewerController;
import de.olafklischat.volkit.model.MeasurementsDB;
import de.olafklischat.volkit.view.SliceViewer;

public class App {

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
                    System.exit(1);
                }
            }
        });
    }
    
    public App() throws Exception {
        Properties appProps = new Properties();
        appProps.load(new InputStreamReader(new FileInputStream("app.properties"), "utf-8"));
        
        //final UndoManager undoMgr = new UndoManager();
        
        final JFrame f = new JFrame("SliceView");
        
        f.getContentPane().setBackground(Color.GRAY);
        f.getContentPane().setLayout(new BorderLayout());
        
        final MainFrameCanvas canvas = new MainFrameCanvas();
        f.getContentPane().add(canvas, BorderLayout.CENTER);
        canvas.setVisible(true);
        
        f.setSize(1200,900);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setVisible(true);
    }

    private class MainFrameCanvas extends Canvas {

        protected LWJGLRenderer renderer;
        protected ThemeManager theme;
        private /*static*/ final String[] THEME_FILES = {
            "simple.xml",
            "guiTheme.xml"
        };

        protected PersistentIntegerModel curThemeIdx;
        protected DisplayMode desktopMode;

        public MainFrameCanvas() throws LWJGLException {
            super();
        }
        
        @Override
        public final void addNotify() {
            super.addNotify();
            startLWJGL();
        }
        
        @Override
        public final void removeNotify() {
            stopLWJGL();
            super.removeNotify();
        }
        
        protected void startLWJGL() {
            //Input.disableControllers();
            try {
                Display.setParent(this);
                Display.setVSyncEnabled(true);
                Display.create();
                desktopMode = Display.getDisplayMode();
                curThemeIdx = new PersistentIntegerModel(
                        Preferences.userNodeForPackage(App.class),
                        "currentThemeIndex", 0, THEME_FILES.length, 0);
                
                final RootPane root = new RootPane();
                renderer = new LWJGLRenderer();
                renderer.setUseSWMouseCursors(true);
                gui = new GUI(root, renderer);
    
                loadTheme();
                
                root.addButton("Exit", new Runnable() {
                    public void run() {
                        //closeRequested = true;
                    }
                });
            } catch (LWJGLException e) {
                throw new RuntimeException(e.getLocalizedMessage(), e);
            } catch (IOException e) {
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
            ThemeManager newTheme = ThemeManager.createThemeManager(
                App.class.getResource(THEME_FILES[curThemeIdx.getValue()]), renderer);
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

        @Override
        public void paint(Graphics g) {
            gui.update();
            Display.update();
        }

        protected void stopLWJGL() {
            
        }
        
    }

    
    
    
    static class RootPane extends Widget {
        final DesktopArea desk;
        final BoxLayout btnBox;
        final BoxLayout vsyncBox;
        boolean reduceLag = true;

        public RootPane() {
            setTheme("");
            
            desk = new DesktopArea();
            desk.setTheme("");

            btnBox = new BoxLayout(BoxLayout.Direction.HORIZONTAL);
            btnBox.setTheme("buttonBox");

            vsyncBox = new BoxLayout(BoxLayout.Direction.HORIZONTAL);
            vsyncBox.setTheme("buttonBox");

            final SimpleBooleanModel vsyncModel = new SimpleBooleanModel(true);
            vsyncModel.addCallback(new Runnable() {
                public void run() {
                    Display.setVSyncEnabled(vsyncModel.getValue());
                }
            });

            ToggleButton vsyncBtn = new ToggleButton(vsyncModel);
            vsyncBtn.setTheme("checkbox");
            Label l = new Label("VSync");
            l.setLabelFor(vsyncBtn);

            vsyncBox.add(l);
            vsyncBox.add(vsyncBtn);

            add(desk);
            add(btnBox);
            add(vsyncBox);
        }

        public Button addButton(String text, Runnable cb) {
            Button btn = new Button(text);
            btn.addCallback(cb);
            btnBox.add(btn);
            invalidateLayout();
            return btn;
        }

        public Button addButton(String text, String ttolTip, Runnable cb) {
            Button btn = addButton(text, cb);
            btn.setTooltipContent(ttolTip);
            return btn;
        }
        
        @Override
        protected void layout() {
            btnBox.adjustSize();
            btnBox.setPosition(0, getParent().getHeight() - btnBox.getHeight());
            desk.setSize(getParent().getWidth(), getParent().getHeight());
            vsyncBox.adjustSize();
            vsyncBox.setPosition(
                    getParent().getWidth() - vsyncBox.getWidth(),
                    getParent().getHeight() - vsyncBox.getHeight());
        }

        @Override
        protected void afterAddToGUI(GUI gui) {
            super.afterAddToGUI(gui);
            validateLayout();
        }

        @Override
        protected boolean handleEvent(Event evt) {
            if(evt.getType() == Event.Type.KEY_PRESSED &&
                    evt.getKeyCode() == Keyboard.KEY_L &&
                    (evt.getModifiers() & Event.MODIFIER_CTRL) != 0 &&
                    (evt.getModifiers() & Event.MODIFIER_SHIFT) != 0) {
                reduceLag ^= true;
                System.out.println("reduceLag = " + reduceLag);
            }

            return super.handleEvent(evt);
        }

    }
    
    
    
    
    
    
    
    
    ////////////////////////////////////////////////////// old, JOGL-base impl.
    
    /**
     * @param args
     */
    public static void main1(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    Properties appProps = new Properties();
                    appProps.load(new InputStreamReader(new FileInputStream("app.properties"), "utf-8"));
                    
                    final UndoManager undoMgr = new UndoManager();
                    
                    final JFrame f = new JFrame("SliceView");
                    
                    f.getContentPane().setBackground(Color.GRAY);
                    f.getContentPane().setLayout(new BorderLayout());
                    
                    JPanel mainPane = new JPanel();
                    mainPane.setBackground(Color.GRAY);
                    f.getContentPane().add(mainPane, BorderLayout.CENTER);
                    
                    mainPane.setLayout(new GridLayout(2, 2, 5, 5));
                    
                    SliceViewer sv1 = new SliceViewer();
                    sv1.setBackground(Color.DARK_GRAY);
                    mainPane.add(sv1);

                    SliceViewer sv2 = new SliceViewer();
                    sv2.setBackground(Color.DARK_GRAY);
                    mainPane.add(sv2);

                    SliceViewer sv3 = new SliceViewer();
                    sv3.setBackground(Color.DARK_GRAY);
                    mainPane.add(sv3);
                    
                    final TripleSliceViewerController slicesController = new TripleSliceViewerController(sv1, sv2, sv3, undoMgr);
                    
                    MeasurementsDB mdb = new MeasurementsDB(appProps.getProperty("mdb.basedir"));
                    mdb.load();
                    JTable measurementsTable = new JTable();
                    final MeasurementsController measurementsController = new MeasurementsController(mdb, measurementsTable, sv1, sv2, sv3);

                    mainPane.add(new JLabel(":-)", JLabel.CENTER));
                    
                    JToolBar toolbar = new JToolBar();
                    toolbar.setFloatable(false);
                    toolbar.setBackground(Color.GRAY);
                    f.getContentPane().add(toolbar, BorderLayout.NORTH);
                    toolbar.add(new AbstractAction("Tx RST") {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            slicesController.resetVolumeToWorldTransform();
                        }
                    });
                    toolbar.add(new AbstractAction("Nav RST") {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            slicesController.resetZNavigations();
                        }
                    });
                    toolbar.add(new AbstractAction("Undo") {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            if (undoMgr.canUndo()) {
                                undoMgr.undo();
                            }
                        }
                    });
                    toolbar.add(new AbstractAction("Redo") {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            if (undoMgr.canRedo()) {
                                undoMgr.redo();
                            }
                        }
                    });
                    // TODO: UndoManager doesn't have PropertyChangeEvents for its
                    // canUndo/canRedo properties, so we can't easily enable/disable the above buttons
                    // at the right time. Would have to make all the involved components
                    // (TripleSliceViewerController in this case) fire UndoableEditEvents, which, frankly, sucks.

                    toolbar.add(new AbstractAction("Zoom RST") {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            slicesController.resetSliceToCanvasTransformations();
                        }
                    });
                    if (null != System.getProperty("VolKit.debug")) {
                        toolbar.add(new AbstractAction("Load Volume") {
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
                        toolbar.add(new AbstractAction("incisx") {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                slicesController.startLoadingVolumeDataSetInBackground("/home/olaf/oliverdicom/INCISIX", 1);
                            }
                        });
                        toolbar.add(new AbstractAction("brainix") {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                slicesController.startLoadingVolumeDataSetInBackground("/home/olaf/oliverdicom/BRAINIX/BRAINIX/IRM/T1-3D-FFE-C - 801", 1);
                            }
                        });
                    }
                    toolbar.add(new JLabel("transparency"));
                    final JSlider transpSlider = new JSlider(0, 10000);
                    toolbar.add(transpSlider);
                    transpSlider.addChangeListener(new ChangeListener() {
                        @Override
                        public void stateChanged(ChangeEvent e) {
                            measurementsController.setTransparencyCoeff((float)Math.exp((float)transpSlider.getValue()/1500f));
                        }
                    });
                    transpSlider.setValue(1650);
                    
                    f.setSize(1200,900);
                    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    f.setVisible(true);
                    

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
                    
                    //slicesController.loadVolumeDataSet("/home/olaf/oliverdicom/INCISIX", 1);
                    //slicesController.loadVolumeDataSet("/home/olaf/gi/resources/DICOM-Testbilder/00001578", 4);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(null, e.getLocalizedMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    throw new RuntimeException(e);
                }
            }
        });
    }

}
