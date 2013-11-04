package de.olafklischat.volkit.view;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EventListener;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.awt.GLCanvas;
import javax.swing.AbstractAction;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import de.matthiasmann.twl.GUI;
import de.matthiasmann.twl.ValueAdjusterInt;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.model.SimpleIntegerModel;
import de.olafklischat.volkit.model.VolumeDataSet;
import de.olafklischat.lang.Runnable1;
import de.sofd.util.IdentityHashSet;
import de.sofd.util.Misc;
import de.sofd.viskit.image3D.jogl.util.GLShader;
import de.sofd.viskit.image3D.jogl.util.LinAlg;
import de.sofd.viskit.image3D.jogl.util.ShaderManager;


public class SliceViewer extends Widget {

    private static final long serialVersionUID = 3961980093372280907L;

    static final Logger logger = Logger.getLogger(SliceViewer.class);

    static {
        System.setProperty("sun.awt.noerasebackground", "true");
        ShaderManager.init("shader");
    }
    
    private VolumeDataSet volumeDataSet;
    private VolumeDataSet previousvolumeDataSet;
    
    /**
     * transformation from volume system to world system.
     * volume system = system whose origin is in the middle of the volume, with x-, y- and z-axes parallel
     *                 to the volume's
     *     
     * 1 unit length = 1 mm in all systems
     */
    private float[] volumeToWorldTransform = new float[16];

    /**
     * transformation from world to "base slice" system, i.e. the system whose
     * z=0 plane defines "base" slice plane (the actual slice plane is parallel
     * to the base slice plane, navigationZ unit lenghts in z axis direction
     * (slice system) away).
     * <p>
     * Normally one of three values corresponding to the three main slice
     * orientations (BASE_SLICE_xx constants).
     */
    private float[] worldToBaseSliceTransform = new float[16];

    /**
     * Transformation that transforms from the slice system to the
     * "canvas system", i.e. the actually visible canvas surface, and thus
     * captures the zoom/pan state. Defaults to the identity. The drawn area is
     * the square with side length navigationCubeLength (see below) in the z=0
     * plane of the canvas system.
     * <p>
     * Only transforms the x and y coordinates, leaves z alone.
     * <p>
     * TODO: Normalizing slice coordinates to [-1,1] coordinate ranges rather
     * than [-navigationCubeLength/2,navigationCubeLength/2] might be more
     * logical, but probably harder to deal with when another volume is loaded
     * and thus navigationCubeLength changes.
     */
    private float[] sliceToCanvasTransform = new float[16];
    
    public static final float[] BASE_SLICE_XY = new float[16];
    public static final float[] BASE_SLICE_XZ = new float[16];
    public static final float[] BASE_SLICE_YZ = new float[16];

    /**
     * z location of the slice, i.e. distance of the slice plane
     * from the z=0 plane of the base slice system.
     */
    private float navigationZ;

    /**
     * Side length of the minimum cube around the origin in the world system
     * that the volume will always stay inside of, no matter how it is rotated
     * (as long as it isn't translated, i.e. as long as the volume system's
     * origin is identical to the world system's origin).
     * <p>
     * Set equal to the diagonal length through the volume.
     * <p>
     * This is used as the default bounds of the drawn slice area; see
     * sliceToCanvasTransform above.
     */
    private float navigationCubeLength;

    private boolean needViewportReset = false;
    
    static {
        // TODO: verify
        LinAlg.fillIdentity(BASE_SLICE_XY);
        LinAlg.fillRotation(BASE_SLICE_XY, -90, 1, 0, 0, BASE_SLICE_XZ);
        LinAlg.fillRotation(BASE_SLICE_XZ, 90, 0, 0, 1, BASE_SLICE_YZ);
    }
    
    //matrices that depend on the above matrices and navigationZ.
    //recomputed by recomputeMatrices()
    private float[] volumeToBaseSliceTransform = new float[16];
    private float[] baseSliceToVolumeTransform = new float[16];
    private float[] baseSliceToWorldTransform = new float[16];
    private float[] volumeToSliceTransform = new float[16];
    private float[] sliceToVolumeTransform = new float[16];
    private float[] canvasToSliceTransform = new float[16];

    public static final String PROP_NAVIGATION_Z = "navigationZ";

    private GLCanvas glCanvas = null;
    private GLShader fragShader;

    private Widget canvas;
    private Widget navZslider;
    
    protected List<SliceViewer> trackedViewers = new ArrayList<SliceViewer>();
    
