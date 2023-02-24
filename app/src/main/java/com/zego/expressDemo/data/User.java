package com.zego.expressDemo.data;

public class User {
    private long userID;
    private String userName;

    public User(int userID, String userName) {
        this.userID = userID;
        this.userName = userName;
    }
    public static User get() {
        return ZegoDataCenter.ZEGO_USER;
    }

    public long getUserId() {
        return userID;
    }
}
