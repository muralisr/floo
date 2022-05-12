package dev.navids.soottutorial.android;


public class HeapDependency {
    String baseName;
    String className;
    String fieldType;
    String fieldName;
    HeapDependency() {
        baseName = "";
        className = "";
        fieldType = "";
        fieldName = "";
    }
    HeapDependency(String inputString) {
        baseName = inputString.split("[.]")[0];

        String tmp = inputString.split("[<]")[1];
        String tmp2 = tmp.split("[:]")[0];
        className = tmp2;
        
        String []tmp3 = inputString.split(" ");
        int lastButOneElementIndex = tmp3.length - 2;
        String tmp4 = tmp3[lastButOneElementIndex];
        fieldType = tmp4;
        
        
        int lastElementIndex = tmp3.length - 1;
        String tmp5 = tmp3[lastElementIndex];
        int lenOfTmp5 = tmp5.length();
        tmp5 = tmp5.substring(0, lenOfTmp5 - 1);
        fieldName = tmp5;
    }
    HeapDependency(String x, String y, String z) {
        baseName = x;
        className = y;
        fieldName = z;
    }
}