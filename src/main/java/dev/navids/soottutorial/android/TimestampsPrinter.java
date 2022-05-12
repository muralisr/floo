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

import java.util.Iterator;

import dev.navids.soottutorial.android.AndroidLogger.Pair;

public class TimestampsPrinter {
    private static String androidJar = "/disk/Android/Sdk/platforms";
    static String androidDemoPath = System.getProperty("user.dir") + File.separator + "demo" + File.separator
            + "Android";
    static String apkPath = androidDemoPath + File.separator + "/simpleemptyapp.apk";
    static String outputPath = androidDemoPath + File.separator + "/Instrumented";

    TimestampsPrinter() {
    }

    public static void main(String[] args) {

        if (System.getenv().containsKey("ANDROID_HOME"))
            androidJar = System.getenv("ANDROID_HOME") + File.separator + "platforms";
        // Clean the outputPath
        final File[] files = (new File(outputPath)).listFiles();
        if (files != null && files.length > 0) {
            Arrays.asList(files).forEach(File::delete);
        }
        // Initialize Soot
        InstrumentUtil.setupSoot(androidJar, apkPath, outputPath);

        // Add a transformation pack in order to add the statement
        // "System.out.println(<content>) at the beginning of each Application method
        PackManager.v().getPack("jtp").add(new Transform("jtp.myLogger", new BodyTransformer() {
            @Override
            protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
                // First we filter out Android framework methods

                if (InstrumentUtil.isAndroidMethod(b.getMethod()) || b.getMethod().getName().equals("printHelper")
                        || b.getMethod().getName().equals("printAll") || b.getMethod().getDeclaringClass().getName().startsWith("com.example.simpleemptyapp.Helper")) {
                    // Also, we filter out the printHelper to avoid a recursion.
                    return;
                }
                // if (!b.getMethod().getClass().getName().startsWith("MainActivity")) {
                // return;
                // }
                System.out.format("Working on Function with signature: %s\n", b.getMethod().getSignature());

                JimpleBody body = (JimpleBody) b;

                UnitPatchingChain units = b.getUnits();

                List<Unit> generatedUnitsEnter = new ArrayList<>(); // Lines of code we want to insert to get enter
                                                                    // timestamps.

                String f_enter = String.format("%s %s", b.getMethod().getSignature(), "ENTER");
                String f_exit = String.format("%s %s", b.getMethod().getSignature(), "EXIT");
                Value printParam1 = StringConstant.v(f_enter);
                Value printParam2 = StringConstant.v(f_exit);

                Local longLocal = InstrumentUtil.generateNewLocal(body, LongType.v());
                SootMethod currentTimeNano = Scene.v().getMethod("<java.lang.System: long currentTimeMillis()>");
                StaticInvokeExpr timeInvoke = Jimple.v().newStaticInvokeExpr(currentTimeNano.makeRef());
                AssignStmt timeInitalize = Jimple.v().newAssignStmt(longLocal, timeInvoke);

                ArrayList<Value> parameter = new ArrayList<Value>();
                parameter.add(printParam1);
                parameter.add(longLocal);

                Pair<Value, List<Unit>> arrayRefAndInstrumentation = AndroidLogger.generateParameterArray(parameter,
                        body);

                List<Unit> generatedArrayInstrumentation = arrayRefAndInstrumentation.getSecond();
                Value arrayRef = arrayRefAndInstrumentation.getFirst();

                Unit generatedInvokeStmt = AndroidLogger.makeJimpleStaticCallForPathExecution(
                        "com.example.simpleemptyapp.Helper", "printAll", AndroidLogger.getParameterArrayType(),
                        (parameter.isEmpty()) ? NullConstant.v() : arrayRef);

                generatedUnitsEnter.add(timeInitalize);
                generatedUnitsEnter.addAll(generatedArrayInstrumentation);
                generatedUnitsEnter.add(generatedInvokeStmt);

                // Insert the generated statement before the first non-identity stmt
                units.insertBefore(generatedUnitsEnter, body.getFirstNonIdentityStmt());

                for (Iterator<Unit> iter = units.snapshotIterator(); iter.hasNext();) {
                    final Stmt stmt = (Stmt) iter.next();
                    if (stmt instanceof ReturnStmt || stmt instanceof ReturnVoidStmt) {
                        Local longLocal2 = InstrumentUtil.generateNewLocal(body, LongType.v());
                        SootMethod currentTimeNano2 = Scene.v().getMethod("<java.lang.System: long nanoTime()>");
                        StaticInvokeExpr timeInvoke2 = Jimple.v().newStaticInvokeExpr(currentTimeNano2.makeRef());
                        AssignStmt timeInitalize2 = Jimple.v().newAssignStmt(longLocal2, timeInvoke2);

                        ArrayList<Value> parameter2 = new ArrayList<Value>();
                        parameter2.add(printParam2);
                        parameter2.add(longLocal2);

                        List<Unit> generatedUnitsExit = new ArrayList<>(); // Lines of code we want to insert to get
                                                                           // exit timestamps.

                        Pair<Value, List<Unit>> arrayRefAndInstrumentation2 = AndroidLogger
                                .generateParameterArray(parameter2, body);

                        List<Unit> generatedArrayInstrumentation2 = arrayRefAndInstrumentation2.getSecond();
                        Value arrayRef2 = arrayRefAndInstrumentation2.getFirst();

                        Unit generatedInvokeStmt2 = AndroidLogger.makeJimpleStaticCallForPathExecution(
                                "com.example.simpleemptyapp.Helper", "printAll", AndroidLogger.getParameterArrayType(),
                                (parameter2.isEmpty()) ? NullConstant.v() : arrayRef2);
                        generatedUnitsExit.add(timeInitalize2);
                        generatedUnitsExit.addAll(generatedArrayInstrumentation2);
                        generatedUnitsExit.add(generatedInvokeStmt2);
                        // if i use the InvokeStmt i created earlier
                        // it gives error saying i am adding the same stmt twice
                        // to a body (when the body contains multiple return/exit points)
                        units.insertBefore(generatedUnitsExit, stmt);
                    }
                }

                // Validate the body to ensure that our code injection does not introduce any
                // problem (at least statically)
                b.validate();

            }
        }));
        // Run Soot packs (note that our transformer pack is added to the phase "jtp")
        PackManager.v().runPacks();
        // Write the result of packs in outputPath
        PackManager.v().writeOutput();
    }

    public static List<Unit> getListOfStmtsToCallHelperWithTimestampAndThreadID(Body body, String otherStringToInclude) {
        Local longLocal1 = InstrumentUtil.generateNewLocal(body, LongType.v()); // for thread id
        SootMethod currentThreadFunction = Scene.v().getMethod("<com.example.asyncprinter.HelperDeepEquals: long getThreadID()>");
        StaticInvokeExpr currentThreadFunctionInvoke = Jimple.v().newStaticInvokeExpr(currentThreadFunction.makeRef());
        AssignStmt threadIDInitialize = Jimple.v().newAssignStmt(longLocal1, currentThreadFunctionInvoke);

        Local longLocal2 = InstrumentUtil.generateNewLocal(body, LongType.v()); // for timestamp
        SootMethod currentTimeNano2 = Scene.v().getMethod("<java.lang.System: long currentTimeMillis()>");
        StaticInvokeExpr timeInvoke2 = Jimple.v().newStaticInvokeExpr(currentTimeNano2.makeRef());
        AssignStmt timeInitalize2 = Jimple.v().newAssignStmt(longLocal2, timeInvoke2);

        ArrayList<Value> parameter2 = new ArrayList<Value>();

        Value printParam2 = StringConstant.v(String.format("%s", otherStringToInclude));
        parameter2.add(printParam2); // function name + entry/exit strings provided by caller
        parameter2.add(longLocal2); // timestamp
        parameter2.add(longLocal1); // thread id

        List<Unit> generatedUnits = new ArrayList<>(); // Lines of code we want to insert to get
                                                           // exit timestamps.

        Pair<Value, List<Unit>> arrayRefAndInstrumentation2 = AndroidLogger
                .generateParameterArray(parameter2, body);

        List<Unit> generatedArrayInstrumentation2 = arrayRefAndInstrumentation2.getSecond();
        Value arrayRef2 = arrayRefAndInstrumentation2.getFirst();

        Unit generatedInvokeStmt2 = AndroidLogger.makeJimpleStaticCallForPathExecution(
            "com.example.asyncprinter.HelperDeepEquals", "printSomeStuff", AndroidLogger.getParameterArrayType(),
                (parameter2.isEmpty()) ? NullConstant.v() : arrayRef2);
        generatedUnits.add(threadIDInitialize);
        generatedUnits.add(timeInitalize2);
        generatedUnits.addAll(generatedArrayInstrumentation2);
        generatedUnits.add(generatedInvokeStmt2);
        return generatedUnits;
    }
}
