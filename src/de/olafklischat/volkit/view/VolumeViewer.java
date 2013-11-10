package de.olafklischat.volkit.view;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EventListener;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.lwjgl.opengl.GL11;

import de.matthiasmann.twl.Event;
import de.matthiasmann.twl.GUI;
import de.matthiasmann.twl.Scrollbar;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.Scrollbar.Orientation;
import de.olafklischat.volkit.model.VolumeDataSet;
import de.olafklischat.lang.Runnable1;
import de.olafklischat.lwjgl.GLShader;
import de.olafklischat.lwjgl.ShaderManager;
import de.olafklischat.twlawt.TwlToAwtMouseEventConverter;
import de.sofd.util.IdentityHashSet;
import de.sofd.util.Misc;
import de.sofd.viskit.image3D.jogl.util.LinAlg;


public class VolumeViewer extends Widget {

    private static final long serialVersionUID = 3961980093372280907L;

    static final Logger logger = Logger.getLogger(VolumeViewer.class);

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
     * transformation from world to eye (camera) system. As is common in OpenGL,
     * the eye system's x axis points to the right relative to the camera view,
     * the y axis points to the top, and the z axis points towards the viewer.
     */
    private float[] worldToEyeTransform = new float[16];

    /**
     * Angular width of viewport in radiants.
     * <p>
     * We use a perspective projection for transforming the scene seen by the
     * eye to the viewport, and this is the angular width of the viewport.
     * Changing it will produce a zoom in/out effect.
     * 
     * <pre>
     * 05:31 < multi_io> what angular resolution (angle per pixel) do you usually choose for perspective (glFrustum) projections?
     * 05:32 < multi_io> or do you choose a specific angular width and height of the viewport?
     * 05:41 < SolraBizna> the latter
     * 05:50 < multi_io> ok
     * 05:51 < multi_io> what would be a common value for that? Are there any conventions/standards?
     * 05:51 < SolraBizna> at least one OpenGL reference recommends calculating the actual angular width of the window from the perspective of the person using
     *                    the computer
     * 05:52 < SolraBizna> in practice, values between 30 and 60 work pretty well
     * </pre>
     * 
     * (0.9 rad = 51.... degrees)
     */
    float vpWidthInRadiants = 0.9F;
    
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

    //matrices that depend on the above matrices and navigationZ.
    //recomputed by recomputeMatrices()
    private float[] volumeToEyeTransform = new float[16];
    private float[] eyeToVolumeTransform = new float[16];
    private float[] eyeToWorldTransform = new float[16];

    private GLShader fragShader;

    private Widget canvas;
    private Widget toolPane;
    
    protected List<VolumeViewer> trackedViewers = new ArrayList<VolumeViewer>();
    
    protected static final Set<VolumeViewer> instances = new IdentityHashSet<VolumeViewer>();
    private static final SharedContextData sharedContextData = new SharedContextData();
    
    private final Collection<PaintListener<VolumeViewer>> uninitializedSlicePaintListeners = new IdentityHashSet<PaintListener<VolumeViewer>>();

    public static final int PAINT_ZORDER_DEFAULT = 100;

    public VolumeViewer() {
        setTheme("");
        canvas = new Canvas();
        canvas.setTheme("");
        this.add(canvas);

        toolPane = new Scrollbar(Orientation.HORIZONTAL);
        this.add(toolPane);
        toolPane.setTheme("hslider");
        LinAlg.fillIdentity(volumeToWorldTransform);
        LinAlg.fillIdentity(worldToEyeTransform);
        vpWidthInRadiants = 0.9F;
        recomputeMatrices();
    }
    
    public VolumeViewer(VolumeDataSet volumeDataSet) {
        this();
        setVolumeDataSet(volumeDataSet);
    }
    
    @Override
    protected void layout() {
        int h = toolPane.getPreferredHeight();
        canvas.setPosition(getInnerX(), getInnerY());
        canvas.setSize(getInnerWidth(), getInnerHeight() - h);
        toolPane.setPosition(getInnerX(), getInnerY() + getInnerHeight() - h);
        toolPane.setSize(getInnerWidth(), h);
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
        // TODO: reset eye position (worldToEyeTransform)
        vpWidthInRadiants = 0.9F;
        recomputeMatrices();
        refresh();
    }

