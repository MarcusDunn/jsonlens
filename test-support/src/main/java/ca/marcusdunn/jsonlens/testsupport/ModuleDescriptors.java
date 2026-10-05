package ca.marcusdunn.jsonlens.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Reads the module descriptor and the public types of a library module.
 *
 * <p>Tests run on the class path, so {@link Class#getModule()} gives the unnamed module.
 * This class reads {@code module-info.class} files from the class path.
 */
public final class ModuleDescriptors {

    private ModuleDescriptors() {}

    /**
     * Finds the module descriptor with a given name on the class path.
     *
     * @param name the module name
     * @return the module descriptor
     */
    public static ModuleDescriptor named(String name) {
        try {
            for (URL url : Collections.list(
                    ModuleDescriptors.class.getClassLoader().getResources("module-info.class"))) {
                try (InputStream in = url.openStream()) {
                    ModuleDescriptor descriptor = ModuleDescriptor.read(in);
                    if (descriptor.name().equals(name)) {
                        return descriptor;
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        throw new IllegalStateException("module not on the class path: " + name);
    }

    /**
     * Returns the names of the modules that a module requires at runtime. This excludes
     * "mandated" ({@code java.base}) and "static" (compile-time only) requirements.
     *
     * @param descriptor the module descriptor
     * @return the names of the modules required at runtime
     */
    public static Set<String> runtimeModules(ModuleDescriptor descriptor) {
        Set<String> names = new TreeSet<>();
        for (ModuleDescriptor.Requires requires : descriptor.requires()) {
            Set<ModuleDescriptor.Requires.Modifier> modifiers = requires.modifiers();
            if (!modifiers.contains(ModuleDescriptor.Requires.Modifier.MANDATED)
                    && !modifiers.contains(ModuleDescriptor.Requires.Modifier.STATIC)) {
                names.add(requires.name());
            }
        }
        return names;
    }

    /**
     * Returns the names of the modules that a module requires only at compile time.
     *
     * @param descriptor the module descriptor
     * @return the names of the modules with a "static" requirement
     */
    public static Set<String> compileOnlyModules(ModuleDescriptor descriptor) {
        Set<String> names = new TreeSet<>();
        for (ModuleDescriptor.Requires requires : descriptor.requires()) {
            if (requires.modifiers().contains(ModuleDescriptor.Requires.Modifier.STATIC)) {
                names.add(requires.name());
            }
        }
        return names;
    }

    /**
     * Returns the exported packages of a module that do not have the JSpecify
     * {@code @NullMarked} annotation.
     *
     * @param descriptor the module descriptor
     * @return the names of the exported packages without {@code @NullMarked}
     */
    public static Set<String> exportedPackagesWithoutNullMarked(ModuleDescriptor descriptor) {
        Set<String> names = new TreeSet<>();
        for (ModuleDescriptor.Exports exports : descriptor.exports()) {
            String name = exports.source();
            try {
                Class<?> info = Class.forName(name + ".package-info");
                if (!info.isAnnotationPresent(org.jspecify.annotations.NullMarked.class)) {
                    names.add(name);
                }
            } catch (ClassNotFoundException e) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * Returns the binary names of the public types in the exported packages of a module.
     *
     * @param member a class of the module
     * @return the binary names of the public, exported types
     */
    public static Set<String> publicExportedTypes(Class<?> member) {
        Path location = location(member);
        if (Files.isDirectory(location)) {
            return publicExportedTypes(location, member.getClassLoader());
        }
        try (FileSystem jar = FileSystems.newFileSystem(location)) {
            return publicExportedTypes(jar.getPath("/"), member.getClassLoader());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Set<String> publicExportedTypes(Path root, ClassLoader loader) {
        Set<String> types = new TreeSet<>();
        try (InputStream in = Files.newInputStream(root.resolve("module-info.class"));
                Stream<Path> files = Files.walk(root)) {
            Set<String> exported = new TreeSet<>();
            ModuleDescriptor.read(in).exports().forEach(e -> exported.add(e.source()));
            files.map(f -> root.relativize(f).toString())
                    .filter(f -> f.endsWith(".class") && !f.endsWith("module-info.class")
                            && !f.endsWith("package-info.class"))
                    .map(f -> f.substring(0, f.length() - ".class".length()).replace('/', '.'))
                    .filter(name -> exported.contains(name.substring(0, name.lastIndexOf('.'))))
                    .filter(name -> isPublic(load(name, loader)))
                    .forEach(types::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return types;
    }

    private static boolean isPublic(Class<?> type) {
        for (Class<?> t = type; t != null; t = t.getEnclosingClass()) {
            if (!Modifier.isPublic(t.getModifiers())) {
                return false;
            }
        }
        return !type.isAnonymousClass() && !type.isSynthetic();
    }

    private static Class<?> load(String name, ClassLoader loader) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path location(Class<?> member) {
        try {
            return Path.of(member.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
