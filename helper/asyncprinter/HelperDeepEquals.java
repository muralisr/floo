package com.example.asyncprinter;

import static org.unitils.reflectionassert.ReflectionAssert.assertReflectionEquals;

import android.os.Environment;

import org.unitils.reflectionassert.ReflectionComparatorMode;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;


public class HelperDeepEquals {

    // In-memory list of all function calls
    public static ArrayList<FunctionRunInfoDeep> listOfFunctionRunInfo = new ArrayList<>();
    private static Executor functionInfoAdder = Executors.newSingleThreadExecutor();
    public static AtomicInteger countOfPendingOperations = new AtomicInteger();
    private static boolean firstRun = true;
    private static File file1, root;
    private static FileOutputStream outputStreamMemoFile = null;
    private static final AtomicInteger numberOfLinesToFlush = new AtomicInteger();
    private static boolean isJavaLangObject(Object input) {
        if (input instanceof java.lang.Integer) {
            return true;
        } else if (input instanceof java.lang.Boolean) {
            return true;
        } else if (input instanceof java.lang.Float) {
            return true;
        } else if (input instanceof java.lang.Byte) {
            return true;
        } else if (input instanceof java.lang.Short) {
            return true;
        } else if (input instanceof java.lang.Long) {
            return true;
        } else if (input instanceof java.lang.Double) {
            return true;
        } else if (input instanceof java.lang.Boolean) {
            return true;
        } else if (input instanceof java.lang.Character) {
            return true;
        } else if (input instanceof java.lang.String) {
            return true;
        } else {
            return false;
        }
    }
    // Class used to keep track of each objects used for particular function call
    public static class FunctionRunInfoDeep {

        public String functionName;
        public ArrayList<Object> listOfObjects = new ArrayList<>();

        // constructor
        public FunctionRunInfoDeep(String functionName, ArrayList<Object> listOfObjects) {
            this.functionName = functionName;
            for (Object obj: listOfObjects) {
                if (obj == null) {
                    this.listOfObjects.add(null);
                } if (isJavaLangObject(obj)) { // java object - add it differently from other custom objects
                    this.listOfObjects.add(obj.getClass().cast(obj));
                } else {
                    // make a deep copy
                    Object deepCopyOfObject = null;
                    try {
                        deepCopyOfObject = this.copy(obj);
                    } catch (Exception e) {
                    }
                    // add to the list of objects for this FunctionRunInfo
                    this.listOfObjects.add(deepCopyOfObject);
                }
            }
        }

        // make a deep copy of a given object
        private <T> T copy(T entity) throws IllegalAccessException, InstantiationException {
            Class<?> clazz = entity.getClass();
            T newEntity = (T) entity.getClass().newInstance();

            while (clazz != null) {
                copyFields(entity, newEntity, clazz);
                clazz = clazz.getSuperclass();
            }

            return newEntity;
        }

        // copies over field
        private <T> T copyFields(T entity, T newEntity, Class<?> clazz) throws IllegalAccessException {
            List<Field> fields = new ArrayList<>();
            for (Field field : clazz.getDeclaredFields()) {
                fields.add(field);
            }
            for (Field field : fields) {
                field.setAccessible(true);
                field.set(newEntity, field.get(entity));
            }
            return newEntity;
        }
    }

    public static long getThreadID() {
        return Thread.currentThread().getId();
    }

    public static void printSomeStuff(Object... arrayOfObjects) {
        functionInfoAdder.execute(() -> {
            for(Object o : arrayOfObjects) {
                try {
                    writeToMemoFile(o.toString() + "_");
                } catch(Exception e) {

                }
            }
            writeToMemoFile("\n");
        });
    }

