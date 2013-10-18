package de.olafklischat.volkit;

import java.awt.BorderLayout;
import java.awt.Color;
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
import org.lwjgl.opengl.GL11;
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

    private class MainFrameCanvas extends AWTGLCanvas {

        protected LWJGLRenderer renderer;
        protected ThemeManager theme;
        private /*static*/ final String[] THEME_FILES = {
            "simple.xml",
            "guiTheme.xml"
        };

        protected PersistentIntegerModel curThemeIdx;

        public MainFrameCanvas() throws LWJGLException {
            super();
        }
        
        protected void initGL() {
            super.initGL();
            try {
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
        protected void paintGL() {
            try {
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glLoadIdentity();
                            
                GL11.glColor3f(1, 0, 0);
                GL11.glBegin(GL11.GL_LINES);
                GL11.glVertex2f(0, 0);
                GL11.glVertex2f(100, 50);
                GL11.glEnd();

                gui.draw();
                //gui.setCursor();
                swapBuffers();
            } catch (LWJGLException ex) {
                throw new RuntimeException(ex);
            }
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
                    //Display.setVSyncEnabled(vsyncModel.getValue());
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
    
    

}
