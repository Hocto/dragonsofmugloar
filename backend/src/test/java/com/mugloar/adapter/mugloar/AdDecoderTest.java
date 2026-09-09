package com.mugloar.adapter.mugloar;

import static org.assertj.core.api.Assertions.assertThat;

import com.mugloar.adapter.mugloar.dto.MessageResponse;
import com.mugloar.domain.Ad;
import com.mugloar.domain.AdEncoding;
import com.mugloar.domain.RiskLevel;
import org.junit.jupiter.api.Test;

/**
 * The payloads here are real responses captured from the live API, not invented ones. That matters
 * for the ROT13 case in particular, where the accented characters are the whole point.
 */
class AdDecoderTest {

    private final AdDecoder decoder = new AdDecoder();

    @Test
    void leavesPlainAdsAlone() {
        Ad ad = decoder.decode(new MessageResponse(
                "dEmoFKc4", "Help Ramadan Parrish to clean their dog", "82", 7, null, "Risky"));

        assertThat(ad.adId()).isEqualTo("dEmoFKc4");
        assertThat(ad.message()).isEqualTo("Help Ramadan Parrish to clean their dog");
        assertThat(ad.reward()).isEqualTo(82);
        assertThat(ad.risk()).isEqualTo(RiskLevel.RISKY);
        assertThat(ad.encoding()).isEqualTo(AdEncoding.NONE);
    }

    @Test
    void decodesBase64Ads() {
        Ad ad = decoder.decode(new MessageResponse(
                "R2h0SjVLb2g=",
                "SW5maWx0cmF0ZSBUaGUgSWNlIFdvbHZlcmluZSBUcmliZSBhbmQgcmVjb3ZlciB0aGVpciBzZWNyZXRzLg==",
                "138", 2, 1, "UmF0aGVyIGRldHJpbWVudGFs"));

        assertThat(ad.adId()).isEqualTo("GhtJ5Koh");
        assertThat(ad.message()).isEqualTo("Infiltrate The Ice Wolverine Tribe and recover their secrets.");
        assertThat(ad.risk()).isEqualTo(RiskLevel.RATHER_DETRIMENTAL);
        assertThat(ad.encoding()).isEqualTo(AdEncoding.BASE64);
    }

    @Test
    void decodesRot13Ads() {
        Ad ad = decoder.decode(new MessageResponse(
                "FLw4HlD6",
                "Xvyy Pbeaé Cnvagre jvgu cbg naq znxr Qryberf Cerfyrl gb gnxr gur oynzr",
                "144", 2, 2, "Fhvpvqr zvffvba"));

        assertThat(ad.adId()).isEqualTo("SYj4UyQ6");
        assertThat(ad.message())
                .isEqualTo("Kill Corné Painter with pot and make Delores Presley to take the blame");
        assertThat(ad.risk()).isEqualTo(RiskLevel.SUICIDE_MISSION);
        assertThat(ad.encoding()).isEqualTo(AdEncoding.ROT13);
    }

    @Test
    void rot13LeavesNonAsciiLettersWhereTheyAre() {
        Ad ad = decoder.decode(new MessageResponse(
                "nopqrstu", "Nééé M", "1", 1, 2, "Fhvpvqr zvffvba"));

        assertThat(ad.message()).isEqualTo("Aééé Z");
    }

    @Test
    void findsTheImpossibleLabelThatOnlyExistsBehindEncoding() {
        Ad ad = decoder.decode(new MessageResponse(
                "DQVQBTzT", "Xvyy Qebtb", "166", 2, 2, "Vzcbffvoyr"));

        assertThat(ad.risk()).isEqualTo(RiskLevel.IMPOSSIBLE);
    }

    @Test
    void acceptsRewardAsAStringBecauseTheDocsSaySoAndTheApiDisagrees() {
        assertThat(decoder.decode(new MessageResponse("a", "m", "77", 3, null, "Gamble")).reward())
                .isEqualTo(77);
    }
}
