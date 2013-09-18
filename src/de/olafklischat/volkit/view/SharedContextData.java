package de.olafklischat.volkit.view;

import de.olafklischat.lang.Runnable2;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import javax.media.opengl.GL;
import javax.media.opengl.GLContext;
import org.apache.log4j.Logger;

/**
 * {@link GLImageListViewCellViewer} uses this class to inform outside parties
 * that the (shared) OpenGL context that all the cell viewers use was created
 * (see {@link #registerContextInitCallback(de.olafklischat.lang.Runnable2) }).
 * An instance of SharedContextData represents (is associated 1:1 with) a shared
 * context. Said outside components may associate arbitrary data (display list IDs,
 * texture IDs etc.) with the context ({@link #setAttribute(java.lang.String, java.lang.Object) })
 * and retrieve it later.
 *
 * @author olaf
 */
public class SharedContextData {

    static final Logger logger = Logger.getLogger(SharedContextData.class);

    private int refCount = 0;
    private GLContext glContext = null;
    private final Map<String, Object> attributes = new HashMap<String, Object>();

    /**
     * Used by GLContext creators (e.g. GL viewer components) only (when initializing a new context).
     */
    public SharedContextData() {
    }

    public GLContext getGlContext() {
        return glContext;
    }

    // TODO: this ref counting scheme for ensuring context re-initializations is probably too brittle
    // and fragile in the presence of possible arbitrary context re-initializations. A more robust
    // approach: "mark" a context, e.g. by storing a well-known, small object (texture, display list or whatever)
    // in it, when it is first created, Check for the presence of that mark whenever the context is
    // to be used, re-initialize the context if the mark is missing.

    // TODO: on top of that, why do we have to do all this ref conting stuff anyway? It would be much better
    // to have an offscreen GL context that is never disposed (before the JVM session ends), and share all
    // shared context data with that, so it'll never run out of date

    /**
     * Used by GLContext creators (e.g. ) only (when initializing a new context).
     *
     * @param glContext
     */
    public void ref(GLContext glContext) {
        refCount++;
        if (refCount == 1) {
            this.glContext = glContext;
        }
    }

    /**
     * Used by GLContext creators (e.g. {@link GLImageListViewCellViewer}) only (when initializing a new context).
     */
    public void unref() {
        if (refCount == 0) {
            // TODO: this is triggered when the user scrolls through the grid quickly. Investigate!
            //throw new IllegalStateException("too many unref calls...");
            logger.error("too many unref calls...");
            return;
        }
        refCount--;
        if (refCount == 0) {
            // at this point, there are no longer any SharedContextDatas in the JVM (this mostly happens
            // when the last GLCanvas/GLDrawable is disposed). Ideally, this would never happen because
            // we should have an offscreen GL context that is never disposed (see above todo)
            this.glContext = null;
            this.attributes.clear();
        }
    }

    public int getRefCount() {
        return refCount;
    }
    
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    public Object setAttribute(String name, Object value) {
        return attributes.put(name, value);
    }

    public Object removeAttribute(String name) {
        return attributes.remove(name);
    }


    private static Collection<Runnable2<SharedContextData, GL> > contextInitCallbacks = new LinkedList<Runnable2<SharedContextData, GL>>();

    public static void registerContextInitCallback(Runnable2<SharedContextData, GL> callback) {
        contextInitCallbacks.add(callback);
    }

    /**
     * Used by GLContext creators (e.g. {@link SliceViewer}) only (when initializing a new context).
     */
    public static void callContextInitCallbacks(SharedContextData cd, GL gl) {
        for (Runnable2<SharedContextData, GL> callback : contextInitCallbacks) {
            callback.run(cd, gl);
        }
    }

}
