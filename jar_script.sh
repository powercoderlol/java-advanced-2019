mkdir build

javac -cp ./lib/info.kgeorgiy.java.advanced.implementor.jar -d ./build src/main/java/ru/ifmo/rain/polyakov/implementor/Implementor.java

cd build

jar xf ../lib/info.kgeorgiy.java.advanced.implementor.jar \
        info/kgeorgiy/java/advanced/implementor/Impler.class \
        info/kgeorgiy/java/advanced/implementor/JarImpler.class \
        info/kgeorgiy/java/advanced/implementor/ImplerException.class

mkdir ../jar

jar cfm ../jar/Implementor.jar ../src/main/java/ru/ifmo/rain/polyakov/implementor/manifest \
     ru/ifmo/rain/polyakov/implementor/*.class \
     info/kgeorgiy/java/advanced/implementor/*.class