package com.mugloar.benchmark;

import java.util.List;

/**
 * @param scores      final score of every game that finished, ascending
 * @param failures    games that ended because Mugloar stopped answering, not because the dragon died
 * @param targetScore the bar being reported against
 */
public record BenchmarkResult(List<Integer> scores, List<String> failures, int targetScore, long elapsedMillis) {

    public int played() {
        return scores.size();
    }

    public double average() {
        return scores.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    public int median() {
        if (scores.isEmpty()) {
            return 0;
        }
        int middle = scores.size() / 2;
        return scores.size() % 2 == 1
                ? scores.get(middle)
                : (scores.get(middle - 1) + scores.get(middle)) / 2;
    }

    public int min() {
        return scores.isEmpty() ? 0 : scores.get(0);
    }

    public int max() {
        return scores.isEmpty() ? 0 : scores.get(scores.size() - 1);
    }

    /** The number the task actually asks about. */
    public double shareClearingTarget() {
        if (scores.isEmpty()) {
            return 0;
        }
        long cleared = scores.stream().filter(score -> score >= targetScore).count();
        return (double) cleared / scores.size();
    }

    public int percentile(int p) {
        if (scores.isEmpty()) {
            return 0;
        }
        int index = Math.min(scores.size() - 1, (int) Math.round((p / 100.0) * (scores.size() - 1)));
        return scores.get(index);
    }
}
