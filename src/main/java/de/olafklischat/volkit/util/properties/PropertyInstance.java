package de.olafklischat.volkit.util.properties;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;

/**
 * Represents the instance of a specific property inside a specific
 * object, e.g. someStudy.studyTime where someStudy is of class Study.
 * <p>
 * Essentially a {@link PropertyDefinition} plus the object in which the property
 * lives
 * 
 * @author Olaf Klischat
 */
public class PropertyInstance {
    private final PropertyDefinition definition;
    private final Object containingObject;
    
    protected PropertyInstance(PropertyDefinition definition, Object containingObject) {
        super();
        this.definition = definition;
        this.containingObject = containingObject;
    }

    public PropertyDefinition getDefinition() {
        return definition;
    }

    public Object getContainingObject() {
        return containingObject;
    }
    
    public String getName() {
        return definition.getName();
    }
    
    public boolean isReadOnly() {
        return definition.isReadOnly();
    }
    
    public Type getType() {
        return definition.getType();
    }

    /**
     * 
     * @return the property's current value
     */
    public Object get() {
        try {
            return definition.getGetter().invoke(containingObject);
        } catch (InvocationTargetException e) {
        	// yet another reason why checked exceptions in Java suck --
        	//  when using reflection, exception types suddenly change
        	//  because everything must be wrapped in RuntimeExceptions unless
        	//  you want to declare "throws Exception" everywhere.
        	// => make sure that at least RuntimeExceptions are correctly
        	//    unwrapped again
        	if (e.getTargetException() instanceof RuntimeException) {
        		throw (RuntimeException)(e.getTargetException());
        	} else {
                throw new RuntimeException("exception in getter method for "+this, e);
        	}
        } catch (Exception e) {
            throw new RuntimeException("exception in getter method for "+this, e);
        }
    }
    
    /**
     * @param value value
     * @return the property's current value to value
     */
    public void set(Object value) {
        if (isReadOnly()) {
            throw new IllegalStateException("can't write read-only property "+this);
        }
        try {
            definition.getSetter().invoke(containingObject, value);
        } catch (InvocationTargetException e) {
        	// make sure that at least RuntimeExceptions are correctly
        	//    unwrapped again (see above)
        	if (e.getTargetException() instanceof RuntimeException) {
        		throw (RuntimeException)(e.getTargetException());
        	} else {
                throw new RuntimeException("exception in setter method for "+this,e);
        	}
        } catch (Exception e) {
            throw new RuntimeException("exception in setter method for "+this,e);
        }
    }

    /**
     * if this is a collection-valued property (see {@link CollectionPropertyDefinition}),
     * add value to it.
     * @param value value
     */
    public void add(Object value) {
        if (!(definition instanceof CollectionPropertyDefinition)) {
            throw new IllegalStateException("can't add value to non-collection-valued property "+this);
        }
        CollectionPropertyDefinition cpd = (CollectionPropertyDefinition)definition;
        if (null==cpd.getCollectionAdder()) {
            throw new IllegalStateException("no adder method for collection-valued property "+this);
        }
        try {
            cpd.getCollectionAdder().invoke(containingObject, value);
        } catch (InvocationTargetException e) {
        	// make sure that at least RuntimeExceptions are correctly
        	//    unwrapped again (see above)
        	if (e.getTargetException() instanceof RuntimeException) {
        		throw (RuntimeException)(e.getTargetException());
        	} else {
                throw new RuntimeException("exception in setter method for "+this,e);
        	}
        } catch (Exception e) {
            throw new RuntimeException("exception in collection adder method for "+this,e);
        }
    }
    
    /**
     * if this is a collection-valued property (see {@link CollectionPropertyDefinition}),
     * remove value from it.
     * @param value value
     */
    public void remove(Object value) {
        if (!(definition instanceof CollectionPropertyDefinition)) {
            throw new IllegalStateException("can't remove value from non-collection-valued property "+this);
        }
        CollectionPropertyDefinition cpd = (CollectionPropertyDefinition)definition;
        if (null==cpd.getCollectionRemover()) {
            throw new IllegalStateException("no remover method for collection-valued property "+this);
        }
        try {
            cpd.getCollectionRemover().invoke(containingObject, value);
        } catch (InvocationTargetException e) {
        	// make sure that at least RuntimeExceptions are correctly
        	//    unwrapped again (see above)
        	if (e.getTargetException() instanceof RuntimeException) {
        		throw (RuntimeException)(e.getTargetException());
        	} else {
                throw new RuntimeException("exception in setter method for "+this,e);
        	}
        } catch (Exception e) {
            throw new RuntimeException("exception in collection remover method for "+this,e);
        }
    }
    
    @Override
    public String toString() {
        return ""+containingObject+"."+definition.getName();
    }
    
    @Override
    public int hashCode() {
    	return 31 * definition.hashCode() + containingObject.hashCode();
    }
    
    @Override
    public boolean equals(Object obj) {
    	if (!(obj instanceof PropertyInstance)) { return false; }
    	PropertyInstance opi = (PropertyInstance)obj;
    	return definition.equals(opi.definition) && containingObject.equals(opi.containingObject);
    }
}
