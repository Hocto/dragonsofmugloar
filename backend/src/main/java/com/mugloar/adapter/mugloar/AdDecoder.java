package com.mugloar.adapter.mugloar;

import com.mugloar.adapter.mugloar.dto.MessageResponse;
import com.mugloar.domain.Ad;
import com.mugloar.domain.AdEncoding;
import com.mugloar.domain.RiskLevel;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.UnaryOperator;

/**
 * The single place an encoded ad is decoded. Ads arrive Base64 when {@code encrypted} is 1 and
 * ROT13 when it is 2, and the encoding covers {@code adId} as well as the text: posting the encoded
 * id to {@code /solve} returns 400. Everything above the adapter sees a decoded {@link Ad}.
 */
public final class AdDecoder {

    private static final Base64.Decoder BASE64 = Base64.getDecoder();

    public Ad decode(MessageResponse raw) {
        AdEncoding encoding = AdEncoding.fromWire(raw.encrypted());
        UnaryOperator<String> decode = decoderFor(encoding);
        return new Ad(
                decode.apply(raw.adId()),
                decode.apply(raw.message()),
                parseReward(raw.reward()),
                raw.expiresIn(),
                RiskLevel.fromLabel(decode.apply(raw.probability())),
                encoding);
    }

    private static UnaryOperator<String> decoderFor(AdEncoding encoding) {
        return switch (encoding) {
            case NONE -> UnaryOperator.identity();
            case BASE64 -> AdDecoder::base64;
            case ROT13 -> AdDecoder::rot13;
        };
    }

    private static String base64(String value) {
        if (value == null) {
            return null;
        }
        return new String(BASE64.decode(value), StandardCharsets.UTF_8);
    }

    /** ROT13 over ASCII letters only; accented characters are left unchanged, as the game does. */
    private static String rot13(String value) {
        if (value == null) {
            return null;
        }
        char[] chars = value.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c >= 'a' && c <= 'z') {
                chars[i] = (char) ('a' + (c - 'a' + 13) % 26);
            } else if (c >= 'A' && c <= 'Z') {
                chars[i] = (char) ('A' + (c - 'A' + 13) % 26);
            }
        }
        return new String(chars);
    }

    /** The docs type reward as a String and the API sends a number; both are accepted. */
    private static int parseReward(String reward) {
        if (reward == null || reward.isBlank()) {
            return 0;
        }
        return Integer.parseInt(reward.strip());
    }
}
