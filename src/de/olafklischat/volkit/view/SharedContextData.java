package de.olafklischat.volkit.view;

import java.util.HashMap;
import java.util.Map;

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
    private final Map<String, Object> attributes = new HashMap<String, Object>();

    /**
     * Used by GLContext creators (e.g. GL viewer components) only (when initializing a new context).
     */
    public SharedContextData() {
    }

    // TODO: this ref counting scheme for ensuring context re-initializations is probably too brittle
    // and fragile in the presence of possible arbitrary context re-initializations. A more robust
    // approach: "mark" a context, e.g. by storing a well-known, small object (texture, display list or whatever)
    // in it, when it is first created, Check for the presence of that mark whenever the context is
    // to be used, re-initialize the context if the mark is missing.

    // TODO: on top of that, why do we have to do all this ref conting stuff anyway? It would be much better
    // to have an offscreen GL context that is never disposed (before the JVM session ends), and share all
    // shared context data with that, so it'll never run out of date

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

}
