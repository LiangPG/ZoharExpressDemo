package com.zego.expressDemo.utils;

public class ThreadUtil {
    public static void execute(Runnable runnable) {
        runnable.run();
    }
}
