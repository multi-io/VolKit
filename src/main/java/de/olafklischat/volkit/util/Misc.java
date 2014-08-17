package de.olafklischat.volkit.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class Misc {

    protected Misc() {
        //
    }

    @SuppressWarnings("unchecked")
    public static <T> T deepCopy(T t) {
        try {
            ByteArrayOutputStream serializedT = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(serializedT);
            oos.writeObject(t);
            oos.flush();
            return (T) (new ObjectInputStream(new ByteArrayInputStream(serializedT.toByteArray())).readObject());
        } catch (NotSerializableException e) {
            throw new RuntimeException("serialization error", e);
        } catch (IOException e) {
            throw new IllegalStateException("should never happen", e);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("should never happen", e);
        }
    }

    public static boolean equal(Object o1, Object o2) {
        return ((o1 == null) && (o2 == null)) ||
                ((o1 != null) && (o1.equals(o2))) ||
                ((o2 != null) && (o2.equals(o1)));
    }

    /**
     * Call method methodName on receiver with arguments, even if the method is
     * protected, private or package-private.
     *
     * @param receiver
     *            receiver
     * @param methodName
     *            methodName
     * @param arguments
     *            arguments
     * @return result returned by the method, or null if there wasn't one
     * @throws IllegalArgumentException
     *             if the method wasn't found
     * @throws Throwable
     *             exception thrown by the method (may also be
     *             IllegalArgumentException)
     */
    public static Object callMethod(Object receiver, String methodName, Object... arguments) {
        Object[] result = {null};
        Class<?> receiverClass = receiver.getClass();
        while (null != receiverClass) {
            if (tryCallMethod(receiverClass, receiver, methodName, result, arguments)) {
                return result[0];
            }
            receiverClass = receiverClass.getSuperclass();
        }
        throw new IllegalArgumentException("method not found: " + methodName + " in object: " + receiver);
    }

    private static boolean tryCallMethod(Class<?> receiverClass, Object receiver, String methodName, Object[] result, Object... arguments) {
        for (Method m : receiverClass.getDeclaredMethods()) {
            if (!m.getName().equals(methodName)) {
                continue;
            }
            m.setAccessible(true);
            try {
                result[0] = m.invoke(receiver, arguments);
            } catch (IllegalAccessException iacce) {
                throw new IllegalStateException("SHOULD NEVER HAPPEN");
            } catch (IllegalArgumentException e) {
                // arguments didn't match. Try other methods (there may be
                // overloaded ones)
                continue;
            } catch (InvocationTargetException e) {
                // ease-of-use (no checked exception declarations) are more
                // important than
                // ultimate clean-ness for us here
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                throw new RuntimeException(cause);
            }
            return true;
        }
        return false;
    }

    /**
     * Same as the other callMethod, except that you also pass in the
     * parameterTypes, which may make the call more efficient (we don't have to
     * search the method space) -- but does it really? (the Java
     * {@link Class#getDeclaredMethod(String, Class...)} may itself do a search
     * internally).
     *
     * @param receiver
     * @param methodName
     * @param parameterTypes
     * @param arguments
     * @return
     * @throws Throwable
     */
    public static Object callMethod(Object receiver, String methodName, Class<?>[] parameterTypes, Object... arguments) {
        Object[] result = {null};
        Class<?> receiverClass = receiver.getClass();
        while (null != receiverClass) {
            if (tryCallMethod(receiverClass, receiver, methodName, result, parameterTypes, arguments)) {
                return result[0];
            }
            receiverClass = receiverClass.getSuperclass();
        }
        throw new IllegalArgumentException("method not found: " + methodName + " in object: " + receiver);
    }

    private static boolean tryCallMethod(Class<?> receiverClass, Object receiver, String methodName, Object[] result, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Method m = receiverClass.getDeclaredMethod(methodName, parameterTypes);
            m.setAccessible(true);
            try {
                result[0] = m.invoke(receiver, arguments);
            } catch (IllegalAccessException iacce) {
                throw new IllegalStateException("SHOULD NEVER HAPPEN");
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("SHOULD NEVER HAPPEN");
            } catch (InvocationTargetException e) {
                // ease-of-use (no checked exception declarations) are more
                // important than
                // ultimate clean-ness for us here
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                throw new RuntimeException(cause);
            }
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
