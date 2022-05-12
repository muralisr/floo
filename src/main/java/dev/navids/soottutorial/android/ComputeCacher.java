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
public class ComputeCacher {
    

    private final static String USER_HOME = System.getProperty("user.home");
    private static String androidJar = USER_HOME + "/Library/Android/sdk/platforms";
    static String androidDemoPath = System.getProperty("user.dir") + File.separator + "demo" + File.separator + "Android";
    static String apkPath = "";
    static String jsonPath = "";
    static String outputPath = androidDemoPath + File.separator + "/Instrumented";
    static HashMap<String, HashMap<String, List<String>>> functionNameToRWDeps = new HashMap<String, HashMap<String, List<String>>>(); // fn_name -> r/w -> list of vars
    ComputeCacher() {}
    
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
            System.out.println("I need one argument after ComputeCacher with the path to the apk file.\n./gradlew run --args=\"ComputeCacher path_to_apk_file path_to_heap_rw_json_file\"");
            return;
        }
        apkPath = args[0];
        jsonPath = args[1];
        // Initialize Soot
        InstrumentUtil.setupSoot(androidJar, apkPath, outputPath);
        ComputeCacher a = new ComputeCacher();
        
        
        
        
        // HashMap<String, HashMap<>> functionNameToRWDeps = new Hashmap<String, HashMap<String, List<String>>>(); 
        PackManager.v().getPack("jtp").add(new Transform("jtp.myLogger", new BodyTransformer() {
            @Override
            protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
                try {
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
                
                // TODO: finish the gson file loading part
                printHeapRWInfo(heapReadDependencies, heapWriteDependencies);
                
                List<Unit> generatedForBeginningOfFunction = new ArrayList<Unit>();
                List<Unit> generatedForEndOfFunction = new ArrayList<Unit>();

                System.out.println("*************");
                System.out.println("Before modification");
                printAllUnits(body);
                System.out.println("*************");

                Unit firstStmtOfBody = body.getFirstNonIdentityStmt();
                // insert statements
                // 1. string x = generate unique rep for this invocation
                // 2. bool b = should i skip this 
                // 3. if b is false: goto first line
                // 4. else do what the memo table says
                // 5. at the end of 3 (i.e. existing first line's block), store items into the memo table. all heap writes & return value


                // for 5, we need all the exit points of the functions
                // before we make any changes, let us get this list of exit points
                Iterator<Unit> iterator = body.getUnits().snapshotIterator();
                List<Unit> returnStmtsInTheOriginalBody = new ArrayList<>();
                while(iterator.hasNext()){
                    Stmt stmt = (Stmt)(iterator.next());
                    if(stmt instanceof ReturnStmt || stmt instanceof ReturnVoidStmt) {
                        returnStmtsInTheOriginalBody.add(stmt);
                    }
                }


                // 1. get the unique rep for this invocation. i.e. string x = generate unique rep for this invocation
                List<Value> thisFunctionDependsOnThisListOfObjects = new ArrayList<>();

                // get parameters and put it into the list of dependencies
                int numOfParams = b.getMethod().getParameterCount();
                for(int i = 0; i < numOfParams; i++) {
                    Value v = body.getParameterLocal(i);
                    Value properParamObject = AndroidLogger.generateCorrectObject(b, v, generatedForBeginningOfFunction); // if v is a primitive, make it an object. else, keep it an object
                    Local paramLocal = AndroidLogger.generateFreshLocal(body, RefType.v("java.lang.Object"));
                    Unit newAssignStmt = Jimple.v().newAssignStmt(paramLocal, properParamObject);
                    thisFunctionDependsOnThisListOfObjects.add(paramLocal);
                    generatedForBeginningOfFunction.add(newAssignStmt);
                }

                // get the heap reads and put it into the list of dependencies
                List<String> heapReadsFunctionDependsOn = new ArrayList<>();
                
                
                heapReadsFunctionDependsOn.add("r0.<com.example.simpleapptomemoize.MainActivity: int computedSquare>"); 
                heapReadsFunctionDependsOn.add("r0.<com.example.simpleapptomemoize.MainActivity: String xyz>"); 
                // uncomment this to see how the heap read dependency works

                for(String dependency : heapReadsFunctionDependsOn) {
                    HeapDependency hd = new HeapDependency(dependency);
                    // we have a heap read. 
                    // this means we have an object dot variable name
                    // hd.baseName tells us what the object is. it could be `this`, or something else
                    Local baseNameToUse = findVariableMatchingNameInContext(hd.baseName, body);
                    SootClass c = RefType.v(hd.className).getSootClass();
                    SootField f = c.getFieldByName(hd.fieldName);
                    InstanceFieldRef ifr = Jimple.v().newInstanceFieldRef(baseNameToUse, f.makeRef());
                    if (isPrimitiveType(hd.fieldType)) { // if primitive, use as it is
                        Local localRepOfHeapRead = AndroidLogger.generateFreshLocal(body, getRefTypeFromDataType(hd.fieldType));
                        Unit newAssignStmt = Jimple.v().newAssignStmt(localRepOfHeapRead, ifr);
                        thisFunctionDependsOnThisListOfObjects.add(localRepOfHeapRead);
                        generatedForBeginningOfFunction.add(newAssignStmt);
                    } else { // else it is an object. use its hashCode
                        Local localRepOfHeapRead = AndroidLogger.generateFreshLocal(body, getRefTypeFromDataType(hd.fieldType));
                        Unit newAssignStmt = Jimple.v().newAssignStmt(localRepOfHeapRead, ifr);
                        generatedForBeginningOfFunction.add(newAssignStmt);
                        SpecialInvokeExpr gettingHashCodeExpr = AndroidLogger.getInvokeSpecialExprForAssignment(localRepOfHeapRead, 
                                                                                    "java.lang.Object",
                                                                                    "hashCode");
                        Local localRepOfHashCodeOfHeapRead = AndroidLogger.generateFreshLocal(body, IntType.v());
                        Unit newAssignStmt2 = Jimple.v().newAssignStmt(localRepOfHashCodeOfHeapRead, gettingHashCodeExpr);
                        generatedForBeginningOfFunction.add(newAssignStmt2);
                        thisFunctionDependsOnThisListOfObjects.add(localRepOfHashCodeOfHeapRead);
                        
                    }
                    
                }

                // put the function name into the list of dependencies
                String functionName = String.format("%s", body.getMethod().getSignature());
                Value functionNameAsValue = StringConstant.v(functionName);
                Local functionNameAsLocal = AndroidLogger.generateFreshLocal(body, RefType.v("java.lang.Object"));
                Unit functionNameAssignStmt = Jimple.v().newAssignStmt(functionNameAsLocal, functionNameAsValue);
                thisFunctionDependsOnThisListOfObjects.add(functionNameAsLocal);
                generatedForBeginningOfFunction.add(functionNameAssignStmt);

                Pair<Value, List<Unit>> arrayRefAndInstrumentation = AndroidLogger.generateParameterArray(thisFunctionDependsOnThisListOfObjects, body);
                List<Unit> generatedArrayInstrumentation = arrayRefAndInstrumentation.getSecond();
                Value arrayRef = arrayRefAndInstrumentation.getFirst();
                StaticInvokeExpr uniqueRepGenerateMethodInvokeExpr = AndroidLogger.getInvokeExprForAssignment("com.example.simpleapptomemoize.MemoTable", "generateHashMapKey", 	
                AndroidLogger.getParameterArrayType(), (thisFunctionDependsOnThisListOfObjects.isEmpty())? NullConstant.v() : arrayRef);

                Local uniqueStringRepresentation = AndroidLogger.generateFreshLocal(body, RefType.v("java.lang.String"));
                Unit unqiueRepAssignStmt = Jimple.v().newAssignStmt(uniqueStringRepresentation, (StaticInvokeExpr)uniqueRepGenerateMethodInvokeExpr);
                generatedForBeginningOfFunction.addAll(generatedArrayInstrumentation);
                generatedForBeginningOfFunction.add(unqiueRepAssignStmt);

                // at this point, uniqueStringRepresentation will contain a unique representation for the current function invocation

                // 2. bool b = should i skip this 
                // i am going to use the unique string rep. to determine if this function invocation has been seen before


                StaticInvokeExpr shouldSkipDecisionInvokeExpr = AndroidLogger.getInvokeExprForAssignment("com.example.simpleapptomemoize.MemoTable", "shouldISkipThisExecution", 	
                RefType.v("java.lang.String"), uniqueStringRepresentation);
                Local shouldSkipDecision = AndroidLogger.generateFreshLocal(body, IntType.v()); // 0 is false, 1 is true.
                shouldSkipDecision.setName("should_skip_decision");
                Unit shouldSkipDecisionAssignStmt = Jimple.v().newAssignStmt(shouldSkipDecision, (StaticInvokeExpr)shouldSkipDecisionInvokeExpr);
                
                generatedForBeginningOfFunction.add(shouldSkipDecisionAssignStmt);

                NopStmt nop = Jimple.v().newNopStmt(); // explanation for how this is a few lines down
                // ?. this is linked to number 3. this inserts a nop just before the original code so we use this to jump to
                body.getUnits().insertBefore(nop, firstStmtOfBody); // we use this nop stmt to distinguish the if and else blocks


                // 3. if the boolean is false, go to the original code block

                EqExpr equalExpr = Jimple.v().newEqExpr(shouldSkipDecision, IntConstant.v(0)); // checking if the boolean is 0. if it is 0, it is false, so it means should not skip
                
                IfStmt ifStmt = Jimple.v().newIfStmt(equalExpr, nop); // if bool == 0, then do nop; i.e. jump to the original code block (look at the multi line comment for explanation)
                generatedForBeginningOfFunction.add(ifStmt);

                // 4. boolean was true; so do what memo table says
                
                Local retrievedCacheEntry = AndroidLogger.generateFreshLocal(body, RefType.v("com.example.simpleapptomemoize.CacheEnty"));
                StaticInvokeExpr getCacheEntryFromTable = AndroidLogger.getInvokeExprForAssignment("com.example.simpleapptomemoize.MemoTable", "getExistingCacheEntry",
                                                                RefType.v("java.lang.String"), uniqueStringRepresentation);
                Unit stmtToGetExistingCacheEntry = Jimple.v().newAssignStmt(retrievedCacheEntry, getCacheEntryFromTable);
                generatedForBeginningOfFunction.add(stmtToGetExistingCacheEntry);

                
                // basically if i do this, then at runtime i will have access errors
                // SpecialInvokeExpr askCacheEntryToDoAllTasks = AndroidLogger.getInvokeSpecialExprForAssignment(retrievedCacheEntry, 
                                                                                    // "com.example.simpleapptomemoize.CacheEnty",
                                                                                    // "getAllHeapWriteVariableInfo");

                // TODO: implement function replay

                
                // TODO: function replay and heap writes would be interleaved. 

                Value heapVariableWriteInfo = AndroidLogger.generateFreshLocal(body, AndroidLogger.getParameterArrayType());
                SpecialInvokeExpr getHeapVariableWrites = AndroidLogger.getInvokeSpecialExprForAssignment(retrievedCacheEntry, 
                                                                                    "com.example.simpleapptomemoize.CacheEnty",
                                                                                    "getAllHeapWriteVariableInfo");
                AssignStmt assignHeapVariableWriteInfo = Jimple.v().newAssignStmt(heapVariableWriteInfo, getHeapVariableWrites);  
                generatedForBeginningOfFunction.add(assignHeapVariableWriteInfo);     
                
                Value numberOfVariableHeapWrites = AndroidLogger.generateFreshLocal(body, IntType.v());                                                             
                SpecialInvokeExpr getNumOfHeapVariableWrites = AndroidLogger.getInvokeSpecialExprForAssignment(retrievedCacheEntry, 
                                                                                    "com.example.simpleapptomemoize.CacheEnty",
                                                                                    "getNumberOfHeapVariableWrites");
                AssignStmt assignNumHeapVariableWriteInfo = Jimple.v().newAssignStmt(numberOfVariableHeapWrites, getNumOfHeapVariableWrites);
                generatedForBeginningOfFunction.add(assignNumHeapVariableWriteInfo);
                
                // temporarily trying to print the heap writes from memo

                // TODO: we hardcode 0 here but need to have a loop and go over all items
			    ArrayRef indexOfHeapVariableWrite = Jimple.v().newArrayRef(heapVariableWriteInfo, IntConstant.v(0)); 
                Local heapWriteInfoLocal = AndroidLogger.generateFreshLocal(body, RefType.v("java.lang.Object"));
                AssignStmt simpleReadFromArray = Jimple.v().newAssignStmt(heapWriteInfoLocal, indexOfHeapVariableWrite);
                generatedForBeginningOfFunction.add(simpleReadFromArray);
                SootClass sootClass = Scene.v().getSootClass("com.example.simpleapptomemoize.MemoTable");
				SootMethod valueOfMethod = sootClass.getMethodByName("simplePrintHelper");
																
                // uncomment this to see the heap write info printed out
				// StaticInvokeExpr simplePrintCaller = Jimple.v().newStaticInvokeExpr(valueOfMethod.makeRef(), heapWriteInfoLocal);
                // generatedForBeginningOfFunction.add(Jimple.v().newInvokeStmt(simplePrintCaller));
                // TODO. we assume heapWriteInfoLocal is a VariableAssignmentOperation because i know index 0 is a variable assignment
                // but it could be either. need to handle both

                CastExpr castingToVariableAssignmentOperation = Jimple.v().newCastExpr(heapWriteInfoLocal, RefType.v("com.example.simpleapptomemoize.VariableAssignOperation"));
                Local heapWriteInfoAfterCasting = AndroidLogger.generateFreshLocal(body, RefType.v("com.example.simpleapptomemoize.VariableAssignOperation"));
                AssignStmt simpleCastAssignment = Jimple.v().newAssignStmt(heapWriteInfoAfterCasting, castingToVariableAssignmentOperation);
                generatedForBeginningOfFunction.add(simpleCastAssignment);
                
                Local variableNameToWriteTo = AndroidLogger.generateFreshLocal(body, RefType.v("java.lang.String"));
                Local variableValueToWriteTo = AndroidLogger.generateFreshLocal(body, RefType.v("java.lang.Object"));

                SpecialInvokeExpr getVariableNameToWriteToExpr = AndroidLogger.getInvokeSpecialExprForAssignment(heapWriteInfoAfterCasting, 
                                                                                    "com.example.simpleapptomemoize.VariableAssignOperation",
                                                                                    "getVariableName");
                AssignStmt assigningNameOfVariableToWriteTo = Jimple.v().newAssignStmt(variableNameToWriteTo, getVariableNameToWriteToExpr);
                generatedForBeginningOfFunction.add(assigningNameOfVariableToWriteTo);
                SpecialInvokeExpr getVariableValueToWriteToExpr = AndroidLogger.getInvokeSpecialExprForAssignment(heapWriteInfoAfterCasting, 
                                                                                    "com.example.simpleapptomemoize.VariableAssignOperation",
                                                                                    "getVariableValue");
                AssignStmt assigningValueOfVariableToWriteTo = Jimple.v().newAssignStmt(variableValueToWriteTo, getVariableValueToWriteToExpr);
                generatedForBeginningOfFunction.add(assigningValueOfVariableToWriteTo);

                insertAssignStmtsInThisSpotFortheMemoizedCase(variableNameToWriteTo, variableValueToWriteTo, generatedForBeginningOfFunction, body);

                
                
                
                /** at this point, we have inserted stmts about replaying the heap writes. */
                /** now we're going to replay stmts about the return value */
                
                // getting the return variable's type and value here
                Local returnVariableType = AndroidLogger.generateFreshLocal(body, RefType.v("java.lang.String"));
                Local returnVariableValue = AndroidLogger.generateFreshLocal(body, RefType.v("java.lang.Object"));

                SpecialInvokeExpr getReturnVariableType = AndroidLogger.getInvokeSpecialExprForAssignment(retrievedCacheEntry, 
                                                                                    "com.example.simpleapptomemoize.CacheEnty",
                                                                                    "getReturnType");

                AssignStmt assignReturnVariableType = Jimple.v().newAssignStmt(returnVariableType, getReturnVariableType);
                generatedForBeginningOfFunction.add(assignReturnVariableType);
                SpecialInvokeExpr getReturnVariableValue = AndroidLogger.getInvokeSpecialExprForAssignment(retrievedCacheEntry, 
                                                                                    "com.example.simpleapptomemoize.CacheEnty",
                                                                                    "getReturnValue");
                AssignStmt assignReturnVariableValue = Jimple.v().newAssignStmt(returnVariableValue, getReturnVariableValue);
                generatedForBeginningOfFunction.add(assignReturnVariableValue);

                // now returnVariableType has the data-type. which can be found from the method signature anyway. 
                // and returnVariableValue has the value we should return from the memo table. 

                insertReturnStmtsInThisSpotForTheMemoizedCase(returnVariableType, returnVariableValue, generatedForBeginningOfFunction, body);

                
                
                // the structure after modification looks like this
                /***
                 * if function is not previously seen, jump to nop
                 *    function was seen already
                 * nop
                 * original function code
                 */
                
                
                
                body.getUnits().insertBefore(generatedForBeginningOfFunction, nop);
                ifStmt.setTarget(nop);

               
               
                // 5. now, in the original function code,
                // for every exit point (i.e a return stmt)
                // add a call to our helper with everything that needs to be cached 
                // we will add such a call to generatedForEndOfFunction
                // and then insert it every time we see a return stmt (but with the return value changing every time)
                
                // key to store in memo table is uniqueStringRepresentation
                // value to store in memo table is CacheEntry
                
                UnitPatchingChain units = b.getUnits();
                for(int i = returnStmtsInTheOriginalBody.size() - 1; i >= 0; i--) {
                    generatedForEndOfFunction = new ArrayList<Unit>();
                    // this line basically does this: CacheEntry instance = new CacheEntry();
                    Local instanceOfCacheEntryClass = AndroidLogger.generateFreshLocal(body, RefType.v("com.example.simpleapptomemoize.CacheEnty"));
                    StaticInvokeExpr instantiatingCacheEntryStmt = AndroidLogger.getInvokeExprForAssignment("com.example.simpleapptomemoize.MemoTable", "getNewCacheEntry");
                    Unit stmtToInstantiateNewCacheEntryInstance = Jimple.v().newAssignStmt(instanceOfCacheEntryClass, instantiatingCacheEntryStmt);
                    generatedForEndOfFunction.add(stmtToInstantiateNewCacheEntryInstance);

                    List<String> heapWritesFunctionDoes = new ArrayList<>(); // TODO: get the heap writes from gson
                    heapWritesFunctionDoes.add("r0.<com.example.simpleapptomemoize.MainActivity: int computedSquare>"); 
                    for(String heapWrite : heapWritesFunctionDoes) {
                        HeapDependency hd = new HeapDependency(heapWrite);
                        // we have a heap read. 
                        // this means we have an object dot variable name
                        // hd.baseName tells us what the object is. it could be `this`, or something else
                        Local baseNameToUse = findVariableMatchingNameInContext(hd.baseName, body);
                        SootClass c = RefType.v(hd.className).getSootClass();
                        SootField f = c.getFieldByName(hd.fieldName);
                        InstanceFieldRef ifr = Jimple.v().newInstanceFieldRef(baseNameToUse, f.makeRef());
                        Local localRepOfHeapWrite = AndroidLogger.generateFreshLocal(body, getRefTypeFromDataType(hd.fieldType));
                        Unit newAssignStmt = Jimple.v().newAssignStmt(localRepOfHeapWrite, ifr);
                        generatedForEndOfFunction.add(newAssignStmt);
                        
                        List<Value> parameters = new ArrayList<>();
                        parameters.add(StringConstant.v(heapWrite));
                        parameters.add(localRepOfHeapWrite);
                        Pair<Value, List<Unit>> arrayRefAndInstrumentation1 = AndroidLogger.generateParameterArray(parameters, body);
                        List<Unit> generatedArrayInstrumentation1 = arrayRefAndInstrumentation1.getSecond();
                        Value arrayRef1 = arrayRefAndInstrumentation1.getFirst();
                        SpecialInvokeExpr settingHeapWriteInCacheEntry = AndroidLogger.getInvokeSpecialExprForAssignment(instanceOfCacheEntryClass, "com.example.simpleapptomemoize.CacheEnty", "addCacheWriteVariableInfo",
                            AndroidLogger.getParameterArrayType(), (parameters.isEmpty())? NullConstant.v() : arrayRef1);
                        generatedForEndOfFunction.addAll(generatedArrayInstrumentation1);
                        generatedForEndOfFunction.add(Jimple.v().newInvokeStmt(settingHeapWriteInCacheEntry));                                    
                    }

                    
                    // we use correct variable value here but box everything into an Object
                    Stmt rtSt = (Stmt)(returnStmtsInTheOriginalBody.get(i));
                    Value variableValueToStore = AndroidLogger.generateCorrectObject(body, NullConstant.v(), generatedForEndOfFunction);
                    if (rtSt instanceof ReturnVoidStmt) {
                        variableValueToStore = AndroidLogger.generateCorrectObject(body, NullConstant.v(), generatedForEndOfFunction);
                    } else {
                        variableValueToStore = AndroidLogger.generateCorrectObject(body, ((ReturnStmt)(rtSt)).getOp(), generatedForEndOfFunction);
                    }
                    
                    Value variableTypeToStore = StringConstant.v(body.getMethod().getReturnType().toString());
                    

                    SpecialInvokeExpr settingReturnValueInCacheEntry = AndroidLogger.getInvokeSpecialExprForAssignment(instanceOfCacheEntryClass, 
                                                                                    "com.example.simpleapptomemoize.CacheEnty",
                                                                                    "setReturnTypeAndValue",
                                                                                    RefType.v("java.lang.String"), variableTypeToStore,
                                                                                    RefType.v("java.lang.Object"), variableValueToStore);


                    
                    generatedForEndOfFunction.add(Jimple.v().newInvokeStmt(settingReturnValueInCacheEntry));


                    // this line does storeItemsInMemoTable(key, value)
                    Unit putValuesIntoMemoTableExpr = AndroidLogger.makeJimpleStaticCallForPathExecution("com.example.simpleapptomemoize.MemoTable", "storeItemsInMemoTable", 	
                    RefType.v("java.lang.String"), uniqueStringRepresentation, RefType.v("com.example.simpleapptomemoize.CacheEnty"), instanceOfCacheEntryClass);
                    generatedForEndOfFunction.add(putValuesIntoMemoTableExpr);

                    units.insertBefore(generatedForEndOfFunction, returnStmtsInTheOriginalBody.get(i));
                }
                


                System.out.println("*************");
                System.out.println("After modification");
                System.out.println("*************");
                printAllUnits(body);
                


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
    }



    public static boolean skipMethod(String methodName) {

        // if (!methodName.contains("getSquareOfNumber")) return true;
        // if (!methodName.contains("someFunctionWithNoReturn")) return true;

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
        System.out.println("***Printing all units***");
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