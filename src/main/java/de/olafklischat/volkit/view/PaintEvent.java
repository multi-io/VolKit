package de.olafklischat.volkit.view;

import java.util.EventObject;
import java.util.Map;

/**
 * Event that indicates that the GL canvas area of a viewer ({@link SliceViewer}
 * or {@link VolumeViewer}) is being painted.
 * 
 * @author Olaf Klischat
 */
public class PaintEvent<ViewerClass> extends EventObject {

    private Map<String, Object> sharedContextData;
    private boolean consumed = false;
    
    public PaintEvent(ViewerClass source, Map<String, Object> sharedContextData) {
        super(source);
        this.sharedContextData = sharedContextData;
    }
    
    @Override
    public ViewerClass getSource() {
        return (ViewerClass) super.getSource();
    }

    /**
     * Shared context data for the paint event. See
     * {@link PaintListener#glSharedContextDataInitialization(Object, Map)}
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
