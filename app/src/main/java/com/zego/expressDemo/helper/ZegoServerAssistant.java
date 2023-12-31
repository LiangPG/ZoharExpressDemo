package com.zego.expressDemo.helper;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 即构公司提供给开发者用于生成与即构服务端交互的 token 信息
 *
 * Copyright © 2021年 Zego. All rights reserved.
 */
public class ZegoServerAssistant {
    static final private String VERSION_FLAG = "03";
    static final private int IV_LENGTH = 16;
    static final private String TRANSFORMATION = "AES/CBC/PKCS5Padding";

    /**
     * 通过此变量控制在生成鉴权 token 过程中是否打印控制台信息
     */
    static public boolean VERBOSE = false;

    static public class Privileges {
        /**
         * 是否允许登录房间, 默认无权限
         */
        public boolean canLoginRoom;

        /**
         * 是否允许推流, 默认无权限
         */
        public boolean canPublishStream;

        public Privileges() {
            canLoginRoom = false;
            canPublishStream = false;
        }
    }

    static public enum ErrorCode {
        /**
         * 生成鉴权 token 成功
         */
        SUCCESS(0),
        /**
         * 传入 appId 参数错误
         */
        ILLEGAL_APP_ID(1),
        /**
         * 传入 roomId 参数错误
         */
        ILLEGAL_ROOM_ID(2),
        /**
         * 传入 userId 参数错误
         */
        ILLEGAL_USER_ID(3),
        /**
         * 传入 privilege 参数错误
         */
        ILLEGAL_PRIVILEGE(4),
        /**
         * 传入 secret 参数错误
         */
        ILLEGAL_SECRET(5),
        /**
         * 传入 effectiveTimeInSeconds 参数错误
         */
        ILLEGAL_EFFECTIVE_TIME(6),
        /**
         * 其它未定义错误
         */
        OTHER(-1);

        private ErrorCode(int code) {
            this.value = code;
        }
        public int value;
    }

    static public class ErrorInfo {
        public ErrorCode code;
        public String message;

        ErrorInfo() {
            code = ErrorCode.SUCCESS;
            message = "";
        }

        @Override
        public String toString() {
            return "{\"code\": " + code.value + ", \"message\": \"" + message + "\"}";
        }
    }
    /**
     * token 结构体
     */
    static public class TokenInfo {
        /**
         * 根据提供的内容生成的 token 主体
         */
        public String data = "";

        /**
         * 错误信息
         */
        public ErrorInfo error;

        TokenInfo() {
            this.error = new ErrorInfo();
        }

        @Override
        public String toString() {
            return "TokenInfo {\"error\": " + error + ", \"data\": \"" + data + "\"}";
        }
    }

    private ZegoServerAssistant() {
    }

