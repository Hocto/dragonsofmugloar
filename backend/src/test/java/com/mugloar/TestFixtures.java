package com.mugloar;

import com.mugloar.domain.Ad;
import com.mugloar.domain.AdEncoding;
import com.mugloar.domain.GameState;
import com.mugloar.domain.RiskLevel;

/** Small builders so the tests read as scenarios rather than as constructor calls. */
public final class TestFixtures {

    private TestFixtures() {
    }

    public static Ad ad(String id, int reward, int expiresIn, RiskLevel risk) {
        return new Ad(id, "Do the thing", reward, expiresIn, risk, AdEncoding.NONE);
    }

    public static GameState state(int lives, int gold, int level) {
        return new GameState("game-1", lives, gold, level, 0, 0, 1);
    }
}
