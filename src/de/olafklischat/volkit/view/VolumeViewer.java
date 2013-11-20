package de.olafklischat.volkit.view;

import java.awt.Color;
import java.awt.event.ActionEvent;
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
import java.util.TreeSet;

import javax.swing.AbstractAction;
import javax.swing.Action;

import org.apache.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import de.matthiasmann.twl.Alignment;
import de.matthiasmann.twl.BoxLayout;
import de.matthiasmann.twl.Button;
import de.matthiasmann.twl.Event;
import de.matthiasmann.twl.GUI;
import de.matthiasmann.twl.Label;
import de.matthiasmann.twl.Scrollbar;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.BoxLayout.Direction;
import de.olafklischat.volkit.model.VolumeDataSet;
import de.olafklischat.lang.Runnable1;
import de.olafklischat.lwjgl.GLShader;
import de.olafklischat.lwjgl.LWJGLTools;
import de.olafklischat.lwjgl.ShaderManager;
import de.olafklischat.math.LinAlg;
import de.olafklischat.twlawt.TwlToAwtMouseEventConverter;
import de.sofd.util.IdentityHashSet;
import de.sofd.util.Misc;


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
    float vpWidthInRadiants = DEFAULT_VP_WIDTH_IN_RADIANTS;
    
    public static final float DEFAULT_VP_WIDTH_IN_RADIANTS = 0.9F;

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
    
    private static final float eyeDistanceInNavCubeLenghts = 2.1f;

    private float shadingPostOffset = 0.0f;
    private float shadingPostScale = 1.0f;

    private float globalAlpha = 1.0f;
    
    private boolean removeSaturatedVoxels = false;
    
    private float sliceCountFactor = 1.0f;  //0.98f; //1.0f; //0.99f; //1.0f;

    private boolean isVolBoxVisible = false;

    /**
     * 6 matrices that transform slices centered on positions -1 to 1 on the z
     * axis to slices centered on vertices on any of the three main axes, in
     * positive or negative direction. Needed for being able to draw slices in
     * back-to-front order in any of the 6 possible main axis directions.
     * 
     * first index: 0: x, 1: y, 2: z
     * 
     * second index: 0: -1...1, 1: 1...-1 (back-to-front direction)
     */
    private final float[][][] sliceBackToFrontTransforms = new float[3][][];

    /**
     * Number of slices to use when slicing the volume, depending on direction
     * in the volume.
     * 
     * first index: 0: x, 1: y, 2: z
     */
    private final float[] sliceCountByDirIdx = new float[3];

    /**
     * Maximum number of slices to use. The volume will be sliced into that many
     * slices in the physically longest direction, and proportionally less in
     * the other directions. (so, if the volume is cube-shaped, there will
     * MAX_SLICE_COUNT slices in all three directions)
     */
    public static final int MAX_SLICE_COUNT = 500;

    private GLShader fragShader;

    private Widget canvas;
    private Widget toolPane;
    
    protected List<SliceViewer> trackedSliceViewers = new ArrayList<SliceViewer>();
    
    private final SharedContextData sharedContextData;
    
    private final Collection<PaintListener<VolumeViewer>> uninitializedSlicePaintListeners = new IdentityHashSet<PaintListener<VolumeViewer>>();

    public static final int ZORDER_START = 0;
    public static final int ZORDER_BEFORE_VOLUME = 100;
    public static final int ZORDER_END = 200;
    
    public VolumeViewer(SharedContextData scd) {
        this.sharedContextData = scd;
        setTheme("");
        canvas = new Canvas();
        canvas.setTheme("");
        this.add(canvas);

        toolPane = new BoxLayout(Direction.HORIZONTAL);
        this.add(toolPane);
        toolPane.setTheme("panel");
        ((BoxLayout)toolPane).setAlignment(Alignment.CENTER);

        LinAlg.fillIdentity(volumeToWorldTransform);
        LinAlg.fillIdentity(worldToEyeTransform);
        vpWidthInRadiants = 0.9F;
        recomputeMatrices();

        sliceBackToFrontTransforms[0] = new float[2][];
        sliceBackToFrontTransforms[1] = new float[2][];
        sliceBackToFrontTransforms[2] = new float[2][];

        sliceBackToFrontTransforms[2][0] = LinAlg.fillIdentity(null);
        sliceBackToFrontTransforms[0][0] = LinAlg.fillRotation(sliceBackToFrontTransforms[2][0], 90, 0, 1, 0, null);
        sliceBackToFrontTransforms[2][1] = LinAlg.fillRotation(sliceBackToFrontTransforms[2][0], 180, 0, 1, 0, null);
        sliceBackToFrontTransforms[1][0] = LinAlg.fillRotation(sliceBackToFrontTransforms[2][0], 270, 1, 0, 0, null);
        sliceBackToFrontTransforms[1][1] = LinAlg.fillRotation(sliceBackToFrontTransforms[2][0], 90, 1, 0, 0, null);
        sliceBackToFrontTransforms[0][1] = LinAlg.fillRotation(sliceBackToFrontTransforms[2][0], 270, 0, 1, 0, null);

        //TODO: all the toolbar functions should move into a separate controller (which could also know about undo/redo, SliceViewers synchronization etc.)
        addToolbarAction(new AbstractAction("XY") {
            @Override
            public void actionPerformed(ActionEvent e) {
                float[] w2e = LinAlg.fillIdentity(null);
                LinAlg.fillTranslation(w2e, 0, 0, - eyeDistanceInNavCubeLenghts * navigationCubeLength, w2e);
                setWorldToEyeTransform(w2e);
            }
        });
        addToolbarAction(new AbstractAction("XZ") {
            @Override
            public void actionPerformed(ActionEvent e) {
                float[] w2e = LinAlg.fillIdentity(null);
                LinAlg.fillMultiplication(w2e, sliceBackToFrontTransforms[1][0], w2e);
                LinAlg.fillTranslation(w2e, 0, eyeDistanceInNavCubeLenghts * navigationCubeLength, 0, w2e);
                setWorldToEyeTransform(w2e);
            }
        });
        addToolbarAction(new AbstractAction("YZ") {
            @Override
            public void actionPerformed(ActionEvent e) {
                float[] w2e = LinAlg.fillIdentity(null);
                LinAlg.fillMultiplication(w2e, sliceBackToFrontTransforms[0][0], w2e);
                LinAlg.fillRotation(w2e, -90, 1, 0, 0, w2e);
                LinAlg.fillTranslation(w2e, eyeDistanceInNavCubeLenghts * navigationCubeLength, 0, 0, w2e);
                setWorldToEyeTransform(w2e);
            }
        });
        addToolbarAction(new AbstractAction("Zoom RST") {
            @Override
            public void actionPerformed(ActionEvent e) {
                setVpWidthInRadiants(DEFAULT_VP_WIDTH_IN_RADIANTS);
            }
        });
        addToolbarAction(new AbstractAction("VolBox") {
            @Override
            public void actionPerformed(ActionEvent e) {
                setVolBoxVisible(!isVolBoxVisible());
            }
        });
        addToolbarAction(new AbstractAction("HideSat") {
            @Override
            public void actionPerformed(ActionEvent e) {
                setRemoveSaturatedVoxels(!isRemoveSaturatedVoxels());
            }
        });
        toolPane.add(new Label("  transp.:"));
        final Scrollbar transpSlider = new Scrollbar(Scrollbar.Orientation.HORIZONTAL);
        transpSlider.setTheme("hslider");
        transpSlider.setMinMaxValue(0, 10000);
        //transpSlider.setMinSize(300, 1);
        toolPane.add(transpSlider);
        transpSlider.addCallback(new Runnable() {
            @Override
            public void run() {
                setGlobalAlpha((float)transpSlider.getValue()/10000f);
            }
        });
        transpSlider.setValue(10000);
    }
    
    public VolumeViewer(SharedContextData scd, VolumeDataSet volumeDataSet) {
        this(scd);
        setVolumeDataSet(volumeDataSet);
    }
    
    public void addToolbarAction(final Action a) {
        Button b = new Button((String) a.getValue(Action.NAME));
        b.setTooltipContent(a.getValue(Action.SHORT_DESCRIPTION));
        b.addCallback(new Runnable() {
            @Override
            public void run() {
                a.actionPerformed(null);
            }
        });
        toolPane.add(b);
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
        float w = volumeDataSet.getWidthInMm();
        float h = volumeDataSet.getHeightInMm();
        float d = volumeDataSet.getDepthInMm();
        navigationCubeLength = (float) Math.sqrt(w * w + h * h + d * d);
        float[] lenghts = new float[]{w,h,d};
        int maxLenIdx = maxIndex(w, h, d);
        float maxLen = lenghts[maxLenIdx];
        for (int i=0; i<3; i++) {
            sliceCountByDirIdx[i] = MAX_SLICE_COUNT * lenghts[i] / maxLen;
        }
        
        LinAlg.fillIdentity(volumeToWorldTransform);
        LinAlg.fillIdentity(worldToEyeTransform);
        LinAlg.fillTranslation(worldToEyeTransform, 0, 0, - eyeDistanceInNavCubeLenghts * navigationCubeLength, worldToEyeTransform);
        
        vpWidthInRadiants = 0.9F;
        recomputeMatrices();

        //shadingPostOffset = 0.0f;
        //shadingPostScale = 1.0f;
        
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
    
    public List<SliceViewer> getTrackedSliceViewers() {
        return trackedSliceViewers;
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
    
    public float getShadingPostScale() {
        return shadingPostScale;
    }
    
    public void setShadingPostScale(float shadingPostScale) {
        this.shadingPostScale = shadingPostScale;
        refresh();
    }

    public float getShadingPostOffset() {
        return shadingPostOffset;
    }
    
    public void setShadingPostOffset(float shadingPostOffset) {
        this.shadingPostOffset = shadingPostOffset;
        refresh();
    }
    
    public float getGlobalAlpha() {
        return globalAlpha;
    }
    
    public void setGlobalAlpha(float globalAlpha) {
        this.globalAlpha = globalAlpha;
        refresh();
    }
    
    public boolean isRemoveSaturatedVoxels() {
        return removeSaturatedVoxels;
    }
    
    public void setRemoveSaturatedVoxels(boolean removeSaturatedVoxels) {
        this.removeSaturatedVoxels = removeSaturatedVoxels;
        refresh();
    }
    
    public boolean isVolBoxVisible() {
        return isVolBoxVisible;
    }
    
    public void setVolBoxVisible(boolean isVolBoxVisible) {
        this.isVolBoxVisible = isVolBoxVisible;
        refresh();
    }
    
    public void addTrackedSliceViewer(SliceViewer sv) {
        trackedSliceViewers.add(sv);
        sv.addPropertyChangeListener(trackedViewersPropChangeHandler);
        refresh();
    }
    
    public void removeTrackedViewer(VolumeViewer sv) {
        sv.removePropertyChangeListener(trackedViewersPropChangeHandler);
        trackedSliceViewers.remove(sv);
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
                ShaderManager.read("volumeviewer");
                fragShader = ShaderManager.get("volumeviewer");
                fragShader.addProgramUniform("tex");
                fragShader.addProgramUniform("scale");
                fragShader.addProgramUniform("offset");
                fragShader.addProgramUniform("removeSaturatedVoxels");
                fragShader.addProgramUniform("sliceCountFactor");
                //fragShader.addProgramUniform("debugColor");
                //fragShader.addProgramUniform("debugZ");
            } catch (Exception e) {
                throw new RuntimeException("couldn't initialize GL shader: " + e.getLocalizedMessage(), e);
            }
            initializeUninitializedSlicePaintListeners();
            isInitialized = true;
        }

        @Override
        protected void paintWidget(GUI gui) {
            GL11.glPushAttrib(GL11.GL_CURRENT_BIT|GL11.GL_LIGHTING_BIT|GL11.GL_LINE_BIT|GL11.GL_HINT_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_POLYGON_BIT|GL11.GL_ENABLE_BIT|GL11.GL_VIEWPORT_BIT|GL11.GL_TRANSFORM_BIT);
            ensureInitialized();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            setupEye2ViewportTransformation(gui);
            try {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glShadeModel(GL11.GL_FLAT);
                GL11.glDisable(GL11.GL_LINE_SMOOTH);
                GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_FASTEST);
                
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);   //clears the complete depth buffer (not just in this widget's area), which might theoretically interact with other widgets in undesired ways

                GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);

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
                    GL11.glShadeModel(GL11.GL_FLAT);
                    GL11.glColor3f(1, 1, 1);
                    GL11.glLoadIdentity();
                    GL11.glMultMatrix(LWJGLTools.toFB(worldToEyeTransform));
                    
                    if (isVolBoxVisible) {
                        GL11.glPushMatrix();
                        GL11.glMultMatrix(LWJGLTools.toFB(volumeToWorldTransform));
                        drawVolumeBounds();
                        GL11.glPopMatrix();
                    }

                    for (SliceViewer trackedViewer : trackedSliceViewers) {
                        Color color = trackedViewer.getColor();
                        GL11.glColor3f((float) color.getRed() / 255F,
                                       (float) color.getGreen() / 255F,
                                       (float) color.getBlue() / 255F);
                        GL11.glPushMatrix();
                        GL11.glMultMatrix(LWJGLTools.toFB(trackedViewer.getBaseSliceToWorldTransform()));
                        GL11.glBegin(GL11.GL_QUADS);
                        GL11.glVertex3f(-navigationCubeLength/2, -navigationCubeLength/2, trackedViewer.getNavigationZ());
                        GL11.glVertex3f( navigationCubeLength/2, -navigationCubeLength/2, trackedViewer.getNavigationZ());
                        GL11.glVertex3f( navigationCubeLength/2,  navigationCubeLength/2, trackedViewer.getNavigationZ());
                        GL11.glVertex3f(-navigationCubeLength/2,  navigationCubeLength/2, trackedViewer.getNavigationZ());
                        GL11.glEnd();
                        GL11.glPopMatrix();
                    }
                    

                    firePaintEvent(new PaintEvent<VolumeViewer>(VolumeViewer.this, sharedContextData.getAttributes()), ZORDER_BEFORE_VOLUME, ZORDER_END - 1);
                    
                    float[] viewDirInVol =  LinAlg.vminusv(LinAlg.mtimesv(eyeToVolumeTransform, new float[]{0, 0,-1}, null),
                                                           LinAlg.mtimesv(eyeToVolumeTransform, new float[]{0, 0, 0}, null),
                                                           null);
                    //System.out.println("viewDirInVol: "); printPt(viewDirInVol);
                    
                    int dirIdx = maxIndex(Math.abs(viewDirInVol[0]), Math.abs(viewDirInVol[1]), Math.abs(viewDirInVol[2]));
                    int direction = (viewDirInVol[dirIdx] < 0 ? 0 : 1);

                    //System.out.println("dirIdx=" + dirIdx + ", direction=" + direction);
                    
                    float[] sliceBackToFrontTransform = sliceBackToFrontTransforms[dirIdx][direction];
                    
                    GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
                    GL11.glEnable(GL11.GL_BLEND);
                    GL11.glAlphaFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    
                    GL11.glPushMatrix();
                    GL11.glMultMatrix(LWJGLTools.toFB(volumeToWorldTransform));

                    float[] debugColor1 = new float[]{0,1,1};
                    float[] debugColor2 = new float[]{0,0,1};
                    
                    GL11.glScalef(volumeDataSet.getWidthInMm()/2, volumeDataSet.getHeightInMm()/2, volumeDataSet.getDepthInMm()/2);
                    GL11.glMultMatrix(LWJGLTools.toFB(sliceBackToFrontTransform));
                    
                    GL11.glMatrixMode(GL11.GL_TEXTURE);
                    GL11.glPushMatrix();
                    GL11.glLoadIdentity();
                    GL11.glTranslatef(0.5f, 0.5f, 0.5f);
                    GL11.glMultMatrix(LWJGLTools.toFB(sliceBackToFrontTransform));
                    GL11.glTranslatef(-0.5f, -0.5f, -0.5f);
                    GL11.glMatrixMode(GL11.GL_MODELVIEW);
                    
                    VolumeDataSet.TextureRef texRef = volumeDataSet.bindTexture(GL13.GL_TEXTURE0, sharedContextData);

                    float[] pixelTransform = new float[]{1,0};
                    LinAlg.matrMult1D(new float[]{texRef.getPreScale(), texRef.getPreOffset()}, pixelTransform, pixelTransform);
                    LinAlg.matrMult1D(new float[]{shadingPostScale, shadingPostOffset}, pixelTransform, pixelTransform);
                    LinAlg.matrMult1D(new float[]{getGlobalAlpha(), 0}, pixelTransform, pixelTransform);

                    fragShader.bind();
                    fragShader.bindUniform("tex", 0);
                    fragShader.bindUniform("scale", pixelTransform[0]);
                    fragShader.bindUniform("offset", pixelTransform[1]);
                    fragShader.bindUniform("removeSaturatedVoxels", removeSaturatedVoxels);
                    fragShader.bindUniform("sliceCountFactor", sliceCountFactor);
                    //TODO: handle sliceCountFactor correctly in the shader
                    GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_REPLACE);
                    float sliceCount = sliceCountFactor * sliceCountByDirIdx[dirIdx];
                    float step = 2f / sliceCount;
                    int idx = 0;
                    // TODO: may use a vertex buffer for these (but as discussed above, the number of steps may depend on the direction for non-cubic volumes)
                    //   => maybe render the minimum surrounding cube of the volume
                    for (float z = -1f; z < 1f; z += step) {
                        //fragShader.bindUniform("debugColor", (idx%2==0?debugColor1:debugColor2));  //debugging (visualize depth buffer accuracy)
                        //fragShader.bindUniform("debugZ", z);
                        
                        GL11.glBegin(GL11.GL_QUADS);
                        
                        GL11.glTexCoord3f(0, 0, z/2 + 0.5f);
                        GL11.glVertex3f(-1, -1, z);

                        GL11.glTexCoord3f( 1, 0, z/2 + 0.5f);
                        GL11.glVertex3f( 1, -1, z);
                        
                        GL11.glTexCoord3f( 1,  1, z/2 + 0.5f);
                        GL11.glVertex3f( 1,  1, z);
                        
                        GL11.glTexCoord3f(0,  1, z/2 + 0.5f);
                        GL11.glVertex3f(-1,  1, z);
                        
                        GL11.glEnd();
                        idx++;
                    }
                    fragShader.unbind();

                    GL11.glMatrixMode(GL11.GL_TEXTURE);
                    GL11.glPopMatrix();

                    GL11.glMatrixMode(GL11.GL_MODELVIEW);

                    GL11.glPopMatrix(); //volumeToWorldTransform

                    GL11.glPopMatrix(); //worldToEyeTransform

                    firePaintEvent(new PaintEvent<VolumeViewer>(VolumeViewer.this, sharedContextData.getAttributes()), ZORDER_END, Integer.MAX_VALUE);
                } finally {
                    GL11.glPopMatrix();
                }
            } finally {
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
                GL11.glPopAttrib();
            }
        }
        
        private void printPt(float[] p) {
            System.out.println(""+p[0] + " " + p[1] + " " + p[2]);
        }

        private void drawVolumeBounds() {
            GL11.glPushMatrix();
            GL11.glScalef(volumeDataSet.getWidthInMm()/2, volumeDataSet.getHeightInMm()/2, volumeDataSet.getDepthInMm()/2);
            GL11.glBegin(GL11.GL_QUAD_STRIP);
            GL11.glVertex3f(-1, -1,  1);
            GL11.glVertex3f( 1, -1,  1);
            GL11.glVertex3f(-1,  1,  1);
            GL11.glVertex3f( 1,  1,  1);
            GL11.glVertex3f(-1,  1, -1);
            GL11.glVertex3f( 1,  1, -1);
            GL11.glVertex3f(-1, -1, -1);
            GL11.glVertex3f( 1, -1, -1);
            GL11.glVertex3f(-1, -1,  1);
            GL11.glVertex3f( 1, -1,  1);
            GL11.glEnd();
            GL11.glPopMatrix();
        }

        private void setupEye2ViewportTransformation(GUI gui) {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            float nearVal = 0.3f * navigationCubeLength;  // http://www.opengl.org/resources/faq/technical/depthbuffer.htm "roughly log2(zFar/zNear) bits of precision are lost"
            float farVal = 10 * navigationCubeLength;     // (see also http://www.sjbaker.org/steve/omniv/love_your_z_buffer.html)
            float vpHeightInRadiants = vpWidthInRadiants * getInnerHeight() / getInnerWidth();
            float right = nearVal * (float) Math.tan(vpWidthInRadiants/2);
            float left = -right;
            float top = nearVal * (float) Math.tan(vpHeightInRadiants/2);
            float bottom = -top;

            GL11.glFrustum(left,
                           right,
                           bottom,
                           top,
                           nearVal,
                           farVal
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
    
    private static int maxIndex(float f1, float f2, float f3) {
        if (f1 < f2) {
            return f2 < f3 ? 2 : 1;
        } else if (f2 < f3) {
            return f1 < f3 ? 2 : 0;
        } else {
            return f1 < f2 ? 1 : 0;
        }
    }

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

    
    public void addPaintListener(PaintListener<VolumeViewer> listener) {
        addPaintListener(ZORDER_BEFORE_VOLUME, listener);
    }

    public void addPaintListener(int zOrder, PaintListener<VolumeViewer> listener) {
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
