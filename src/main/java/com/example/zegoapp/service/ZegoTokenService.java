package com.example.zegoapp.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Generates ZegoCloud "Token04" auth tokens used by the web SDK to log into a room.
 *
 * NOTE: the previous version of this class had a bug — it called
 * envLong("738726452", 0L) i.e. it treated the App ID *value* as the name of
 * an environment variable to look up, so APP_ID / SERVER_SECRET always ended
 * up 0 / empty. This version reads them the standard Spring way via
 * application.properties, which itself pulls from ZEGO_APP_ID / ZEGO_SERVER_SECRET
 * env vars if set, falling back to the values that were in the original project.
 */
@Service
public class ZegoTokenService {

    private final long appId;
    private final String serverSecret;

    public ZegoTokenService(
            @Value("${zego.app-id}") long appId,
            @Value("${zego.server-secret}") String serverSecret) {
        this.appId = appId;
        this.serverSecret = serverSecret;
    }

    public boolean isConfigured() {
        return appId != 0 && serverSecret != null && !serverSecret.isBlank();
    }

    public long getAppId() {
        return appId;
    }

    public String generateToken(String userId, long effectiveTimeInSeconds) throws Exception {
        if (serverSecret.length() != 16 && serverSecret.length() != 24 && serverSecret.length() != 32) {
            throw new IllegalArgumentException("ZEGO_SERVER_SECRET must be 16, 24 or 32 characters long");
        }

        long createTime = System.currentTimeMillis() / 1000L;
        long expire = createTime + effectiveTimeInSeconds;

        Map<String, Object> tokenInfo = new LinkedHashMap<>();
        tokenInfo.put("app_id", appId);
        tokenInfo.put("user_id", userId);
        tokenInfo.put("nonce", new SecureRandom().nextInt());
        tokenInfo.put("ctime", createTime);
        tokenInfo.put("expire", expire);
        tokenInfo.put("payload", "");

        String plainText = new ObjectMapper().writeValueAsString(tokenInfo);

        String iv = makeRandomIv();
        byte[] encrypted = aesEncrypt(plainText, serverSecret, iv);

        ByteBuffer buffer = ByteBuffer.allocate(8 + 2 + iv.length() + 2 + encrypted.length);
        buffer.putLong(expire);
        buffer.putShort((short) iv.length());
        buffer.put(iv.getBytes(StandardCharsets.UTF_8));
        buffer.putShort((short) encrypted.length);
        buffer.put(encrypted);

        return "04" + Base64.getEncoder().encodeToString(buffer.array());
    }

    private static byte[] aesEncrypt(String plainText, String key, String iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        return cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
    }

    private static String makeRandomIv() {
        String chars = "0123456789abcdefghijklmnopqrstuvwxyz";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
