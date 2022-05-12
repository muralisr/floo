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
import java.nio.file.Paths;
import java.io.Reader;
import java.nio.file.Files;
import java.io.IOException;
import java.io.OutputStreamWriter;  
import java.io.FileOutputStream;  
import java.util.HashMap;
import com.google.gson.*;
import soot.jimple.internal.JInvokeStmt;

import java.util.Iterator;

import java.io.Serializable;
import java.lang.reflect.Type;

import dev.navids.soottutorial.android.AndroidLogger;
import dev.navids.soottutorial.android.AndroidLogger.Pair;
import fj.test.Bool;

import java.util.Collections;
public class PrintTimestampsWithoutHelper {
    

    private final static String USER_HOME = System.getProperty("user.home");
    private static String androidJar = USER_HOME + "/Library/Android/sdk/platforms";
    static String androidDemoPath = System.getProperty("user.dir") + File.separator + "demo" + File.separator + "Android";
    static String apkPath = "";
    static String jsonPath = "";
    static String outputPath = androidDemoPath + File.separator + "/Instrumented";
    static HashMap<String, Boolean> functionNameToWhetherInstrumented = new HashMap<String, Boolean>(); // fn_name -> whether instrumented
    PrintTimestampsWithoutHelper() {}
    
    public static void main(String[] args){

        if(System.getenv().containsKey("ANDROID_HOME"))
            androidJar = System.getenv("ANDROID_HOME")+ File.separator+"platforms";
        
        System.out.format("args size is %d\n", args.length);
        if (args.length < 2) {
            System.out.println("I need two arguments after PrintTimestampsWithoutHelper with the path to the apk file and path to the json file containing names of functions that were modified.\n./gradlew run --args=\"PrintTimestampsWithoutHelper path_to_apk_file path_to_json_file_with_list_of_modified_functions\"");
            return;
        }
        apkPath = args[0];
        jsonPath = args[1];
        // Initialize Soot
        InstrumentUtil.setupSoot(androidJar, apkPath, outputPath);
        
        // HashMap<String, HashMap<>> functionNameToRWDeps = new Hashmap<String, HashMap<String, List<String>>>(); 
        PackManager.v().getPack("jtp").add(new Transform("jtp.myLogger", new BodyTransformer() {
            @Override
            protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
                try {
                    if(InstrumentUtil.isAndroidMethod(b.getMethod())) {
                        return;
                    } 
                    System.out.format("Working on Function with signature: %s\n", b.getMethod().getSignature());

                    JimpleBody body = (JimpleBody) b;
                    List<Unit> generatedUnitsEnter = new ArrayList<>();
                    List<Unit> generatedUnitsExit = new ArrayList<>();
                    Local psLocal = InstrumentUtil.generateNewLocal(body, RefType.v("java.io.PrintStream"));
                    // Now we assign "System.out" to psLocal
                    SootField sysOutField = Scene.v().getField("<java.lang.System: java.io.PrintStream out>");
                    AssignStmt sysOutAssignStmt = Jimple.v().newAssignStmt(psLocal, Jimple.v().newStaticFieldRef(sysOutField.makeRef()));
                    generatedUnitsEnter.add(sysOutAssignStmt);
                    Local longLocal = InstrumentUtil.generateNewLocal(body, LongType.v());
                    SootMethod currentTimeNano = Scene.v().getMethod("<java.lang.System: long currentTimeMillis()>");
                    StaticInvokeExpr timeInvoke = Jimple.v().newStaticInvokeExpr(currentTimeNano.makeRef());
                    AssignStmt timeInitalize = Jimple.v().newAssignStmt(longLocal, timeInvoke);
                    SootMethod printlnMethod = Scene.v().grabMethod("<java.io.PrintStream: void println(java.lang.String)>");
                    InvokeStmt printlnMethodCallStmt = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(psLocal, printlnMethod.makeRef(), longLocal));
                    generatedUnitsEnter.add(timeInitalize);
                    generatedUnitsEnter.add(printlnMethodCallStmt);

                    UnitPatchingChain units = b.getUnits();
                    for (Iterator<Unit> iter = units.snapshotIterator(); iter.hasNext();) {
                        final Stmt stmt = (Stmt) iter.next();
                        if (stmt instanceof ReturnStmt || stmt instanceof ReturnVoidStmt) {
                            Local longLocal2 = InstrumentUtil.generateNewLocal(body, LongType.v());
                            SootMethod currentTimeNano2 = Scene.v().getMethod("<java.lang.System: long currentTimeMillis()>");
                            StaticInvokeExpr timeInvoke2 = Jimple.v().newStaticInvokeExpr(currentTimeNano2.makeRef());
                            AssignStmt timeInitalize2 = Jimple.v().newAssignStmt(longLocal2, timeInvoke2);
                            SootMethod printlnMethod2 = Scene.v().grabMethod("<java.io.PrintStream: void println(java.lang.String)>");
                            InvokeStmt printlnMethodCallStmt2 = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(psLocal, printlnMethod2.makeRef(), longLocal2));
                            generatedUnitsExit.add(timeInitalize2);
                            generatedUnitsExit.add(printlnMethodCallStmt2);
                            // if i use the InvokeStmt i created earlier
                            // it gives error saying i am adding the same stmt twice
                            // to a body (when the body contains multiple return/exit points)
                            units.insertBefore(generatedUnitsExit, stmt);
                        }
                    }
                    b.validate();  
                } catch (Exception e) {
                        System.out.println("MUR: Caught exception and skipped");
                }
            }
        }));
        // Run Soot packs (note that our transformer pack is added to the phase "jtp")
        PackManager.v().runPacks();
        // Write the result of packs in outputPath
        PackManager.v().writeOutput();    
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        String jsonReadWWriteDeps = gson.toJson(functionNameToWhetherInstrumented);

        try {
            OutputStreamWriter myWriter = new OutputStreamWriter(new FileOutputStream(jsonPath),
                    Charset.forName("UTF-8").newEncoder());
            myWriter.write(jsonReadWWriteDeps);
            myWriter.close();
            System.out.format("Successfully wrote list of instrumented functions to the file %s.\n", jsonPath);
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }    
    }


}