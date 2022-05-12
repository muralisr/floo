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

public class HelperCallInjector {
    // cmd line way to invoke this: ./gradlew run --args="HelperCallInjector file_path.apk"

    private final static String USER_HOME = System.getProperty("user.home");
    private static String androidJar = USER_HOME + "/Library/Android/sdk/platforms";
    static String androidDemoPath = System.getProperty("user.dir") + File.separator + "demo" + File.separator + "Android";
    static String apkPath = "";
    static String outputPath = androidDemoPath + File.separator + "/Instrumented";
    HelperCallInjector() {}
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
            System.out.println("I need one argument after HelperCallInjector with the path to the apk file.\n./gradlew run --args=\"HelperCallInjector path_to_apk_file\"");
            return;
        }
        apkPath = args[0];
        // Initialize Soot
        InstrumentUtil.setupSoot(androidJar, apkPath, outputPath);
        HelperCallInjector a = new HelperCallInjector();
        
        
        
        
        // HashMap<String, HashMap<>> functionNameToRWDeps = new Hashmap<String, HashMap<String, List<String>>>(); 
        PackManager.v().getPack("jtp").add(new Transform("jtp.myLogger", new BodyTransformer() {
            @Override
            protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
                // First we filter out Android framework methods
                if(InstrumentUtil.isAndroidMethod(b.getMethod()) || skipMethod(b.getMethod().getName())) {
                    return;
                } 
                String uniqueFunctionSignature = b.getMethod().getSignature();
                
                List<Value> paramRefs = b.getParameterRefs();
                
                JimpleBody body = (JimpleBody) b;
                List<Unit> generated = new ArrayList<Unit>();
                List<Value> parameter = new ArrayList<>();
                
                
                System.out.format("function signature is %s and num paramRefs is %d\n", uniqueFunctionSignature, paramRefs.size());


                List<String> heapReads = getHeapReadDependencies(uniqueFunctionSignature);
                List<String> heapWrites = getHeapWriteDependencies(uniqueFunctionSignature);
                // for myMethod, there are no heap reads. only a parameter, and then some heap writes
                // if memo table contains (this function name + param value)
                // do the heap writes and return "x" which is original return value
                // else do original code and add stuff to memo table
                

                Type Int = IntType.v();
                Local condition = Jimple.v().newLocal("i", Int);
                //Creating if condition
                IntConstant zero = IntConstant.v(0);
                EqExpr equalExpr = Jimple.v().newEqExpr(condition, zero);
                NopStmt nop = Jimple.v().newNopStmt();
                IfStmt ifStmt = Jimple.v().newIfStmt(equalExpr, nop);

                // replay function calls: this and non-this
                Iterator<Unit> iterator = body.getUnits().snapshotIterator();
                while(iterator.hasNext()){
                    Unit unit = iterator.next();
                    if(unit instanceof InvokeStmt) {
                        System.out.format("encountered unit: %s\n", unit);
                        JInvokeStmt jis = (JInvokeStmt)unit;
                        InvokeExpr iexpr = jis.getInvokeExpr();
                        if (iexpr instanceof InstanceInvokeExpr ) {
                            System.out.format(".. iExpr base is %s\n", ((InstanceInvokeExpr)(iexpr)).getBase());    
                            Value properParamObject = AndroidLogger.generateCorrectObject(b, ((InstanceInvokeExpr)(iexpr)).getBase(), generated); // if v is a primitive, make it an object. else, keep it an object
                            Local paramLocal = AndroidLogger.generateFreshLocal(body, RefType.v("java.lang.Object"));
                            Unit newAssignStmt = Jimple.v().newAssignStmt(paramLocal, properParamObject);
                            parameter.add(paramLocal);
                            generated.add(newAssignStmt);
                        } else {
                            System.out.format("not an InstanceInvokeExpr\n");
                        }
                        
                    } else {
                        System.out.format("encountered non-invoke unit %s\n", unit);
                    }
                }


               // get parameters
               int numOfParams = b.getMethod().getParameterCount();
               for(int i = 0; i < numOfParams; i++) {
                   Value v = body.getParameterLocal(i);
                   Value properParamObject = AndroidLogger.generateCorrectObject(b, v, generated); // if v is a primitive, make it an object. else, keep it an object
                   Local paramLocal = AndroidLogger.generateFreshLocal(body, RefType.v("java.lang.Object"));
                   Unit newAssignStmt = Jimple.v().newAssignStmt(paramLocal, properParamObject);
                   parameter.add(paramLocal);
                   generated.add(newAssignStmt);
               }

                parameter.add(StringConstant.v("soot is now calling accumulate helper."));
            
                Pair<Value, List<Unit>> arrayRefAndInstrumentation = AndroidLogger.generateParameterArray(parameter, body);
                
                List<Unit> generatedArrayInstrumentation = arrayRefAndInstrumentation.getSecond();
                Value arrayRef = arrayRefAndInstrumentation.getFirst();
                
                Unit generatedInvokeStmt = AndroidLogger.makeJimpleStaticCallForPathExecution("com.example.crossclasscalls.MainActivity", "accumulateHelper", 	
                AndroidLogger.getParameterArrayType(), (parameter.isEmpty())? NullConstant.v() : arrayRef);
                
                generated.addAll(generatedArrayInstrumentation);
                generated.add(generatedInvokeStmt);
                body.getUnits().insertBefore(generated, body.getFirstNonIdentityStmt());
            
                

                

                // heap reads: this and non-this
               // TODO
                

                b.validate();  

            }
        }));
        // Run Soot packs (note that our transformer pack is added to the phase "jtp")
        PackManager.v().runPacks();
        // Write the result of packs in outputPath
        PackManager.v().writeOutput();        
    }

    public static boolean skipMethod(String methodName) {

        // if (!methodName.contains("myMethod")) return true;

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

    public static List<String> getHeapReadDependencies(String methodName) {
        return new ArrayList<>();
    }
    public static List<String> getHeapWriteDependencies(String methodName) {
        return new ArrayList<>();
    }

}