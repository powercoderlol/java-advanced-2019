package ru.ifmo.rain.polyakov.implementor;

import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;

import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.tools.ToolProvider;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


import static java.nio.file.Files.getLastModifiedTime;
import static java.nio.file.Files.newInputStream;

public class Implementor implements JarImpler {
    /**
     * Type token for the implementation creation
     */
    private Class<?> token;

    /**
     * Path for the implementation
     */
    private Path filePath;

    /**
     * Implementation of the token
     */
    private StringBuilder fileImpl;

    /**
     * Primitives default
     */
    private final Map<String, String> primitives = new HashMap<>();

    /**
     * Initial Implementor method.
     * Validates user input, etc.
     *
     * @param token {@link Class} class type token.
     * @param root  {@link Path} root directory.
     * @throws ImplerException covers possibility of the realization generation for a specific token.
     */
    private void init(Class<?> token, Path root) throws ImplerException {
        if (token == Enum.class || token.isEnum() || token.isArray() || Modifier.isFinal(token.getModifiers())) {
            throw new ImplerException("Not implementable class!");
        }

        List<Constructor<?>> constructors = Arrays.stream(token.getDeclaredConstructors())
                .filter(c -> !Modifier.isPrivate(c.getModifiers()))
                .collect(Collectors.toList());

        if (constructors.isEmpty() && !token.isInterface()) {
            throw new ImplerException("No available default constructors!");
        }

        this.token = token;
        fileImpl = new StringBuilder();

        String filename = token.getName().replace(".", "/");
        filePath = Paths.get(root + "/" + filename + "Impl.java");

        primitives.put("int", "0");
        primitives.put("char", "0");
        primitives.put("byte", "0");
        primitives.put("long", "0L");
        primitives.put("short", "0");
        primitives.put("double", "0.0");
        primitives.put("float", "0.0f");
        primitives.put("boolean", "false");
        primitives.put("void", "");

        try {
            Files.createDirectories(filePath.getParent());
        } catch (IOException e) {
            System.out.println("IOException " + e.getMessage());
        }
    }

    /**
     * Generates set of methods for the implementation
     *
     * @return {@link Set} of methods to implement
     */
    private Set<Method> getMethodsToImplement() {
        Set<Method> methods = new TreeSet<>(DEFAULT_METHOD_COMPARATOR);

        Set<Method> implementedMethods = new TreeSet<>(DEFAULT_METHOD_COMPARATOR);

        appendOwnMethods(methods, implementedMethods);
        appendInterfaces(methods);
        appendSuperclassesMethods(methods, implementedMethods);

        methods.removeAll(implementedMethods);
        return methods;
    }

    /**
     * Generates set of methods to implement and set of methods which are already implemented in class itself
     * And fills it in corresponding input sets
     *
     * @param methods            {@link Set} of methods to implement.
     * @param implementedMethods {@link Set} of methods which are already implemented
     */
    private void appendOwnMethods(Set<Method> methods, Set<Method> implementedMethods) {
        runAndDistributeMethods(token.getDeclaredMethods(), methods, implementedMethods);
    }

    /**
     * Generates set of methods to implement and set of methods which are already implemented in superclasses of token
     * And fills it in corresponding input sets
     *
     * @param methods            {@link Set} of methods to implement.
     * @param implementedMethods {@link Set} of methods which are already implemented
     */
    private void appendSuperclassesMethods(Set<Method> methods, Set<Method> implementedMethods) {
        Class superClass = token.getSuperclass();
        while (superClass != null) {
            runAndDistributeMethods(superClass.getDeclaredMethods(), methods, implementedMethods);
            superClass = superClass.getSuperclass();
        }
    }

    /**
     * Validates list of not distributed methods and add every of it either in methods list, or in implemented
     * methods list. If method is not abstract, there is no need to add implementation for it, so add it in implemented
     * metods. Otherwise we have to implement declared method.
     *
     * @param methodList         Array of unknown {@link Method}
     * @param methods            list of methods to be implemented
     * @param implementedMethods list of already implemented methods
     */
    private void runAndDistributeMethods(Method[] methodList, Set<Method> methods, Set<Method> implementedMethods) {
        for (Method m : methodList) {
            if (Modifier.isAbstract(m.getModifiers())) {
                methods.add(m);
            } else {
                implementedMethods.add(m);
            }
        }
    }

