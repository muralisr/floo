package com.example.asyncprinter;

import androidx.annotation.RequiresPermission;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.example.asyncprinter.ReadState;
import com.example.asyncprinter.WriteState;


public class Helper {
    public static ArrayList<String> fList = new ArrayList<String>();
    public static ArrayList<FunctionInfo> fInfoList = new ArrayList<FunctionInfo>();
    private static Executor executorService = Executors.newSingleThreadExecutor();
    private static Executor functionInfoAdder = Executors.newSingleThreadExecutor();
    public static class FunctionInfo {
        public String functionId;
        public ArrayList<ReadState> readState;
        public ArrayList<WriteState> writeState;
        public int timestamp;
        public String entry;
        public int threadId;
        public boolean isNonDeterministic;

        public FunctionInfo(String functionId, ArrayList<ReadState> readState, ArrayList<WriteState> writeState,
                            int timestamp, String entry, int threadId, boolean isNonDeterministic) throws InstantiationException, IllegalAccessException {
            this.functionId = functionId;
            if (readState != null) {
                this.readState = new ArrayList<ReadState>();
                for (ReadState obj: readState) {
                    ReadState deepRead = this.copy(obj);
                    this.readState.add(deepRead);
                }
            } else {
                this.readState = null;
            }
            if (writeState != null) {
                this.writeState = new ArrayList<WriteState>();
                for (WriteState obj: writeState) {
                    WriteState deepWrite = this.copy(obj);
                    this.writeState.add(deepWrite);
                }
            } else {
                this.writeState = null;
            }
            this.timestamp = timestamp;
            this.entry = entry;
            this.threadId = threadId;
            this.isNonDeterministic = isNonDeterministic;
        }

        private <T> T copy(T entity) throws IllegalAccessException, InstantiationException {
            Class<?> clazz = entity.getClass();
            T newEntity = (T) entity.getClass().newInstance();

            while (clazz != null) {
                copyFields(entity, newEntity, clazz);
                clazz = clazz.getSuperclass();
            }

            return newEntity;
        }

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


    public static void addInfo(String fInfo) {

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                fList.add("FLOO: " + fInfo);
            }
        });
    }

    public static void addFunctionInfo(String functionId, ArrayList<ReadState> readState,
                                       ArrayList<WriteState> writeState, int timestamp, String entry,
                                       int threadId, boolean isNonDeterministic) {
        // add to array list in Helper Class
        // asynchronously
        functionInfoAdder.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    FunctionInfo item = new FunctionInfo(functionId, readState, writeState,
                            timestamp, entry, threadId, isNonDeterministic);
                    fInfoList.add(item);
                } catch (InstantiationException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        });
    }

}
