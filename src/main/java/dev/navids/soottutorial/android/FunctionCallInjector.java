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
import java.util.Collections;
public class FunctionCallInjector {
    

    private final static String USER_HOME = System.getProperty("user.home");
    private static String androidJar = USER_HOME + "/Library/Android/sdk/platforms";
    static String androidDemoPath = System.getProperty("user.dir") + File.separator + "demo" + File.separator + "Android";
    static String apkPath = "";
    static String jsonPath = "";
    static String outputPath = androidDemoPath + File.separator + "/Instrumented";
    static HashMap<String, HashMap<String, List<String>>> functionNameToRWDeps = new HashMap<String, HashMap<String, List<String>>>(); // fn_name -> r/w -> list of vars
    FunctionCallInjector() {}
    
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
            System.out.println("I need one argument after FunctionCallInjector with the path to the apk file.\n./gradlew run --args=\"FunctionCallInjector path_to_apk_file path_to_heap_rw_json_file\"");
            return;
        }
        apkPath = args[0];
        jsonPath = args[1];
        // Initialize Soot
        InstrumentUtil.setupSoot(androidJar, apkPath, outputPath);
        FunctionCallInjector a = new FunctionCallInjector();
        
        
        
        
        // HashMap<String, HashMap<>> functionNameToRWDeps = new Hashmap<String, HashMap<String, List<String>>>(); 
        PackManager.v().getPack("jtp").add(new Transform("jtp.myLogger", new BodyTransformer() {
            @Override
            protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
                // First we filter out Android framework methods
                // if (!InstrumentUtil.isAndroidMethod(b.getMethod())) {
                //     System.out.format("MUR: method name is %s\n", b.getMethod().getSignature());
                // }
                if(InstrumentUtil.isAndroidMethod(b.getMethod()) || skipMethod(b.getMethod().getName())) {
                    return;
                } 
                load_heap_rw_hashmap(jsonPath);

                JimpleBody body = (JimpleBody) b;
                List<Value> paramRefs = b.getParameterRefs();
                String uniqueFunctionSignature = b.getMethod().getSignature();
                System.out.format("function signature is %s and num paramRefs is %d\n", uniqueFunctionSignature, paramRefs.size());
                
                List<String> heapReadDependencies;
                List<String> heapWriteDependencies;
                
                if (functionNameToRWDeps.containsKey(uniqueFunctionSignature)) {
                    heapReadDependencies = functionNameToRWDeps.get(uniqueFunctionSignature).get("reads");
                    heapWriteDependencies = functionNameToRWDeps.get(uniqueFunctionSignature).get("writes");
                } else {
                    heapReadDependencies = new ArrayList<>();
                    heapWriteDependencies = new ArrayList<>();
                }
                
                List<Unit> generatedForBeginningOfFunction = new ArrayList<Unit>();
                List<Unit> generatedForEndOfFunction = new ArrayList<Unit>();

                System.out.println("*************");
                System.out.println("Before modification");
                printAllUnits(body);
                System.out.println("*************");

                
                Iterator<Unit> iterator = body.getUnits().snapshotIterator();
                while(iterator.hasNext()){
                    Unit unit = iterator.next();
                    Stmt st = (Stmt)unit;
                    if (st instanceof AssignStmt) { 
                        final AssignStmt ast = (AssignStmt) st;
                        // this is an assign statement
                        // check if left side is a heap: if so, copy the value of the heap variable after this line to helper
                        // checkif right side is a function invoke: if so, add this function invoke to the helper (only if it is not r0.something as r0 means `this`)
                        Value lhs = ast.getLeftOp();
                        if (lhs instanceof InstanceFieldRef) {
                            System.out.format("MUR: lhs is an instance field %s\n", lhs);
                            // TODO: somehow call helper with the lhs value right after this unit
                        }

                        Value rhs = ast.getRightOp();
                        if(rhs instanceof InvokeExpr) {
                            // here, RHS is a function call. 
                            // send info about this function call to the helper
                            System.out.format("MUR: RHS is a function call %s\n", rhs);
                        }
                    } else if (unit instanceof InvokeStmt) {
                        // same as point 2 of above. 
                        // i.e., add function invoke to the helper (only if it is not r0.something as r0 means `this`)
                        InvokeExpr ie = ((InvokeStmt)unit).getInvokeExpr();
                        System.out.format("MUR: stmt is an invoke stmt %s\n", ie);
                    }
                }

                System.out.println("*************");
                System.out.println("After modification");
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

        // if (!methodName.contains("getSquareOfNumber")) return true;
        if (!methodName.contains("someFunctionWithNoReturn")) return true;

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

    public static Local findVariableMatchingNameInContext(String matchingName, Body body) {
        for (Local l : body.getLocals()) {
            if (l.toString().equals(matchingName)) {
                return l;
            }
        }
        System.out.println("ERROR: Unable to find a local matching the heap dependency.");
        return body.getThisLocal();
    }

    public static void insertAssignStmtsInThisSpotFortheMemoizedCase(Local variableName, Local variableValue, List<Unit> generatedForBeginningOfFunction, Body body) {
        

        // given r0.<com.example.simpleapptomemoize.MainActivity: int computedSquare>
        // get "r0"
        StaticInvokeExpr gettingBaseObjectName = AndroidLogger.getInvokeExprForAssignment("com.example.simpleapptomemoize.Helpers", "parseVariableNameAndGetBaseObjectName"
                                    , RefType.v("java.lang.String"), variableName);
        Local baseObjectAsString = AndroidLogger.generateFreshLocal(body, RefType.v("java.lang.String"));
        AssignStmt assignReturnVariableType = Jimple.v().newAssignStmt(baseObjectAsString, gettingBaseObjectName);
        generatedForBeginningOfFunction.add(assignReturnVariableType);

        // Local baseObject = findVariableMatchingNameInContext(baseObjectAsString.toString(), body); TODO: make this work instead of the following line
        Local baseObject = findVariableMatchingNameInContext("r0", body);


        // get class name as string
        StaticInvokeExpr gettingClassName = AndroidLogger.getInvokeExprForAssignment("com.example.simpleapptomemoize.Helpers", "parseVariableNameAndGetClassName"
                                    , RefType.v("java.lang.String"), variableName);
        Local classNameAsString = AndroidLogger.generateFreshLocal(body, RefType.v("java.lang.String"));
        AssignStmt assignClassNameAsString = Jimple.v().newAssignStmt(classNameAsString, gettingClassName);
        generatedForBeginningOfFunction.add(assignClassNameAsString);

        // get variable name as string
        StaticInvokeExpr gettingVariableName = AndroidLogger.getInvokeExprForAssignment("com.example.simpleapptomemoize.Helpers", "parseVariableNameAndGetVariableName"
                                    , RefType.v("java.lang.String"), variableName);
        Local varNameAsString = AndroidLogger.generateFreshLocal(body, RefType.v("java.lang.String"));
        AssignStmt assignVarNameAsString = Jimple.v().newAssignStmt(varNameAsString, gettingVariableName);
        generatedForBeginningOfFunction.add(assignVarNameAsString);

        
        
        StaticInvokeExpr assignVariableValueUsingReflectExpr = AndroidLogger.getInvokeExprForAssignment("com.example.simpleapptomemoize.Helpers", "setFieldValue", 	
                RefType.v("java.lang.String"), varNameAsString,
                RefType.v("java.lang.String"), classNameAsString,
                RefType.v("java.lang.Object"), baseObject,
                RefType.v("java.lang.Object"), variableValue);
        
        generatedForBeginningOfFunction.add(Jimple.v().newInvokeStmt(assignVariableValueUsingReflectExpr));

    }

    public static void insertReturnStmtsInThisSpotForTheMemoizedCase(Local returnVariableType, Local returnVariableValue, List<Unit> generatedForBeginningOfFunction, Body body) {
        
        String returnTypeOfFunction = body.getMethod().getReturnType().toString();
        if (returnTypeOfFunction.equals("void")) {
            ReturnVoidStmt returnStmt = Jimple.v().newReturnVoidStmt();
            generatedForBeginningOfFunction.add(returnStmt);
        } else if (isPrimitiveType(returnTypeOfFunction)) {
            soot.Type typeOfReturnLocal = getRefTypeFromDataType(returnTypeOfFunction); // now typeOfReturnLocal will be something like IntType.v()
            

            // first convert Object to Integer
            CastExpr castExpr1 = Jimple.v().newCastExpr(returnVariableValue, RefType.v(getBoxedTypeName(returnTypeOfFunction))); // cast the object to Integer
            Local temporaryTypeCastedLocal = AndroidLogger.generateFreshLocal(body, RefType.v(getBoxedTypeName(returnTypeOfFunction)));
            AssignStmt assignTempCastVariable = Jimple.v().newAssignStmt(temporaryTypeCastedLocal, castExpr1);
            generatedForBeginningOfFunction.add(assignTempCastVariable);


            // then do Integer.intValue and set it to someLocalToReturn
            Local someLocalToReturn = AndroidLogger.generateFreshLocal(body, typeOfReturnLocal);
            someLocalToReturn.setName("my_return");
            SpecialInvokeExpr unboxExpr = AndroidLogger.getInvokeSpecialExprForAssignment(temporaryTypeCastedLocal, getBoxedTypeName(returnTypeOfFunction), getUnboxFunctionName(returnTypeOfFunction));
            AssignStmt assignReturnToSomething = Jimple.v().newAssignStmt(someLocalToReturn, unboxExpr);
            generatedForBeginningOfFunction.add(assignReturnToSomething);

            
            ReturnStmt returnStmt = Jimple.v().newReturnStmt(someLocalToReturn);
            generatedForBeginningOfFunction.add(returnStmt);
            
            // according to https://github.com/soot-oss/soot/issues/375
            // we cannot do return (int)(Integer)(Object) x so we are doing .intValue

        } else { 
            // we use a generic java.lang.Object box
            Local someLocalToReturn = AndroidLogger.generateFreshLocal(body, RefType.v("java.lang.Object"));
            someLocalToReturn.setName("my_return");
            AssignStmt assignReturnToSomething = Jimple.v().newAssignStmt(someLocalToReturn, returnVariableValue);
            generatedForBeginningOfFunction.add(assignReturnToSomething);
            ReturnStmt returnStmt = Jimple.v().newReturnStmt(someLocalToReturn);
            generatedForBeginningOfFunction.add(returnStmt);
        }
        
        
    }

    public static void load_heap_rw_hashmap(String jsonFilePath) {
        try {
            Gson gson = new Gson();
            Reader reader = Files.newBufferedReader(Paths.get(jsonFilePath));
            Map<?, ?> map = gson.fromJson(reader, Map.class);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                // String function_name = entry.getKey();
                // String reads_and_writes = entry.getValue();
                // Map<?, ?> function_map = gson.fromJson(entry.getValue(), Map.class);
                // System.out.format("function map is %s\n", function_map);
                // System.out.println(entry.getKey() + "=" + entry.getValue());
            }
            reader.close();
        }  catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void printHeapRWInfo(List<String> heapReadDependencies, List<String> heapWriteDependencies) {
        System.out.format("\t There are %d read dependencies and %d write dependencies.\n", heapReadDependencies.size(), heapWriteDependencies.size());
    }

    public static boolean isPrimitiveType(String dataType) {
        if (dataType.equals("int")) {
            return true;
        } else if (dataType.equals("float")) {
            return true;
        } else if (dataType.equals("double")) {
            return true;
        } else if (dataType.equals("boolean")) {
            return true;
        } else if (dataType.equals("char")) {
            return true;
        } else if (dataType.equals("byte")) {
            return true;
        } else if (dataType.equals("long")) {
            return true;
        } else if (dataType.equals("short")) {
            return true;
        } else { 
            return false;
        }
    }

    public static soot.Type getRefTypeFromDataType(String dataType) {
        if (dataType.equals("int")) {
            return IntType.v(); 
        } else if (dataType.equals("float")) {
            return FloatType.v();
        } else if (dataType.equals("double")) {
            return DoubleType.v();
        } else if (dataType.equals("boolean")) {
            return BooleanType.v();
        } else if (dataType.equals("char")) {
            return CharType.v();
        } else if (dataType.equals("byte")) {
            return ByteType.v();
        } else if (dataType.equals("long")) {
            return LongType.v();
        } else if (dataType.equals("short")) {
            return ShortType.v();
        } else { 
            return RefType.v("java.lang.Object");
        }
    }

    public static String getBoxedTypeName(String dataType) {
        if (dataType.equals("int")) {
            return "java.lang.Integer"; 
        } else if (dataType.equals("float")) {
            return "java.lang.Float"; 
        } else if (dataType.equals("double")) {
            return "java.lang.Double"; 
        } else if (dataType.equals("boolean")) {
            return "java.lang.Boolean"; 
        } else if (dataType.equals("char")) {
            return "java.lang.Character"; 
        } else if (dataType.equals("byte")) {
            return "java.lang.Byte"; 
        } else if (dataType.equals("long")) {
            return "java.lang.Long"; 
        } else if (dataType.equals("short")) {
            return "java.lang.Short"; 
        } else { 
            return dataType;
        }
    }

    public static String getUnboxFunctionName(String dataType) {
        if (dataType.equals("int")) {
            return "intValue"; 
        } else if (dataType.equals("float")) {
            return "floatValue"; 
        } else if (dataType.equals("double")) {
            return "doubleValue"; 
        } else if (dataType.equals("boolean")) {
            return "booleanValue"; 
        } else if (dataType.equals("char")) {
            return "charValue"; 
        } else if (dataType.equals("byte")) {
            return "byteValue"; 
        } else if (dataType.equals("long")) {
            return "longValue"; 
        } else if (dataType.equals("short")) {
            return "shortValue"; 
        } else { 
            return dataType;
        }
    }
    
    public static void printALocal(Local itemToPrint, List<Unit> generated, Body body) {
        List<Value> parameter = new ArrayList<>();
        parameter.add(itemToPrint);
        Pair<Value, List<Unit>> arrayRefAndInstrumentation = AndroidLogger.generateParameterArray(parameter, body);
				
        List<Unit> generatedArrayInstrumentation = arrayRefAndInstrumentation.getSecond();
        Value arrayRef = arrayRefAndInstrumentation.getFirst();
        
        Unit generatedInvokeStmt = AndroidLogger.makeJimpleStaticCallForPathExecution("com.example.simpleapptomemoize.Helpers", "printValues", 	
        AndroidLogger.getParameterArrayType(), (parameter.isEmpty())? NullConstant.v() : arrayRef);
        
        
        generated.addAll(generatedArrayInstrumentation);
        generated.add(generatedInvokeStmt);
    }
}