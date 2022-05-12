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

import java.nio.charset.Charset;
import java.io.IOException;
import java.io.OutputStreamWriter;  
import java.io.FileOutputStream;  
import java.util.HashMap;
import com.google.gson.*;

import java.util.Iterator;

import java.io.Serializable;

public class Statistics {
    // cmd line way to invoke this: ./gradlew run --args="Statistics /disk/ubuntu_data/projects/SootTutorial/demo/HeapRW/stats.json"
    // prints stats about apk
    /**
    *  number of params (functiontions wwith no params)
    functions with no return value
    number of heap reads
    number of heap writes
    number of non-this heap access
    number of functions called wwithin a function
    
    */
    
    private final static String USER_HOME = System.getProperty("user.home");
    private static String androidJar = USER_HOME + "/Library/Android/sdk/platforms";
    static String androidDemoPath = System.getProperty("user.dir") + File.separator + "demo" + File.separator + "Android";
    static String apkPath = "";
    static String outputPath = androidDemoPath + File.separator + "/Instrumented";
    static int numberOfSkippedFunctionDueToException = 0;
    Statistics() {}
    public static void main(String[] args){
        
        if(System.getenv().containsKey("ANDROID_HOME"))
        androidJar = System.getenv("ANDROID_HOME")+ File.separator+"platforms";
        // Clean the outputPath
        final File[] files = (new File(outputPath)).listFiles();
        if (files != null && files.length > 0) {
            Arrays.asList(files).forEach(File::delete);
        }
        System.out.format("args size is %d\n", args.length);
        if (args.length < 2) {
            System.out.println("I need one argument after Statistics with the path to the file to create with stats info and one with apk name.\n./gradlew run --args=\"Statistics path_to_json_file path_to_apk\"");
            return;
        }
        String outputFileToWriteDepsTo = args[0];
        apkPath = args[1];
        System.out.format("I am going to write json output to %s\n", outputFileToWriteDepsTo);
        
        // Initialize Soot
        InstrumentUtil.setupSoot(androidJar, apkPath, outputPath);
        Statistics a = new Statistics();
        
        HashMap<String, List<Integer>> statsToCount = new HashMap<>(); // key is stats attribute. value is list of integers, with each item in list representing one function. 
        HashMap<String, HashMap<String, List<String>>> functionNameToRWDeps = new HashMap<String, HashMap<String, List<String>>>();  // fn_name -> r/w -> list of vars
        
        statsToCount.put("num this fn invokes", new ArrayList<>()); // list of ints
        statsToCount.put("num non-this fn invokes", new ArrayList<>()); // list of ints
        statsToCount.put("num heap reads", new ArrayList<>()); // list of ints
        statsToCount.put("num heap writes", new ArrayList<>()); // list of ints
        statsToCount.put("num non-this heap reads", new ArrayList<>()); // list of ints
        statsToCount.put("num non-this heap writes", new ArrayList<>()); // list of ints
        statsToCount.put("num params", new ArrayList<>()); // list of ints
        statsToCount.put("has return value", new ArrayList<>()); // list of ints. 0 for no return type, 1 for has a return value
        
        
        PackManager.v().getPack("jtp").add(new Transform("jtp.myLogger", new BodyTransformer() {
            @Override
            protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
                // First we filter out Android framework methods
                if(InstrumentUtil.isAndroidMethod(b.getMethod())/* || !b.getMethod().getName().equals("myMethod")*/) {
                    return;
                } 
                
                try {
                    String uniqueFunctionSignature = b.getMethod().getSignature();
                    String thisClass = b.getMethod().getDeclaringClass().toString();
                    System.out.format("function signature is %s\n", uniqueFunctionSignature);
                    functionNameToRWDeps.put(uniqueFunctionSignature, new HashMap<>());
                    functionNameToRWDeps.get(uniqueFunctionSignature).put("reads", new ArrayList<>());
                    functionNameToRWDeps.get(uniqueFunctionSignature).put("writes", new ArrayList<>());
                    int numThisInvokeStmts = 0;
                    int numNonThisInvokeStmts = 0;
                    int numThisHeapReads = 0;
                    int numThisHeapWrites = 0;
                    int numNonThisHeapReads = 0;
                    int numNonThisHeapWrites = 0;
                    
                    JimpleBody body = (JimpleBody) b;
                    Iterator<Unit> iterator = body.getUnits().snapshotIterator();
                    
                    /* for heap rw begin */
                    UnitPatchingChain units = b.getUnits();
                    String heapState = "";
                    List<Unit> generatedUnits = new ArrayList<>();
                    /* for heap rw end */
                    
                    while(iterator.hasNext()){
                        
                        Unit unit = iterator.next();
                        if(unit instanceof Stmt) {
                            /* for heap rw begin */
                            Stmt gtSt = (Stmt) unit;
                            if(gtSt instanceof AssignStmt) {
                                final AssignStmt js = (AssignStmt) gtSt;
                                if (js.getRightOp() instanceof InvokeExpr) { // we have something like x = function(). need to see if function is in same class or different class
                                    String calledFunctionsClass = ((InvokeExpr)(js.getRightOp())).getMethod().getDeclaringClass().toString();
                                    if (thisClass.equalsIgnoreCase(calledFunctionsClass)) {
                                        numThisInvokeStmts ++;
                                    } else {
                                        numNonThisInvokeStmts ++;
                                    }
                                } else {
                                    List<Value> heapItemsWeReadFrom = getHeapRValues(js);
                                    List<Value> heapItemsWeWriteTo = getHeapLValues(js);
                                    if (heapItemsWeReadFrom.size() > 0) {
                                        for(Value v : heapItemsWeReadFrom) {
                                            String declaringClass = ((FieldRef)(v)).getField().getDeclaringClass().toString();
                                            functionNameToRWDeps.get(uniqueFunctionSignature).get("reads").add(v.toString());
                                            if (declaringClass.equalsIgnoreCase(thisClass)) {
                                                numThisHeapReads ++;
                                            } else {
                                                numNonThisHeapReads ++;
                                            }
                                        }
                                    }
                                    if (heapItemsWeWriteTo.size() > 0) {
                                        for(Value v : heapItemsWeWriteTo) {
                                            String declaringClass = ((FieldRef)(v)).getField().getDeclaringClass().toString();
                                            functionNameToRWDeps.get(uniqueFunctionSignature).get("writes").add(v.toString());    
                                            if (declaringClass.equalsIgnoreCase(thisClass)) {
                                                numThisHeapWrites ++;
                                            } else {
                                                numNonThisHeapWrites ++;
                                            }
                                        }
                                    }
                                }
                            }
                            /* for heap rw end */
                            else if (gtSt instanceof InvokeStmt) {
                                
                                if (isThisInvokeStmt((InvokeStmt)gtSt, thisClass)) {
                                    numThisInvokeStmts ++;
                                } else {
                                    numNonThisInvokeStmts ++;
                                }
                            }
                        }
                        
                        
                        
                        
                        
                    }	
                    
                    int numParams = body.getMethod().getParameterCount();
                    boolean hasReturnType = !body.getMethod().getReturnType().equals(VoidType.v());
                    
                    
                    statsToCount.get("num this fn invokes").add(numThisInvokeStmts);
                    statsToCount.get("num non-this fn invokes").add(numNonThisInvokeStmts);
                    statsToCount.get("num heap reads").add(numThisHeapReads);
                    statsToCount.get("num heap writes").add(numThisHeapWrites);
                    statsToCount.get("num non-this heap reads").add(numNonThisHeapReads);
                    statsToCount.get("num non-this heap writes").add(numNonThisHeapWrites);
                    statsToCount.get("num params").add(numParams);
                    if (hasReturnType) {
                        statsToCount.get("has return value").add(1);
                    } else {
                        statsToCount.get("has return value").add(0);
                    }
                    
                    
                    
                    // b.validate();  
                    
                }
                catch (Exception e) {
                    System.out.println("found an exception and skipped it");
                    numberOfSkippedFunctionDueToException++;
                }
                
            } 
        }));
        // Run Soot packs (note that our transformer pack is added to the phase "jtp")
        PackManager.v().runPacks();
        // Write the result of packs in outputPath
        PackManager.v().writeOutput();
        System.out.format("number of skipped function due to exception is %d\n", numberOfSkippedFunctionDueToException);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String stats_out = gson.toJson(statsToCount); 
        
