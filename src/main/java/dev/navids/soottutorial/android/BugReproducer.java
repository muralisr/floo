package dev.navids.soottutorial.android;


import soot.*;
import soot.jimple.*;
import soot.PatchingChain;
import soot.util.Chain;
import soot.javaToJimple.LocalGenerator;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import java.nio.charset.Charset;
import java.io.IOException;
import java.io.OutputStreamWriter;  
import java.io.FileOutputStream;  
import java.util.HashMap;
import com.google.gson.*;
import soot.jimple.internal.JInvokeStmt;

import java.util.Iterator;

import java.io.Serializable;

import dev.navids.soottutorial.android.AndroidLogger;
import dev.navids.soottutorial.android.AndroidLogger.Pair;

public class BugReproducer {
    // cmd line way to invoke this: ./gradlew run --args="BugReproducer file_path.apk"

    private final static String USER_HOME = System.getProperty("user.home");
    private static String androidJar = USER_HOME + "/Library/Android/sdk/platforms";
    static String androidDemoPath = System.getProperty("user.dir") + File.separator + "demo" + File.separator + "Android";
    static String apkPath = "";
    static String outputPath = androidDemoPath + File.separator + "/Instrumented";
    BugReproducer() {}
    public static void main(String[] args){

        if(System.getenv().containsKey("ANDROID_HOME"))
            androidJar = System.getenv("ANDROID_HOME")+ File.separator+"platforms";
        // Clean the outputPath
        final File[] files = (new File(outputPath)).listFiles();
        if (files != null && files.length > 0) {
            Arrays.asList(files).forEach(File::delete);
        }
        System.out.format("args size is %d\n", args.length);
        if (args.length < 1) {
            System.out.println("I need one argument after BugReproducer with the path to the apk file.\n./gradlew run --args=\"BugReproducer path_to_apk_file\"");
            return;
        }
        apkPath = args[0];
        // Initialize Soot
        InstrumentUtil.setupSoot(androidJar, apkPath, outputPath);
        BugReproducer a = new BugReproducer();
        
        
        
        
        // HashMap<String, HashMap<>> functionNameToRWDeps = new Hashmap<String, HashMap<String, List<String>>>(); 
        PackManager.v().getPack("jtp").add(new Transform("jtp.myLogger", new BodyTransformer() {
            @Override
            protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
                // First we filter out Android framework methods
                if (b.getMethod().getSignature().contains("simpleapptomemoize"))
                    System.out.format("checking if i should optimize %s\n", b.getMethod().getSignature());
                if(InstrumentUtil.isAndroidMethod(b.getMethod()) || skipMethod(b.getMethod().getName())) {
                    return;
                } 

                JimpleBody body = (JimpleBody) b;
                List<Value> paramRefs = b.getParameterRefs();
                String uniqueFunctionSignature = b.getMethod().getSignature();
                System.out.format("function signature is %s and num paramRefs is %d\n", uniqueFunctionSignature, paramRefs.size());

                List<Unit> generatedForBeginningOfFunction = new ArrayList<Unit>();
                List<Unit> generatedForEndOfFunction = new ArrayList<Unit>();


                printAllUnits(body);

                NopStmt nop = Jimple.v().newNopStmt();
                ReturnVoidStmt returnStmt = Jimple.v().newReturnVoidStmt(); // return;
                generatedForEndOfFunction.add(returnStmt);
                generatedForEndOfFunction.add(nop);
                

                Local conditionVariable = InstrumentUtil.generateNewLocal(body, IntType.v()); // int i
                
                AssignStmt assignConditionToZero = Jimple.v().newAssignStmt(conditionVariable, IntConstant.v(0));
                generatedForBeginningOfFunction.add(assignConditionToZero);


                EqExpr equalExpr = Jimple.v().newEqExpr(conditionVariable, IntConstant.v(0)); // i == 0
                IfStmt ifStmt = Jimple.v().newIfStmt(equalExpr, nop); // if i == 0, then do nop
                generatedForBeginningOfFunction.add(ifStmt);
               
                body.getUnits().insertBefore(generatedForBeginningOfFunction, body.getFirstNonIdentityStmt());
                for (int i = generatedForEndOfFunction.size() - 1; i >= 0; i--) {
                    body.getUnits().addLast(generatedForEndOfFunction.get(i));
                }
                
             

                System.out.println("*************");

                printAllUnits(body);


                b.validate();  

            }
        }));
        // Run Soot packs (note that our transformer pack is added to the phase "jtp")
        PackManager.v().runPacks();
        // Write the result of packs in outputPath
        PackManager.v().writeOutput();        
    }

    public static boolean skipMethod(String methodName) {
        
        if (!methodName.contains("myFunction")) return true;

        List<String> exactMatchesToSkip = new ArrayList<>();
        List<String> partialMatchesToSkip = new ArrayList<>();

        exactMatchesToSkip.add("accumulateHelper");
        exactMatchesToSkip.add("isPrimitive");
        
        partialMatchesToSkip.add("onCreate");
        


        for(String s : exactMatchesToSkip) {
            if (methodName.equalsIgnoreCase(s)) {
                return true;
            }
        }
        for(String s : partialMatchesToSkip) {
            if (methodName.contains(s)) {
                return true;
            }
        }
        return false; 
    }

    public static void printAllUnits(Body body) {
        Iterator<Unit> iterator = body.getUnits().snapshotIterator();
        while(iterator.hasNext()){
            Unit unit = iterator.next();
            System.out.println(unit);
        }
    }
}