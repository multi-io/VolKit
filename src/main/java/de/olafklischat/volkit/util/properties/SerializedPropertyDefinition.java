package de.olafklischat.volkit.util.properties;

import java.io.Serializable;

public class SerializedPropertyDefinition implements Serializable {
	private static final long serialVersionUID = 3344844312617244602L;

	Class<?> clazz;
	String propertyName;

	public PropertyDefinition toPropertyDefinition() {
		return ClassInfo.getExistingClassInfoFor(clazz).getPropertyDefinition(propertyName);
	}

	public static SerializedPropertyDefinition fromPropertyDefinition(PropertyDefinition pd) {
		SerializedPropertyDefinition result = new SerializedPropertyDefinition();
		result.clazz = pd.getOwningClassInfo().getAssociatedClass();
		result.propertyName = pd.getName();
		return result;
	}
}
