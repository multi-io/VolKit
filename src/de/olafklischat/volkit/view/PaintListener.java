package de.olafklischat.volkit.view;

import java.util.Map;

/**
 * Listener for receiving {@link PaintEvent}s from a viewer (
 * {@link SliceViewer} or {@link VolumeViewer}).
 * 
 * @author olaf
 */
public interface PaintListener<ViewerClass> {
    /**
     * Invoked every time a slice is being painted. The listener must perform its
     * painting tasks here.
     * 
     * @param e
     *            event describing the paint request.
     */
    void onPaint(PaintEvent<ViewerClass> e);

    /**
     * An OpenGL drawable is being initialized.
     * <p>
     * This method will be called at least once per listener. I.e., if you
     * register a listener with a viewer that is already being displayed (and
     * thus, has already initialized its GL canvasses), this method will still
     * be called once, right after listener registration.
     * 
     * @param glAutoDrawable
     * @param sharedData
     */
    void glDrawableInitialized(ViewerClass sv, Map<String, Object> sharedData);

    /**
     * Method that will be called every time a GL context is being set up whose
     * data (e.g., textures and display lists) may later be shared with new GL
     * contexts being created. sharedData is a place where the listener can put
     * arbitrary data (e.g., IDs of created textures or display lists) that it
     * needs for painting. The sharedData will be passed to
     * {@link #onPaint(PaintEvent)} again (in
     * {@link PaintEvent#getSharedContextData()}).
     * <p>
     * There will be one such sharedData per set of data-sharing GL contexts
     * (most of the time, this amounts to just one per VM), and all listeners
     * will receive that sharedData and be able to write to it. Thus, the
     * listeners must ensure that they're not overwriting each other's values in
     * the map. Thus, it is advised to use unique strings for the keys in the
     * map, e.g. dotted names similar to Java class names.
     * <p>
     * Just like {@link #glDrawableInitialized(Object, Map)}, this method
     * will be called at least once per listener.
     * 
     * @param sharedData
     */
    void glSharedContextDataInitialization(ViewerClass sv, Map<String, Object> sharedData);

    /**
     * An OpenGL drawable that has previously been initialized (and for which
     * {@link #glDrawableInitialized(Object, Map)} was called then) is being
     * disposed.
     * 
     * @param sv
     * @param sharedData
     */
    void glDrawableDisposing(ViewerClass sv, Map<String, Object> sharedData);

}
