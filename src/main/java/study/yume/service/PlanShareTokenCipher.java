package study.yume.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class PlanShareTokenCipher {
    private static final int IV_LENGTH_BYTES = 12;
    private static final int AUTH_TAG_LENGTH_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public PlanShareTokenCipher(
            @Value("${plan-share.encryption-key:${JWT_SECRET_KEY:}}") String secret
    ) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("PLAN_SHARE_ENCRYPTION_KEY 환경변수는 UTF-8 기준 32바이트 이상이어야 합니다.");
        }
        try {
            Mac keyDerivation = Mac.getInstance("HmacSHA256");
            keyDerivation.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
            byte[] derivedKey = keyDerivation.doFinal("YUME_PLAN_SHARE_TOKEN_ENCRYPTION_V1".getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(derivedKey, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("공유 링크 암호화 키를 준비하지 못했습니다.", e);
        }
    }

    public String encrypt(String token, Long planId) {
        byte[] iv = new byte[IV_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(AUTH_TAG_LENGTH_BITS, iv));
            cipher.updateAAD(planId.toString().getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array()
            );
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("공유 링크를 안전하게 저장하지 못했습니다.", e);
        }
    }

    public String decrypt(String ciphertext, Long planId) {
        try {
            byte[] payload = Base64.getUrlDecoder().decode(ciphertext);
            if (payload.length <= IV_LENGTH_BYTES) {
                throw new IllegalStateException("저장된 공유 링크 데이터가 올바르지 않습니다.");
            }
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(AUTH_TAG_LENGTH_BITS, iv));
            cipher.updateAAD(planId.toString().getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("공유 링크를 복구하지 못했습니다. 암호화 키 설정을 확인해 주세요.", e);
        }
    }
}
