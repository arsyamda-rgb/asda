package com.waystones;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/** Helper reflection kecil untuk akses kelas NMS (nama Mojang mappings, Paper 1.21.1). */
final class Nms {
    private static final Map<String, Class<?>> CLASSES = new HashMap<>();
    private static final Map<Class<?>, Class<?>> WRAP = Map.of(
            boolean.class, Boolean.class, byte.class, Byte.class, short.class, Short.class,
            int.class, Integer.class, long.class, Long.class, float.class, Float.class,
            double.class, Double.class, char.class, Character.class);

    private Nms() {
    }

    static Class<?> cls(String name) throws ClassNotFoundException {
        Class<?> c = CLASSES.get(name);
        if (c == null) {
            c = Class.forName(name, true, Nms.class.getClassLoader());
            CLASSES.put(name, c);
        }
        return c;
    }

    private static boolean accepts(Class<?> p, Object a) {
        if (a == null) return !p.isPrimitive();
        return (p.isPrimitive() ? WRAP.get(p) : p).isInstance(a);
    }

    private static boolean matches(Class<?>[] params, Object[] args) {
        if (params.length != args.length) return false;
        for (int i = 0; i < params.length; i++) if (!accepts(params[i], args[i])) return false;
        return true;
    }

    private static Method findMethod(Class<?> start, String name, Object[] args) throws NoSuchMethodException {
        for (Class<?> c = start; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.isBridge() && m.getName().equals(name) && matches(m.getParameterTypes(), args)) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        for (Method m : start.getMethods()) {
            if (!m.isBridge() && m.getName().equals(name) && matches(m.getParameterTypes(), args)) {
                m.setAccessible(true);
                return m;
            }
        }
        throw new NoSuchMethodException(start.getName() + "#" + name + "/" + args.length);
    }

    static Object invoke(Object target, String name, Object... args) throws ReflectiveOperationException {
        return findMethod(target.getClass(), name, args).invoke(target, args);
    }

    static Object invokeStatic(Class<?> c, String name, Object... args) throws ReflectiveOperationException {
        return findMethod(c, name, args).invoke(null, args);
    }

    static Object construct(Class<?> c, Object... args) throws ReflectiveOperationException {
        for (Constructor<?> k : c.getDeclaredConstructors()) {
            if (matches(k.getParameterTypes(), args)) {
                k.setAccessible(true);
                return k.newInstance(args);
            }
        }
        throw new NoSuchMethodException(c.getName() + " <init>/" + args.length);
    }

    static Object constructExact(Class<?> c, Class<?>[] types, Object... args) throws ReflectiveOperationException {
        Constructor<?> k = c.getDeclaredConstructor(types);
        k.setAccessible(true);
        return k.newInstance(args);
    }

    private static Field findField(Class<?> start, String name) throws NoSuchFieldException {
        for (Class<?> c = start; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(start.getName() + "." + name);
    }

    static Object staticField(Class<?> c, String name) throws ReflectiveOperationException {
        return findField(c, name).get(null);
    }

    static Object field(Object o, String name) throws ReflectiveOperationException {
        return findField(o.getClass(), name).get(o);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object enumConst(String className, String constant) throws ReflectiveOperationException {
        return Enum.valueOf((Class<Enum>) cls(className), constant);
    }
}
