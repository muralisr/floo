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

public class InsertHelperCallsForSubFunctionCalls {
    private static String androidJar = "/disk/Android/Sdk/platforms";
    static String androidDemoPath = System.getProperty("user.dir") + File.separator + "demo" + File.separator
            + "Android";
    static String apkPath = "";
    static String outputPath = androidDemoPath + File.separator + "/Instrumented";
    InsertHelperCallsForSubFunctionCalls() {
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
                    "I need one argument after InsertHelperCallsForSubFunctionCalls with the apk path\n./gradlew run --args=\"InsertHelperCallsForSubFunctionCalls path_to_apk_file\"");
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
                UnitPatchingChain units = b.getUnits();
                JimpleBody body = (JimpleBody) b;
                for (Iterator<Unit> iter = units.snapshotIterator(); iter.hasNext();) {
                    final Stmt stmt = (Stmt) iter.next();
                    if (stmt instanceof InvokeStmt || (stmt instanceof AssignStmt && ((AssignStmt)stmt).containsInvokeExpr())) {
                        String subfunctionUniqueIdentifier = DetermineCacheability.getNewChildFunctionIdentifier("");
                        List<Unit> generatedUnitsSubEnter = TimestampsPrinter.getListOfStmtsToCallHelperWithTimestampAndThreadID(body, "CHILDSTART_" + subfunctionUniqueIdentifier);
                        List<Unit> generatedUnitsSubExit = TimestampsPrinter.getListOfStmtsToCallHelperWithTimestampAndThreadID(body, "CHILDEND_" + subfunctionUniqueIdentifier);
                        units.insertBefore(generatedUnitsSubEnter, stmt); // before calling the child/sub function, print CHILDSTART_
                        units.insertAfter(generatedUnitsSubExit, stmt); // after calling the child/sub function, print CHILDEND_
                    }
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
    