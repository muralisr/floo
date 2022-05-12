package dev.navids.soottutorial.android;


import soot.*;
import soot.jimple.*;
import soot.PatchingChain;
import soot.javaToJimple.LocalGenerator;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import soot.jimple.internal.JInvokeStmt;
import java.util.Iterator;

import java.io.Serializable;

public class MemoFnWrites {

    private final static String USER_HOME = System.getProperty("user.home");
    private static String androidJar = USER_HOME + "/Library/Android/sdk/platforms";
    static String androidDemoPath = System.getProperty("user.dir") + File.separator + "demo" + File.separator + "Android";
    static String apkPath = androidDemoPath + File.separator + "/simple_func_write.apk";
    static String outputPath = androidDemoPath + File.separator + "/Instrumented";
    MemoFnWrites() {}
    public static void main(String[] args){

        if(System.getenv().containsKey("ANDROID_HOME"))
            androidJar = System.getenv("ANDROID_HOME")+ File.separator+"platforms";
        // Clean the outputPath
        final File[] files = (new File(outputPath)).listFiles();
        if (files != null && files.length > 0) {
            Arrays.asList(files).forEach(File::delete);
        }
        // Initialize Soot
        InstrumentUtil.setupSoot(androidJar, apkPath, outputPath);
        MemoFnWrites a = new MemoFnWrites();
        // Add a transformation pack in order to add the statement "System.out.println(<content>) at the beginning of each Application method
        PackManager.v().getPack("jtp").add(new Transform("jtp.myLogger", new BodyTransformer() {
            @Override
            protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
                // First we filter out Android framework methods
                if(InstrumentUtil.isAndroidMethod(b.getMethod()) || b.getMethod().getName().equals("printTimer")) {
                    return;
                } else if (! b.getMethod().getName().equals("functionToMemo")) {
                    // we only want to instrument this one specific function
                    return;
                }
                System.out.format("function signature is %s\n", b.getMethod().getSignature());
                
                
                JimpleBody body = (JimpleBody) b;
                Iterator<Unit> iterator = body.getUnits().snapshotIterator();
                
                
                while(iterator.hasNext()){
                    Unit unit = iterator.next(); 
                    System.out.format("* unit: %s\n", unit);
                    if (unit instanceof JInvokeStmt) {
                        JInvokeStmt jis = (JInvokeStmt)unit;
                        InvokeExpr ie = jis.getInvokeExpr();
                        System.out.format("class name is %s.\n", ie.getMethod().getDeclaringClass().toString());
                        System.out.format("function name is %s.\n", ie.getMethod());
                        List<ValueBox> useBoxes = jis.getUseBoxes();
                        for(ValueBox v : useBoxes) {
                            System.out.format("use box: %s\n", v);
                        }
                    }
                }	
                
                // Validate the body to ensure that our code injection does not introduce any problem (at least statically)
                b.validate();  

            }
        }));
        // Run Soot packs (note that our transformer pack is added to the phase "jtp")
        PackManager.v().runPacks();
        // Write the result of packs in outputPath
        PackManager.v().writeOutput();
    }

   
}