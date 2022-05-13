package com.example.asyncprinter;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


// Helper class to iterate through all functions in an app
// and add the FunctionRunInfo to list in memory (recording)
public class HelperObj {

    public static ArrayList<FunctionRunInfo> listOfFunctionRunInfo = new ArrayList<FunctionRunInfo>();
    private static Executor functionInfoAdder = Executors.newSingleThreadExecutor();

    // Class used to keep track of each objects used for particular function call
    public static class FunctionRunInfo {
        public String functionName;
        public ArrayList<Object> listOfObjects;

        // constructor
        public FunctionRunInfo(String functionName, ArrayList<Object> listOfObjects) throws InstantiationException, IllegalAccessException {
            this.functionName = functionName;
            for (Object obj: listOfObjects) {
                // make a deep copy
                Object deepCopyOfObject = this.copy(obj);
                // add to the list of objects for this FunctionRunInfo
                this.listOfObjects.add(deepCopyOfObject);
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

    // Call this function to add function details to the in memory record
    public static void addFunctionObj(String functionName, ArrayList<Object> listOfObjects) throws IllegalAccessException, InstantiationException {
        // asynchronously
        functionInfoAdder.execute(() -> {
            try {
                // making deep copy
                FunctionRunInfo item = new FunctionRunInfo(functionName, listOfObjects);
                boolean repeat = false;
                // TODO: need to function to check for equals object
                if (listContainsFunctionRunInfo(item)) {
                    repeat = true;
                }
                // just adding to set
                // setOfFunctionRunInfo.add(item);
                listOfFunctionRunInfo.add(item);
                if (repeat) {
                    System.out.println("Can memoize function call to" + functionName);
                }
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        });
    }

    private static boolean listContainsFunctionRunInfo(FunctionRunInfo item) {
        for (FunctionRunInfo f1: listOfFunctionRunInfo) {
            if ((f1.functionName.equals(item.functionName)) && (functionInfoContainSameObjs(f1.listOfObjects, item.listOfObjects))) {
                return true;
            }
        }
        return false;
    }

    private static boolean functionInfoContainSameObjs(ArrayList<Object> objectList1, ArrayList<Object> objectList2) {
        // if the size of the ArrayList Objects are not the same, then not same ArrayList<Object>
        if (objectList1.size() != objectList2.size()) {
            return false;
        }
        // Iterate through list of objects
        Iterator<Object> it1 = objectList1.iterator();
        Iterator<Object> it2 = objectList2.iterator();
        while (it1.hasNext()) {
            Object t1 = it1.next();
            Object t2 = it2.next();
            // if class of Objects are not the same, then also not equal ArrayList<Object>
            if (t1.getClass() != t2.getClass()) {
                return false;
            }
            List<Field> fields = getObjFields(t1);
            for (Field field: fields) {
                try {
                    // if value of fields are not same, not equal ArrayList<Object>
                    if (field.get(t1) != field.get(t2)) {
                        return false;
                    }
                    // if not found field, not equal ArrayList<Object>
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    return false;
                }
            }
        }
        // iterated through and everything seems good!
        return true;
    }

    // get all fields for a particular object
    private static List<Field> getObjFields(Object obj) {
        List<Field> fields = new ArrayList<>();
        Class<?> clazz = obj.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            fields.add(field);
        }
        return fields;
    }
}



