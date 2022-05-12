package dev.navids.soottutorial.android;

import soot.*;
import soot.jimple.*;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.infoflow.android.manifest.ProcessManifest;
import soot.options.Options;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Iterator;


/**
 * Instrumenting a .class File and just using jar commands directly

    1. From a java file with a class - running javac myClass.java creates a myClass.class file
    2. /classes directory contains myClass1.class myClass2.class myClass3.class …
    3. Run ./gradlew run --args=“ClassLogger” where classesDir = “/classes” and instrumented files end up in outputPath
    4. Take instrumented .class files and run jar cfe myJar.jar [Main-class] myClass1.class myClass2.class Main-class.class to obtain jar file containing all the classes 
        4a.Main-class is just a sort of helper class where we can call myClass1 and myClass2 and for execution, it is necessary for using jar cfe as the manifest file
    5. We can execute myJar.jar by running java -jar myJar.jar which executes the main function written in the Main-class
 */
public class ClassLogger {

	private final static String USER_HOME = System.getProperty("user.home");
    private static String androidJar = USER_HOME + "/Library/Android/sdk/platforms";
    static String androidDemoPath = System.getProperty("user.dir") + File.separator + "demo" + File.separator + "Android";
    // Directory of all .class files
    static String classesDir = "/disk/Code/projects/soot-instrument/demo/Android/class_file_processing/input/";
    // Outputs into this directory of all instrumented .class files
    static String outputPath = "/disk/Code/projects/soot-instrument/demo/Android/class_file_processing/output/";
    ClassLogger() {}
	 public static void main(String[] args) {
        if(System.getenv().containsKey("ANDROID_HOME"))
            androidJar = System.getenv("ANDROID_HOME")+ File.separator+"platforms";
        

        //set classpath
        //String jreDir = System.getProperty("java.home") + "\\lib\\jce.jar";
        //String jceDir = System.getProperty("java.home") + "\\lib\\rt.jar";
        //String path = jreDir + File.pathSeparator + jceDir + File.pathSeparator + classesDir;
        //Scene.v().setSootClassPath(path);

        // excludeJDKLibrary();
        // Setting up Soot for instrumentation
        Options.v().set_validate(true);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_prepend_classpath(true);
        Options.v().set_whole_program(true);
        Options.v().set_process_multiple_dex(true);
        Options.v().set_include_all(true);
        Options.v().set_process_dir(Arrays.asList(classesDir));
        Options.v().set_src_prec(Options.src_prec_class);
        Options.v().set_output_format(Options.output_format_class);
        Options.v().set_output_dir(outputPath);


        //SootClass appClass = Scene.v().loadClassAndSupport(mainClass);
        //Scene.v().setMainClass(appClass);
        Scene.v().addBasicClass("java.io.PrintStream",SootClass.SIGNATURES);
        Scene.v().addBasicClass("java.lang.System",SootClass.SIGNATURES);
        Scene.v().loadNecessaryClasses();

        PackManager.v().getPack("jtp").add(new Transform("jtp.myLogger", new BodyTransformer() {
            @Override
            protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
                
                if (!(b.getMethod().getSignature().contains("applyStyle(long,int,int,android.content.res.XmlBlock$Parser,int[],long,long)>"))) {
                    
                    return;
                }
                
                System.out.println("Printing method:" + b.getMethod());
                printAllUnits(b);
            	JimpleBody body = (JimpleBody) b;
                UnitPatchingChain units = b.getUnits();

                List<Unit> generatedUnits = new ArrayList<>();

                // The message that we want to log
                String content = String.format("%s MURALI Beginning of method %s", InstrumentUtil.TAG, body.getMethod().getSignature());
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
                // Validate the body to ensure that our code injection does not introduce any problem (at least statically)
                b.validate();
            }
	 	}));
	 	// Run Soot packs (note that our transformer pack is added to the phase "jtp")
        PackManager.v().runPacks();
        // Write the result of packs in outputPath
        System.out.println("writing output");
        PackManager.v().writeOutput();
	}
    public static void printAllUnits(Body body) {
        System.out.println("***Printing all units***");
        Iterator<Unit> iterator = body.getUnits().snapshotIterator();
        while(iterator.hasNext()){
            Unit unit = iterator.next();
            System.out.println(unit);
        }
    }
}