    public float[] getVolumeToWorldTransform() {
        return LinAlg.copyArr(volumeToWorldTransform, null);
    }

    public float[] getVolumeToBaseSliceTransform() {
        return LinAlg.copyArr(volumeToEyeTransform, null);
    }

    public float[] getBaseSliceToVolumeTransform() {
        return LinAlg.copyArr(eyeToVolumeTransform, null);
    }
    
    public float[] getBaseSliceToWorldTransform() {
        return LinAlg.copyArr(eyeToWorldTransform, null);
    }
    
    public List<VolumeViewer> getTrackedViewers() {
        return trackedViewers;
    }
    
    public void setVolumeToWorldTransform(float[] volumeToWorldTransform) {
        LinAlg.copyArr(volumeToWorldTransform, this.volumeToWorldTransform);
        recomputeMatrices();
        refresh();
    }
    
    public float[] getWorldToEyeTransform() {
        return LinAlg.copyArr(worldToEyeTransform, null);
    }
    
    public void setWorldToEyeTransform(float[] worldToBaseSliceTransform) {
        LinAlg.copyArr(worldToBaseSliceTransform, this.worldToEyeTransform);
        recomputeMatrices();
        refresh();
    }

    public float getVpWidthInRadiants() {
        return vpWidthInRadiants;
    }
    
    public void setVpWidthInRadiants(float value) {
        this.vpWidthInRadiants = value;
        recomputeMatrices(); // not really necessary...
        refresh();
    }
    
    public void addTrackedViewer(VolumeViewer sv) {
        trackedViewers.add(sv);
        sv.addPropertyChangeListener(trackedViewersPropChangeHandler);
        refresh();
    }
    
    public void removeTrackedViewer(VolumeViewer sv) {
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
        LinAlg.inverse(worldToEyeTransform, eyeToWorldTransform);
        LinAlg.fillMultiplication(worldToEyeTransform, volumeToWorldTransform, volumeToEyeTransform);
        LinAlg.inverse(volumeToEyeTransform, eyeToVolumeTransform);
    }
    
    public void refresh() {
        //TODO
    }
    
    public float getNavigationCubeLength() {
        return navigationCubeLength;
    }

    protected void initializeUninitializedSlicePaintListeners() {
        forEachPaintListenerInZOrder(new Runnable1<PaintListener<VolumeViewer>>() {
            @Override
            public void run(PaintListener<VolumeViewer> l) {
                if (uninitializedSlicePaintListeners.contains(l)) {
                    l.glSharedContextDataInitialization(VolumeViewer.this, sharedContextData.getAttributes());
                    l.glDrawableInitialized(VolumeViewer.this, sharedContextData.getAttributes());
                }
            }
        });
        uninitializedSlicePaintListeners.clear();
    }
    
    protected class Canvas extends Widget {

        protected boolean isInitialized = false;
        
        /**
         * dimensions of viewport in canvas coordinate system
         */
        float viewWidth, viewHeight;

        protected void ensureInitialized() {
            if (isInitialized) {
                return;
            }
            try {
                ShaderManager.read("sliceviewer");
                fragShader = ShaderManager.get("sliceviewer");
                fragShader.addProgramUniform("tex");
                fragShader.addProgramUniform("scale");
                fragShader.addProgramUniform("offset");
            } catch (Exception e) {
                throw new RuntimeException("couldn't initialize GL shader: " + e.getLocalizedMessage(), e);
            }
            initializeUninitializedSlicePaintListeners();
            isInitialized = true;
        }

