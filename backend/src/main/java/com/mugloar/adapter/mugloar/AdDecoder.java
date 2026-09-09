package com.mugloar.adapter.mugloar;

import com.mugloar.adapter.mugloar.dto.MessageResponse;
import com.mugloar.domain.Ad;
import com.mugloar.domain.AdEncoding;
import com.mugloar.domain.RiskLevel;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.UnaryOperator;

/**
 * The one place an encoded ad is turned back into a readable one.
 *
 * <p>Some ads arrive obfuscated - Base64 when {@code encrypted} is 1, ROT13 when it is 2 - and the
 * obfuscation covers {@code adId} as well as the text. That matters: posting the encoded id to
 * {@code /solve} returns 400, and posting the decoded one works. I checked, because getting it
 * wrong is a silent 400 loop.
 *
 * <p>Everything above the adapter sees a decoded {@link Ad} and never has to ask. Keeping this in a
 * single class is what makes that true - there is no second place that could decode differently, and
 * the frontend cannot get it wrong because the frontend never sees an encoded payload at all.
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

    /**
     * ROT13 over ASCII letters only. The messages contain accented characters ("Corné", "Aurélio")
     * and those are left alone, which is what the game does too.
     */
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

    /** The docs type reward as a String, the live API sends a number. Accept either. */
    private static int parseReward(String reward) {
        if (reward == null || reward.isBlank()) {
            return 0;
        }
        return Integer.parseInt(reward.strip());
    }
}
