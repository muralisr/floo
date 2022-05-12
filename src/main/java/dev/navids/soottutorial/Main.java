package dev.navids.soottutorial;

import dev.navids.soottutorial.android.AndroidClassInjector;
import dev.navids.soottutorial.android.AndroidLogger;
import dev.navids.soottutorial.android.FlooScratch;
import dev.navids.soottutorial.android.ClassLogger;
import dev.navids.soottutorial.android.HeapRWFinder;
import dev.navids.soottutorial.android.DetermineCacheability;
import dev.navids.soottutorial.android.PrintTimestampsWithoutHelper;
import dev.navids.soottutorial.android.InsertHelperCallsForSubFunctionCalls;
import dev.navids.soottutorial.android.Statistics;
import dev.navids.soottutorial.android.HelperCallInjector;
import dev.navids.soottutorial.android.InsertIfBlock;
import dev.navids.soottutorial.android.ComputeCacher;
import dev.navids.soottutorial.android.NondeterminismLogger;
import dev.navids.soottutorial.android.TimestampsPrinter;
import dev.navids.soottutorial.android.FunctionCallInjector;
import dev.navids.soottutorial.android.MemoFnWrites;
import dev.navids.soottutorial.android.BugReproducer;
import dev.navids.soottutorial.basicapi.BasicAPI;
import dev.navids.soottutorial.hellosoot.HelloSoot;
import dev.navids.soottutorial.intraanalysis.npanalysis.NPAMain;
import dev.navids.soottutorial.intraanalysis.usagefinder.UsageFinder;

import java.util.Arrays;

public class Main {
    public static void main(String[] args){
        if (args.length == 0){
            System.err.println("You must provide the name of the Java class file that you want to run.");
            return;
        }
        String[] restOfTheArgs = Arrays.copyOfRange(args, 1, args.length);
        if(args[0].equals("HelloSoot"))
            HelloSoot.main(restOfTheArgs);
        else if(args[0].equals("BasicAPI"))
            BasicAPI.main(restOfTheArgs);
        else if(args[0].equals("AndroidLogger")) {
            AndroidLogger.main(restOfTheArgs);
        }
        else if(args[0].equals("FlooScratch")) {
            FlooScratch.main(restOfTheArgs);
        }
        else if(args[0].equals("ClassLogger")) {
            ClassLogger.main(restOfTheArgs);
        }
        else if(args[0].equals("PrintTimestampsWithoutHelper")) {
            PrintTimestampsWithoutHelper.main(restOfTheArgs);
        }
        else if(args[0].equals("HeapRWFinder")) {
            HeapRWFinder.main(restOfTheArgs);
        }
        else if(args[0].equals("DetermineCacheability")) {
            DetermineCacheability.main(restOfTheArgs);
        }
        else if(args[0].equals("NondeterminismLogger")) {
            NondeterminismLogger.main(restOfTheArgs);
        }
        else if(args[0].equals("InsertHelperCallsForSubFunctionCalls")) {
            InsertHelperCallsForSubFunctionCalls.main(restOfTheArgs);
        }
        else if(args[0].equals("HelperCallInjector")) {
            HelperCallInjector.main(restOfTheArgs);
        }
        else if(args[0].equals("Statistics")) {
            Statistics.main(restOfTheArgs);
        }
        else if(args[0].equals("MemoFnWrites")) {
            MemoFnWrites.main(restOfTheArgs);
        }
        else if(args[0].equals("AndroidClassInjector")) {
            AndroidClassInjector.main(restOfTheArgs);
        }
        else if(args[0].equals("BugReproducer")) {
            BugReproducer.main(restOfTheArgs);
        }
        else if(args[0].equals("InsertIfBlock")) {
            InsertIfBlock.main(restOfTheArgs);
        }
        else if(args[0].equals("ComputeCacher")) {
            ComputeCacher.main(restOfTheArgs);
        }
        else if(args[0].equals("TimestampsPrinter")) {
            TimestampsPrinter.main(restOfTheArgs);
        }
        else if(args[0].equals("FunctionCallInjector")) {
            FunctionCallInjector.main(restOfTheArgs);
        }
        else if(args[0].equals("UsageFinder"))
            UsageFinder.main(restOfTheArgs);
        else if(args[0].equals("NullPointerAnalysis"))
            NPAMain.main(restOfTheArgs);
        else
            System.err.println("The class '" + args[0] + "' does not exists or does not have a main method.");
    }
}