    /**
     * Generates set of methods to implement in all interfaces of the token
     * And fills it in corresponding input sets
     *
     * @param methods {@link Set} of methods to implement.
     */
    private void appendInterfaces(Set<Method> methods) {
        LinkedList<Class> interfaces = new LinkedList<>(Arrays.asList(token.getInterfaces()));
        while (!interfaces.isEmpty()) {
            Class superInterface = interfaces.poll();
            interfaces.addAll(Arrays.asList(superInterface.getInterfaces()));
            // interface doesn't contain any implemented method, so add them all
            methods.addAll(Arrays.stream(superInterface.getDeclaredMethods())
                    .filter(m -> Modifier.isAbstract(m.getModifiers()))
                    .collect(Collectors.toList()));
        }
    }

    /**
     * Appends first line of class - package of this class
     */
    private void appendPackage() {
        fileImpl.append("package ")
                .append(token.getPackage().getName())
                .append(";\n");
    }

    /**
     * Appends implemented class declaration
     */
    private void appendClassDeclaration() {
        fileImpl.append("\npublic class ").append(token.getSimpleName()).append("Impl ")
                .append(token.isInterface() ? "implements " : "extends ")
                .append(token.getCanonicalName())
                .append(" {\n\n");
    }

    /**
     * Appends implemented class constructor inplementations
     */
    private void appendClassConstructors() {
        for (Constructor<?> constr : token.getDeclaredConstructors()) {
            fileImpl.append("\t").append(getAccess(constr.getModifiers()))
                    .append(" ").append(token.getSimpleName()).append("Impl");

            Class<?>[] parameterTypes = constr.getParameterTypes();
            fileImpl.append("(")
                    .append(String.join(", ", getInputParametersList(parameterTypes)))
                    .append(")");

            Class<?>[] exceptions = constr.getExceptionTypes();
            if (exceptions.length > 0) {
                fileImpl.append(" throws ")
                        .append(String.join(", ",
                                Arrays.stream(exceptions)
                                        .map(Class::getCanonicalName)
                                        .collect(Collectors.toList())));

            }

            fileImpl.append(" {\n\t\tsuper(")
                    .append(String.join(", ",
                            IntStream.range(0, parameterTypes.length)
                                    .mapToObj(i -> " argument" + i)
                                    .collect(Collectors.toList())))
                    .append(");\n\t};\n\n");
        }

    }

