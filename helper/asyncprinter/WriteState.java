package com.example.asyncprinter;

public class WriteState {
    private String write;
    private int num;

    public WriteState() {

    }

    public WriteState(String write, int num) {
        this.write = write;
        this.num = num;
    }

    public String getWrite() {
        return this.write;
    }

    public int getNum() {
        final int num = this.num;
        return num;
    }
}
