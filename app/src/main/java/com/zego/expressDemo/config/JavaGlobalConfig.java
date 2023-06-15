package com.zego.expressDemo.config;

public class JavaGlobalConfig {
    public static JavaGlobalConfig getInstance() {
        return new JavaGlobalConfig();
    }

    private JavaGlobalConfig() {

    }

    public String getConfig(String key, String defaultValue) {
        return defaultValue;
    }
}
