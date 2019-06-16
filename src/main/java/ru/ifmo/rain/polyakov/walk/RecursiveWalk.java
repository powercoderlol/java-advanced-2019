package ru.ifmo.rain.polyakov.walk;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class RecursiveWalk {
    private static int CHUNK_ = 4096;
    private static int FNV_32_INIT = 0x811c9dc5;
    private static int FNV_32_PRIME = 0x01000193;


    public static void main(String[] args) {
        if (args == null || args.length != 2 || args[0] == null || args[1] == null) {
            System.err.println("Corrupted arguments");
            return;
        }
        File inputFile = new File(args[0]);
        File outputFile = new File(args[1]);
        RecursiveWalk walk = new RecursiveWalk();
        String line="";
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), StandardCharsets.UTF_8))) {
                while ((line = reader.readLine()) != null) {
                    walk.processFiles(Paths.get(line), writer);
                }
            } catch (InvalidPathException | IOException e) {
                writer.write(String.format("%08x %s\n", 0, line));
            }
        } catch (IOException | InvalidPathException exe) {
            System.err.println("doopsie");
        }
    }

    private RecursiveWalk() {
    }

    private void processFiles(Path root, BufferedWriter writer) {
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    writer.write(String.format("%08x %s\n", calculateChecksum(file), file));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) throws IOException {
                    writer.write(String.format("%08x %s\n", 0, file));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int calculateChecksum(Path path) {
        try (FileInputStream is = new FileInputStream(path.toFile())) {
            byte[] chunk = new byte[CHUNK_];
            int chunkLen;
            int hv = FNV_32_INIT;
            while ((chunkLen = is.read(chunk)) != -1) {
                for (int i = 0; i < chunkLen; i++) {
                    hv = (hv * FNV_32_PRIME) ^ (chunk[i] & 0xff);
                }
            }
            return hv;
        } catch (IOException | InvalidPathException e) {
            System.err.println("IO exception");
            return 0;
        }
    }
}
