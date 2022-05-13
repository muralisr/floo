package com.example.asyncprinter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class TestObject {
    private String s;
    private ArrayList<String> stringList = new ArrayList<String>();
    private ArrayList<ReadState> readList = new ArrayList<ReadState>();
    private Set<String> stringSet = new HashSet<>();

    public TestObject() {

    }

    public TestObject(int num, String s) {
        this.s = s;
        for (int i = 0; i < num; i++) {
            this.stringList.add(s + i);
            this.readList.add(new ReadState(s , i));
            this.stringSet.add(s + "set" + i);
        }
    }
}
