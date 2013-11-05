package de.olafklischat.twlawt;

import java.awt.Component;
import java.awt.event.MouseEvent;

import de.matthiasmann.twl.Event;
import de.matthiasmann.twl.Widget;

public class TwlAwtEventUtil {
    
    public static int mouseButtonAwtToTwl(int awtButton) {
        switch (awtButton) {
        case MouseEvent.BUTTON1:
            return Event.MOUSE_LBUTTON;
        case MouseEvent.BUTTON2:
            return Event.MOUSE_MBUTTON;
        case MouseEvent.BUTTON3:
            return Event.MOUSE_RBUTTON;
        default:
            return Event.MOUSE_LBUTTON; //or what?
        }
    }
    
    public static int mouseButtonTwlToAwt(int twlButton) {
        switch (twlButton) {
        case Event.MOUSE_LBUTTON:
            return MouseEvent.BUTTON1;
        case Event.MOUSE_MBUTTON:
            return MouseEvent.BUTTON2;
        case Event.MOUSE_RBUTTON:
            return MouseEvent.BUTTON3;
        default:
            return MouseEvent.BUTTON1; //or what?
        }
    }
    
    public static int mouseEventTypeTwlToAwt(Event.Type twlType) {
        switch (twlType) {
        case MOUSE_BTNDOWN:
            return MouseEvent.MOUSE_PRESSED;
        case MOUSE_BTNUP:
            return MouseEvent.MOUSE_RELEASED;
        case MOUSE_CLICKED:
            return MouseEvent.MOUSE_CLICKED;
        case MOUSE_DRAGED:
            return MouseEvent.MOUSE_DRAGGED;
        case MOUSE_MOVED:
            return MouseEvent.MOUSE_MOVED;
        case MOUSE_WHEEL:
            return MouseEvent.MOUSE_WHEEL;
        case MOUSE_ENTERED:
            return MouseEvent.MOUSE_ENTERED;
        case MOUSE_EXITED:
            return MouseEvent.MOUSE_EXITED;
        default:
            return -1; //or what?
        }
    }
    
    public static int modifiersTwlToAwt(int twlMods) {
        int result = 0;
        if (0 != (twlMods | Event.MODIFIER_LSHIFT)) {
            result &= MouseEvent.SHIFT_DOWN_MASK;
        }
        if (0 != (twlMods | Event.MODIFIER_LMETA)) {
            result &= MouseEvent.META_DOWN_MASK;
        }
        if (0 != (twlMods | Event.MODIFIER_LCTRL)) {
            result &= MouseEvent.CTRL_DOWN_MASK;
        }
        if (0 != (twlMods | Event.MODIFIER_RSHIFT)) {
            result &= MouseEvent.SHIFT_DOWN_MASK;
        }
        if (0 != (twlMods | Event.MODIFIER_RMETA)) {
            result &= MouseEvent.META_DOWN_MASK;
        }
        if (0 != (twlMods | Event.MODIFIER_RCTRL)) {
            result &= MouseEvent.CTRL_DOWN_MASK;
        }
        if (0 != (twlMods | Event.MODIFIER_LALT)) {
            result &= MouseEvent.ALT_DOWN_MASK;
        }
        if (0 != (twlMods | Event.MODIFIER_RALT)) {
            result &= MouseEvent.ALT_DOWN_MASK;
        }
        return result;
    }

    public static MouseEvent mouseEventTwlToAwt(Event evt) {
        return mouseEventTwlToAwt(evt, (Object)null);
    }
    
    public static MouseEvent mouseEventTwlToAwt(Event evt, Widget sourceWidget) {
        MouseEvent result = mouseEventTwlToAwt(evt, (Object)sourceWidget);
        result.translatePoint(- sourceWidget.getX(), - sourceWidget.getY());
        return result;
    }

    public static MouseEvent mouseEventTwlToAwt(Event evt, Object source) {
        if(!evt.isMouseEvent()) {
            return null;
        }
        int id = mouseEventTypeTwlToAwt(evt.getType());
        int modifiers = modifiersTwlToAwt(evt.getModifiers());
        long when = System.currentTimeMillis();
        int x = evt.getMouseX();
        int y = evt.getMouseY();
        int xAbs = evt.getMouseX();
        int yAbs = evt.getMouseY();
        int clickCount = evt.getMouseClickCount();
        boolean popupTrigger = false; //...?
        int button = mouseButtonTwlToAwt(evt.getMouseButton());
        MouseEvent result = new MouseEvent(dummy, id, when, modifiers, x, y, xAbs, yAbs, clickCount, popupTrigger, button);
        result.setSource(source);
        return result;
    }
    
    protected static Component dummy = new Component() {
    };

}
