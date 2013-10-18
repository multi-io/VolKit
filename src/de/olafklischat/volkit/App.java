package de.olafklischat.volkit;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;

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
                    e.printStackTrace();
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
        canvas.setFocusable(true);
        canvas.requestFocus();
        //canvas.setIgnoreRepaint(true);
        canvas.setVisible(true);
        
        JToolBar tb = new JToolBar();
        f.getContentPane().add(tb, BorderLayout.NORTH);
        tb.add(new AbstractAction("startLWJGL") {
            @Override
            public void actionPerformed(ActionEvent e) {
                canvas.startLWJGL();
            }
        });
        
        f.setSize(1200,900);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setVisible(true);
    }

    Thread gameThread;

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
            System.out.println("addNotify");
            //startLWJGL();  // results in  X Error - [..] 32 error: BadValue (integer parameter out of range for operation) request_code: 1 minor_code: 0
                             // (in Display.create)
        }
        
        @Override
        public final void removeNotify() {
            stopLWJGL();
            super.removeNotify();
        }
        
        protected void startLWJGL() {
            try {
                //Thread.sleep(3000);
                Display.setParent(MainFrameCanvas.this);
                Display.setVSyncEnabled(true);
                Display.create();
                desktopMode = Display.getDisplayMode();
                curThemeIdx = new PersistentIntegerModel(
                        Preferences.userNodeForPackage(App.class),
                        "currentThemeIndex", 0, THEME_FILES.length, 0);
                //simpleTest.mainLoop(true);
                setupUi();
            } catch (Exception e) {
                throw new RuntimeException(e.getLocalizedMessage(), e);
            }
            
            /*
            //multi-threaded attempt (works, but having to use MT for this sucks)
            gameThread = new Thread() {
                @Override
                public void run() {
                    try {
                        Display.setParent(MainFrameCanvas.this);
                        Display.setVSyncEnabled(true);
                        Display.create();
                        //simpleTest = new SimpleTest();
                        desktopMode = Display.getDisplayMode();
                        curThemeIdx = new PersistentIntegerModel(
                                Preferences.userNodeForPackage(App.class),
                                "currentThemeIndex", 0, THEME_FILES.length, 0);
                        //simpleTest.mainLoop(true);
                        setupUi();
                        mainLoop(true);
                        //
                        Display.destroy();
                    } catch (Exception ex) {
                        StringWriter sw = new StringWriter();
                        PrintWriter pw = new PrintWriter(sw);
                        ex.printStackTrace(pw);
                        pw.flush();
                        Sys.alert("Error", sw.toString());
                    }
                    System.exit(0);
                }
            };
            gameThread.start();
            */

            /*
            //first attempt
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
            */
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

        RootPane root;
        boolean closeRequested = false;
        
        protected void setupUi() throws LWJGLException, IOException {
            root = new RootPane();
            renderer = new LWJGLRenderer();
            renderer.setUseSWMouseCursors(true);
            gui = new GUI(root, renderer);

            loadTheme();

            root.addButton("Exit", new Runnable() {
                public void run() {
                    closeRequested = true;
                }
            });

            //fInfo.requestKeyboardFocus();
        }
        
        protected void mainLoop(boolean isApplet) throws LWJGLException, IOException {
            while(!Display.isCloseRequested() && !closeRequested) {
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

                gui.update();
                Display.update();

                if(root.reduceLag) {
                    reduceInputLag();
                }

                if(!Display.isVisible()) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException unused) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        /**
         * reduce input lag by polling input devices after waiting for vsync
         * 
         * Call after Display.update()
         */
        public void reduceInputLag() {
            GL11.glGetError();          // this call will burn the time between vsyncs
            Display.processMessages();  // process new native messages since Display.update();
            Mouse.poll();               // now update Mouse events
            Keyboard.poll();            // and Keyboard too
        }
        
        @Override
        public void paint(Graphics g) {
            System.out.println("paint()");
            if (Display.isCreated() && Display.isVisible()) {
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                gui.update();
                Display.update();
            }
        }

        protected void stopLWJGL() {
            
        }
        
    }

    
    
    
    static class RootPane extends Widget {   // from twl SimpleTest demo
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

}
