package de.olafklischat.twlawt;

import de.matthiasmann.twl.GUI;

/**
 * HACK HACK (beware) Temporary helper class for tracking keyboard modifier
 * states for mouse events when converting AWT->TWL->AWT mouse events. This is
 * the wrong way to do this -- we should instead handle regular AWT key events
 * too and convert them to TWL key events (
 * {@link GUI#handleKey(int, char, boolean)}, which will cause TWL to keep track
 * of modifier states and include them in the TWL mouse events sent out by it.
 * 
 * @author Olaf Klischat
 */
public class ModifiersState {

    private static int modifiers = 0;
    
    public static int getModifiers() {
        return modifiers;
    }
    
    public static void setModifiers(int modifiers) {
        ModifiersState.modifiers = modifiers;
    }
    
    public static void addModifiers(int modifiers) {
        ModifiersState.modifiers |= modifiers;
    }
}