        @Override
        protected void paintWidget(GUI gui) {
            GL11.glPushAttrib(GL11.GL_CURRENT_BIT|GL11.GL_LIGHTING_BIT|GL11.GL_ENABLE_BIT|GL11.GL_VIEWPORT_BIT|GL11.GL_TRANSFORM_BIT);
            ensureInitialized();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            setupEye2ViewportTransformation(gui);
            try {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glShadeModel(GL11.GL_FLAT);

                if (null != previousvolumeDataSet) {
                    previousvolumeDataSet.dispose(sharedContextData);  // TODO: reference count on the VolumeDataSet
                    previousvolumeDataSet = null;
                }
                if (null == volumeDataSet) {
                    return;
                }
                initializeUninitializedSlicePaintListeners();
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPushMatrix();
                try {
                    /*
                    GL11.glLoadIdentity();
                    GL11.glColor3f(0, 1, 0);
                    
                    VolumeDataSet.TextureRef texRef = volumeDataSet.bindTexture(GL13.GL_TEXTURE0, sharedContextData);
                    fragShader.bind();
                    fragShader.bindUniform("tex", 0);
                    fragShader.bindUniform("scale", texRef.getPreScale());
                    fragShader.bindUniform("offset", texRef.getPreOffset());
                    GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_REPLACE);
                    GL11.glBegin(GL11.GL_QUADS);
                    texturedCanvasPoint(-viewWidth/2, -viewHeight/2);
                    texturedCanvasPoint( viewWidth/2, -viewHeight/2);
                    texturedCanvasPoint( viewWidth/2,  viewHeight/2);
                    texturedCanvasPoint(-viewWidth/2,  viewHeight/2);
                    outputSlicePoint("bottom-left: ", -navigationCubeLength/2, -navigationCubeLength/2);
                    outputSlicePoint("top-right:   ", navigationCubeLength/2,  navigationCubeLength/2);
                    GL11.glEnd();
                    fragShader.unbind();
                    volumeDataSet.unbindCurrentTexture();
                    GL11.glShadeModel(GL11.GL_FLAT);
                    for (VolumeViewer trackedViewer : trackedViewers) {
                        GL11.glColor3f(1f, 0f, 0f);
                        float[] trackedViewerSliceToOurCanvas = LinAlg.fillIdentity(null);
                        LinAlg.fillMultiplication(trackedViewer.getBaseSliceToVolumeTransform(), trackedViewerSliceToOurCanvas, trackedViewerSliceToOurCanvas);
                        LinAlg.fillMultiplication(getVolumeToBaseSliceTransform(), trackedViewerSliceToOurCanvas, trackedViewerSliceToOurCanvas);
                        LinAlg.fillMultiplication(getSliceToCanvasTransform(), trackedViewerSliceToOurCanvas, trackedViewerSliceToOurCanvas);
                        float[] pt1 = new float[]{-trackedViewer.navigationCubeLength/2, -trackedViewer.navigationCubeLength/2, trackedViewer.getNavigationZ()};
                        float[] pt2 = new float[]{ trackedViewer.navigationCubeLength/2,  trackedViewer.navigationCubeLength/2, trackedViewer.getNavigationZ()};
                        GL11.glBegin(GL11.GL_LINES);
                        LWJGLTools.glVertex3fv(LinAlg.mtimesv(trackedViewerSliceToOurCanvas, pt1, null));
                        LWJGLTools.glVertex3fv(LinAlg.mtimesv(trackedViewerSliceToOurCanvas, pt2, null));
                        GL11.glEnd();
                        // TODO: the lines don't look right (too thick and too dark). Must be some unwanted state from TWL.
                    }
                    */

                    firePaintEvent(new PaintEvent<VolumeViewer>(VolumeViewer.this, sharedContextData.getAttributes()));
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
            LWJGLTools.glTexCoord3fv(ptInTex);
            LWJGLTools.glVertex3fv(new float[]{x, y, 0});
        }
        
        private void outputSlicePoint(String caption, float x, float y) {
            float[] ptInSlice = new float[]{x,y,0};
            float[] ptInVolume = LinAlg.mtimesv(sliceToVolumeTransform, ptInSlice, null);
            //float[] ptInWorld = LinAlg.mtimesv(baseSliceToWorldTransform, ptInBase, null);
            //System.out.println(caption + ": x=" + ptInVolume[0] + ", y=" + ptInVolume[1] + ", z=" + ptInVolume[2]);
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
        }
        
        protected TwlToAwtMouseEventConverter mouseEvtConv = new TwlToAwtMouseEventConverter();

        @Override
        protected boolean handleEvent(Event evt) {
            MouseEvent awtMevt = mouseEvtConv.mouseEventTwlToAwt(evt, this);
            if (null != awtMevt) {
                dispatchEventToCanvas(awtMevt);
                return true; // consume all mouse event b/c otherwise TWL won't send some other events, apparently
                //return awtMevt.isConsumed() || evt.getType().equals(Event.Type.MOUSE_ENTERED); //always handle MOUSE_ENTERED b/c otherwise TWL assumes the widget doesn't handle any mouse events
            } else {
                return false;
            }
        }
        
    };
    
    //TODO: the following is copy&paste from SliceViewer. Introduce a common base class.

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
        // TODO: unify with code in setupEye2ViewportTransformation()
        float mmPerPixel;
        if (canvas.getWidth() > canvas.getHeight()) {
            mmPerPixel = navigationCubeLength / canvas.getHeight();
        } else {
            mmPerPixel = navigationCubeLength / canvas.getWidth();
        }
        float x = awtPtOnGlCanvas.x - canvas.getWidth() / 2;
        float y = - (awtPtOnGlCanvas.y - canvas.getHeight() / 2);
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

    
    public void addSlicePaintListener(PaintListener<VolumeViewer> listener) {
        addSlicePaintListener(PAINT_ZORDER_DEFAULT, listener);
    }

    public void addSlicePaintListener(int zOrder, PaintListener<VolumeViewer> listener) {
        slicePaintListeners.add(new ListenerRecord<PaintListener<VolumeViewer>>(listener, zOrder));
        uninitializedSlicePaintListeners.add(listener);
    }
    
    public void removeSlicePaintListener(PaintListener<VolumeViewer> listener) {
        for (Iterator<ListenerRecord<PaintListener<VolumeViewer>>> it = slicePaintListeners.iterator(); it.hasNext();) {
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
    public void firePaintEvent(PaintEvent<VolumeViewer> e) {
        for (ListenerRecord<PaintListener<VolumeViewer>> rec : slicePaintListeners) {
            rec.listener.onPaint(e);
            if (e.isConsumed()) {
                break;
            }
        }
    }

    protected void forEachPaintListenerInZOrder(Runnable1<PaintListener<VolumeViewer>> callback) {
        for (ListenerRecord<PaintListener<VolumeViewer>> rec : slicePaintListeners) {
            callback.run(rec.listener);
        }
    }
    
    protected void firePaintEvent(PaintEvent<VolumeViewer> e, int minZ, int maxZ) {
        PaintListener<VolumeViewer> dummy = new PaintListener<VolumeViewer>() {
            @Override
            public void glSharedContextDataInitialization(VolumeViewer sv, Map<String, Object> sharedData) {
            }
            @Override
            public void glDrawableInitialized(VolumeViewer sv, Map<String, Object> sharedData) {
            }
            @Override
            public void onPaint(PaintEvent<VolumeViewer> e) {
            }
            @Override
            public void glDrawableDisposing(VolumeViewer sv, Map<String, Object> sharedData) {
            }
        };
        ListenerRecord<PaintListener<VolumeViewer>> min = new ListenerRecord<PaintListener<VolumeViewer>>(dummy, minZ);
        ListenerRecord<PaintListener<VolumeViewer>> max = new ListenerRecord<PaintListener<VolumeViewer>>(dummy, maxZ);
        for (ListenerRecord<PaintListener<VolumeViewer>> rec : slicePaintListeners.subSet(min, max)) {
            rec.listener.onPaint(e);
            if (e.isConsumed()) {
                break;
            }
        }
    }

    private NavigableSet<ListenerRecord<PaintListener<VolumeViewer>>> slicePaintListeners = new TreeSet<ListenerRecord<PaintListener<VolumeViewer>>>();
    
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

}
