package org.unikitty.testing;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * This helper class can be used to unit test the get/set methods of JavaBean-style Value Objects.
 */
public abstract class JavaBeanGetterSetterIntrospector {

    /**
     * Tests the get/set methods of the specified class.
     *
     * @param <T> the type parameter associated with the class under test
     * @param clazz the Class under test
     * @param skipThese the names of any properties that should not be tested
     * @throws IntrospectionException thrown if the Introspector.getBeanInfo() method throws this exception for the class under test
     */
    public <T> void testGettersAndSetters(final Class<T> clazz, final String... skipThese) throws IntrospectionException {
        final PropertyDescriptor[] props = Introspector.getBeanInfo(clazz).getPropertyDescriptors();
        nextProp: for (final PropertyDescriptor prop : props) {
            // Check the list of properties that we don't want to test
            for (final String skipThis : skipThese) {
                if (skipThis.equals(prop.getName())) {
                    continue nextProp;
                }
            }
            findBooleanIsMethods(clazz, prop);
            final Method getter = prop.getReadMethod();
            final Method setter = prop.getWriteMethod();

            if (getter != null && setter != null) {
                // We have both a get and set method for this property
                final Class<?> returnType = getter.getReturnType();
                final Class<?>[] params = setter.getParameterTypes();

                if (params.length == 1 && params[0] == returnType) {
                    // The set method has 1 argument, which is of the same type as the return type of the get method, so we can test this property
                    try {
                        // Build a value of the correct type to be passed to the set method
                        final Object value = buildValue(returnType);

                        // Build an instance of the bean that we are testing (each property test gets a new instance)
                        final T bean = clazz.newInstance();

                        // Call the set method, then check the same value comes back out of the get method
                        setter.invoke(bean, value);

                        final Object expectedValue = value;
                        final Object actualValue = getter.invoke(bean);

                        assertEqualsCustom(String.format("Failed while testing property %s", prop.getName()), expectedValue, actualValue);
                    } catch (final Exception ex) {
                        failCustom(String.format("An exception was thrown while testing the property %s: %s", prop.getName(), ex.toString()));
                    }
                }
            }
        }
    }

    private Object buildValue(final Class<?> clazz) throws InstantiationException, IllegalAccessException, InvocationTargetException {

        // If we are using a Mocking framework try that first...
        final Object mockedObject = buildMockValue(clazz);
        if (mockedObject != null) {
            return mockedObject;
        }

        // Specific rules for common classes
        if (clazz == String.class) {
            return getTestStringValue();
        } else if (clazz.isArray()) {
            return getTestArrayValue(clazz);
        } else if (clazz == boolean.class || clazz == Boolean.class) {
            return getTestBooleanValue();
        } else if (clazz == int.class || clazz == Integer.class) {
            return getTestIntValue();
        } else if (clazz == long.class || clazz == Long.class) {
            return getTestLongValue();
        } else if (clazz == double.class || clazz == Double.class) {
            return getTestDoubleValue();
        } else if (clazz == float.class || clazz == Float.class) {
            return getTestFloatValue();
        } else if (clazz == char.class || clazz == Character.class) {
            return getTestCharValue();
        } else if (clazz.isEnum()) {
            return getTestEnumValue(clazz);
        }

        // Next check for a no-arg constructor
        Object returnTestValue = null;
        returnTestValue = getNonStandardTestValue(clazz);

        if (returnTestValue == null) {
            failCustom("Unable to build an instance of class " + clazz.getName() + ", please add some code to the " + JavaBeanGetterSetterIntrospector.class.getName() + " class to do this.");
        }

        return null; // for the compiler
    }

    /**
     * Call assertEquals() equivalent of whatever testing framework you are using. This method is abstract in order to avoid dependency on junit in src/main/java
     * @param message
     * @param expectedValue
     * @param actualValue
     */
    protected abstract void assertEqualsCustom(final String message, final Object expectedValue, final Object actualValue);

    /**
     * Call the fail() equivalent of whatever testing framework you are using. This method is abstract in order to avoid dependency on junit in src/main/java
     * @param message
     */
    protected abstract void failCustom(final String message);

    protected Object getNonStandardTestValue(final Class<?> clazz) throws InstantiationException, IllegalAccessException, InvocationTargetException {
        final Constructor<?>[] ctrs = clazz.getConstructors();
        for (final Constructor<?> ctr : ctrs) {
            if (ctr.getParameterTypes().length == 0) {
                // The class has a no-arg constructor, so just call it
                return ctr.newInstance();
            }
        }
        return null;
    }

    protected Object getTestEnumValue(final Class<?> clazz) {
        return clazz.getEnumConstants()[0];
    }

    protected Object getTestCharValue() {
        return 'Y';
    }

    protected Object getTestFloatValue() {
        return 1.0F;
    }

    protected Object getTestDoubleValue() {
        return 1.0D;
    }

    protected Object getTestLongValue() {
        return 1L;
    }

    protected Object getTestIntValue() {
        return 1;
    }

    protected Object getTestBooleanValue() {
        return true;
    }

    protected Object getTestArrayValue(final Class<?> clazz) {
        return Array.newInstance(clazz.getComponentType(), 1);
    }

    protected Object getTestStringValue() {
        return "test string";
    }

    protected Object buildMockValue(final Class<?> clazz) {
        if (!Modifier.isFinal(clazz.getModifiers())) {
            // Insert a call to your favourite mocking framework here
            return null;
        } else {
            return null;
        }
    }

    /**
     * Hunt down missing Boolean read method if needed as Introspector cannot find 'is' getters for Boolean type properties.
     *
     * @param clazz the type being introspected
     * @param descriptor the property descriptor found so far
     */
    public static <T> void findBooleanIsMethods(final Class<T> clazz, final PropertyDescriptor descriptor) throws IntrospectionException {
        if (needToFindReadMethod(descriptor)) {
            findTheReadMethod(descriptor, clazz);
        }
    }

    private static boolean needToFindReadMethod(final PropertyDescriptor property) {
        return property.getReadMethod() == null && property.getPropertyType() == Boolean.class;
    }

    private static <T> void findTheReadMethod(final PropertyDescriptor descriptor, final Class<T> clazz) throws IntrospectionException {
        final PropertyDescriptor pd = new PropertyDescriptor(descriptor.getName(), clazz);
        descriptor.setReadMethod(pd.getReadMethod());
    }
}

