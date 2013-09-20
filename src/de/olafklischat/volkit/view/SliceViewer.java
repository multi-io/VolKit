package de.olafklischat.volkit.view;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Set;

import javax.media.opengl.DebugGL2;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.GLProfile;
import javax.media.opengl.awt.GLCanvas;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.apache.log4j.Logger;

import de.olafklischat.volkit.model.TextureDebug;
import de.olafklischat.volkit.model.VolumeDataSet;
import de.sofd.util.IdentityHashSet;
import de.sofd.viskit.controllers.cellpaint.ImageTextureManager;
import de.sofd.viskit.image3D.jogl.util.GLShader;
import de.sofd.viskit.image3D.jogl.util.LinAlg;
import de.sofd.viskit.image3D.jogl.util.ShaderManager;


public class SliceViewer extends JPanel {

    static final Logger logger = Logger.getLogger(SliceViewer.class);

    static {
        System.setProperty("sun.awt.noerasebackground", "true");
        ShaderManager.init("shader");
    }
    
    private final VolumeDataSet volumeDataSet;
    
    private final TextureDebug tdb = new TextureDebug();

    /**
     * transformation from volume system to world system.
     * volume system = system whose z=0 plane cuts the volume quad in the middle in volume-z direction
     *     (system whose origin is in the middle of the volume, with x-, y- and z-axes parallel
     *     to the volume's)
     *     
     * 1 unit length = 1 mm in all systems
     */
    private float[] volumeToWorldTransform = new float[16];
    
     /**
     * transformation from world to "slice" system, i.e. the system whose z=0 plane
     * defines "base" slice plane (the actual slice plane is parallel to the
     * base slice plane, navigationZ unit lenghts
     * in z axis direction (slice system) away).
     * <p>
     * Normally one of three values
     * corresponding to the three main slice orientations.
     */
    private float[] worldToBaseSliceTransform = new float[16];
    
    //dependent matrices, updated by recomputeMatrices()
    private float[] volumeToBaseSliceTransform = new float[16];
    private float[] baseSliceToVolumeTransform = new float[16];
    

    private float navigationCubeLength;
    private float navigationZ;

    private GLCanvas glCanvas = null;

    private GLShader rescaleShader;
    
    protected static final Set<SliceViewer> instances = new IdentityHashSet<SliceViewer>();
    private static final SharedContextData sharedContextData = new SharedContextData();

    public SliceViewer(VolumeDataSet volumeDataSet) {
        setLayout(new BorderLayout());
        if (instances.isEmpty() || sharedContextData.getGlContext() != null) {
            createGlCanvas();
        }
        instances.add(this);
        this.volumeDataSet = volumeDataSet;
        navigationCubeLength = (float) Math.sqrt(volumeDataSet.getWidthInMm() * volumeDataSet.getWidthInMm() +
                volumeDataSet.getHeightInMm() * volumeDataSet.getHeightInMm() +
                volumeDataSet.getDepthInMm() * volumeDataSet.getDepthInMm());
        LinAlg.fillIdentity(volumeToWorldTransform);
        LinAlg.fillIdentity(worldToBaseSliceTransform);
        navigationZ = 0;
        recomputeMatrices();
    }

    private void createGlCanvas() {
        GLCapabilities caps = new GLCapabilities(GLProfile.get(GLProfile.GL2));
        caps.setDoubleBuffered(true);
        glCanvas = new GLCanvas(caps, null, sharedContextData.getGlContext(), null);
        glCanvas.addGLEventListener(new GLEventHandler());
        this.add(glCanvas, BorderLayout.CENTER);
        revalidate();
        setupInternalUiInteractions();
        //cellsViewer.addKeyListener(internalMouseEventHandler);
        glCanvas.addMouseListener(cellMouseEventDispatcher);
        glCanvas.addMouseMotionListener(cellMouseEventDispatcher);
        glCanvas.addMouseWheelListener(cellMouseEventDispatcher);
        //cellsViewer.addKeyListener(cellsViewerMouseAndKeyHandler);
    }

    public void refreshCells() {
        if (null == glCanvas) {
            return;
        }
        glCanvas.repaint();
    }

    public GLAutoDrawable getCellsViewer() {
        return glCanvas;
    }
    
    protected void recomputeMatrices() {
        LinAlg.fillMultiplication(worldToBaseSliceTransform, volumeToWorldTransform, volumeToBaseSliceTransform);
        LinAlg.inverse(volumeToBaseSliceTransform, baseSliceToVolumeTransform);
    }

    protected class GLEventHandler implements GLEventListener {

