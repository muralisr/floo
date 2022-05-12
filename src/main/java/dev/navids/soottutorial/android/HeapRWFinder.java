package dev.navids.soottutorial.android;

import soot.*;
import soot.jimple.*;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.Charset;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.HashSet;

import com.google.common.collect.Table;
import com.google.gson.*;


import java.util.Iterator;

import java.io.Serializable;

public class HeapRWFinder {
    // cmd line way to invoke this: ./gradlew run --args="HeapRWFinder
    // /disk/ubuntu_data/projects/SootTutorial/demo/HeapRW/simple_out.json <apk>"

    private static String androidJar = "/disk/Android/Sdk/platforms";
    static String androidDemoPath = System.getProperty("user.dir") + File.separator + "demo" + File.separator
            + "Android";
    static String apkPath = "";
    static String outputPath = androidDemoPath + File.separator + "/Instrumented";

    HeapRWFinder() {
    }

    public static void main(String[] args) {

        // Clean the outputPath
        final File[] files = (new File(outputPath)).listFiles();
        if (files != null && files.length > 0) {
            Arrays.asList(files).forEach(File::delete);
        }
        System.out.format("args size is %d\n", args.length);
        if (args.length < 2) {
            System.out.println(
                    "I need one argument after HeapRWFinder with the path to the file to create with heap rw info, and one with the apk path\n./gradlew run --args=\"HeapRWFinder path_to_json_file path_to_apk_file\"");
            return;
        }
        apkPath = args[1];
        // Initialize Soot
        InstrumentUtil.setupSoot(androidJar, apkPath, outputPath);

        String outputFileToWriteDepsTo = args[0];
        System.out.format("I am going to write json output to %s\n", outputFileToWriteDepsTo);
        System.out.format("I am going to process apk file %s\n", apkPath);
        ConcurrentHashMap<String, HashMap<String, List<String>>> functionNameToRWDeps = new ConcurrentHashMap<String, HashMap<String, List<String>>>(); // fn_name
        // ->
        // r/w
        // ->
        // list
        // of
        // vars
        PackManager.v().getPack("jtp").add(new Transform("jtp.myLogger", new BodyTransformer() {
            @Override
            protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
                // First we filter out Android framework methods
                if (InstrumentUtil.isAndroidMethod(b.getMethod())) {
                    return;
                }
                String uniqueFunctionSignature = b.getMethod().getSignature();
                System.out.format("function signature is %s\n", uniqueFunctionSignature);
                functionNameToRWDeps.put(uniqueFunctionSignature, new HashMap<>());
                functionNameToRWDeps.get(uniqueFunctionSignature).put("reads", new ArrayList<>());
                functionNameToRWDeps.get(uniqueFunctionSignature).put("writes", new ArrayList<>());

                JimpleBody body = (JimpleBody) b;
                Iterator<Unit> iterator = body.getUnits().snapshotIterator();

                while (iterator.hasNext()) {
                    Unit unit = iterator.next();
                    if (unit instanceof Stmt) {
                        /* for heap rw begin */
                        Stmt gtSt = (Stmt) unit;
                        if (gtSt instanceof AssignStmt) {

                            final AssignStmt js = (AssignStmt) gtSt;
                            List<Value> heapItemsWeReadFrom = dedupArrayList(getHeapRValues(js));
                            List<Value> heapItemsWeWriteTo = dedupArrayList(getHeapLValues(js));
                            if (heapItemsWeReadFrom.size() > 0) {
                                for (int i = 0; i < heapItemsWeReadFrom.size(); i++) {
                                    functionNameToRWDeps.get(uniqueFunctionSignature).get("reads")
                                            .add(heapItemsWeReadFrom.get(i).toString());
                                }

                            }

                            for (int i = 0; i < heapItemsWeWriteTo.size(); i++) {
                                functionNameToRWDeps.get(uniqueFunctionSignature).get("writes")
                                        .add(heapItemsWeWriteTo.get(i).toString());
                            }

                        } else {
                            // If we have any other statements, we extract the read values from the
                            // statement.
                            // List of Stmts to handle
                            /**
                             * https://www.sable.mcgill.ca/soot/doc/soot/jimple/Stmt.html
                             * http://www-labs.iro.umontreal.ca/~dufour/cours/ift6315/docs/soot-tutorial.pdf
                             * EnterMonitorStmt, ExitMonitorStmt , GotoStmt , IdentityStmt , IfStmt: get
                             * condition? , InvokeStmt: getInvokeExpr.getArgs , LookupSwitchStmt ??? ,
                             * MonitorStmt: ??? , NopStmt: no variables. can ignore. , RetStmt: no
                             * variables. can ignore. , ReturnStmt: todo., ReturnVoidStmt: no variables. can
                             * ignore. , TableSwitchStmt: ?? , ThrowStmt: throw new Exception. Could be
                             * reading from a variable.,
                             */
                            List<Value> heapItemsWeReadFrom = dedupArrayList(
                                    getReadValuesFromReadOnlyStmts((Stmt) unit));

                            for (int i = 0; i < heapItemsWeReadFrom.size(); i++) {
                                functionNameToRWDeps.get(uniqueFunctionSignature).get("reads")
                                        .add(heapItemsWeReadFrom.get(i).toString());
                            }

                        }

                    }

                }

                b.validate();

            }
        }));
        // Run Soot packs (note that our transformer pack is added to the phase "jtp")
        PackManager.v().runPacks();
        // Write the result of packs in outputPath
        PackManager.v().writeOutput();

        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        String jsonReadWWriteDeps = gson.toJson(functionNameToRWDeps);

