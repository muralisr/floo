package dev.navids.soottutorial.android;

import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JInvokeStmt;


import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.concurrent.*;
import java.util.*;  


public class NondeterminismLogger {
    private final static String USER_HOME = System.getProperty("user.home");
    private static String androidJar = USER_HOME + "/Library/Android/sdk/platforms";
    static String androidDemoPath = System.getProperty("user.dir") + File.separator + "demo" + File.separator + "Android";
    // static String apkPath = androidDemoPath + File.separator + "/helloworld.apk";
    static String outputPath = androidDemoPath + File.separator + "/Instrumented";
    // TODO ConcurrentHashMap...
    static ConcurrentHashMap<String, Boolean> isFunctionNondeterministic = new ConcurrentHashMap<String, Boolean>();
    static HashSet<String> nondeterministicClasses = new HashSet<String>(){{
        add("java.util.Random");
        add("java.net.URL");
        add("java.security.SecureRandom");
        add("java.util.Date");
        add("java.time.LocalDate");
        add("java.time.LocalTime");
        add("java.time.LocalDateTime");
        add("java.time.Clock");
        add("java.time.Month");
        add("java.time.Duration");
        add("java.time.Instant");
        add("java.time.MonthDay");
        add("java.time.OffsetDateTime");
        add("java.time.OffsetTime");
        add("java.time.Period");
        add("java.time.Year");
        add("java.time.YearMonth");
        add("java.time.ZonedDateTime");
        add("java.time.ZonedId");
        add("java.time.ZonedOffset");
        add("java.time.DayOfWeek");
        add("java.io.File");
        add("java.nio.file.Files");
        add("java.nio.file.Paths");
        add("java.net.URL");
        add("java.net.URLConnection");
        add("java.net.NetworkInterface");
        add("java.net.Authenticator");
        add("java.net.CacheRequest");
        add("java.net.CacheResponse");
        add("java.net.ContentHandler");
        add("java.net.CookieHandler");
        add("java.net.CookieManager");
        add("java.net.DatagramPacket");
        add("java.net.DatagramSocket");
        add("java.net.HttpCookie");

    }};


    public static void printAllUnits(Body body) {
        System.out.println("***Printing all units***");
        Iterator<Unit> iterator = body.getUnits().snapshotIterator();
        while(iterator.hasNext()){
            Unit unit = iterator.next();
            System.out.println(unit);
        }
    }

    public static boolean containNondeterministicUnit(Body body) {
        Iterator<Unit> iterator = body.getUnits().snapshotIterator();
        while(iterator.hasNext()){
            Unit unit = iterator.next();
            // 1. Check if invoke statement
            if(unit instanceof InvokeStmt) {
                JInvokeStmt jis = (JInvokeStmt)unit;
                // 2. If invoke statement, getInvokeExpr()
                InvokeExpr iexpr = jis.getInvokeExpr();
                // 3. Get SootMethod getMethod()
                SootMethod sootMethod = iexpr.getMethod();
                // 4. Get SootClass getDeclaringClass()
                SootClass sootClass = sootMethod.getDeclaringClass(); 
                if (nondeterministicClasses.contains(sootClass.toString())) {
                    System.out.format("#####soot class is %s\n", sootClass);
                    return true;
                }
            }
        }
        return false;
    }

    // Add to HashMap - function signature + T/F if nondeterministic
    public static void main(String[] args){
        System.out.println("NondeterminismLogger");
        if (System.getenv().containsKey("ANDROID_HOME"))
            androidJar = System.getenv("ANDROID_HOME")+ File.separator+"platforms";
        // Clean the outputPath
        final File[] files = (new File(outputPath)).listFiles();
        if (files != null && files.length > 0) {
            Arrays.asList(files).forEach(File::delete);
        }
        // Initialize Soot
        InstrumentUtil.setupSoot(androidJar, args[0], outputPath);
        // Add a transformation pack in order to add the statement "System.out.println(<content>) at the beginning of each Application method
        PackManager.v().getPack("jtp").add(new Transform("jtp.myLogger", new BodyTransformer() {
            @Override
            protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
                // First we filter out Android framework methods
                if (InstrumentUtil.isAndroidMethod(b.getMethod()))
                    return;
                // 1. Get FunctionSignature
                String uniqueFunctionSignature = b.getMethod().getSignature();
                JimpleBody body = (JimpleBody) b;
                boolean eval = containNondeterministicUnit(body);
                if (eval) {
                    System.out.println("adding to concurrent hash map");
                    isFunctionNondeterministic.put(uniqueFunctionSignature, true);
                }
                b.validate();
            }
        }));
        // Run Soot packs (note that our transformer pack is added to the phase "jtp")
        PackManager.v().runPacks();
        // Write the result of packs in outputPath
        PackManager.v().writeOutput();
    }

}