        @Override
        public void init(GLAutoDrawable glAutoDrawable) {
            // Use debug pipeline
            glAutoDrawable.setGL(new DebugGL2(glAutoDrawable.getGL().getGL2()));
            GL2 gl = glAutoDrawable.getGL().getGL2();
            gl.setSwapInterval(1);
            gl.glClearColor(0,0,0,0);
            gl.glShadeModel(gl.GL_FLAT);
            sharedContextData.ref(getCellsViewer().getContext());
            logger.debug("new GLCanvas being initialized, refcount=" + sharedContextData.getRefCount());
            if (sharedContextData.getRefCount() == 1) {
                SharedContextData.callContextInitCallbacks(sharedContextData, gl);
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        for (SliceViewer v : instances) {
                            if (v != SliceViewer.this) {
                                v.createGlCanvas();
                            }
                        }
                    }
                });
            }

            try {
                ShaderManager.read(gl, "sliceviewer");
                rescaleShader = ShaderManager.get("sliceviewer");
                rescaleShader.addProgramUniform("tex");
            } catch (Exception e) {
                throw new RuntimeException("couldn't initialize GL shader: " + e.getLocalizedMessage(), e);
            }
        }

        @Override
        public void display(GLAutoDrawable glAutoDrawable) {
            //System.out.println("DISP " + drawableToString(glAutoDrawable));
            GL2 gl = glAutoDrawable.getGL().getGL2();
            gl.glClear(gl.GL_COLOR_BUFFER_BIT);
            gl.glMatrixMode(gl.GL_MODELVIEW);
            //gl.glPushMatrix();
            gl.glLoadIdentity();

            gl.glPushAttrib(GL2.GL_CURRENT_BIT|GL2.GL_ENABLE_BIT);
            try {
                try {
                    //gl.glColor3f(1.0f, 0.0f, 1.0f);
                    //volumeDataSet.bindTexture(GL2.GL_TEXTURE1, gl, sharedContextData);
                    tdb.bindTexture(GL2.GL_TEXTURE1, gl, sharedContextData);
                    rescaleShader.bind();
                    rescaleShader.bindUniform("tex", 1);
                    gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, gl.GL_REPLACE);
                    gl.glBegin(GL2.GL_QUADS);
                    texturedSlicePoint(gl,-navigationCubeLength/2, -navigationCubeLength/2, navigationZ);
                    texturedSlicePoint(gl, navigationCubeLength/2, -navigationCubeLength/2, navigationZ);
                    texturedSlicePoint(gl, navigationCubeLength/2,  navigationCubeLength/2, navigationZ);
                    texturedSlicePoint(gl,-navigationCubeLength/2,  navigationCubeLength/2, navigationZ);
                    gl.glEnd();
                    tdb.unbindCurrentTexture(gl);
                    rescaleShader.unbind();
                } finally {
                }
            } finally {
                gl.glPopAttrib();
            }
            
            //gl.glPopMatrix();
        }
        
        private void texturedSlicePoint1(GL2 gl, float x, float y, float z) {
            // TODO: use the texture matrix rather than calculating the tex coordinates in here
            float[] pt = new float[]{x,y,z};
            float[] ptInVolume = LinAlg.mtimesv(baseSliceToVolumeTransform, pt, null);
            LinAlg.stimesv(1.0f/navigationCubeLength, ptInVolume, ptInVolume);
            LinAlg.vplusv(ptInVolume, new float[]{0.5f,0.5f,0.5f}, ptInVolume);
            gl.glTexCoord3fv(ptInVolume, 0);
            gl.glVertex2f(x, y);
        }

        private void texturedSlicePoint(GL2 gl, float x, float y, float z) {
            // TODO: use the texture matrix rather than calculating the tex coordinates in here
            float[] pt = new float[]{x,y,z};
            float[] ptInVolume = LinAlg.mtimesv(baseSliceToVolumeTransform, pt, null);
            LinAlg.stimesv(1.0f/navigationCubeLength, ptInVolume, ptInVolume);
            LinAlg.vplusv(ptInVolume, new float[]{0.5f,0.5f,0.5f}, ptInVolume);
            gl.glTexCoord2fv(ptInVolume, 0);
            gl.glVertex2f(x, y);
        }
        

        @Override
        public void reshape(GLAutoDrawable glAutoDrawable, int x, int y, int width, int height) {
            GL2 gl = (GL2) glAutoDrawable.getGL();
            setupEye2ViewportTransformation(gl);
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    //updateCellSizes(true, false);
                }
            };
            if (EventQueue.isDispatchThread()) {
                r.run();
            } else {
                try {
                    EventQueue.invokeAndWait(r);
                } catch (Exception e) {
                    throw new RuntimeException("CAN'T HAPPEN");
                }
            }
        }

        private void setupEye2ViewportTransformation(GL2 gl) {
            gl.glMatrixMode(gl.GL_PROJECTION);
            gl.glLoadIdentity();
            Dimension sz = glCanvas.getSize();
            if (sz != null) {
                /*
                 // visible area depending on canvas size
                gl.glOrtho(-sz.width / 2,   //  GLdouble    left,
                        sz.width / 2,   //    GLdouble      right,
                       -sz.height / 2,  //    GLdouble      bottom,
                        sz.height / 2,  //    GLdouble      top,
                       -1000, //  GLdouble      nearVal,
                        1000   //  GLdouble     farVal
                       );
                */
                
                // fixed visible area of size navigationCubeLength
                float viewWidth, viewHeight;
                if (sz.width > sz.height) {
                    viewHeight = navigationCubeLength;
                    viewWidth = viewHeight * sz.width / sz.height;
                } else {
                    viewWidth = navigationCubeLength;
                    viewHeight = viewWidth * sz.height / sz.width;
                }
                gl.glOrtho(-viewWidth / 2,   //  GLdouble    left,
                            viewWidth / 2,   //    GLdouble      right,
                           -viewHeight / 2,  //    GLdouble      bottom,
                            viewHeight / 2,  //    GLdouble      top,
                           -1000, //  GLdouble      nearVal,
                            1000   //  GLdouble     farVal
                           );

                /*
                // TODO: if we have a glViewPort() call, strange things happen
                //  (completely wrong viewport in some cells) if the J2D OGL pipeline is active.
                //  If we don't include it, everything works. Why? The JOGL UserGuide says
                //  that the viewport is automatically set to the drawable's size, but why
                //  is it harmful to do this manually too?
                gl.glViewport(0, //GLint x,
                              0, //GLint y,
                              getWidth(), //GLsizei width,
                              getHeight() //GLsizei height
                              );
                */
                gl.glDepthRange(0,1);
            }
        }

        @Override
        public void dispose(GLAutoDrawable glAutoDrawable) {
            logger.debug("disposing GLCanvas...");
            sharedContextData.unref();
            instances.remove(SliceViewer.this);
            // TODO: call dispose methods on paint listeners here
            logger.debug("GLCanvas disposed, refcount=" + sharedContextData.getRefCount() + ", GLCanvas inst. count = " + instances.size());
        }

    };

    
    private MouseAdapter cellMouseEventDispatcher = new MouseAdapter() {

        @Override
        public void mouseClicked(MouseEvent evt) {
            dispatchEventToCell(evt);
        }

        @Override
        public void mousePressed(MouseEvent evt) {
            dispatchEventToCell(evt);
        }

        @Override
        public void mouseReleased(final MouseEvent evt) {
             dispatchEventToCell(evt);
        }

        // TODO: generate correct enter/exit events for the cells?
        //       this is something that would be much easier with per-cell
        //       mouse listeners of course... (see TODO in commted-out block above)

        @Override
        public void mouseEntered(MouseEvent evt) {
             //dispatchEventToCell(evt);
        }

        @Override
        public void mouseExited(MouseEvent evt) {
            //dispatchEventToCell(evt);
        }

        @Override
        public void mouseMoved(MouseEvent evt) {
             dispatchEventToCell(evt);
        }

        @Override
        public void mouseDragged(MouseEvent evt) {
             dispatchEventToCell(evt);
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent evt) {
             dispatchEventToCell(evt);
        }

    };

    protected void dispatchEventToCell(MouseEvent evt) {
        Point mousePosInCell = new Point();
    }



    protected void setupInternalUiInteractions() {
        this.setFocusable(true);
        glCanvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
            }
        });

        InputMap inputMap = this.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap actionMap = this.getActionMap();
        if (inputMap != null && actionMap != null) {
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "up");
            actionMap.put("up", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                }
            });
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "down");
            actionMap.put("down", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                }
            });
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "left");
            actionMap.put("left", new SelectionShiftAction(-1));
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "right");
            actionMap.put("right", new SelectionShiftAction(1));
        }
    }
    
    protected class SelectionShiftAction extends AbstractAction {
        private int shift;
        public SelectionShiftAction(int shift) {
            this.shift = shift;
        }
        @Override
        public void actionPerformed(ActionEvent e) {
        }
    }

}