        try {
            OutputStreamWriter myWriter = new OutputStreamWriter(new FileOutputStream(outputFileToWriteDepsTo),
                    Charset.forName("UTF-8").newEncoder());
            myWriter.write(jsonReadWWriteDeps);
            myWriter.close();
            System.out.format("Successfully wrote heap r/w to the file %s.\n", outputFileToWriteDepsTo);
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }

    }

    static List<Value> dedupArrayList(List<Value> input) {
        List<Value> output = new ArrayList<>();
        HashSet<String> seenValues = new HashSet<String>();
        for (Value v : input) {
            if (seenValues.contains(v.toString())) {
                continue;
            } else {
                seenValues.add(v.toString());
                output.add(v);
            }
        }
        return output;
    }

    static List<Value> getListOfAllowedValuesForNonThisReadDependencies(Body body) {
        Iterator<Unit> iterator = body.getUnits().snapshotIterator();
        // go through each identity section and track the set of variables that have
        // been created
        // only those variables can be used as a read dependency.
        Unit firstStmtOfBody = ((JimpleBody) body).getFirstNonIdentityStmt();
        boolean withinIdentityStmts = true;
        List<Value> listOfValuesWeAreAllowedToHaveReadDependencyFrom = new ArrayList<>();
        while (iterator.hasNext()) {
            Unit unit = iterator.next();
            if (unit.equals(firstStmtOfBody))
                withinIdentityStmts = false;
            if (!withinIdentityStmts)
                break;
            List<ValueBox> defBoxes = unit.getDefBoxes();
            for (ValueBox vb : defBoxes) {
                listOfValuesWeAreAllowedToHaveReadDependencyFrom.add(vb.getValue());
            }

        }
        // System.out.format("the set of variables from which we are allowed to have a read dependency is %s\n",
                // listOfValuesWeAreAllowedToHaveReadDependencyFrom);
        return listOfValuesWeAreAllowedToHaveReadDependencyFrom;
    }

    static List<Value> getHeapLValues(AssignStmt stmt) {
        List<Value> valuesToReturn = new ArrayList<>();
        Value lhs = stmt.getLeftOp();

        if (variableWeAreInterestedIn(lhs)) {
            valuesToReturn.add(lhs);
        } else if (lhs instanceof ArrayRef) {
            List<Value> valuesInArrayRef = getValuesFromArrayRef((ArrayRef) lhs);
            for (Value v : valuesInArrayRef) {
                valuesToReturn.add(v);
            }
        }

        return valuesToReturn;
    }

    static List<Value> getHeapRValues(AssignStmt stmt) {
        List<Value> valuesToReturn = new ArrayList<>();
        Value rhs = stmt.getRightOp();

        if (variableWeAreInterestedIn(rhs)) {
            valuesToReturn.add(rhs);
        } else if (rhs instanceof ArrayRef) {
            List<Value> valuesInArrayRef = getValuesFromArrayRef((ArrayRef) rhs);
            for (Value v : valuesInArrayRef) {
                valuesToReturn.add(v);
            }
        } else if (rhs instanceof Expr) {
            List<Value> valuesInExpr = getValuesFromExpr((Expr) rhs);
            for (Value v : valuesInExpr) {
                valuesToReturn.add(v);
            }
        }

        return valuesToReturn;
    }

    static List<Value> getValuesFromExpr(Expr e) {
        List<Value> values = new ArrayList<>();
        List<ValueBox> useBoxes = ((Expr) (e)).getUseBoxes();
        values = getValuesFromBoxes(useBoxes);
        List<Value> output = new ArrayList<>();
        for (Value v : values) {
            if (variableWeAreInterestedIn(v)) {
                output.add(v);
            }
        }
        return output;
    }

    static List<Value> getValuesFromArrayRef(ArrayRef input) {
        List<Value> output = new ArrayList<>();
        Value base = input.getBase();
        Value index = input.getIndex();
        if (variableWeAreInterestedIn(base)) {
            output.add(base);
        }
        if (variableWeAreInterestedIn(index)) {
            output.add(index);
        }
        return output;
    }

    static boolean variableWeAreInterestedIn(Value v) {
        if ((v instanceof InstanceFieldRef) || v instanceof StaticFieldRef) {
            return true;
        } else {
            return false;
        }
    }

    static List<Value> getValuesFromBoxes(List<ValueBox> valueBoxes) {
        List<Value> useValues = new ArrayList<>();
        for (ValueBox v : valueBoxes) {
            useValues.add(v.getValue());
        }
        return useValues;
    }

    static List<Value> getReadValuesFromReadOnlyStmts(Stmt stmt) {
        List<Value> listOfValues = new ArrayList<>();
        if (stmt instanceof IfStmt) {
            List<ValueBox> useBoxes = ((IfStmt) (stmt)).getUseBoxes();
            listOfValues = getValuesFromBoxes(useBoxes);
        } else if (stmt instanceof InvokeStmt) {
            List<ValueBox> useBoxes = ((InvokeStmt) (stmt)).getUseBoxes();
            listOfValues = getValuesFromBoxes(useBoxes);
            // System.out.format("Invoke stmt is %s\n", stmt);

        } else if (stmt instanceof LookupSwitchStmt) {
            List<ValueBox> useBoxes = ((LookupSwitchStmt) (stmt)).getUseBoxes();
            listOfValues = getValuesFromBoxes(useBoxes);
        } else if (stmt instanceof ReturnStmt) {
            List<ValueBox> useBoxes = ((ReturnStmt) (stmt)).getUseBoxes();
            listOfValues = getValuesFromBoxes(useBoxes);
        } else if (stmt instanceof TableSwitchStmt) {
            List<ValueBox> useBoxes = ((TableSwitchStmt) (stmt)).getUseBoxes();
            listOfValues = getValuesFromBoxes(useBoxes);
        }
        List<Value> output = new ArrayList<>();
        for (Value v : listOfValues) {
            if (variableWeAreInterestedIn(v)) {
                output.add(v);
            }
        }
        return output;
    }

}