    /**
     * Appemds methods implementations for implemented class
     *
     * @param methods {@link Set} of methods to implement
     */
    private void appendClassMethods(Set<Method> methods) {
        for (Method method : methods) {
            if (!token.isInterface()) {
                fileImpl.append("\t@Override\n");
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            fileImpl.append("\t ").append(getAccess(method.getModifiers()))
                    .append(" ")
                    .append(method.getReturnType().getCanonicalName()).append(" ")
                    .append(method.getName()).append("(")
                    .append(String.join(", ", getInputParametersList(parameterTypes)))
                    .append(") {\n\t\treturn ")
                    .append(primitives.getOrDefault(method.getReturnType().getCanonicalName(), "null"))
                    .append(";\n\t}\n\n");
        }
    }

    /**
     * Appends class finish lines
     */
    private void appendClassEnding() {
        fileImpl.append("}\n");
    }

    /**
     * Generates access modifier from modifiers of {@link Class} or {@link Method}
     *
     * @param modifiers modifiers of {@link Class} or {@link Method}
     * @return {@link String} access modifier
     */
    private String getAccess(int modifiers) {
        return Modifier.toString(modifiers).split(" ")[0];
    }

    /**
     * Generates {@link List} of input parameters in pattern "argugment1, argument2"
     *
     * @param parameterTypes {@link Class}[] array of input parameter types
     * @return {@link List} of arguments
     */
    private List getInputParametersList(Class<?>[] parameterTypes) {
        return IntStream.range(0, parameterTypes.length)
                .mapToObj(i -> parameterTypes[i].getCanonicalName() + " argument" + i)
                .collect(Collectors.toList());
    }

    /**
     * Generates class or interface realization and writes this realization in java file
     *
     * @param token {@link Class} type token to create implementation for.
     * @param root  {@link Path} root directory.
     * @throws ImplerException if it is impossible to generate realization for type token.
     */
    @Override
    public void implement(Class<?> token, Path root) throws ImplerException {
        init(token, root);
        Set<Method> methods = getMethodsToImplement();

        appendPackage();
        appendClassDeclaration();
        appendClassConstructors();
        appendClassMethods(methods);
        appendClassEnding();

        try (FileWriter fWriter = new FileWriter(filePath.toString(), false);
             BufferedWriter out = new BufferedWriter(fWriter)) {
            out.write(fileImpl.toString());
        } catch (IOException e) {
            System.err.println("Error while writing in output file, " + e.getMessage());
        }
    }

    /**
     * Main method to run from console
     *
     * @param args param arguments
     */
    public static void main(String[] args) {
        try {
            if (args.length < 2) {
                throw new IllegalAccessException("Count of arguments must be equals to 2 or 3");
            }

            if (args.length == 2) {
                new Implementor().implement(Class.forName(args[0]), Paths.get(args[1]));
            } else {
                Class<?> clazz = Class.forName(args[0]);
                Path path = Paths.get(args[1]);
                Implementor impler = new Implementor();
                impler.implement(clazz, path);
                impler.implementJar(clazz, path);
            }

        } catch (ClassNotFoundException | ImplerException e) {
            System.out.println("Error while implementing, exit...");
        } catch (IllegalAccessException e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Generates jar file for generated .java file
     *
     * @param token   type token to create implementation for.
     * @param jarFile target <tt>.jar</tt> file.
     * @throws ImplerException on IO exceptions
     */
    @Override
    public void implementJar(Class<?> token, Path jarFile) throws ImplerException {
        // get path of the generated java file
        List<String> javaPathSplitted = Arrays.asList(jarFile.toString().split("\\."));
        javaPathSplitted = javaPathSplitted.subList(0, javaPathSplitted.size() - 1);

        String generatedClassesRootPath = String.join(File.separator, javaPathSplitted);
        Path javaPath = Paths.get(generatedClassesRootPath + "Impl.java");
        Path jarPath = Paths.get(String.join(File.separator, javaPathSplitted) + "Impl.class");

        // compile file to class
        ToolProvider.getSystemJavaCompiler().run(null, null, null, javaPath.toString());

        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(jarFile))) {
            generateJar(jarOutputStream, jarPath);
        } catch (IOException e) {
            throw new ImplerException("Error while writing in jar", e);
        }
    }

    /**
     * Generates jar file in specified path from compiled files
     *
     * @param jarOutputStream      {@link JarOutputStream} which contains creating jar
     * @param pathToGeneratedClass path for JAR
     * @throws IOException on IO issues
     */
    private void generateJar(JarOutputStream jarOutputStream, Path pathToGeneratedClass) throws IOException {
        Iterator<Path> iter = pathToGeneratedClass.iterator();
        String rootName = iter.next().getFileName().toString();
        String relativePath = "";

        while (iter.hasNext()) {
            relativePath += iter.next().getFileName();
            if (iter.hasNext()) {
                relativePath += "/";
            }

            addJarEntryToJarStream(relativePath, pathToGeneratedClass, jarOutputStream);

            if (!iter.hasNext()) {
                try (InputStream in = newInputStream(pathToGeneratedClass)) {
                    byte[] bytes = new byte[4096];
                    int count = in.read(bytes);
                    while (count != -1) {
                        jarOutputStream.write(bytes, 0, count);
                        count = in.read(bytes);
                    }
                } catch (IOException e) {
                    throw e;
                }
            }
        }

        jarOutputStream.close();
    }

    /**
     * Adds jar entry to current jarOutputStream
     *
     * @param entryPath       path of entry to be added
     * @param path            JAR path
     * @param jarOutputStream {@link JarOutputStream} used output stream
     * @throws IOException on IO exceptions
     */
    private void addJarEntryToJarStream(String entryPath, Path path, JarOutputStream jarOutputStream) throws IOException {
        JarEntry jarEntry = new JarEntry(entryPath);
        jarEntry.setLastModifiedTime(getLastModifiedTime(path));
        jarOutputStream.putNextEntry(jarEntry);
    }

    /**
     * Default comparator
     */
    private static Comparator<Method> DEFAULT_METHOD_COMPARATOR = Comparator
            .comparing(m -> m.getName() + Arrays.stream(m.getParameterTypes())
                    .map(Class::getSimpleName)
                    .collect(Collectors.joining("")));

}