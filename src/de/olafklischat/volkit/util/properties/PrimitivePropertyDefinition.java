package de.olafklischat.volkit.util.properties;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

public class PrimitivePropertyDefinition extends PropertyDefinition {

    protected PrimitivePropertyDefinition(ClassInfo owningClassInfo, String name, Type type, Method getter, Method setter) {
        super(owningClassInfo, name, type, getter, setter);
    }

}