    protected static final Set<SliceViewer> instances = new IdentityHashSet<SliceViewer>();
    private static final SharedContextData sharedContextData = new SharedContextData();
    
    private final Collection<SlicePaintListener> uninitializedSlicePaintListeners = new IdentityHashSet<SlicePaintListener>();

    public static final int PAINT_ZORDER_DEFAULT = 100;

    public SliceViewer() {
        setTheme("");
        canvas = new Canvas();
        canvas.setTheme("");
        this.add(canvas);

        /*
        ////TODO: reintegrate
        setupInternalUiInteractions();
        //canvas.addKeyListener(internalMouseEventHandler);
        canvas.addMouseListener(canvasMouseEventDispatcher);
        canvas.addMouseMotionListener(canvasMouseEventDispatcher);
        canvas.addMouseWheelListener(canvasMouseEventDispatcher);
        //canvas.addKeyListener(canvasMouseAndKeyHandler);
         */

        navZslider = new ValueAdjusterInt(new SimpleIntegerModel(0, 10000, 5000));
        //navZslider = new Button("navZslider");
        this.add(navZslider);
        //navZslider.getModel().addChangeListener(navZsliderChangeListener);
        LinAlg.fillIdentity(volumeToWorldTransform);
        LinAlg.fillIdentity(worldToBaseSliceTransform);
        LinAlg.fillIdentity(sliceToCanvasTransform);
        navigationZ = 0;
        recomputeMatrices();
        updateNavZslider();
    }
    
    public SliceViewer(VolumeDataSet volumeDataSet) {
        this();
        setVolumeDataSet(volumeDataSet);
    }
    
    @Override
    protected void layout() {
        int h = navZslider.getPreferredHeight();
        canvas.setPosition(getInnerX(), getInnerY());
        canvas.setSize(getInnerWidth(), getInnerHeight() - h);
        navZslider.setPosition(getInnerX(), getInnerY() + getInnerHeight() - h);
        navZslider.setSize(getInnerWidth(), h);
    }

    public VolumeDataSet getVolumeDataSet() {
        return volumeDataSet;
    }
    
    public void setVolumeDataSet(VolumeDataSet volumeDataSet) {
        if (previousvolumeDataSet != null) {
            System.err.println("WARNING: previousvolumeDataSet wasn't disposed. Unused texture memory may not have been freed.");
        }
        previousvolumeDataSet = this.volumeDataSet;
        this.volumeDataSet = volumeDataSet;
        navigationCubeLength = (float) Math.sqrt(volumeDataSet.getWidthInMm() * volumeDataSet.getWidthInMm() +
                volumeDataSet.getHeightInMm() * volumeDataSet.getHeightInMm() +
                volumeDataSet.getDepthInMm() * volumeDataSet.getDepthInMm());

        LinAlg.fillIdentity(volumeToWorldTransform);
        LinAlg.fillIdentity(sliceToCanvasTransform);
        navigationZ = 0;
        recomputeMatrices();
        updateNavZslider();
        needViewportReset = true;
        refresh();
    }

    public GLAutoDrawable getGlCanvas() {
        return glCanvas;
    }
    
    public float getNavigationZ() {
        return navigationZ;
    }
    
    public void setNavigationZ(float navigationZ) {
        float oldValue = this.navigationZ;
        this.navigationZ = navigationZ;
        recomputeMatrices();
        updateNavZslider();
        firePropertyChange(PROP_NAVIGATION_Z, oldValue, navigationZ);
        refresh();
    }
    
    public float[] getVolumeToWorldTransform() {
        return LinAlg.copyArr(volumeToWorldTransform, null);
    }

    public float[] getVolumeToBaseSliceTransform() {
        return LinAlg.copyArr(volumeToBaseSliceTransform, null);
    }

    public float[] getBaseSliceToVolumeTransform() {
        return LinAlg.copyArr(baseSliceToVolumeTransform, null);
    }
    
    public float[] getBaseSliceToWorldTransform() {
        return LinAlg.copyArr(baseSliceToWorldTransform, null);
    }
    
    public float[] getVolumeToSliceTransform() {
        return LinAlg.copyArr(volumeToSliceTransform, null);
    }
    
    public float[] getSliceToVolumeTransform() {
        return LinAlg.copyArr(sliceToVolumeTransform, null);
    }
    
    public void setVolumeToWorldTransform(float[] volumeToWorldTransform) {
        LinAlg.copyArr(volumeToWorldTransform, this.volumeToWorldTransform);
        recomputeMatrices();
        needViewportReset = true;
        refresh();
    }
    