        try {
            OutputStreamWriter myWriter = new OutputStreamWriter(
            new FileOutputStream(outputFileToWriteDepsTo),
            Charset.forName("UTF-8").newEncoder() 
            );
            myWriter.write(stats_out);
            myWriter.close();
            System.out.format("Successfully wrote heap r/w to the file %s.\n", outputFileToWriteDepsTo);
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
        
    }
    static boolean isThisInvokeStmt(InvokeStmt gtSt, String thisClass) {
        String invokedFunctionClass = gtSt.getInvokeExpr().getMethod().getDeclaringClass().toString();
        return invokedFunctionClass.equalsIgnoreCase(thisClass); 
    }
    
    static String getClassFromFunctionSignature(String thisClass) {
        return thisClass;
    }
    
    static int numberOfHeapReads(List<String> listOfHeapReads) {
        return listOfHeapReads.size();
    }
    
    static int numberOfHeapWrites(List<String> listOfHeapWrites) {
        return listOfHeapWrites.size();
    }
    
    
    static List<Value> getHeapLValues(AssignStmt stmt) {
        List<Value> valuesToReturn = new ArrayList<>();
        Value lhs = stmt.getLeftOp();
        Value rhs = stmt.getRightOp();
        
        if(lhs instanceof InstanceFieldRef) {
            
            valuesToReturn.add(lhs);
        }
        
        return valuesToReturn;
    }
    static List<Value> getHeapRValues(AssignStmt stmt) {
        List<Value> valuesToReturn = new ArrayList<>();
        Value lhs = stmt.getLeftOp();
        Value rhs = stmt.getRightOp();
        
        if(rhs instanceof InstanceFieldRef) { 
            valuesToReturn.add(rhs);
        } else if (rhs instanceof Expr) { // if right side has an expr which reads from multiple fields, handle it here. 
            List<ValueBox> usedValues = ((Expr)(rhs)).getUseBoxes();
            for (ValueBox valuesReadFrom : usedValues) {
                Value v = valuesReadFrom.getValue();
                if (v instanceof InstanceFieldRef) {
                    valuesToReturn.add(v);
                }
                
            }
        }
        
        return valuesToReturn;
    }
    
}