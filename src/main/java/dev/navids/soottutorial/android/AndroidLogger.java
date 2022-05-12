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

import java.io.Serializable;

public class AndroidLogger {

    private static String androidJar = "/disk/Android/Sdk/platforms";
    static String androidDemoPath = System.getProperty("user.dir") + File.separator + "demo" + File.separator + "Android";
    static String apkPath = androidDemoPath + File.separator + "/sootForFuncArgWithDynamicArg.apk";
    static String outputPath = androidDemoPath + File.separator + "/Instrumented";
    AndroidLogger() {}
    public static void main(String[] args){

        if(System.getenv().containsKey("ANDROID_HOME"))
            androidJar = System.getenv("ANDROID_HOME")+ File.separator+"platforms";
        // Clean the outputPath
        final File[] files = (new File(outputPath)).listFiles();
        if (files != null && files.length > 0) {
            Arrays.asList(files).forEach(File::delete);
        }
        // Initialize Soot
        InstrumentUtil.setupSoot(androidJar, apkPath, outputPath);
        AndroidLogger a = new AndroidLogger();
        // Add a transformation pack in order to add the statement "System.out.println(<content>) at the beginning of each Application method
        PackManager.v().getPack("jtp").add(new Transform("jtp.myLogger", new BodyTransformer() {
            @Override
            protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
                // First we filter out Android framework methods
                if(InstrumentUtil.isAndroidMethod(b.getMethod()) || b.getMethod().getName().equals("printTimer")) {
                    return;
                } else if ((! b.getMethod().getName().equals("onCreate")) && (! b.getMethod().getName().equals("simpleFunction"))) {
                    // we only want to instrument this one specific function
                    // so only work on the oncreate function or simpleFunction
                    return;
                }
                System.out.format("function signature is %s\n", b.getMethod().getSignature());
                
                
                JimpleBody body = (JimpleBody) b;
                Iterator<Unit> iterator = body.getUnits().snapshotIterator();
                
                /* for heap rw begin */
                UnitPatchingChain units = b.getUnits();
                String heapState = "";
                List<Unit> generatedUnits = new ArrayList<>();
                /* for heap rw end */
                
                while(iterator.hasNext()){
                    Unit unit = iterator.next();
                    if(unit instanceof InvokeStmt && unit.toString().contains("simpleFunction")) {
                        System.out.format("Calling our instrumenter for unit %s\n", unit);

                        // this is for printing all params
                        // a.instrumentInfoAboutNonApiCaller(body, unit);


                        // this is for printing all heap reads
                        String varWeReadInFunc = "r0.<com.example.sootforfuncargs.MainActivity: java.lang.String heapVar2>";
                        a.instrumentFunctionCallAndInsertHeapReadBeforeIt(varWeReadInFunc, body, unit);
                        
                        System.out.format("skip calling instrumenter");
                        System.out.println("Finished calling our instrumenter");
                    } if(unit instanceof Stmt) { // calling heap stuff from shagha
                        /* for heap rw begin */
                        Stmt gtSt = (Stmt) unit;
                        if(gtSt instanceof AssignStmt && !gtSt.toString().contains("PrintStream")) {
                            System.out.format("we have seen an assign stmt which is not a printStmt but is a %s\n", gtSt.toString());
                            final AssignStmt js = (AssignStmt) gtSt;
                            List<Value> heapItemsWeReadFrom = getHeapRValues(js);
                            if (heapItemsWeReadFrom.size() > 0) {
                                // a.instrumentHeapReadLine(body, unit, heapItemsWeReadFrom);
                                System.out.format("we are in some heap read stmt %s\n.", heapItemsWeReadFrom.get(0)); 
                                // this returns string r0.<com.example.sootforfuncargs.MainActivity: java.lang.String heapVar2>
                                // now using this string, how to get the value prior to function call? 
                            }
                        }
                        /* for heap rw end */
                    }
                        
                }	
                /* for heap rw begin */
                String content = String.format("%s [%s]", InstrumentUtil.TAG, body.getMethod().getSignature());;
                if(heapState != "") {
                    content += ": " + heapState;
                }
                // In order to call "System.out.println" we need to create a local containing "System.out" value
                Local psLocal = InstrumentUtil.generateNewLocal(body, RefType.v("java.io.PrintStream"));
                // Now we assign "System.out" to psLocal
                SootField sysOutField = Scene.v().getField("<java.lang.System: java.io.PrintStream out>");
                AssignStmt sysOutAssignStmt = Jimple.v().newAssignStmt(psLocal, Jimple.v().newStaticFieldRef(sysOutField.makeRef()));
                generatedUnits.add(sysOutAssignStmt);

                // Create println method call and provide its parameter
                SootMethod printlnMethod = Scene.v().grabMethod("<java.io.PrintStream: void println(java.lang.String)>");
                Value printlnParamter = StringConstant.v(content);
                InvokeStmt printlnMethodCallStmt = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(psLocal, printlnMethod.makeRef(), printlnParamter));
                generatedUnits.add(printlnMethodCallStmt);

                // Insert the generated statement before the first  non-identity stmt
                units.insertBefore(generatedUnits, body.getFirstNonIdentityStmt());
                /* for heap rw end */

                /**
                 * added by murali
                 * 
                 */

                // int num_params = body.getMethod().getParameterCount();
                // String info_about_params = "NO PARAMETERS";
                // if (num_params >= 1) {
                // //  info_about_params = String.format("we have %d params with 0th param lhs being %s", num_params, body.getParameterLocal(0).getName());
                //     System.out.format("we have %d params\n", num_params);
                //     for(int i = 0; i < num_params; i++) {
                //         System.out.format("param no. %d is %s\n", i, body.getParameterLocal(i).getName());
                //     }
                // }
                // String invokeExprMethodSignature = b.getMethod().getSignature();
				
				// List<Value> parameter = b.getArgs();
				// List<Unit> generated = new ArrayList<Unit>();
				// Pair<Value, List<Unit>> arrayRefAndInstrumentation = AndroidLogger.generateParameterArray(parameter, body);
				
				// List<Unit> generatedArrayInstrumentation = arrayRefAndInstrumentation.getSecond();
				// Value arrayRef = arrayRefAndInstrumentation.getFirst();
			

                /**
                 * 
                 * end added by murali
                 */
                /*

                // The message that we want to log
                String content = String.format("%s %s", InstrumentUtil.TAG, body.getMethod().getSignature());
                // In order to call "System.out.println" we need to create a local containing "System.out" value
                // Local psLocal = InstrumentUtil.generateNewLocal(body, RefType.v("java.io.PrintStream"));
                // Now we assign "System.out" to psLocal
                SootField sysOutField = Scene.v().getField("<java.lang.System: java.io.PrintStream out>");
                AssignStmt sysOutAssignStmt = Jimple.v().newAssignStmt(psLocal, Jimple.v().newStaticFieldRef(sysOutField.makeRef()));
                generatedUnits.add(sysOutAssignStmt);

                // Create println method call and provide its parameter
                SootMethod printlnMethod = Scene.v().grabMethod("<java.io.PrintStream: void println(java.lang.String)>");
                Value printlnParamter = StringConstant.v(content);
                InvokeStmt printlnMethodCallStmt = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(psLocal, printlnMethod.makeRef(), printlnParamter));
                generatedUnits.add(printlnMethodCallStmt);

             */
                 /**
                  * murali option2 begin
                  */
                //   String f_enter = String.format("%s %s", b.getMethod().getSignature(), "ENTER");
                //   String f_exit = String.format("%s %s", b.getMethod().getSignature(), "EXIT");
                //   Value printParam1 = StringConstant.v(f_enter);
                //   Value printParam2 = StringConstant.v(f_exit);
                //   SootMethod myInstrumentMethod = Scene.v().grabMethod("<com.example.mytimerprinter.MuraliAnnotation: void printTimer(java.lang.String)>");
                //   InvokeStmt myInstrumentPrintEnter = Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(myInstrumentMethod.makeRef(), printParam1));
                //   InvokeStmt myInstrumentPrintExit = Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(myInstrumentMethod.makeRef(), printParam2));
                //   generatedUnitsEnter.add(myInstrumentPrintEnter);
                //   generatedUnitsExit.add(myInstrumentPrintExit);
                  /**
                  * murali option2 end
                  */

                
                /**
                  * murali for timestamp begin
                  */
                //   Local longLocal = InstrumentUtil.generateNewLocal(body, LongType.v());
                //   SootMethod currentTimeNano = Scene.v().getMethod("<java.lang.System: long nanoTime()>");		
                //   StaticInvokeExpr timeInvoke = Jimple.v().newStaticInvokeExpr(currentTimeNano.makeRef());		
                //   AssignStmt timeInitalize = Jimple.v().newAssignStmt(longLocal, timeInvoke);
                  
                //   SootMethod printlnMethod2 = Scene.v().grabMethod("<java.io.PrintStream: void println(java.lang.String)>");

                //   InvokeStmt printlnMethodCallStmt2 = Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(psLocal, printlnMethod2.makeRef(), longLocal));
                //   generatedUnits.add(timeInitalize);
                //   generatedUnits.add(printlnMethodCallStmt2);


                /**
                  * murali for timestamp end
                  */

                // Insert the generated statement before the first  non-identity stmt
                // units.insertBefore(generatedUnitsEnter, body.getFirstNonIdentityStmt());

                // for (Iterator<Unit> iter = units.snapshotIterator(); iter.hasNext();) {
                //     final Stmt stmt = (Stmt) iter.next();
                //     if(stmt instanceof ReturnStmt || stmt instanceof ReturnVoidStmt) {
                //         // if i use the InvokeStmt i created earlier
                //         // it gives error saying i am adding the same stmt twice 
                //         // to a body (when the body contains multiple return/exit points)
                //         units.insertBefore(Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(myInstrumentMethod.makeRef(), printParam2)), stmt);
                //     }
                // }

                // List<Stmt> exitPoints = getExitPoints(b);
                // for(Stmt s : exitPoints) {
                //     units.insertBefore(generatedUnitsExit, s);
                // }


                // Validate the body to ensure that our code injection does not introduce any problem (at least statically)
                b.validate();  

            }
        }));
        // Run Soot packs (note that our transformer pack is added to the phase "jtp")
        PackManager.v().runPacks();
        // Write the result of packs in outputPath
        PackManager.v().writeOutput();
    }

    public static Local generateFreshLocal(Body body, Type type){
		LocalGenerator lg = new LocalGenerator(body);
		return lg.generateLocal(type);
	}
    public static Pair<Value, List<Unit>> generateParameterArray(List<Value> parameterList, Body body){
		List<Unit> generated = new ArrayList<Unit>();
		
		NewArrayExpr arrayExpr = Jimple.v().newNewArrayExpr(RefType.v("java.lang.Object"), IntConstant.v(parameterList.size()));
		
		Value newArrayLocal = generateFreshLocal(body, getParameterArrayType());
		Unit newAssignStmt = Jimple.v().newAssignStmt(newArrayLocal, arrayExpr);
		generated.add(newAssignStmt);
		
		for(int i = 0; i < parameterList.size(); i++){
			Value index = IntConstant.v(i);
			ArrayRef leftSide = Jimple.v().newArrayRef(newArrayLocal, index);
			Value rightSide = generateCorrectObject(body, parameterList.get(i), generated);
			try {
                Unit parameterInArray = Jimple.v().newAssignStmt(leftSide, rightSide);
                generated.add(parameterInArray);    
                // System.out.format("successfully added parameters to print. lhs=rhs as %s=%s\n", leftSide, rightSide);
            } catch (Exception e) {
                // System.out.format("unable to print parameters with error %s. lhs=rhs as %s=%s failed.\n", e.getMessage(), leftSide, rightSide);
                throw e;
            }
			
		}
		
		return new Pair<Value, List<Unit>>(newArrayLocal, generated);
	}



    public static Value generateCorrectObject(Body body, Value value, List<Unit> generated){
		if(value.getType() instanceof PrimType){
			//in case of a primitive type, we use boxing (I know it is not nice, but it works...) in order to use the Object type
			if(value.getType() instanceof BooleanType){
				Local booleanLocal = generateFreshLocal(body, RefType.v("java.lang.Boolean"));
				
				SootClass sootClass = Scene.v().getSootClass("java.lang.Boolean");
				SootMethod valueOfMethod = sootClass.getMethod("java.lang.Boolean valueOf(boolean)");
				StaticInvokeExpr staticInvokeExpr = Jimple.v().newStaticInvokeExpr(valueOfMethod.makeRef(), value);
				
				Unit newAssignStmt = Jimple.v().newAssignStmt(booleanLocal, staticInvokeExpr);
				generated.add(newAssignStmt);
				
				return booleanLocal;
			}
			else if(value.getType() instanceof ByteType){
				Local byteLocal = generateFreshLocal(body, RefType.v("java.lang.Byte"));
				
				SootClass sootClass = Scene.v().getSootClass("java.lang.Byte");
				SootMethod valueOfMethod = sootClass.getMethod("java.lang.Byte valueOf(byte)");
				StaticInvokeExpr staticInvokeExpr = Jimple.v().newStaticInvokeExpr(valueOfMethod.makeRef(), value);
				
				Unit newAssignStmt = Jimple.v().newAssignStmt(byteLocal, staticInvokeExpr);
				generated.add(newAssignStmt);
				
				return byteLocal;
			}
			else if(value.getType() instanceof CharType){
				Local characterLocal = generateFreshLocal(body, RefType.v("java.lang.Character"));
				
				SootClass sootClass = Scene.v().getSootClass("java.lang.Character");
				SootMethod valueOfMethod = sootClass.getMethod("java.lang.Character valueOf(char)");
				StaticInvokeExpr staticInvokeExpr = Jimple.v().newStaticInvokeExpr(valueOfMethod.makeRef(), value);
				
				Unit newAssignStmt = Jimple.v().newAssignStmt(characterLocal, staticInvokeExpr);
				generated.add(newAssignStmt); 
				
				return characterLocal;
			}
			else if(value.getType() instanceof DoubleType){
				Local doubleLocal = generateFreshLocal(body, RefType.v("java.lang.Double"));
				
				SootClass sootClass = Scene.v().getSootClass("java.lang.Double");
				SootMethod valueOfMethod = sootClass.getMethod("java.lang.Double valueOf(double)");
																
				StaticInvokeExpr staticInvokeExpr = Jimple.v().newStaticInvokeExpr(valueOfMethod.makeRef(), value);
				
				Unit newAssignStmt = Jimple.v().newAssignStmt(doubleLocal, staticInvokeExpr);
				generated.add(newAssignStmt); 
				
				return doubleLocal;
			}
			else if(value.getType() instanceof FloatType){
				Local floatLocal = generateFreshLocal(body, RefType.v("java.lang.Float"));
				
				SootClass sootClass = Scene.v().getSootClass("java.lang.Float");
				SootMethod valueOfMethod = sootClass.getMethod("java.lang.Float valueOf(float)");
				StaticInvokeExpr staticInvokeExpr = Jimple.v().newStaticInvokeExpr(valueOfMethod.makeRef(), value);
				
				Unit newAssignStmt = Jimple.v().newAssignStmt(floatLocal, staticInvokeExpr);
				generated.add(newAssignStmt); 
				
				return floatLocal;
			}
			else if(value.getType() instanceof IntType){
				Local integerLocal = generateFreshLocal(body, RefType.v("java.lang.Integer"));
				
				SootClass sootClass = Scene.v().getSootClass("java.lang.Integer");
				SootMethod valueOfMethod = sootClass.getMethod("java.lang.Integer valueOf(int)");
				StaticInvokeExpr staticInvokeExpr = Jimple.v().newStaticInvokeExpr(valueOfMethod.makeRef(), value);
				
				Unit newAssignStmt = Jimple.v().newAssignStmt(integerLocal, staticInvokeExpr);
				generated.add(newAssignStmt); 
				
				return integerLocal;
			}
			else if(value.getType() instanceof LongType){
				Local longLocal = generateFreshLocal(body, RefType.v("java.lang.Long"));
				
				SootClass sootClass = Scene.v().getSootClass("java.lang.Long");
				SootMethod valueOfMethod = sootClass.getMethod("java.lang.Long valueOf(long)");
				StaticInvokeExpr staticInvokeExpr = Jimple.v().newStaticInvokeExpr(valueOfMethod.makeRef(), value);
				
				Unit newAssignStmt = Jimple.v().newAssignStmt(longLocal, staticInvokeExpr);
				generated.add(newAssignStmt); 
				
				return longLocal;
			}
			else if(value.getType() instanceof ShortType){
				Local shortLocal = generateFreshLocal(body, RefType.v("java.lang.Short"));
				
				SootClass sootClass = Scene.v().getSootClass("java.lang.Short");
				SootMethod valueOfMethod = sootClass.getMethod("java.lang.Short valueOf(short)");
				StaticInvokeExpr staticInvokeExpr = Jimple.v().newStaticInvokeExpr(valueOfMethod.makeRef(), value);
				
				Unit newAssignStmt = Jimple.v().newAssignStmt(shortLocal, staticInvokeExpr);
				generated.add(newAssignStmt); 
				
				return shortLocal;
			}
			else
				throw new RuntimeException("Ooops, something went all wonky!");
		}
		else
			//just return the value, there is nothing to box
			return value;
	}
    public static Type getParameterArrayType(){
		Type parameterArrayType = RefType.v("java.lang.Object");
		Type parameterArray = ArrayType.v(parameterArrayType, 1);
		
		return parameterArray;
	}
	public static class Pair<F, S> implements Serializable, Cloneable {
        private static final long serialVersionUID = 7408444626787884925L;
        
        private F first; 
        private S second;
        
        protected int hashCode = 0;
    
        public Pair(F first, S second) {
            this.first = first;
            this.second = second;
        }
    
        public void setFirst(F first) {
            this.first = first;
            hashCode = 0;
        }
    
        public void setSecond(S second) {
            this.second = second;
            hashCode = 0;
        }
        
        public void setPair(F no1, S no2) {
            first = no1;
            second = no2;
            hashCode = 0;
        }
    
        public F getFirst() {
            return first;
        }
    
        public S getSecond() {
            return second;
        }
        
        @Override
        public int hashCode() {
            if (hashCode != 0)
                return hashCode;
            
            final int prime = 31;
            int result = 1;
            result = prime * result + ((first == null) ? 0 : first.hashCode());
            result = prime * result + ((second == null) ? 0 : second.hashCode());
            hashCode = result;
            
            return hashCode;
        }
    
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            @SuppressWarnings("rawtypes")
            Pair other = (Pair) obj;
            if (first == null) {
                if (other.first != null)
                    return false;
            } else if (!first.equals(other.first))
                return false;
            if (second == null) {
                if (other.second != null)
                    return false;
            } else if (!second.equals(other.second))
                return false;
            return true;
        }
    
        public String toString() {
            return "Pair " + first + "," + second;
        }
        
    }

    private void instrumentFunctionCallAndInsertHeapReadBeforeIt(String classFieldWeNeedToGetValueOf, Body body, Unit unit) {
        // classFieldWeNeedToGetValueOf contains info about a class field i.e. a variable

        // body is the body of the function currently being executed
        // unit is the line of code we are interested in. by entering this function, we know that unit currently is a line that invokes a function
        // and within that function, classFieldWeNeedToGetValueOf is read
        // so we're going to print the value of classFieldWeNeedToGetValueOf just prior to the unit (i.e. the function is invoked)


        InvokeExpr invokeExpr = null;
		if(unit instanceof DefinitionStmt)
		{
			DefinitionStmt defStmt = (DefinitionStmt)unit;
			if(defStmt.containsInvokeExpr())
			{
				invokeExpr = defStmt.getInvokeExpr();
			}
		}					
		else if(unit instanceof InvokeStmt)
		{
			InvokeStmt invokeStmt = (InvokeStmt)unit;
			invokeExpr = invokeStmt.getInvokeExpr();
		}				
		
		if(invokeExpr != null)
		{			
			if(!AndroidLogger.isApiCall(invokeExpr))
			{
				
				
                // todo: how to generate lines that do what we want
                
                // classFieldWeNeedToGetValueOf = r0.<com.example.sootforfuncargs.MainActivity: java.lang.String heapVar2>
                // i split that into few variables below
                String className = "com.example.sootforfuncargs.MainActivity";
                String dataType = "java.lang.String";
                String fieldName = "heapVar2";

                System.out.println("going to print value of heap variable here somehow");
                
                SootClass c = RefType.v(className).getSootClass();
                SootField f = c.getFieldByName(fieldName);
                InstanceFieldRef ifr = Jimple.v().newInstanceFieldRef(body.getThisLocal(), f.makeRef());
                
                Local strLocal = generateFreshLocal(body, RefType.v("java.lang.String"));
				
				Unit newAssignStmt = Jimple.v().newAssignStmt(strLocal, ifr);

                List<Value> parameter = new ArrayList<>();
                parameter.add(strLocal);
                parameter.add(StringConstant.v("WHEEEE we are printing stuff from the heap"));
				List<Unit> generated = new ArrayList<Unit>();
				Pair<Value, List<Unit>> arrayRefAndInstrumentation = AndroidLogger.generateParameterArray(parameter, body);
				
				List<Unit> generatedArrayInstrumentation = arrayRefAndInstrumentation.getSecond();
				Value arrayRef = arrayRefAndInstrumentation.getFirst();
				
				Unit generatedInvokeStmt = AndroidLogger.makeJimpleStaticCallForPathExecution("com.example.crossclasscalls.MainActivity", "accumulateHelper", 	
                AndroidLogger.getParameterArrayType(), (parameter.isEmpty())? NullConstant.v() : arrayRef);
				
                generated.add(newAssignStmt);
                generated.addAll(generatedArrayInstrumentation);
				generated.add(generatedInvokeStmt);



				body.getUnits().insertBefore(generated, unit);
			}
		}	
    }


    private void instrumentInfoAboutNonApiCaller(Body body, Unit unit)
	{	
		
		InvokeExpr invokeExpr = null;
		if(unit instanceof DefinitionStmt)
		{
			DefinitionStmt defStmt = (DefinitionStmt)unit;
			if(defStmt.containsInvokeExpr())
			{
				invokeExpr = defStmt.getInvokeExpr();
			}
		}					
		else if(unit instanceof InvokeStmt)
		{
			InvokeStmt invokeStmt = (InvokeStmt)unit;
			invokeExpr = invokeStmt.getInvokeExpr();
		}				
		
		if(invokeExpr != null)
		{			
			if(!AndroidLogger.isApiCall(invokeExpr))
			{
				String invokeExprMethodSignature = invokeExpr.getMethod().getSignature();
				
				List<Value> parameter = invokeExpr.getArgs();
				List<Unit> generated = new ArrayList<Unit>();
				Pair<Value, List<Unit>> arrayRefAndInstrumentation = AndroidLogger.generateParameterArray(parameter, body);
				
				List<Unit> generatedArrayInstrumentation = arrayRefAndInstrumentation.getSecond();
				Value arrayRef = arrayRefAndInstrumentation.getFirst();
				
				Unit generatedInvokeStmt = AndroidLogger.makeJimpleStaticCallForPathExecution("com.example.crossclasscalls.MainActivity", "accumulateHelper", 	
                AndroidLogger.getParameterArrayType(), (parameter.isEmpty())? NullConstant.v() : arrayRef);
				generated.addAll(generatedArrayInstrumentation);
				generated.add(generatedInvokeStmt);

				body.getUnits().insertBefore(generated, unit);
			}
		}		
	}

    public static Unit  makeJimpleStaticCallForPathExecution(String className, String methodName, Object... args) {
		SootClass sootClass = Scene.v().getSootClass(className);
		
		Unit generated = null;

		ArrayList<Type> argTypes = new ArrayList<Type>();
		ArrayList<Value> argList = new ArrayList<Value>();

		if (args != null) {
		if (args.length % 2 != 0) {
			throw new RuntimeException(
					"Mismatched argument types:values in static call to "
							+ methodName);
		} else {
			for (int i = 0; i < args.length; i++)
				if (i % 2 == 0) // First type, then argument
					argTypes.add((Type) args[i]);
				else
					argList.add((Value) args[i]);
		}
		}

		SootMethod createAndAdd = sootClass.getMethod(methodName, argTypes);
		StaticInvokeExpr sie = Jimple.v().newStaticInvokeExpr(
				createAndAdd.makeRef(), argList);

		
		generated = Jimple.v().newInvokeStmt(sie);
		
		return generated;
	}

    public static SpecialInvokeExpr  getInvokeSpecialExprForAssignment(Local base, String className, String methodName, Object... args) {
		SootClass sootClass = Scene.v().getSootClass(className);
		
		

		ArrayList<Type> argTypes = new ArrayList<Type>();
		ArrayList<Value> argList = new ArrayList<Value>();

		if (args != null) {
            if (args.length % 2 != 0) {
                throw new RuntimeException(
                        "Mismatched argument types:values in static call to "
                                + methodName);
            } else {
                for (int i = 0; i < args.length; i++) {
                    if (i % 2 == 0) { // First type, then argument
                        System.out.format("XXX working on %s\n", args[i]);
                        argTypes.add((Type) args[i]);
                    } else {
                        System.out.format("XXX working on %s\n", args[i]);
                        argList.add((Value) args[i]);
                    }
                }
            }
		}

		SootMethod createAndAdd = sootClass.getMethod(methodName, argTypes);
		SpecialInvokeExpr sie = Jimple.v().newSpecialInvokeExpr(base,
				createAndAdd.makeRef(), argList);

		
		
		return sie;
	}
    
    public static StaticInvokeExpr  getInvokeExprForAssignment(String className, String methodName, Object... args) {
		SootClass sootClass = Scene.v().getSootClass(className);
		
		

		ArrayList<Type> argTypes = new ArrayList<Type>();
		ArrayList<Value> argList = new ArrayList<Value>();

		if (args != null) {
		if (args.length % 2 != 0) {
			throw new RuntimeException(
					"Mismatched argument types:values in static call to "
							+ methodName);
		} else {
			for (int i = 0; i < args.length; i++)
				if (i % 2 == 0) // First type, then argument
					argTypes.add((Type) args[i]);
				else
					argList.add((Value) args[i]);
		}
		}

		SootMethod createAndAdd = sootClass.getMethod(methodName, argTypes);
		StaticInvokeExpr sie = Jimple.v().newStaticInvokeExpr(
				createAndAdd.makeRef(), argList);

		
		
		return sie;
	}

    public static boolean isApiCall(InvokeExpr invokeExpr){
		if(invokeExpr.getMethod().getDeclaringClass().isLibraryClass() 
				|| invokeExpr.getMethod().getDeclaringClass().isJavaLibraryClass())
			return true;
		else
			return false;
	}

    static List<Value> getHeapRValues(AssignStmt stmt) {
        List<Value> valuesToReturn = new ArrayList<>();
        Value lhs = stmt.getLeftOp();
        Value rhs = stmt.getRightOp();

        if(rhs instanceof InstanceFieldRef) {
            // if rhs is an instance field. then lhs is local in our example. 
            // so let us return lhs to be printed
            // System.out.format("instanceref is happening %s\n", stmt.toString());
            // valuesToReturn.add(lhs); // only return right side as we only want reads. but printing right side gives error. let us try left.
            // System.out.format("lhs is %s\n", lhs.toString());

            // temporarily return rhs
            valuesToReturn.add(rhs);
        }
        
        return valuesToReturn;
    }
    static String checkHeapReadWrite(AssignStmt statement) {
        //check if a assignment statement is accessing heap
        Value lhs = statement.getLeftOp();
        Value rhs = statement.getRightOp();
        String statementStr = lhs.toString() + " = " + rhs.toString()  + " ";
        if(lhs instanceof ArrayRef) {
            statementStr = "heap write: array: " + statementStr;
        }
        else if(lhs instanceof InstanceFieldRef)  {
            statementStr = "heap write: instance: " + statementStr;
        }
        else if(lhs instanceof StaticFieldRef) {
            statementStr = "heap write: static: " + statementStr;
        }

        if(rhs instanceof ArrayRef) {
            statementStr = "heap read: array " + statementStr;
        }
        else if(rhs instanceof InstanceFieldRef) {
            statementStr = "heap read: instance " + statementStr;
        }
        else if(rhs instanceof StaticFieldRef) {
            statementStr = "heap read: static " + statementStr;
        }
        return statementStr;
    }

    private void instrumentHeapReadLine(Body body, Unit unit, List<Value> parameter)
	{			
        List<Unit> generated = new ArrayList<Unit>();
        Pair<Value, List<Unit>> arrayRefAndInstrumentation = AndroidLogger.generateParameterArray(parameter, body);
        
        List<Unit> generatedArrayInstrumentation = arrayRefAndInstrumentation.getSecond();
        Value arrayRef = arrayRefAndInstrumentation.getFirst();
        
        Unit generatedInvokeStmt = AndroidLogger.makeJimpleStaticCallForPathExecution("com.example.crossclasscalls.MainActivity", "accumulateHelper", 	
        AndroidLogger.getParameterArrayType(), (parameter.isEmpty())? NullConstant.v() : arrayRef);
        generated.addAll(generatedArrayInstrumentation);
        generated.add(generatedInvokeStmt);

        body.getUnits().insertAfter(generated, unit);
        
	}
}