package dev.navids.soottutorial.android;


import soot.*;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.JimpleBody;
import soot.jimple.internal.InvokeExprBox;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class FlooScratch {

    private static String androidJar = "/disk/Android/Sdk/platforms";
    static String androidDemoPath = System.getProperty("user.dir") + File.separator + "demo" + File.separator + "Android";
    static String apkPath = "/home/murali/AndroidStudioProjects/MyTestApplicationForFloo/app/build/outputs/apk/debug/app-debug.apk";
    static String outputPath = androidDemoPath + File.separator + "/Instrumented_Scratch/";
    FlooScratch() {}
    public static void main(String[] args){

        if(System.getenv().containsKey("ANDROID_HOME"))
            androidJar = System.getenv("ANDROID_HOME")+ File.separator+"platforms";
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");  
        LocalDateTime now = LocalDateTime.now();  
        outputPath += dtf.format(now);
        // Initialize Soot
        InstrumentUtil.setupSoot(androidJar, apkPath, outputPath);
        // Add a transformation pack in order to add the statement "System.out.println(<content>) at the beginning of each Application method
        PackManager.v().getPack("jtp").add(new Transform("jtp.myLogger", new BodyTransformer() {
            @Override
            protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
                if(InstrumentUtil.isAndroidMethod(b.getMethod())) 
                    return;
                if (!b.getMethod().getSignature().equals("<com.example.mytestapplicationforfloo.MainActivity: void myFunction()>"))
                    return;
                System.out.format("function signature is %s\n", b.getMethod().getSignature());
                System.out.println("***Printing all units***");
                ComputeCacher.printAllUnits(b);
                System.out.println("***Printing only invokes");
                Iterator<Unit> iterator = b.getUnits().snapshotIterator();
                Iterator<Unit> iteratorStartingOfUnitsSnapshot = b.getUnits().snapshotIterator();
                while(iterator.hasNext()){
                    Unit unit = iterator.next();
                    if (unit instanceof InvokeStmt) {
                        System.out.format("Floo: invoke stmt %s\n", unit);
                        List<Unit> unitsNeededToReplayThisInvoke = getUnitsNeededToReplayThisInvoke(unit, b);
                        System.out.println("units it uses below");
                        printAListOfUnits(unitsNeededToReplayThisInvoke);
                        System.out.println("units it uses above");
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
    public static List<Unit> getUnitsNeededToReplayThisInvoke(Unit functionInvokeToGetNecessaryHelpersFor, Body b) {
        // to replay one function invoke, we need to do the preliminary set up work
        // the function invoke would have used a bunch of statements, all of which need to be repeated
        List<Unit> unitsNeededToReplay = new ArrayList<>();
        List<ValueBox> listOfValueBoxUsedInReplay = functionInvokeToGetNecessaryHelpersFor.getUseAndDefBoxes();
        // for(Value vb : getListOfValuesFromValueBoxes(listOfValueBoxUsedInReplay)) System.out.format("0vb is %s\n", vb);
        Iterator<Unit> iterator = b.getUnits().snapshotIterator();
        // a, b, c, .. in this example represent units
        // 1, 2, 3, .. represent variables

        /**
         * assume we have the following program
         * a uses variable 1
         * b uses 1, 2
         * c uses 3, 4
         * d uses 2, 3, 5
         * 
         * the program's statements are in the order d, then c, then b, then a. 
         * a is the function invocation we're interested in. 
         * 
         * in this setting, a uses 1. so we need all the lines that touch 1 before a. so we have b. 
         * but now we have b touching 2. we need all the lines before b that touch 1 or 2. 
         * so we need d. 
         * 
         * so to replay a correctly, we need to replay d, then b, then a. 
         */
        PatchingChain<Unit> unitsOfBody = b.getUnits();
        Unit pointer = null;
        for(pointer = unitsOfBody.getFirst(); pointer != functionInvokeToGetNecessaryHelpersFor; pointer = unitsOfBody.getSuccOf(pointer)); 
        // now pointer points to the invocation we're interested in. 
        unitsNeededToReplay.add((Unit) pointer); 
        Set<Value> valuesInfluencingOurInvocation = new HashSet<>(getListOfValuesFromValueBoxes(pointer.getUseAndDefBoxes()));

        while(pointer != null && !(pointer instanceof IdentityUnit)) { 
            // keep moving one statement at a time towards the top of the function
            pointer = unitsOfBody.getPredOf(pointer);
            // list of values used in this unit
            List<Value> listOfValuesInThisUnit = getListOfValuesFromValueBoxes(pointer.getUseAndDefBoxes());
            if (doValueSetsOverlap(listOfValuesInThisUnit, new ArrayList<>(valuesInfluencingOurInvocation))) {
                valuesInfluencingOurInvocation.addAll(listOfValuesInThisUnit);
                unitsNeededToReplay.add((Unit) pointer);
                // if a variable is defined in this unit, then we do not need to check that particular variable further till the function start
                List<Value> listOfValuesDefinedInThisUnit = getListOfValuesFromValueBoxes(pointer.getDefBoxes());
                valuesInfluencingOurInvocation.removeAll(listOfValuesDefinedInThisUnit);
            }
        }
        Collections.reverse(unitsNeededToReplay);
           
        
        // while(iterator.hasNext()){
        //     Unit unit = iterator.next();
        //     if (unit instanceof IdentityUnit) continue;
        //     if (unit.equals(functionInvokeToGetNecessaryHelpersFor)) break;
        //     // System.out.format("1checking unit %s\n", unit);
        //     List<ValueBox> listOfValuesInThisUnit = unit.getUseAndDefBoxes();
        //     // for(Value vb : getListOfValuesFromValueBoxes(listOfValuesInThisUnit)) System.out.format("2this unit contains box %s\n", vb);
        //     if (doValueSetsOverlap(getListOfValuesFromValueBoxes(listOfValuesInThisUnit), getListOfValuesFromValueBoxes(listOfValueBoxUsedInReplay))) {
        //         unitsNeededToReplay.add((Unit) unit.clone());
        //     }
        // }
        // unitsNeededToReplay.add((Unit) functionInvokeToGetNecessaryHelpersFor.clone());
        return unitsNeededToReplay;
    }
    public static List<Value> getListOfValuesFromValueBoxes(List<ValueBox> input) {
        List<Value> output = new ArrayList<>();
        for (ValueBox i : input) output.add(i.getValue());
        return output;
    }
    public static boolean doValueBoxSetsOverlap(List<ValueBox> list1, List<ValueBox> list2) {
        if (list1.size() == 0 || list2.size() == 0) return false;
        else return !Collections.disjoint(list1, list2);
    }
    public static boolean doValueSetsOverlap(List<Value> list1, List<Value> list2) {
        if (list1.size() == 0 || list2.size() == 0) return false;
        else return !Collections.disjoint(list1, list2);
    }
    public static void printAListOfUnits(List<Unit> unitsToPrint) {
        for(Unit u : unitsToPrint) System.out.println(u);
    }
}