    public float[] getWorldToBaseSliceTransform() {
        return LinAlg.copyArr(worldToBaseSliceTransform, null);
    }
    
    public void setWorldToBaseSliceTransform(float[] worldToBaseSliceTransform) {
        LinAlg.copyArr(worldToBaseSliceTransform, this.worldToBaseSliceTransform);
        recomputeMatrices();
        refresh();
    }
    
    public float[] getSliceToCanvasTransform() {
        return LinAlg.copyArr(sliceToCanvasTransform, null);
    }
    
    public float[] getCanvasToSliceTransform() {
        return LinAlg.copyArr(canvasToSliceTransform, null);
    }

    public void setSliceToCanvasTransform(float[] sliceToCanvasTransform) {
        this.sliceToCanvasTransform = sliceToCanvasTransform;
        recomputeMatrices();
        refresh();
    }
    
    public void addTrackedViewer(SliceViewer sv) {
        trackedViewers.add(sv);
        sv.addPropertyChangeListener(trackedViewersPropChangeHandler);
        refresh();
    }
    
    public void removeTrackedViewer(SliceViewer sv) {
        sv.removePropertyChangeListener(trackedViewersPropChangeHandler);
        trackedViewers.remove(sv);
        refresh();
    }
    
    private PropertyChangeListener trackedViewersPropChangeHandler = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            refresh();
        }
    };
    
    protected void recomputeMatrices() {
        LinAlg.inverse(worldToBaseSliceTransform, baseSliceToWorldTransform);
        LinAlg.fillMultiplication(worldToBaseSliceTransform, volumeToWorldTransform, volumeToBaseSliceTransform);
        LinAlg.inverse(volumeToBaseSliceTransform, baseSliceToVolumeTransform);
        LinAlg.fillTranslationL(volumeToBaseSliceTransform, 0, 0, -getNavigationZ(), volumeToSliceTransform);
        LinAlg.fillTranslation(baseSliceToVolumeTransform, 0, 0, getNavigationZ(), sliceToVolumeTransform);
        LinAlg.inverse(sliceToCanvasTransform, canvasToSliceTransform);
    }
    
    public void refresh() {
        if (glCanvas != null) {
            glCanvas.repaint();
        }
    }

    protected void initializeUninitializedSlicePaintListeners(final GL gl, final GLAutoDrawable glAutoDrawable) {
        forEachPaintListenerInZOrder(new Runnable1<SlicePaintListener>() {
            @Override
            public void run(SlicePaintListener l) {
                if (uninitializedSlicePaintListeners.contains(l)) {
                    l.glSharedContextDataInitialization(SliceViewer.this, gl, sharedContextData.getAttributes());
                    l.glDrawableInitialized(SliceViewer.this, glAutoDrawable, sharedContextData.getAttributes());
                }
            }
        });
        uninitializedSlicePaintListeners.clear();
    }
    
    static int stmp = 0;
    
    protected class Canvas extends Widget {

        protected boolean isInitialized = false;
        
        private int oid = stmp++;

        /**
         * dimensions of viewport in canvas coordinate system
         */
        float viewWidth, viewHeight;

        protected void ensureInitialized() {
            if (isInitialized) {
                return;
            }
            try {
                //TODO: reintegrate
                //ShaderManager.read(gl, "sliceviewer");
                //fragShader = ShaderManager.get("sliceviewer");
                //fragShader.addProgramUniform("tex");
                //fragShader.addProgramUniform("scale");
                //fragShader.addProgramUniform("offset");
            } catch (Exception e) {
                throw new RuntimeException("couldn't initialize GL shader: " + e.getLocalizedMessage(), e);
            }
            //TODO: reintegrate
            //initializeUninitializedSlicePaintListeners(gl, glAutoDrawable);
            isInitialized = true;
        }

        @Override
        protected void paintWidget(GUI gui) {
            GL11.glPushAttrib(GL11.GL_CURRENT_BIT|GL11.GL_LIGHTING_BIT|GL11.GL_ENABLE_BIT|GL11.GL_VIEWPORT_BIT|GL11.GL_TRANSFORM_BIT);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            setupEye2ViewportTransformation(gui);
            try {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glShadeModel(GL11.GL_FLAT);

                if (null != previousvolumeDataSet) {
                    //TODO: reintegrate
                    //previousvolumeDataSet.dispose(sharedContextData);  // TODO: reference count on the VolumeDataSet
                    previousvolumeDataSet = null;
                }
                if (null == volumeDataSet) {
                    return;
                }
                //TODO: reintegrate
                //initializeUninitializedSlicePaintListeners(gl, glAutoDrawable);
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPushMatrix();
                try {
                    GL11.glLoadIdentity();
                    GL11.glColor3f(0, 1, 0);
                    //GL11.glBegin(GL11.GL_LINES);
                    //GL11.glVertex2f(10 + 10*oid, 10);
                    //GL11.glVertex2f(100 + 10*oid, 50);
                    //GL11.glEnd();
                    
                    //TODO: reintegrate
                    //VolumeDataSet.TextureRef texRef = volumeDataSet.bindTexture(GL2.GL_TEXTURE0, gl, sharedContextData);
                    //fragShader.bind();
                    //fragShader.bindUniform("tex", 0);
                    //fragShader.bindUniform("scale", texRef.getPreScale());
                    //fragShader.bindUniform("offset", texRef.getPreOffset());
                    //gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, gl.GL_REPLACE);
                    GL11.glBegin(GL11.GL_QUADS);
                    texturedCanvasPoint(-viewWidth/2, -viewHeight/2);
                    texturedCanvasPoint( viewWidth/2, -viewHeight/2);
                    texturedCanvasPoint( viewWidth/2,  viewHeight/2);
                    texturedCanvasPoint(-viewWidth/2,  viewHeight/2);
                    outputSlicePoint("bottom-left: ", -navigationCubeLength/2, -navigationCubeLength/2);
                    outputSlicePoint("top-right:   ", navigationCubeLength/2,  navigationCubeLength/2);
                    GL11.glEnd();
                    //TODO: reintegrate
                    //fragShader.unbind();
                    //volumeDataSet.unbindCurrentTexture(gl);
                    GL11.glShadeModel(GL11.GL_FLAT);
                    for (SliceViewer trackedViewer : trackedViewers) {
                        GL11.glColor3f(1f, 0f, 0f);
                        float[] trackedViewerSliceToOurCanvas = LinAlg.fillIdentity(null);
                        LinAlg.fillMultiplication(trackedViewer.getBaseSliceToVolumeTransform(), trackedViewerSliceToOurCanvas, trackedViewerSliceToOurCanvas);
                        LinAlg.fillMultiplication(getVolumeToBaseSliceTransform(), trackedViewerSliceToOurCanvas, trackedViewerSliceToOurCanvas);
                        LinAlg.fillMultiplication(getSliceToCanvasTransform(), trackedViewerSliceToOurCanvas, trackedViewerSliceToOurCanvas);
                        float[] pt1 = new float[]{-trackedViewer.navigationCubeLength/2, -trackedViewer.navigationCubeLength/2, trackedViewer.getNavigationZ()};
                        float[] pt2 = new float[]{ trackedViewer.navigationCubeLength/2,  trackedViewer.navigationCubeLength/2, trackedViewer.getNavigationZ()};
                        GL11.glBegin(GL11.GL_LINES);
                        glVertex3fv(LinAlg.mtimesv(trackedViewerSliceToOurCanvas, pt1, null));
                        glVertex3fv(LinAlg.mtimesv(trackedViewerSliceToOurCanvas, pt2, null));
                        GL11.glEnd();
                        // TODO: the lines don't look right (too thick and too dark). Must be some unwanted state from TWL.
                    }

                    //TODO: reintegrate
                    //firePaintEvent(new SlicePaintEvent(SliceViewer.this, gl, sharedContextData.getAttributes()));
                } finally {
                    GL11.glPopMatrix();
                }
            } finally {
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
                GL11.glPopAttrib();
            }
        }

        /*
        protected void paintWidget_(GUI gui) {
            ensureInitialized();
            
            GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_CURRENT_BIT|GL11.GL_ENABLE_BIT);
            try {
                GL11.glClearColor(0,0,0,0);
                GL11.glShadeModel(GL11.GL_FLAT);
                
                if (null != previousvolumeDataSet) {
                    previousvolumeDataSet.dispose(sharedContextData);  // TODO: reference count on the VolumeDataSet
                    previousvolumeDataSet = null;
                }
                if (null == volumeDataSet) {
                    return;
                }
                if (needViewportReset) {
                    setupEye2ViewportTransformation();
                }
                //TODO: reintegrate
                //initializeUninitializedSlicePaintListeners(gl, glAutoDrawable);
                gl.glClear(gl.GL_COLOR_BUFFER_BIT);
                gl.glMatrixMode(gl.GL_MODELVIEW);
                gl.glLoadIdentity();

                try {
                    VolumeDataSet.TextureRef texRef = volumeDataSet.bindTexture(GL2.GL_TEXTURE0, gl, sharedContextData);
                    fragShader.bind();
                    fragShader.bindUniform("tex", 0);
                    fragShader.bindUniform("scale", texRef.getPreScale());
                    fragShader.bindUniform("offset", texRef.getPreOffset());
                    gl.glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, gl.GL_REPLACE);
                    gl.glBegin(GL2.GL_QUADS);
                    texturedCanvasPoint(gl,-viewWidth/2, -viewHeight/2);
                    texturedCanvasPoint(gl, viewWidth/2, -viewHeight/2);
                    texturedCanvasPoint(gl, viewWidth/2,  viewHeight/2);
                    texturedCanvasPoint(gl,-viewWidth/2,  viewHeight/2);
                    outputSlicePoint("bottom-left: ", -navigationCubeLength/2, -navigationCubeLength/2);
                    outputSlicePoint("top-right:   ", navigationCubeLength/2,  navigationCubeLength/2);
                    gl.glEnd();
                    fragShader.unbind();
                    volumeDataSet.unbindCurrentTexture(gl);
                    gl.glShadeModel(gl.GL_FLAT);
                    for (SliceViewer trackedViewer : trackedViewers) {
                        gl.glColor3f(1f, 0f, 0f);
                        float[] trackedViewerSliceToOurCanvas = LinAlg.fillIdentity(null);
                        LinAlg.fillMultiplication(trackedViewer.getBaseSliceToVolumeTransform(), trackedViewerSliceToOurCanvas, trackedViewerSliceToOurCanvas);
                        LinAlg.fillMultiplication(getVolumeToBaseSliceTransform(), trackedViewerSliceToOurCanvas, trackedViewerSliceToOurCanvas);
                        LinAlg.fillMultiplication(getSliceToCanvasTransform(), trackedViewerSliceToOurCanvas, trackedViewerSliceToOurCanvas);
                        float[] pt1 = new float[]{-trackedViewer.navigationCubeLength/2, -trackedViewer.navigationCubeLength/2, trackedViewer.getNavigationZ()};
                        float[] pt2 = new float[]{ trackedViewer.navigationCubeLength/2,  trackedViewer.navigationCubeLength/2, trackedViewer.getNavigationZ()};
                        gl.glBegin(gl.GL_LINES);
                        gl.glVertex3fv(LinAlg.mtimesv(trackedViewerSliceToOurCanvas, pt1, null), 0);
                        gl.glVertex3fv(LinAlg.mtimesv(trackedViewerSliceToOurCanvas, pt2, null), 0);
                        gl.glEnd();
                    }

                    firePaintEvent(new SlicePaintEvent(SliceViewer.this, gl, sharedContextData.getAttributes()));
                } finally {
                }
            } finally {
                gl.glPopAttrib();
            }
        }
        */
        
        private void texturedCanvasPoint(float x, float y) {
            // TODO: use the texture matrix rather than calculating the tex coordinates in here
            float[] ptInCanvas = new float[]{x,y,0};
            float[] ptInSlice = LinAlg.mtimesv(canvasToSliceTransform, ptInCanvas, null);
            float[] ptInVolume = LinAlg.mtimesv(sliceToVolumeTransform, ptInSlice, null);
            float[] vol2tex = new float[16];
            LinAlg.fillIdentity(vol2tex);
            LinAlg.fillTranslation(vol2tex, 0.5f, 0.5f, 0.5f, vol2tex);
            LinAlg.fillScale(vol2tex,
                             1.0f/volumeDataSet.getWidthInMm(),
                             1.0f/volumeDataSet.getHeightInMm(),
                             1.0f/volumeDataSet.getDepthInMm(),
                             vol2tex);
            float[] ptInTex = LinAlg.mtimesv(vol2tex, ptInVolume, null);
            glTexCoord3fv(ptInTex);
            glVertex3fv(new float[]{x, y, 0});
        }
        
        private void outputSlicePoint(String caption, float x, float y) {
            float[] ptInSlice = new float[]{x,y,0};
            float[] ptInVolume = LinAlg.mtimesv(sliceToVolumeTransform, ptInSlice, null);
            //float[] ptInWorld = LinAlg.mtimesv(baseSliceToWorldTransform, ptInBase, null);
            System.out.println(caption + ": x=" + ptInVolume[0] + ", y=" + ptInVolume[1] + ", z=" + ptInVolume[2]);
        }

        private final FloatBuffer fb3 = BufferUtils.createFloatBuffer(3);
        
        private FloatBuffer toFB3(float[] arr) {
            fb3.clear();
            fb3.put(arr, 0, 3);
            fb3.rewind();
            return fb3;
        }
        
        //03:38 < multi_io> how would you port gl*3fv(arr) calls to lwjgl?
        //03:38 < multi_io> gl*3f(arr[0],arr[1],arr[2]) ?
        //03:38 < multi_io> or is there a variant that takes a FloatBuffer?

        private void glVertex2fv(float[] arr) {
            GL11.glVertex2f(arr[0], arr[1]);
        }

        private void glVertex3fv(float[] arr) {
            GL11.glVertex3f(arr[0], arr[1], arr[2]);
        }
        
        private void glTexCoord3fv(float[] arr) {
            GL11.glTexCoord3f(arr[0], arr[1], arr[2]);
        }

        /*
        //@Override
        public void reshape(GLAutoDrawable glAutoDrawable, int x, int y, int width, int height) {
            GL2 gl = (GL2) glAutoDrawable.getGL();
            if (null != previousvolumeDataSet) {
                previousvolumeDataSet.dispose(gl, sharedContextData);
                previousvolumeDataSet = null;
            }
            if (null == volumeDataSet) {
                return;
            }
            setupEye2ViewportTransformation();
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
        */

        private void setupEye2ViewportTransformation(GUI gui) {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            if (getInnerWidth() > getInnerHeight()) {
                viewHeight = navigationCubeLength;
                viewWidth = viewHeight * getInnerWidth() / getInnerHeight();
            } else {
                viewWidth = navigationCubeLength;
                viewHeight = viewWidth * getInnerHeight() / getInnerWidth();
            }
            GL11.glOrtho(-viewWidth / 2,   //  GLdouble    left,
                          viewWidth / 2,   //    GLdouble      right,
                         -viewHeight / 2,  //    GLdouble      bottom,
                          viewHeight / 2,  //    GLdouble      top,
                         -navigationCubeLength, //  GLdouble      nearVal,
                          navigationCubeLength   //  GLdouble     farVal  // depth 2 times the navigationCubeLength to support navZ in [-ncl,ncl]
                         );
            //TODO: it's probably not a good idea to (temporarily) change the viewport dimensions
            // when drawing in a TWL widget (as opposed to an AWTGLCanvas that hosts a single SliceViewer only)
            GL11.glViewport(getInnerX(), gui.getRenderer().getHeight() - getInnerY() - getInnerHeight(), getInnerWidth(), getInnerHeight());
            GL11.glDepthRange(0,1);
            needViewportReset = false;
        }

        //@Override
        public void dispose(final GLAutoDrawable glAutoDrawable) {
            logger.debug("disposing GLCanvas...");
            forEachPaintListenerInZOrder(new Runnable1<SlicePaintListener>() {
                @Override
                public void run(SlicePaintListener l) {
                    if (uninitializedSlicePaintListeners.contains(l)) {
                        l.glDrawableDisposing(SliceViewer.this, glAutoDrawable, sharedContextData.getAttributes());
                    }
                }
            });
            sharedContextData.unref();
            instances.remove(SliceViewer.this);
            logger.debug("GLCanvas disposed, refcount=" + sharedContextData.getRefCount() + ", GLCanvas inst. count = " + instances.size());
        }

    };

    private MouseAdapter canvasMouseEventDispatcher = new MouseAdapter() {

        @Override
        public void mouseClicked(MouseEvent evt) {
            dispatchEventToCanvas(evt);
        }

        @Override
        public void mousePressed(MouseEvent evt) {
            dispatchEventToCanvas(evt);
        }

        @Override
        public void mouseReleased(final MouseEvent evt) {
             dispatchEventToCanvas(evt);
        }

        @Override
        public void mouseEntered(MouseEvent evt) {
             //dispatchEventToCanvas(evt);
        }

        @Override
        public void mouseExited(MouseEvent evt) {
            //dispatchEventToCanvas(evt);
        }

        @Override
        public void mouseMoved(MouseEvent evt) {
             dispatchEventToCanvas(evt);
        }

        @Override
        public void mouseDragged(MouseEvent evt) {
             dispatchEventToCanvas(evt);
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent evt) {
             dispatchEventToCanvas(evt);
        }

    };

    protected void dispatchEventToCanvas(MouseEvent evt) {
        MouseEvent ce = Misc.deepCopy(evt);
        ce.setSource(this);
        if (ce instanceof MouseWheelEvent) {
            fireCanvasMouseWheelEvent((MouseWheelEvent) ce);
        } else {
            fireCanvasMouseEvent(ce);
        }
    }

    public float[] convertAwtToCanvas(Point awtPtOnGlCanvas) {
        // TODO: have a tx matrix for this as well
        // TODO: unify with code in reshape() / setupEye2ViewportTransformation()
        Dimension sz = glCanvas.getSize();
        float mmPerPixel;
        if (sz.width > sz.height) {
            mmPerPixel = navigationCubeLength / sz.height;
        } else {
            mmPerPixel = navigationCubeLength / sz.width;
        }
        float x = awtPtOnGlCanvas.x - sz.width / 2;
        float y = - (awtPtOnGlCanvas.y - sz.height / 2);
        return new float[]{x*mmPerPixel, y*mmPerPixel, 0};
    }

    public void addCanvasMouseListener(MouseListener listener) {
        addAnyCanvasMouseListener(listener);
    }
    
    public void removeCanvasMouseListener(MouseListener listener) {
        removeAnyCanvasMouseListener(listener);
    }
    
    protected void fireCanvasMouseEvent(MouseEvent e) {
        fireAnyCanvasMouseEvent(e);
    }

    public void addCanvasMouseMotionListener(MouseMotionListener listener) {
        addAnyCanvasMouseListener(listener);
    }

    public void removeCanvasMouseMotionListener(MouseMotionListener listener) {
        removeAnyCanvasMouseListener(listener);
    }

    protected void fireCanvasMouseMotionEvent(MouseEvent e) {
        fireAnyCanvasMouseEvent(e);
    }

    public void addCanvasMouseWheelListener(MouseWheelListener listener) {
        addAnyCanvasMouseListener(listener);
    }
    
    public void removeCanvasMouseWheelListener(MouseWheelListener listener) {
        removeAnyCanvasMouseListener(listener);
    }

    protected void fireCanvasMouseWheelEvent(MouseWheelEvent e) {
        fireAnyCanvasMouseEvent(e);
    }


    
    private List<EventListener> canvasMouseListeners = new ArrayList<EventListener>();
    
    protected void addAnyCanvasMouseListener(EventListener listener) {
        // check if it's been added before already. TODO: this is not really correct, get rid of it?
        //   (it was added for compatibility with clients that call all three add methods with just
        //   one listener instance (extending MouseHandler and thus implementing all Mouse*Listener interfaces),
        //   and expect the listener to be called only once per event.
        //   Check how standard Swing components handle this)
        for (EventListener l : canvasMouseListeners) {
            if (l == listener) {
                return;
            }
        }
        canvasMouseListeners.add(listener);
    }

    protected void removeAnyCanvasMouseListener(EventListener listener) {
        canvasMouseListeners.remove(listener);
    }

    protected void fireAnyCanvasMouseEvent(MouseEvent e) {
        for (EventListener listener : canvasMouseListeners) {
            boolean eventProcessed = false;
            if (listener instanceof MouseWheelListener && e instanceof MouseWheelEvent) {
                MouseWheelListener l = (MouseWheelListener) listener;
                l.mouseWheelMoved((MouseWheelEvent) e);
                eventProcessed = true;
            }
            if (!eventProcessed && listener instanceof MouseMotionListener) {
                MouseMotionListener l = (MouseMotionListener) listener;
                switch (e.getID()) {
                case MouseEvent.MOUSE_MOVED:
                    l.mouseMoved(e);
                    eventProcessed = true;
                    break;
                case MouseEvent.MOUSE_DRAGGED:
                    l.mouseDragged(e);
                    eventProcessed = true;
                    break;
                }
            }
            if (!eventProcessed) {
                MouseListener l = (MouseListener) listener;
                switch (e.getID()) {
                case MouseEvent.MOUSE_CLICKED:
                    l.mouseClicked(e);
                    break;
                case MouseEvent.MOUSE_PRESSED:
                    l.mousePressed(e);
                    break;
                case MouseEvent.MOUSE_RELEASED:
                    l.mouseReleased(e);
                    break;
                case MouseEvent.MOUSE_ENTERED:
                    l.mouseEntered(e);
                    break;
                case MouseEvent.MOUSE_EXITED:
                    l.mouseExited(e);
                    break;
                }
            }
            if (e.isConsumed()) {
                break;
            }
        }
    }

    
    public void addSlicePaintListener(SlicePaintListener listener) {
        addSlicePaintListener(PAINT_ZORDER_DEFAULT, listener);
    }

    public void addSlicePaintListener(int zOrder, SlicePaintListener listener) {
        slicePaintListeners.add(new ListenerRecord<SlicePaintListener>(listener, zOrder));
        uninitializedSlicePaintListeners.add(listener);
    }
    
    public void removeSlicePaintListener(SlicePaintListener listener) {
        for (Iterator<ListenerRecord<SlicePaintListener>> it = slicePaintListeners.iterator(); it.hasNext();) {
            if (it.next().listener == listener) {
                it.remove();
                uninitializedSlicePaintListeners.remove(listener);
                return;
            }
        }
    }

    /**
     * NOT A PUBLIC API! DON'T CALL.
     * 
     * (method is defined public so internal classes in subpackages can call it)
     * @param e
     */
    public void firePaintEvent(SlicePaintEvent e) {
        for (ListenerRecord<SlicePaintListener> rec : slicePaintListeners) {
            rec.listener.onPaint(e);
            if (e.isConsumed()) {
                break;
            }
        }
    }

    protected void forEachPaintListenerInZOrder(Runnable1<SlicePaintListener> callback) {
        for (ListenerRecord<SlicePaintListener> rec : slicePaintListeners) {
            callback.run(rec.listener);
        }
    }
    
    protected void firePaintEvent(SlicePaintEvent e, int minZ, int maxZ) {
        SlicePaintListener dummy = new SlicePaintListener() {
            @Override
            public void glSharedContextDataInitialization(SliceViewer sv, GL gl, Map<String, Object> sharedData) {
            }
            @Override
            public void glDrawableInitialized(SliceViewer sv, GLAutoDrawable glAutoDrawable, Map<String, Object> sharedData) {
            }
            @Override
            public void onPaint(SlicePaintEvent e) {
            }
            @Override
            public void glDrawableDisposing(SliceViewer sv, GLAutoDrawable glAutoDrawable, Map<String, Object> sharedData) {
            }
        };
        ListenerRecord<SlicePaintListener> min = new ListenerRecord<SlicePaintListener>(dummy, minZ);
        ListenerRecord<SlicePaintListener> max = new ListenerRecord<SlicePaintListener>(dummy, maxZ);
        for (ListenerRecord<SlicePaintListener> rec : slicePaintListeners.subSet(min, max)) {
            rec.listener.onPaint(e);
            if (e.isConsumed()) {
                break;
            }
        }
    }

    private NavigableSet<ListenerRecord<SlicePaintListener>> slicePaintListeners = new TreeSet<ListenerRecord<SlicePaintListener>>();
    
    private static class ListenerRecord<ListenerType> implements Comparable<ListenerRecord<ListenerType>> {
        ListenerType listener;
        Integer zOrder;
        Integer instanceNumber;
        private static int lastInstanceNumber;
        public ListenerRecord(ListenerType listener, int zOrder) {
            this.listener = listener;
            this.zOrder = zOrder;
            this.instanceNumber = lastInstanceNumber++;
        }
        @Override
        public int compareTo(ListenerRecord<ListenerType> o) {
            int res = zOrder.compareTo(o.zOrder);
            if (res == 0) {
                return instanceNumber.compareTo(o.instanceNumber);
            } else {
                return res;
            }
        }
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
        //BoundedRangeModel sliderModel = navZslider.getModel();
        //internalNavZsliderValueIsAdjusting = true;
        //int min = sliderModel.getMinimum();
        //int max = sliderModel.getMaximum();
        //sliderModel.setValue((int)((max-min) * (navigationZ + navigationCubeLength/2)/navigationCubeLength));
        //sliderModel.setExtent(1);
        //internalNavZsliderValueIsAdjusting = false;
    }

    private ChangeListener navZsliderChangeListener = new ChangeListener() {
        private boolean inCall = false;
        @Override
        public void stateChanged(ChangeEvent e) {
            if (inCall) { return; }
            inCall = true;
            try {
                if (internalNavZsliderValueIsAdjusting) { return; }
                //BoundedRangeModel sliderModel = navZslider.getModel();
                //int min = sliderModel.getMinimum();
                //int max = sliderModel.getMaximum();
                //setNavigationZ(navigationCubeLength * ((float)sliderModel.getValue() - min) / (max-min) - navigationCubeLength/2);
            } finally {
                inCall = false;
            }
        }
    };

    /*
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
    */
    
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
