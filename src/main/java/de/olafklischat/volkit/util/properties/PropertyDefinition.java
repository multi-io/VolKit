package de.olafklischat.volkit.util.properties;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a property definition contained inside a class, e.g.
 * Study.studyTime. A class is defined to "contain" a property definition for a
 * property named &lt;name&gt; iff it contains at least a public method
 * get&lt;name&gt;().
 * <p>
 * The class (Study in this example) is {@link #getClass()}, the name
 * ("studyTime" in the example) is {@link #getName()}, the getter and possibly
 * setter method is in {@link #getGetter()} and {@link #getSetter()},
 * respectively.
 * 
 * @see PropertyInstance
 * @author Olaf Klischat
 */
public abstract class PropertyDefinition {

	private final ClassInfo owningClassInfo;
	private final String name;
	private final Type type;
	private final Method getter, setter;

	public String getName() {
		return name;
	}

	public Type getType() {
		return type;
	}

	public boolean isReadOnly() {
		return setter == null;
	}

	protected PropertyDefinition(final ClassInfo owningClassInfo, final String name, final Type type, final Method getter, final Method setter) {
		super();
		this.owningClassInfo = owningClassInfo;
		this.name = name;
		this.type = type;
		this.getter = getter;
		this.setter = setter;
	}

	public Method getGetter() {
		return getter;
	}

	public Method getSetter() {
		return setter;
	}

	@Override
	public String toString() {
		return getOwningClassInfo().getAssociatedClass() + "." + getName();
	}

	public ClassInfo getOwningClassInfo() {
		return owningClassInfo;
	}

	// properties of this property definition.
	// Property definitions may themselves contain properties :-P
	// This can be used e.g. for specifying whether a property should
	// be visible or even editable in a GUI.
	//
	// properties of properties have a name (a String) and a value
	// (an arbitrary object)

	private Map<String, Object> properties = new HashMap<String, Object>();

	public Object getProperty(final String _name) {
		return properties.get(_name);
	}

	public boolean hasProperty(final String _name) {
		return properties.containsKey(_name);
	}

	public void setProperty(final String name, final Object value) {
		properties.put(name, value);
	}

	public boolean isImg() {
		return getType().equals(byte[].class) && getName().toLowerCase().contains("thumbnail");
	}
}