    // Call this function to add function details to the in memory record
    public static void addFunctionObj(String functionName, Object []arrayOfObjects)  {
        if (firstRun) {
            //Checking the availability state of the External Storage.
            String state = Environment.getExternalStorageState();
            if (!Environment.MEDIA_MOUNTED.equals(state)) {
                //If it isn't mounted - we can't write into it.
                return;
            }
            //Create a new file that points to the root directory, with the given name:
            root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            file1 = new File(root, "memo_data_oct19.txt");
            try {
                outputStreamMemoFile = new FileOutputStream(file1, true);
            } catch (FileNotFoundException e) {

            }
            countOfPendingOperations.set(0);
            firstRun = false;

        }

        // asynchronously
        functionInfoAdder.execute(() -> {

                ArrayList<Object> listOfObjects = new ArrayList<>(Arrays.asList(arrayOfObjects));
                // organize name + listObjects into a new object class
                FunctionRunInfoDeep item = new FunctionRunInfoDeep(functionName, listOfObjects);
                boolean repeat = false;
                if (listContainsFunctionRunInfo(item)) {
                    repeat = true;
                } else {
                    listOfFunctionRunInfo.add(item);
                }
                if (repeat) {
                    String s = String.format("%sYES\n", functionName);// String.format("Can memoize function call for %s - length of info list %d\n", functionName, listOfFunctionRunInfo.size());
                    writeToMemoFile(s);
                } else {
                    String s = String.format("%sNO\n", functionName); // String.format("Cannot memoize function call for %s - length of info list %d\n", functionName, listOfFunctionRunInfo.size());
                    writeToMemoFile(s);
                }

        });
    }

    public static void printStuffAndLogMemo(String functionName, Object []arrayOfObjectsToPrint,Object []arrayOfObjectsInRead) {
        if (firstRun) {
            //Checking the availability state of the External Storage.
            String state = Environment.getExternalStorageState();
            if (!Environment.MEDIA_MOUNTED.equals(state)) {
                //If it isn't mounted - we can't write into it.
                return;
            }
            //Create a new file that points to the root directory, with the given name:
            root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            file1 = new File(root, "memo_data_oct19.txt");
            try {
                outputStreamMemoFile = new FileOutputStream(file1, true);
            } catch (FileNotFoundException e) {

            }
            countOfPendingOperations.set(0);
            firstRun = false;

        }

        // asynchronously
        functionInfoAdder.execute(() -> {
            if(countOfPendingOperations.incrementAndGet() > 10000) {
                StringBuilder sb = new StringBuilder();
                for(Object o : arrayOfObjectsToPrint) {
                    sb.append(o + "_");
                }
                String s = String.format("%s_UNK_%s\n", functionName, sb.toString()); // String.format("Cannot memoize function call for %s - length of info list %d\n", functionName, listOfFunctionRunInfo.size());
                sb.setLength(0);
                writeToMemoFile(s);
                return;
            }
            ArrayList<Object> listOfObjects = new ArrayList<>(Arrays.asList(arrayOfObjectsInRead));
            // organize name + listObjects into a new object class
            FunctionRunInfoDeep item = new FunctionRunInfoDeep(functionName, listOfObjects);
            boolean repeat = false;
            if (listContainsFunctionRunInfo(item)) {
                repeat = true;
            } else {
                listOfFunctionRunInfo.add(item);
            }
            if (repeat) {
                StringBuilder sb = new StringBuilder();
                for(Object o : arrayOfObjectsToPrint) {
                    sb.append(o + "_");
                }

                String s = String.format("%s_YES_%s\n", functionName, sb.toString());// String.format("Can memoize function call for %s - length of info list %d\n", functionName, listOfFunctionRunInfo.size());
                sb.setLength(0);
                writeToMemoFile(s);
            } else {
                StringBuilder sb = new StringBuilder();
                for(Object o : arrayOfObjectsToPrint) {
                    sb.append(o + "_");
                }
                String s = String.format("%s_NO_%s\n", functionName, sb.toString()); // String.format("Cannot memoize function call for %s - length of info list %d\n", functionName, listOfFunctionRunInfo.size());
                sb.setLength(0);
                writeToMemoFile(s);
            }
            countOfPendingOperations.decrementAndGet();
        });

    }

    private static boolean listContainsFunctionRunInfo(FunctionRunInfoDeep item) {
        for (FunctionRunInfoDeep f1: listOfFunctionRunInfo) {
            try {
                if ((f1.functionName.equals(item.functionName))) {
                    assertReflectionEquals(f1.listOfObjects, item.listOfObjects, ReflectionComparatorMode.IGNORE_DEFAULTS);
                    return true;
                }
            } catch (AssertionError e) {
                continue;
            }
        }
        return false;
    }

    private static void writeToMemoFile(String finalString) {
        try {
            outputStreamMemoFile.write(finalString.getBytes());
            if (numberOfLinesToFlush.incrementAndGet() % 10000 == 0)
                outputStreamMemoFile.flush(); // flush after each X lines rather than each line.
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
