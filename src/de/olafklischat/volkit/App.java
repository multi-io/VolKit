package de.olafklischat.volkit;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.undo.UndoManager;

import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.AWTGLCanvas;
import org.lwjgl.opengl.GL11;

import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.Event;
import de.matthiasmann.twl.GUI;
import de.matthiasmann.twl.renderer.lwjgl.LWJGLRenderer;
import de.matthiasmann.twl.theme.ThemeManager;
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
                    throw new RuntimeException(e);
                }
            }
        });
    }
    
    public App() throws Exception {
        Properties appProps = new Properties();
        appProps.load(new InputStreamReader(new FileInputStream("app.properties"), "utf-8"));
        
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
                    gui.setSize();
                    gui.updateTime();
                    gui.updateTimers();
                    gui.invokeRunables();
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
                  wasHandled = gui.handleMouse(e.getX(), e.getY(), mouseButtonAwtToTwl(e.getButton()), true);
                  break;
              case MouseEvent.MOUSE_RELEASED:
                  wasHandled = gui.handleMouse(e.getX(), e.getY(), mouseButtonAwtToTwl(e.getButton()), false);
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
        
        protected int mouseButtonAwtToTwl(int awtButton) {
            switch (awtButton) {
            case MouseEvent.BUTTON1:
                return Event.MOUSE_LBUTTON;
            case MouseEvent.BUTTON2:
                return Event.MOUSE_MBUTTON;
            case MouseEvent.BUTTON3:
                return Event.MOUSE_RBUTTON;
            default:
                return Event.MOUSE_LBUTTON; //or what?
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

        public void createUI(boolean isApplet) throws LWJGLException, IOException {
            
            de.olafklischat.twl.GridLayout mainPane = new de.olafklischat.twl.GridLayout(2, 2);
            mainPane.setTheme(""); //"buttonBox");
            renderer = new LWJGLRenderer();
            renderer.setUseSWMouseCursors(true);
            gui = new GUI(mainPane, renderer);
            
            Properties appProps = new Properties();
            appProps.load(new InputStreamReader(new FileInputStream("app.properties"), "utf-8"));
            
            final UndoManager undoMgr = new UndoManager();
            
            final JFrame f = new JFrame("SliceView");
            
            f.getContentPane().setBackground(Color.GRAY);
            f.getContentPane().setLayout(new BorderLayout());
            
            SliceViewer sv1 = new SliceViewer();
            mainPane.add(sv1);

            SliceViewer sv2 = new SliceViewer();
            mainPane.add(sv2);

            SliceViewer sv3 = new SliceViewer();
            mainPane.add(sv3);

            final TripleSliceViewerController slicesController = new TripleSliceViewerController(this, sv1, sv2, sv3, undoMgr);
            
            MeasurementsDB mdb = new MeasurementsDB(appProps.getProperty("mdb.basedir"));
            mdb.load();
            JTable measurementsTable = new JTable();
            final MeasurementsController measurementsController = new MeasurementsController(mdb, measurementsTable, sv1, sv2, sv3);

            mainPane.add(new Button(":-)"));

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

}
