package de.olafklischat.volkit.view;

import java.util.EventObject;
import java.util.Map;

import javax.media.opengl.GL;

/**
 * Event that indicates that the GL canvas area of a {@link SliceViewer} is
 * being painted.
 * 
 * @author Olaf Klischat
 */
public class SlicePaintEvent extends EventObject {

    protected GL gl;
    private Map<String, Object> sharedContextData;
    private boolean consumed = false;
    
    public SlicePaintEvent(SliceViewer source, GL gl, Map<String, Object> sharedContextData) {
        super(source);
        this.gl = gl;
        this.sharedContextData = sharedContextData;
    }
    
    @Override
    public SliceViewer getSource() {
        return (SliceViewer) super.getSource();
    }
    
    public GL getGl() {
        return gl;
    }

    /**
     * Shared context data for the paint event. See
     * {@link SlicePaintListener#glSharedContextDataInitialization(javax.media.opengl.GL, Map)}
     * for details.
     * <p>
     * Not relevant for Java2D paint events.
     * 
     * @return
     */
    public Map<String, Object> getSharedContextData() {
        return sharedContextData;
    }

    public boolean isConsumed() {
        return consumed;
    }
    
    public void consume() {
        consumed = true;
    }

}
