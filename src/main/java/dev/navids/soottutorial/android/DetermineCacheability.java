package dev.navids.soottutorial.android;

import soot.*;
import soot.jimple.*;
import soot.jimple.infoflow.collect.ConcurrentHashSet;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashSet;

import java.nio.charset.Charset;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.util.HashMap;

import com.google.common.collect.Table;
import com.google.gson.*;

import java.util.Iterator;
import dev.navids.soottutorial.android.AndroidLogger.Pair;
import java.io.Serializable;

/***
 * This class transforms a given APK. For each function, it first identifies the
 * list of variables it reads from. Then, at the entrance of the function, it
 * adds a call to a helper with this list of variables.
 */
public class DetermineCacheability {
    private static String androidJar = "/disk/Android/Sdk/platforms";
    static String androidDemoPath = System.getProperty("user.dir") + File.separator + "demo" + File.separator
            + "Android";
    static String apkPath = "";
    static String outputPath = androidDemoPath + File.separator + "/Instrumented";
    static String nonDeterminismOutputFile = androidDemoPath + File.separator + "/Instrumented/non_determinism_output.txt";
    static ConcurrentHashMap<String, Boolean> isFunctionNondeterministic = new ConcurrentHashMap<String, Boolean>();
    static ConcurrentHashSet<String> setOfFunctionsWeCanOptimize = new ConcurrentHashSet<>();
    private static final AtomicInteger numberOfFunctionsSeen = new AtomicInteger();
    private static final AtomicInteger numberOfChildFunctionsSeen = new AtomicInteger();
    DetermineCacheability() {
    }

    // for each new function we see, we create an identifier like func_XXX 
    // where XXX is an integer.
    public static String getNewFunctionIdentifier(String fnameToUseIfNeeded) {
        StringBuilder sb = new StringBuilder();
        sb.append("f");
        sb.append(numberOfFunctionsSeen.incrementAndGet());
        return sb.toString();
    }
    public static String getNewChildFunctionIdentifier(String fnameToUseIfNeeded) {
        StringBuilder sb = new StringBuilder();
        sb.append("c");
        sb.append(numberOfChildFunctionsSeen.incrementAndGet());
        return sb.toString();
    }

