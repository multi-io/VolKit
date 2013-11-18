package de.olafklischat.twlawt;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

import de.matthiasmann.twl.Event;
import de.matthiasmann.twl.Widget;
import de.matthiasmann.twl.Event.Type;

/**
 * Class for converting TWL mouse events to AWT mouse events. Can't really use
 * static methods in {@link TwlAwtEventUtil} for this because we need to track
 * some state (the pressed button in case of a mouse drag event, which isn't
 * included in the TWL Event object [TODO: or is it? Check the
 * {@link Event#getModifiers()} bitfield!] and thus must be tracked from the
 * preceding mouse press event).
 * 
 * @author olaf
 */
public class TwlToAwtMouseEventConverter {
    
    protected int lastButton = -1;

    public MouseEvent mouseEventTwlToAwt(Event evt) {
        return mouseEventTwlToAwt(evt, (Object)null);
    }
    
    public MouseEvent mouseEventTwlToAwt(Event evt, Widget sourceWidget) {
        MouseEvent result = mouseEventTwlToAwt(evt, (Object)sourceWidget);
        result.translatePoint(- sourceWidget.getX(), - sourceWidget.getY());
        return result;
    }

    public MouseEvent mouseEventTwlToAwt(Event evt, Object source) {
        if(!evt.isMouseEvent()) {
            return null;
        }
        int id = TwlAwtEventUtil.mouseEventTypeTwlToAwt(evt.getType());
        int modifiers = TwlAwtEventUtil.modifiersTwlToAwt(evt.getModifiers());
        modifiers |= ModifiersState.getModifiers();
        long when = System.currentTimeMillis();
        int x = evt.getMouseX();
        int y = evt.getMouseY();
        int xAbs = evt.getMouseX();
        int yAbs = evt.getMouseY();
        int clickCount = evt.getMouseClickCount();
        boolean popupTrigger = false; //...?
        MouseEvent result;
        if (evt.isMouseEventNoWheel()) {
            int button = MouseEvent.NOBUTTON;
            if (evt.getType() == Type.MOUSE_DRAGED) {
                button = lastButton;
            } else {
                button = TwlAwtEventUtil.mouseButtonTwlToAwt(evt.getMouseButton());
            }
            if (evt.getType() == Type.MOUSE_BTNDOWN) {
                lastButton = button;
            } else if (evt.getType() == Type.MOUSE_BTNUP) {
                lastButton = MouseEvent.NOBUTTON;
            }
            result = new MouseEvent(dummy, id, when, modifiers, x, y, xAbs, yAbs, clickCount, popupTrigger, button);
        } else {
            int scrollType = MouseWheelEvent.WHEEL_UNIT_SCROLL;
            int scrollAmount = 3;
            int wheelRotation = - evt.getMouseWheelDelta();
            result = new MouseWheelEvent(dummy, id, when, modifiers, x, y, clickCount, popupTrigger, scrollType, scrollAmount, wheelRotation);
        }
        result.setSource(source);
        return result;
    }
    
    protected static Component dummy = new Component() {
    };

}
