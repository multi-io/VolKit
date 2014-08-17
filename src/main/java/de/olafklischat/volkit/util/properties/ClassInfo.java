package de.olafklischat.volkit.util.properties;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

//TODO: ideally ClassInfo, PropertyDefinition and PropertyInstance would all be
//      parameterized with the class they refer to, but this gets complicated...
//      (see http://user.cs.tu-berlin.de/~klischat/GenericsTest1.java)


/**
 * A ClassInfo object is associated 1:1 to a Java class. It provides information
 * about properties (see {@link PropertyDefinition}) defined by that class.
 */
public class ClassInfo {

    private Class<?> clazz;
    
    private Map<String,PropertyDefinition> propertyDefs
        = new HashMap<String,PropertyDefinition>();
    
    private ClassInfo(Class<?> clazz) {
        this.clazz = clazz;
    }

    //map class => (map property name => property definition)
    private static Map<Class<?>, ClassInfo> classInfosByClass =
        new HashMap<Class<?>, ClassInfo>();
    
    private static final Set<String> invalidPropertyNames = new HashSet<String>();
    static {
        //TODO: get rid of this, use appropriate property-properties instead
        invalidPropertyNames.add("class");
        invalidPropertyNames.add("repository");
        invalidPropertyNames.add("property");
        invalidPropertyNames.add("propertyDefinition");
        invalidPropertyNames.add("propertyNames");
        invalidPropertyNames.add("publicClass");
    }
    
    public static ClassInfo getClassInfoFor(Class<?> clazz) {
        //could use java.beans.BeanInfo/BeanInspector if its interface weren't so awkward,
        //   if its collections ("indexed properties") weren't always arrays,
        //   and if we wouldn't also be looking for "addToXxx()/removeFromXxx()" methods
        //TODO: try to store the properties in some definite order (LinkedHashMap, but
        //        does Class.getMethods() return the methods in a defined order?)
        //TODO: consider public methods only
        if (!classInfosByClass.containsKey(clazz)) {
            //map property name => {getter,setter,collection adder,collection remover}
            Map<String,Method[]> methodsByPropertyName = new HashMap<String,Method[]>();
            for (Method m: clazz.getMethods()) {
                String methodName = m.getName();
                String propName = null;
                Method getterMethod = null, setterMethod = null,
                       collAdderMethod = null, collRemoverMethod = null;
                if (methodName.startsWith("get") && methodName.length()>3 && m.getParameterTypes().length==0) {
                    propName = methodName.substring(3,4).toLowerCase()+methodName.substring(4);
                    getterMethod = m;
                } else if (methodName.startsWith("set") && methodName.length()>3 && m.getParameterTypes().length==1) {
                    propName = methodName.substring(3,4).toLowerCase()+methodName.substring(4);
                    setterMethod = m;
                } else if (methodName.startsWith("addTo") && methodName.length()>5 && !(methodName.endsWith("AtIndex"))) {
                    propName = methodName.substring(5,6).toLowerCase()+methodName.substring(6);
                    collAdderMethod = m;
                } else if (methodName.startsWith("removeFrom") && methodName.length()>10) {
                    propName = methodName.substring(10,11).toLowerCase()+methodName.substring(11);
                    collRemoverMethod = m;
                }
                if (null != propName) {
                    Method[] methods = methodsByPropertyName.get(propName);
                    if (null==methods) {
                        methods = new Method[4];
                        methodsByPropertyName.put(propName, methods);
                    }
                    if (null!=getterMethod) { methods[0]=getterMethod; }
                    if (null!=setterMethod) { methods[1]=setterMethod; }
                    if (null!=collAdderMethod) { methods[2]=collAdderMethod; }
                    if (null!=collRemoverMethod) { methods[3]=collRemoverMethod; }
                }
            }

            ClassInfo newClassInfo = new ClassInfo(clazz);
            classInfosByClass.put(clazz, newClassInfo);
            newClassInfo.propertyDefs = new HashMap<String,PropertyDefinition>();
            for (Map.Entry<String,Method[]> e: methodsByPropertyName.entrySet()) {
                String propertyName = e.getKey();
                if (invalidPropertyNames.contains(propertyName)) { continue; }
                Method[] methods = e.getValue();
                Method getter=methods[0], setter=methods[1], collAdder=methods[2], collRemover=methods[3];
                //we always expect at least a public getter
                if (null==getter) {
                    throw new IllegalStateException("property "+clazz+"."+propertyName+": no getter method");
                }
                PropertyDefinition pd;
                //TODO: move this stuff into PropertyDefinition constructors
                if (collAdder!=null || collRemover!=null) {
                    Type returnType = getter.getGenericReturnType();
                    Type[] compTypes;
                    if (!(returnType instanceof ParameterizedType)) {
                        //throw new IllegalStateException("collection property "+this.getClass()+"."+propertyName+": return type not parameterized");
                        compTypes = new Type[]{Object.class};
                    } else {
                        compTypes = ((ParameterizedType)returnType).getActualTypeArguments();
                    }
                    if (compTypes.length!=1) {
                        throw new IllegalStateException("collection property "+clazz+"."+propertyName+": no return type with one type parameter");
                    }
                    pd = new CollectionPropertyDefinition(newClassInfo, propertyName, returnType, compTypes[0], getter, setter, collAdder, collRemover);
                } else {
                    pd = new PrimitivePropertyDefinition(newClassInfo, propertyName, getter.getReturnType(), getter, setter);
                }
                newClassInfo.propertyDefs.put(propertyName, pd);
            }
        }
        return classInfosByClass.get(clazz);
    }

