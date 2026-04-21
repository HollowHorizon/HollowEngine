package ru.hollowhorizon.hollowengine.bootstrap.impl;


import ru.hollowhorizon.hollowengine.bootstrap.impl.transform.RuntimeClassTransformer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

public class ChildFirstUrlClassLoader extends URLClassLoader {
    private final Set<String> parentFirstPackages;
    private final List<RuntimeClassTransformer> transformers;

    public ChildFirstUrlClassLoader(URL[] urls, ClassLoader parent, Set<String> parentFirstPackages, List<RuntimeClassTransformer> transformers) {
        super(urls, parent);
        this.parentFirstPackages = parentFirstPackages;
        this.transformers = transformers;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded != null) {
                if (resolve) resolveClass(loaded);
                return loaded;
            }

            for (String prefix : parentFirstPackages) {
                if (name.startsWith(prefix)) {
                    return super.loadClass(name, resolve);
                }
            }

            try {
                Class<?> childClass = findClass(name);
                if (resolve) resolveClass(childClass);
                return childClass;
            } catch (ClassNotFoundException ignored) {
                return super.loadClass(name, resolve);
            }
        }
    }

    @Override
    public URL getResource(String name) {
        URL child = findResource(name);
        return child != null ? child : super.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        LinkedHashSet<URL> resources = new LinkedHashSet<>();
        add(resources, findResources(name));
        add(resources, super.getResources(name));
        return Collections.enumeration(resources);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String path = name.replace('.', '/') + ".class";
        URL resource = findResource(path);
        if (resource == null) {
            throw new ClassNotFoundException(name);
        }

        try (InputStream input = resource.openStream()) {
            byte[] bytecode = input.readAllBytes();
            for (RuntimeClassTransformer transformer : transformers) {
                if (transformer.supports(name)) {
                    bytecode = transformer.transform(name, bytecode);
                    break;
                }
            }

            int packageSeparator = name.lastIndexOf('.');
            if (packageSeparator > 0) {
                String packageName = name.substring(0, packageSeparator);
                if (getDefinedPackage(packageName) == null) {
                    definePackage(packageName, null, null, null, null, null, null, null);
                }
            }

            return defineClass(name, bytecode, 0, bytecode.length);
        } catch (IOException exception) {
            throw new ClassNotFoundException(name, exception);
        }
    }

    private static void add(Set<URL> target, Enumeration<URL> values) {
        while (values.hasMoreElements()) {
            target.add(values.nextElement());
        }
    }
}