    public static void main(String[] args) {

        // Clean the outputPath
        final File[] files = (new File(outputPath)).listFiles();
        // if (files != null && files.length > 0) {
        //     Arrays.asList(files).forEach(File::delete);
        // }
        System.out.format("args size is %d\n", args.length);
        if (args.length < 1) {
            System.out.println(
                    "I need one argument after DetermineCacheability with the apk path\n./gradlew run --args=\"DetermineCacheability path_to_apk_file\"");
            return;
        }
        apkPath = args[0];
        // Initialize Soot
        InstrumentUtil.setupSoot(androidJar, apkPath, outputPath);

        System.out.format("I am going to process apk file %s\n", apkPath);
        PackManager.v().getPack("jtp").add(new Transform("jtp.myLogger", new BodyTransformer() {
            @Override
            protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
                // First we filter out Android framework methods
                if (InstrumentUtil.isAndroidMethod(b.getMethod()) || b.getMethod().getDeclaringClass().getName()
                        .startsWith("com.example.asyncprinter.Helper")) {
                    return;
                }
                String functionSignature = b.getMethod().getSignature();
                setOfFunctionsWeCanOptimize.add(b.getMethod().getDeclaringClass() + "." + b.getMethod().getName());
                String uniqueFunctionSignature = getNewFunctionIdentifier(functionSignature);

                // if (!uniqueFunctionSignature
                //         .equals("<com.foxnews.android.common.ItemEntry: boolean equals(java.lang.Object)>")) {
                //     return;
                // }
                System.out.format("function signature is %s\n", uniqueFunctionSignature);
                
                JimpleBody body = (JimpleBody) b;
                if (NondeterminismLogger.containNondeterministicUnit(body)) {
                    isFunctionNondeterministic.put(uniqueFunctionSignature, true);
                }
      
                Iterator<Unit> iterator = body.getUnits().snapshotIterator();
                List<Value> readStateOfFunction = new ArrayList<>();
                while (iterator.hasNext()) {
                    Unit unit = iterator.next();
                    if (unit instanceof Stmt) {
                        /* for heap rw begin */
                        Stmt gtSt = (Stmt) unit;
                        if (gtSt instanceof AssignStmt) {
                            final AssignStmt js = (AssignStmt) gtSt;
                            readStateOfFunction.addAll(HeapRWFinder.getHeapRValues(js));
                        } else {
                            readStateOfFunction.addAll(HeapRWFinder.getReadValuesFromReadOnlyStmts((Stmt) unit));
                        }
                    }
                }

                List<Value> allowedValuesForNonThisReads = HeapRWFinder
                        .getListOfAllowedValuesForNonThisReadDependencies(body);
                HashSet<String> allowedValuesSetForNonThisReads = new HashSet<>();
                for (Value v : allowedValuesForNonThisReads) {
                    allowedValuesSetForNonThisReads.add(v.toString());
                }
          
                List<Unit> generatedUnits = new ArrayList<>();
                readStateOfFunction = HeapRWFinder.dedupArrayList(readStateOfFunction);
                List<Value> thisFunctionDependsOnThisListOfObjects = new ArrayList<>();
                // put the function name into the list of dependencies (not used now as we're passing function name as a separate param to helper now)
                // String functionName = String.format("ENTER_%s", body.getMethod().getSignature());
                // Value functionNameAsValue = StringConstant.v(functionName);
                // Local functionNameAsLocal = AndroidLogger.generateFreshLocal(body, RefType.v("java.lang.Object"));
                // Unit functionNameAssignStmt = Jimple.v().newAssignStmt(functionNameAsLocal, functionNameAsValue);
                // thisFunctionDependsOnThisListOfObjects.add(functionNameAsLocal);
                // generatedUnits.add(functionNameAssignStmt);
                for (Value v : readStateOfFunction) {
                  //  System.out.format("read state is %s\n", v);
                    try {
                        if (v instanceof InstanceFieldRef) {

                            if (((InstanceFieldRef) (v)).getBase() == body.getThisLocal()) {
                                // heap read is of the form x.object. where x is an instance of a class, and
                                // object is a field name.
                                // System.out.format("working on instance field ref %s\n", v);
                                HeapDependency hd = new HeapDependency(v.toString());
                                Local baseNameToUse = ComputeCacher.findVariableMatchingNameInContext(hd.baseName,
                                        body);
                                SootClass c = RefType.v(hd.className).getSootClass();
                                SootField f = c.getFieldByName(hd.fieldName);
                                InstanceFieldRef ifr = Jimple.v().newInstanceFieldRef(baseNameToUse, f.makeRef());
                                if (ComputeCacher.isPrimitiveType(hd.fieldType)) { // if primitive, use as it is
                                    Local localRepOfHeapRead = AndroidLogger.generateFreshLocal(body,
                                            ComputeCacher.getRefTypeFromDataType(hd.fieldType));
                                    Unit newAssignStmt = Jimple.v().newAssignStmt(localRepOfHeapRead, ifr);
                                    thisFunctionDependsOnThisListOfObjects.add(localRepOfHeapRead);
                                    generatedUnits.add(newAssignStmt);
                                } else { // else it is an object.
                                    Local localRepOfHeapRead = AndroidLogger.generateFreshLocal(body,
                                            ComputeCacher.getRefTypeFromDataType(hd.fieldType));
                                    Unit newAssignStmt = Jimple.v().newAssignStmt(localRepOfHeapRead, ifr);
                                    generatedUnits.add(newAssignStmt);
                                    thisFunctionDependsOnThisListOfObjects.add(localRepOfHeapRead);

                                }
                            } else { // A non-this object. it is possible that the non-this object is initialized
                                     // within this function & then one of its fields is read.
                                // for example, we might be looking at a heap read like object.value where
                                // object is a non-this instance of another class.
                                // when we want to check object.value, object might not have been initialized
                                // (i.e., within the function, later on, app does object = new and then
                                // reads object.value). so at the beginning of the function, doing object.value
                                // would be invalid as object is null.
                                // so, when we insert object.value as a part of the read state, if object itself
                                // is undefined, ignore it in the read state.
                                // System.out.format("working on instance field ref %s\n", v);
                                // System.out.println("non-this instance");
                                HeapDependency hd = new HeapDependency(v.toString());
                                // in this place, if hd.basename is written to earlier than this particular
                                // read, then this read
                                // should be ignored.
                                Local baseNameToUse = ComputeCacher.findVariableMatchingNameInContext(hd.baseName,
                                        body);
                                SootClass c = RefType.v(hd.className).getSootClass();
                                SootField f = c.getFieldByName(hd.fieldName);
                                InstanceFieldRef ifr = Jimple.v().newInstanceFieldRef(baseNameToUse, f.makeRef());
                                if (ComputeCacher.isPrimitiveType(hd.fieldType)) { // if primitive, use as it is
                                    // System.out.println("non-this instance primitive");
                                    List<ValueBox> usedBoxes = v.getUseBoxes();
                                    boolean useAsReadDependency = true;
                                    for (ValueBox vb : usedBoxes) {
                                        if (!allowedValuesSetForNonThisReads.contains(vb.getValue().toString())) {
                                            useAsReadDependency = false;
                                        }
                                    }
                                    if (!useAsReadDependency) {
                                        // System.out.format("not including non-this primitive read %s\n",
                                                // v.getUseBoxes());
                                    } else {
                                        Local localRepOfHeapRead = AndroidLogger.generateFreshLocal(body,
                                                ComputeCacher.getRefTypeFromDataType(hd.fieldType));
                                        Unit newAssignStmt = Jimple.v().newAssignStmt(localRepOfHeapRead, ifr);
                                        thisFunctionDependsOnThisListOfObjects.add(localRepOfHeapRead);
                                        generatedUnits.add(newAssignStmt);
                                    }

                                } else { // else it is an object.
                                    // the object may contain things that have not yet been initialized at the entry
                                    // of the function.
                                    List<ValueBox> usedBoxes = v.getUseBoxes();
                                    boolean useAsReadDependency = true;
                                    for (ValueBox vb : usedBoxes) {
                                        if (!allowedValuesSetForNonThisReads.contains(vb.getValue().toString())) {
                                            useAsReadDependency = false;
                                        }
                                    }
                                    if (!useAsReadDependency) {
                                        // System.out.format("not including non-this read %s\n", v.getUseBoxes());
                                    } else {
                                        // System.out.format("including non-this read %s\n", v.getUseBoxes());
                                        Local localRepOfHeapRead = AndroidLogger.generateFreshLocal(body,
                                                ComputeCacher.getRefTypeFromDataType(hd.fieldType));
                                        Unit newAssignStmt = Jimple.v().newAssignStmt(localRepOfHeapRead, ifr);
                                        generatedUnits.add(newAssignStmt);
                                        thisFunctionDependsOnThisListOfObjects.add(localRepOfHeapRead);
                                    }

                                }
                            }
                        } else if (v instanceof StaticFieldRef) {
                            // heap read is of the form x.object. where x is an instance of a class, and
                            // object is a field name.
                            // System.out.format("working on static field ref %s\n", v);
                            StaticFieldRef sfr = (StaticFieldRef) v;
                            if (ComputeCacher.isPrimitiveType(sfr.getType().toString())) { // if primitive, use as it is
                                Local localRepOfHeapRead = AndroidLogger.generateFreshLocal(body,
                                        ComputeCacher.getRefTypeFromDataType(sfr.getType().toString()));
                                Unit newAssignStmt = Jimple.v().newAssignStmt(localRepOfHeapRead, sfr);
                                thisFunctionDependsOnThisListOfObjects.add(localRepOfHeapRead);
                                generatedUnits.add(newAssignStmt);
                            } else { // else it is an object.
                                Local localRepOfHeapRead = AndroidLogger.generateFreshLocal(body,
                                        ComputeCacher.getRefTypeFromDataType(sfr.getType().toString()));
                                Unit newAssignStmt = Jimple.v().newAssignStmt(localRepOfHeapRead, sfr);
                                generatedUnits.add(newAssignStmt);
                                thisFunctionDependsOnThisListOfObjects.add(localRepOfHeapRead);

                            }

                        }
                    } catch (java.lang.RuntimeException e) {
                        // System.out.format("Unable to include variable %s in read state\n", v);
                    }

                }

                // get parameters and put it into the list of dependencies
                int numOfParams = b.getMethod().getParameterCount();
                for (int i = 0; i < numOfParams; i++) {
                    Value v = body.getParameterLocal(i);
                    Value properParamObject = AndroidLogger.generateCorrectObject(b, v, generatedUnits); // if v is a
                                                                                                         // primitive,
                                                                                                         // make it an
                                                                                                         // object.
                                                                                                         // else, keep
                                                                                                         // it an object
                    Local paramLocal = AndroidLogger.generateFreshLocal(body, RefType.v("java.lang.Object"));
                    Unit newAssignStmt = Jimple.v().newAssignStmt(paramLocal, properParamObject);
                    thisFunctionDependsOnThisListOfObjects.add(paramLocal);
                    generatedUnits.add(newAssignStmt);
                }

                if (thisFunctionDependsOnThisListOfObjects.size() > 0) {
                    Local longLocal1 = InstrumentUtil.generateNewLocal(body, LongType.v()); // for thread id
                    SootMethod currentThreadFunction = Scene.v().getMethod("<com.example.asyncprinter.HelperDeepEquals: long getThreadID()>");
                    StaticInvokeExpr currentThreadFunctionInvoke = Jimple.v().newStaticInvokeExpr(currentThreadFunction.makeRef());
                    AssignStmt threadIDInitialize = Jimple.v().newAssignStmt(longLocal1, currentThreadFunctionInvoke);
            
                    Local longLocal2 = InstrumentUtil.generateNewLocal(body, LongType.v()); // for timestamp
                    SootMethod currentTimeNano2 = Scene.v().getMethod("<java.lang.System: long currentTimeMillis()>");
                    StaticInvokeExpr timeInvoke2 = Jimple.v().newStaticInvokeExpr(currentTimeNano2.makeRef());
                    AssignStmt timeInitalize2 = Jimple.v().newAssignStmt(longLocal2, timeInvoke2);
                    List<Value> listOfStuffToBePrinted = new ArrayList<>();
                    listOfStuffToBePrinted.add(longLocal2);
                    listOfStuffToBePrinted.add(longLocal1);

                    Pair<Value, List<Unit>> arrayRefAndInstrumentation2 = AndroidLogger
                    .generateParameterArray(listOfStuffToBePrinted, body);
    
                    List<Unit> generatedArrayInstrumentation2 = arrayRefAndInstrumentation2.getSecond();
                    Value arrayRef2 = arrayRefAndInstrumentation2.getFirst();

                    Pair<Value, List<Unit>> arrayRefAndInstrumentation = AndroidLogger
                            .generateParameterArray(thisFunctionDependsOnThisListOfObjects, body);
                    List<Unit> generatedArrayInstrumentation = arrayRefAndInstrumentation.getSecond();
                    Value arrayRef = arrayRefAndInstrumentation.getFirst();
                    StaticInvokeExpr uniqueRepGenerateMethodInvokeExpr = AndroidLogger.getInvokeExprForAssignment(
                            "com.example.asyncprinter.HelperDeepEquals", "printStuffAndLogMemo",
                            RefType.v("java.lang.String"), StringConstant.v("ENTER_"+uniqueFunctionSignature),
                            AndroidLogger.getParameterArrayType(),
                            (listOfStuffToBePrinted.isEmpty()) ? NullConstant.v() : arrayRef2,
                            AndroidLogger.getParameterArrayType(),
                            (thisFunctionDependsOnThisListOfObjects.isEmpty()) ? NullConstant.v() : arrayRef);
                    
                    Unit invokeHelperStmt = Jimple.v().newInvokeStmt((StaticInvokeExpr) uniqueRepGenerateMethodInvokeExpr);
                    generatedUnits.add(timeInitalize2);
                    generatedUnits.add(threadIDInitialize);
                    generatedUnits.addAll(generatedArrayInstrumentation2);
                    generatedUnits.addAll(generatedArrayInstrumentation);
                    generatedUnits.add(invokeHelperStmt); // at this point we have called the helper with an entry timestamp

                    // now let us call the helper with current timestamp so we can compute how long the helper call took.
                    List<Unit> generatedUnitsToIndicateEndOfHelperCall = TimestampsPrinter.getListOfStmtsToCallHelperWithTimestampAndThreadID(body, "HELPEREND_" + uniqueFunctionSignature);
                    generatedUnits.addAll(generatedUnitsToIndicateEndOfHelperCall);
                    UnitPatchingChain units = b.getUnits();
                    try {
                        units.insertBefore(generatedUnits, body.getFirstNonIdentityStmt());
                    } catch (Exception e) {
                        // System.out.format("unable to transform function %s\n.", body.getMethod().getSignature());
                    }
                    // At this point, we have instrumented the entry of the function and added a
                    // call to the helper with all of our read state. 


                    // Exit timestamp stuff
                    for (Iterator<Unit> iter = units.snapshotIterator(); iter.hasNext();) {
                        final Stmt stmt = (Stmt) iter.next();
                        // TODO 1: how to handle cases when the function does not throw anything but simply crashes?
                        // TODO 2: what is a ThrowInst? Should we handle that too? 
                        if (stmt instanceof ThrowStmt) {
                            List<Unit> generatedUnitsExit = TimestampsPrinter.getListOfStmtsToCallHelperWithTimestampAndThreadID(body, "THROW_" + uniqueFunctionSignature);
                            units.insertBefore(generatedUnitsExit, stmt);
                        }
                        if (stmt instanceof ReturnStmt || stmt instanceof ReturnVoidStmt || stmt instanceof ThrowStmt) {
                            List<Unit> generatedUnitsExit = TimestampsPrinter.getListOfStmtsToCallHelperWithTimestampAndThreadID(body, "EXIT_" + uniqueFunctionSignature);
                            units.insertBefore(generatedUnitsExit, stmt);
                        } else if (stmt instanceof InvokeStmt || (stmt instanceof AssignStmt && ((AssignStmt)stmt).containsInvokeExpr())) {
                            // This component has been moved to InsertHelperCallsForSubFunctionCalls
                            // Because if we do too much instrumentation in one go, we see java.lang.OutOfMemoryError: GC overhead limit exceeded even with a memory limit of 8G.
                            
                            // String subfunctionUniqueIdentifier = getNewChildFunctionIdentifier(functionSignature);
                            // List<Unit> generatedUnitsSubEnter = TimestampsPrinter.getListOfStmtsToCallHelperWithTimestampAndThreadID(body, "CHILDSTART_" + subfunctionUniqueIdentifier);
                            // List<Unit> generatedUnitsSubExit = TimestampsPrinter.getListOfStmtsToCallHelperWithTimestampAndThreadID(body, "CHILDEND_" + subfunctionUniqueIdentifier);
                            // units.insertBefore(generatedUnitsSubEnter, stmt); // before calling the child/sub function, print SUBENTER
                            // units.insertAfter(generatedUnitsSubExit, stmt); // after calling the child/sub function, print SUBEXIT
                        }
                    }
                   
                    b.validate();
                }
            }
        }));
        // Run Soot packs (note that our transformer pack is added to the phase "jtp")
        PackManager.v().runPacks();
        // Write the result of packs in outputPath
        PackManager.v().writeOutput();
        writeNonDeterminismToFile();
        writeSetOfFunctionsWeCanOptimizeToFile();

    }

    public static void writeSetOfFunctionsWeCanOptimizeToFile() {
        String fileContainingSetOfFunctionsWeCanOptimize = "/disk/Code/projects/soot-instrument/demo/Android/Instrumented/set_of_functions_we_can_optimize.txt";
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        String jsonReadWWriteDeps = gson.toJson(setOfFunctionsWeCanOptimize);

        try {
            OutputStreamWriter myWriter = new OutputStreamWriter(new FileOutputStream(fileContainingSetOfFunctionsWeCanOptimize),
                    Charset.forName("UTF-8").newEncoder());
            myWriter.write(jsonReadWWriteDeps);
            myWriter.close();
            System.out.format("Successfully wrote non determinism info to the file %s.\n", fileContainingSetOfFunctionsWeCanOptimize);
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    public static void writeNonDeterminismToFile() {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        String jsonReadWWriteDeps = gson.toJson(isFunctionNondeterministic);

        try {
            OutputStreamWriter myWriter = new OutputStreamWriter(new FileOutputStream(nonDeterminismOutputFile),
                    Charset.forName("UTF-8").newEncoder());
            myWriter.write(jsonReadWWriteDeps);
            myWriter.close();
            System.out.format("Successfully wrote non determinism info to the file %s.\n", nonDeterminismOutputFile);
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }
}
