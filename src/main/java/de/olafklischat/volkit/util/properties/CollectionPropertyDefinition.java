package de.olafklischat.volkit.util.properties;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

/**
 * {@link PropertyDefinition} for collection-values properties, i.e. properties
 * whose class defines methods addTo&lt;name&gt;(value) and possibly
 * removeFrom&lt;name&gt;(value), where &lt;name&gt; is the {@link PropertyDefinition#getName()}
 * of the property definition.
 * @author Olaf Klischat
 */
public class CollectionPropertyDefinition extends PropertyDefinition {

    private final Type componentType;
    private final Method collectionAdder, collectionRemover;
    
    CollectionPropertyDefinition(ClassInfo owningClassInfo, String name, Type type, Type componentType,
                                        Method getter, Method setter,
                                        Method collectionAdder, Method collectionRemover) {
        super(owningClassInfo, name, type, getter, setter);
        this.componentType = componentType;
        this.collectionAdder = collectionAdder;
        this.collectionRemover = collectionRemover;
    }

    /**
     * @return type of objects that may be included in this collection-valued
     *         property definition (determined via reflecting the generic type argument)
     */
    public Type getComponentType() {
        return componentType;
    }

    /**
     * @return the addTo&lt;name&gt;(value) method mentioned above
     */
    public Method getCollectionAdder() {
        return collectionAdder;
    }

    /**
     * @return the removeFrom&lt;name&gt;(value) method mentioned above
     */
    public Method getCollectionRemover() {
        return collectionRemover;
    }

}
