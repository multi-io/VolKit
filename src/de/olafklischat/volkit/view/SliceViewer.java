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
import javax.swing.BoundedRangeModel;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.log4j.Logger;

import de.olafklischat.volkit.model.VolumeDataSet;
import de.sofd.util.IdentityHashSet;
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
    
    /**
     * transformation from volume system to world system.
     * volume system = system whose origin is in the middle of the volume, with x-, y- and z-axes parallel
     *                 to the volume's
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
     * corresponding to the three main slice orientations (BASE_SLICE_xx constants).
     */
    private float[] worldToBaseSliceTransform = new float[16];
    
    public static final float[] BASE_SLICE_XY = new float[16];
    public static final float[] BASE_SLICE_XZ = new float[16];
    public static final float[] BASE_SLICE_YZ = new float[16];
    
    static {
        // TODO: verify
        LinAlg.fillIdentity(BASE_SLICE_XY);
        LinAlg.fillRotation(BASE_SLICE_XY, -90, 1, 0, 0, BASE_SLICE_XZ);
        LinAlg.fillRotation(BASE_SLICE_XZ, 90, 0, 0, 1, BASE_SLICE_YZ);
    }
    
    //dependent matrices, updated by recomputeMatrices()
    private float[] volumeToBaseSliceTransform = new float[16];
    private float[] baseSliceToVolumeTransform = new float[16];

    private float navigationCubeLength;
    private float navigationZ;

    private GLCanvas glCanvas = null;
    private GLShader fragShader;
    
    private JSlider navZslider;
    
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
        navZslider = new JSlider(JSlider.HORIZONTAL);
        this.add(navZslider, BorderLayout.SOUTH);
        navZslider.getModel().setMinimum(0);
        navZslider.getModel().setMaximum(10000);

        LinAlg.fillIdentity(volumeToWorldTransform);
        LinAlg.fillIdentity(worldToBaseSliceTransform);
        navigationZ = 0;
        recomputeMatrices();
        
        navZslider.getModel().addChangeListener(navZsliderChangeListener);
        updateNavZslider();
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

    public GLAutoDrawable getGlCanvas() {
        return glCanvas;
    }
    
    public float getNavigationZ() {
        return navigationZ;
    }
    
    public void setNavigationZ(float navigationZ) {
        this.navigationZ = navigationZ;
        recomputeMatrices();
        updateNavZslider();
        refresh();
    }
    
    public float[] getVolumeToWorldTransform() {
        return volumeToWorldTransform;
    }
    
    public void setVolumeToWorldTransform(float[] volumeToWorldTransform) {
        LinAlg.copyArr(volumeToWorldTransform, this.volumeToWorldTransform);
        recomputeMatrices();
        refresh();
    }
    
    public float[] getWorldToBaseSliceTransform() {
        return worldToBaseSliceTransform;
    }
    
    public void setWorldToBaseSliceTransform(float[] worldToBaseSliceTransform) {
        LinAlg.copyArr(worldToBaseSliceTransform, this.worldToBaseSliceTransform);
        recomputeMatrices();
        refresh();
    }
    
    protected void recomputeMatrices() {
        LinAlg.fillMultiplication(worldToBaseSliceTransform, volumeToWorldTransform, volumeToBaseSliceTransform);
        LinAlg.inverse(volumeToBaseSliceTransform, baseSliceToVolumeTransform);
    }
    
    public void refresh() {
        if (glCanvas != null) {
            glCanvas.repaint();
        }
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
            sharedContextData.ref(getGlCanvas().getContext());
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
                fragShader = ShaderManager.get("sliceviewer");
                fragShader.addProgramUniform("tex");
                fragShader.addProgramUniform("scale");
                fragShader.addProgramUniform("offset");
            } catch (Exception e) {
                throw new RuntimeException("couldn't initialize GL shader: " + e.getLocalizedMessage(), e);
            }
        }

        @Override
        public void display(GLAutoDrawable glAutoDrawable) {
            GL2 gl = glAutoDrawable.getGL().getGL2();
            gl.glClear(gl.GL_COLOR_BUFFER_BIT);
            gl.glMatrixMode(gl.GL_MODELVIEW);
            gl.glLoadIdentity();

            gl.glPushAttrib(GL2.GL_CURRENT_BIT|GL2.GL_ENABLE_BIT);
            try {
                try {
                    VolumeDataSet.TextureRef texRef = volumeDataSet.bindTexture(GL2.GL_TEXTURE0, gl, sharedContextData);
                    fragShader.bind();
                    fragShader.bindUniform("tex", 0);
                    fragShader.bindUniform("scale", texRef.getPreScale());
                    fragShader.bindUniform("offset", texRef.getPreOffset());
                    gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, gl.GL_REPLACE);
                    gl.glBegin(GL2.GL_QUADS);
                    texturedSlicePoint(gl,-navigationCubeLength/2, -navigationCubeLength/2, navigationZ);
                    texturedSlicePoint(gl, navigationCubeLength/2, -navigationCubeLength/2, navigationZ);
                    texturedSlicePoint(gl, navigationCubeLength/2,  navigationCubeLength/2, navigationZ);
                    texturedSlicePoint(gl,-navigationCubeLength/2,  navigationCubeLength/2, navigationZ);
                    outputSlicePoint("bottom-left: ", -navigationCubeLength/2, -navigationCubeLength/2, navigationZ);
                    outputSlicePoint("top-right:   ", navigationCubeLength/2,  navigationCubeLength/2, navigationZ);
                    gl.glEnd();
                    fragShader.unbind();
                    volumeDataSet.unbindCurrentTexture(gl);
                } finally {
                }
            } finally {
                gl.glPopAttrib();
            }
        }
        
        private void texturedSlicePoint(GL2 gl, float x, float y, float z) {
            // TODO: use the texture matrix rather than calculating the tex coordinates in here
            float[] pt = new float[]{x,y,z};
            float[] ptInVolume = LinAlg.mtimesv(baseSliceToVolumeTransform, pt, null);
            float[] vol2tex = new float[16];
            LinAlg.fillIdentity(vol2tex);
            LinAlg.fillTranslation(vol2tex, 0.5f, 0.5f, 0.5f, vol2tex);
            LinAlg.fillScale(vol2tex,
                             1.0f/volumeDataSet.getWidthInMm(),
                             1.0f/volumeDataSet.getHeightInMm(),
                             1.0f/volumeDataSet.getDepthInMm(),
                             vol2tex);
            float[] ptInTex = LinAlg.mtimesv(vol2tex, ptInVolume, null);
            gl.glTexCoord3fv(ptInTex, 0);
            //System.out.println("texCoord.Z="+ptInTex[2]);
            gl.glVertex2f(x, y);
        }
        
        private void outputSlicePoint(String caption, float x, float y, float z) {
            float[] pt = new float[]{x,y,z};
            float[] ptInVolume = LinAlg.mtimesv(baseSliceToVolumeTransform, pt, null);
            System.out.println(caption + ": x=" + ptInVolume[0] + ", y=" + ptInVolume[1] + ", z=" + ptInVolume[2]);
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


    /**
     * need our own valueIsAdjusting for navZslider instead of using
     * navZslider.getModel().getValueIsAdjusting() because we want to be able to
     * tell the difference between the user dragging the thumb (we want to
     * update the display during that) and our own temporarily invalid
     * model value settings in updateNavZslider() (we do NOT want to update
     * the display during that)
     */
    private boolean internalNavZsliderValueIsAdjusting = false;
    
    private void updateNavZslider() {
        if (null == navZslider) {
            return;
        }
        if (! navZslider.isEnabled()) {
            navZslider.setEnabled(true);
        }
        BoundedRangeModel sliderModel = navZslider.getModel();
        internalNavZsliderValueIsAdjusting = true;
        int min = sliderModel.getMinimum();
        int max = sliderModel.getMaximum();
        sliderModel.setValue((int)((max-min) * (navigationZ + navigationCubeLength/2)/navigationCubeLength));
        sliderModel.setExtent(1);
        internalNavZsliderValueIsAdjusting = false;
    }

    private ChangeListener navZsliderChangeListener = new ChangeListener() {
        private boolean inCall = false;
        @Override
        public void stateChanged(ChangeEvent e) {
            if (inCall) { return; }
            inCall = true;
            try {
                if (internalNavZsliderValueIsAdjusting) { return; }
                BoundedRangeModel sliderModel = navZslider.getModel();
                int min = sliderModel.getMinimum();
                int max = sliderModel.getMaximum();
                setNavigationZ(navigationCubeLength * ((float)sliderModel.getValue() - min) / (max-min) - navigationCubeLength/2);
            } finally {
                inCall = false;
            }
        }
    };

    @Override
    public void setBackground(java.awt.Color bg) {
        super.setBackground(bg);
        if (navZslider != null) {
            navZslider.setBackground(bg);
        }
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
