package Bootstrapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Scanner;
import java.net.URLClassLoader;

/**
 * @author ashraf
 *
 */
public class Main {

    /**
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        URL[] classLoaderUrls = new URL[]{new URL("file://" + System.getProperty("user.dir") + "/HelloWorld.jar")};
        URLClassLoader urlClassLoader = new URLClassLoader(classLoaderUrls);
        Class<?> beanClass = urlClassLoader.loadClass("HelloWorld.Main");
        Constructor<?> constructor = beanClass.getConstructor();
        Object beanObj = constructor.newInstance();
        Method method = beanClass.getMethod("hello");
        method.invoke(beanObj);

        // Initiating the secondary class on boot, this is the one we replace the inner class of
        Class<?> secondaryClass = urlClassLoader.loadClass("HelloWorld.Secondary");
        Constructor<?> secondaryConstructor = secondaryClass.getConstructor();
        Object secondaryObj = secondaryConstructor.newInstance();
        Method secondaryMethod = secondaryClass.getMethod("hello");

        Scanner scanner = new Scanner(System.in);
        System.out.print("Click enter when you want to trigger the secondary class method\n(run ./build-exploit.sh to replace JAR)\n");
        scanner.nextLine();
        // Invoke secondary class hello that contains an inner class
        secondaryMethod.invoke(secondaryObj);
    }

}
