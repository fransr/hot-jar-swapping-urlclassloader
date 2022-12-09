package HelloWorld;

import java.nio.file.*;
import java.io.*;
import java.nio.file.attribute.*;

public class Secondary {
  public static void hello() {
    try {
      System.out.println("Hello from secondary class, here are all files in testdir/\n");
      Files.walkFileTree(Paths.get("testdir"), new SimpleFileVisitor<Path>() {
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            System.out.println(file);
            return FileVisitResult.CONTINUE;
          }

          public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            System.out.println("\nThis is from the legit postVisitDirectory function:");
            System.out.println(dir);
            return FileVisitResult.CONTINUE;
          }
        });
      System.out.println("\nEnd of run, goodbye");
    } catch(Exception e) {
      System.out.println("ERROR");
      System.out.println(e);
    }
  }
}