    /**
     * 根据所提供的参数列表生成用于与即构服务端通信的鉴权 token
     * @param appId Zego派发的数字ID, 各个开发者的唯一标识
     * @param roomId 房间 ID
     * @param userId 用户 ID
     * @param privilege 房间权限
     * @param secret 由即构提供的与 appId 对应的密钥，请妥善保管，切勿外泄
     * @param effectiveTimeInSeconds token 的有效时长，单位：秒
     * @return 返回 token 内容，在使用前，请检查 error 字段是否为 SUCCESS
     */
    @SuppressWarnings("unchecked")
    public static TokenInfo generateToken(long appId, String roomId, String userId, Privileges privilege, String secret, int effectiveTimeInSeconds){
        TokenInfo token = new TokenInfo();

        // check the appId
        if (appId == 0) {
            token.error.code = ErrorCode.ILLEGAL_APP_ID;
            token.error.message = "illegal appId";
            debugInfo("illegal appId");
            return token;
        }

        // check the roomId
        if (roomId == null || roomId == "" || roomId.length() > 64) {
            token.error.code = ErrorCode.ILLEGAL_ROOM_ID;
            token.error.message = "illegal roomId";
            debugInfo("roomId can't empty and must no more than 64 characters");
            return token;
        }

        // check the userId
        if (userId == null || userId == "" || userId.length() > 64) {
            token.error.code = ErrorCode.ILLEGAL_USER_ID;
            token.error.message = "illegal userId";
            debugInfo("userId can't empty and must no more than 64 characters");
            return token;
        }

        // check the secret
        if (secret == null || secret == "" || secret.length() != 32) {
            token.error.code = ErrorCode.ILLEGAL_SECRET;
            token.error.message = "illegal secret";
            debugInfo("secret must 32 characters");
            return token;
        }

        // check the effectiveTimeInSeconds
        if (effectiveTimeInSeconds <= 0) {
            token.error.code = ErrorCode.ILLEGAL_EFFECTIVE_TIME;
            token.error.message = "effectiveTimeInSeconds must > 0";
            debugInfo("effectiveTimeInSeconds must > 0");
            return token;
        }

        // check the privilege
        if (privilege == null) {
            token.error.code = ErrorCode.ILLEGAL_PRIVILEGE;
            token.error.message = "privilege can't be null";
            debugInfo("privilege can't be null");
            return token;
        }

        debugInfo("generate random IV ...");
        byte[] ivBytes = new byte[IV_LENGTH];
        ThreadLocalRandom.current().nextBytes(ivBytes);

        try {
            JSONObject _privilege_json = new JSONObject();
            _privilege_json.put("1", privilege.canLoginRoom ? 1 : 0);
            _privilege_json.put("2", privilege.canPublishStream ? 1 : 0);

            JSONObject json = new JSONObject();
            json.put("app_id", appId);
            json.put("room_id", roomId);
            json.put("user_id", userId);
            json.put("privilege", _privilege_json);

            long nowTime = System.currentTimeMillis() / 1000;
            long expire_time = nowTime + effectiveTimeInSeconds;
            json.put("create_time", nowTime);
            json.put("expire_time", expire_time);
            json.put("nonce", ThreadLocalRandom.current().nextLong());

            String content = json.toString();

            debugInfo("encrypt content ...");
            byte[] contentBytes = encrypt(content.getBytes("UTF-8"), secret.getBytes(), ivBytes);

            ByteBuffer buffer = ByteBuffer.wrap(new byte[contentBytes.length + IV_LENGTH + 12]);
            buffer.order(ByteOrder.BIG_ENDIAN);
            buffer.putLong(expire_time);     // 8 bytes
            packBytes(ivBytes, buffer);      // IV_LENGTH + 2 bytes
            packBytes(contentBytes, buffer); // contentBytes.length + 2 bytes

            debugInfo("serialize with base64 ...");
            token.data = VERSION_FLAG + Base64.getEncoder().encodeToString(buffer.array());

            token.error.code  = ErrorCode.SUCCESS;
        } catch (Exception e) {
            debugInfo("generate token failed: " + e);
            token.error.code  = ErrorCode.OTHER;
            token.error.message = "" + e;
        }

        return token;
    }

    static private byte[] encrypt(byte[] content, byte[] secretKey, byte[] ivBytes) throws Exception {
        if (secretKey == null || secretKey.length != 32) {
            throw new IllegalArgumentException("secret key's length must be 32 bytes");
        }

        if (ivBytes == null || ivBytes.length != 16) {
            throw new IllegalArgumentException("ivBytes's length must be 16 bytes");
        }

        if (content == null) {
            content = new byte[] { };
        }
        SecretKeySpec key = new SecretKeySpec(secretKey, "AES");
        IvParameterSpec iv = new IvParameterSpec(ivBytes);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key, iv);

        return cipher.doFinal(content);
    }

    static private void packBytes(byte[] buffer, ByteBuffer target) {
        target.putShort((short)buffer.length);
        target.put(buffer);
    }

    static private void debugInfo(String info) {
        if (VERBOSE) {
            System.out.println(info);
        }
    }
}