    /**
	 * Searches for an already existing ClassInfo for clazz or one of its
	 * superinterfaces and superclasses. An "already existing" ClassInfo is one
	 * that was previously obtained by calling {@link #getClassInfoFor(Class)}
	 * at least once. If no such ClassInfo exists for clazz, its superinterfaces
	 * and superclasses are searched using a heuristic approach (first all the
	 * direct and indirect superinterfaces, then the direct and indirect
	 * superclasses, both using depth-first search). So, if there is an existing
	 * ClassInfo for clazz or at least one of its superinterfaces and
	 * superclasses, this method will return one, but you don't have much
	 * control over which one if there's more than one.
	 * 
	 * @param clazz
	 *            the class
	 * @return the ClassInfo, or null if none was found
	 */
    public static ClassInfo getExistingClassInfoFor(Class<?> clazz) {
        if (classInfosByClass.containsKey(clazz)) {
        	return classInfosByClass.get(clazz);
        }
        for (Class<?> iface: clazz.getInterfaces()) {
        	ClassInfo ifaceInfo = getExistingClassInfoFor(iface);
        	if (ifaceInfo != null) { return ifaceInfo; }
        }
        Class<?> superclass = clazz.getSuperclass();
        if (null != superclass) {
        	return getExistingClassInfoFor(superclass);
        } else {
        	return null;
        }
    }

    public Class<?> getAssociatedClass() {
        return clazz;
    }
    
    public Collection<String> getPropertyNames() {
        return propertyDefs.keySet();
    }
    
    public PropertyDefinition getPropertyDefinition(String name) {
        return propertyDefs.get(name);
    }
    
    public Map<String,PropertyDefinition> getPropertyDefinitions() {
    	return propertyDefs;
    }

    public PropertyInstance getProperty(String propertyName, Object containingObject) {
        PropertyDefinition pd = getPropertyDefinition(propertyName);
        return pd==null? null : new PropertyInstance(pd, containingObject);
    }
    


    /**
	 * "class properties" of this ClassInfo. Serve just the same purpose as the
	 * properties of a {@link PropertyDefinition} -- user can use them to
	 * associate arbitrary, e.g. GUI-related, information like display names,
	 * visibility flags etc. with a ClassInfo.
	 * <p>
	 * Called "class properties" rather than just "properties" because ClassInfo
	 * of course already have "regular"
	 * {@link #getPropertyDefinition(String) properties}.
	 * <p>
	 * Just like {@link PropertyDefinition} properties, class properties have a
	 * name (a String) and a value (an arbitrary object)
	 */
	public Object getClassProperty(final String _name) {
		return classProperties.get(_name);
	}

	public boolean hasClassProperty(final String _name) {
		return classProperties.containsKey(_name);
	}

	public void setClassProperty(final String name, final Object value) {
		classProperties.put(name, value);
	}

	private Map<String, Object> classProperties = new HashMap<String, Object>();
}
