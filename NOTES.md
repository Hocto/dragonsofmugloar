# Notes: what I measured, and what it changed

The README carries the decisions. This carries the evidence and the detours, in the order they
happened. Every number here came from calling the live API or running the benchmark; none of it is
reasoned from the docs.

## The API contract, as actually served

The docs at `/doc` are an apiDoc bundle with the data inlined in a JS file. Extracting it and then
calling every endpoint side by side turned up these gaps:

- `GET /:gameId/messages` returns a bare JSON array. The docs say `{"messages": [...]}`.
- `probability` and `encrypted` are undocumented. `encrypted` is `null`, `1` (Base64) or `2`
  (ROT13). The encoding covers `adId`, `message` and `probability`; `reward` and `expiresIn` stay
  numeric.
- `reward` is documented as a String and arrives as a number. The DTO binds it as a String so
  either works and it is parsed once.
- Solve responses have no `level`. Purchase responses have no `score` or `highScore`. The client
  takes the previous state and carries the missing fields across.
- `shoppingSuccess` is documented as a String and arrives as a JSON boolean. Bound as a String,
  parsed leniently.
- A failed purchase still consumes a turn. The shop policy therefore never suggests something it
  cannot afford.
- `POST /:gameId/investigate/reputation` consumes a turn. Verified by watching an ad's `expiresIn`
  drop across the call.
- Error bodies are HTML, not JSON. Nothing tries to parse them.
- Posting an encoded `adId` to `/solve` returns 400; the decoded one returns 200. This is the one
  that would have been a silent failure loop.

Eleven probability labels exist. "Impossible" only ever appeared on encrypted ads, in a sample of
about 2,800 board reads.

## The risk scale, measured

With `-Pturnlog=true` the benchmark prints one line per attempt with the label and the outcome.
Thirty-odd games, roughly 440 attempts:

```
Piece of cake       80/88   0.909      Risky               13/26   0.500
Walk in the park    59/70   0.843      Rather detrimental   8/24   0.333
Sure thing          26/35   0.743      Playing with fire    5/16   0.312
Gamble              18/27   0.667      Suicide mission      1/44   0.023
Quite likely        35/53   0.660      Impossible           0/16   0.000
Hmmm....            22/43   0.512
```

Two adjacent pairs came out the wrong way round from what the words suggest — "Gamble" beats
"Quite likely", "Rather detrimental" beats "Playing with fire" — by margins inside the noise at those
counts. I left them where the data put them.

Before this table, with a scale I had guessed from the words, the same strategy scored a median of
2,323 over thirteen runs. After it, 3,934 over thirty. Nothing else about the strategy changed
between those two numbers.

## What dragon level does

I had modelled level as lifting the success chance: `p = prior + (1 - prior) × (1 - e^(-k·level))`.
Bucketing the same 440 attempts by level showed no lift worth the name. Bucketing the *rewards*
showed this:

```
level 0   n=74   mean reward   48
level 2   n=31                 64
level 4   n=17                138
level 6   n=26                212
level 9   n=13                253
```

And for the same label: "Piece of cake" pays a mean of 50 at level ≤ 2 and 193 at level ≥ 6.
Level multiplies the board; it does not tilt the dice. `SuccessModel` was deleted and the shop
policy rewritten around that.

## The two strategies, head to head

```
                        expected-value      reward-per-risk
  Games                     30                  15
  Average                 3582                3744
  Median                  3934                4072
  Min / Max          505 / 5629         1832 / 5080
  p10 / p90         1511 / 4983         1902 / 4824
  Cleared 1000            93.3%              100.0%
  Aborted upstream           0                   0
```

expected-value, all thirty: 505, 769, 1031, 1511, 1642, 1819, 2882, 3215, 3720, 3739, 3771, 3795,
3802, 3851, 3877, 3991, 4045, 4057, 4090, 4102, 4255, 4282, 4301, 4320, 4379, 4559, 4983, 5106,
5423, 5629.

reward-per-risk: I captured thirteen of the fifteen individually — 1832, 1902, 2889, 3299, 3439,
4072, 4087, 4088, 4337, 4345, 4656, 4824, 5080 — before the log was lost. The summary line
(average 3744, median 4072, min 1832, max 5080) is from the benchmark's own report over all
fifteen, so the two I do not have listed are somewhere between 1832 and 5080 and average out
consistently with the rest. That is a gap in the record and I am naming it rather than filling it.

Why the baseline holds up: once the scale is measured, `reward / (rank + 1)` gives a 250-gold
"Suicide mission" a score of 250/11 ≈ 23, which loses to almost any safe ad. The hopeless-label
guard I added was doing work the arithmetic already did. The remaining difference is the survival
floor (0.80 at one life, 0.60 at two), which the baseline lacks, and which on this evidence makes
the tuned strategy play smaller when behind without saving enough runs to pay for it.

Caveats: unequal samples, and the difference is inside either one's spread. I would not call it
either way. I keep `expected-value` as the default for design reasons — the per-ad success chance it
produces is what the UI shows — and say so.

## Upgrades switched off

Same strategy, shop policy set to buy healing only (`MUGLOAR_STRATEGY_HEALINGTHRESHOLDLIVES=99`):
791, 865, 1,905, 1,945. Four games, because Mugloar's rate limit had me in minute-long backoffs by
then. Not a benchmark; consistent with everything above.

## Waiting out a turn

This one went wrong twice before it went right, and the second time I only noticed because someone
asked the obvious question.

**Turn cost.** Early on I measured that `investigate/reputation` costs a turn and wrote it off as
useless. Later I saw that the turn cost *is* the feature: it is the only call that spends a turn and
risks nothing, which makes it a way to pass. So when every ad is below the survival floor and there
is no gold for a potion, the bot waits instead of gambling a life.

**What waiting does to the board.** I assumed a fresh ad arrives each turn. Eight turns of pure
waiting on a fresh game:

```
turn  ads  same  new  gone
  0    10     -    -     -
  1-6  10    10    0     0
  7    10     0   10    10
```

Nothing changes for seven turns; then the whole cohort expires at once. Solving is different:
after two solves, `same=9 new=1 gone=1` each time. So the board holds ten ads, solving replaces one,
and waiting replaces nothing — it only ages what is there. The rule became: the turns waiting has
to buy is the soonest expiry on the board, and the bot only starts if its remaining budget covers
that. The player's panel shows the same countdown.

**How often it fires.** Three games of realistic play, 154 turns, counting turns where every ad on
the board sat below the floor for the current lives: **zero**. At one life the floor is 0.80,
cleared by "Piece of cake" (0.91) and "Walk in the park" (0.84), which together are about 43% of
everything posted; the chance none of ten ads is one of them is about 0.4%. And on a played board
the soonest expiry has a median of 1, so the seven static turns above only ever happen on a virgin
board.

Status: correct, tested, nearly dead. Its usefulness is a direct function of the floor's strictness.
Left in because it is one branch and covers throwing a run away. The manual button stands on its
own reasoning — a person can pass for their own reasons — and does not depend on any of this.

## Rate limiting

Mugloar sits behind Cloudflare and answers a burst with `error code: 1015`, which outlasts any
sane retry budget. Three things fixed it: pacing game starts with a 1.2 s stagger rather than
retrying through the ban; caching the shop listing per game, since it is identical every turn;
and caching the board on the run so the auto stream's redraw does not refetch it. The benchmark's
concurrency of three is tuned to my IP and is guesswork anywhere else. Runs killed by the upstream
are counted in their own bucket, not as zero scores.

## The stranded-cards bug

Watching the DOM during an auto run: 66 `.notice` elements by turn 25, all stuck with
`notice-leave-from` applied and opacity 0. A tab that is not rendering pauses `requestAnimationFrame`,
so Vue never got the frame where it swaps `leave-from` for `leave-to`, and the leave never
completed. The board was being replaced two or three times a second, so this compounded quickly.
Auto runs now swap the board without animating it. Manual mode keeps the animation; the same stall
is possible there in a backgrounded tab, resolves on return, and a manual board only changes when
the player acts.

## Retrying a solve that timed out

`Backoff` retries a status of 0, which covers dropped connections and read timeouts, on every call
including `POST /solve` and `/buy`. Those are not idempotent. If Mugloar applied the first attempt
and the response was lost, the retry either fails (the ad is gone) or, for a purchase, buys twice.
The turn is then reported as an error and the local state lags the upstream by one until the next
successful call reads it back.

This is a known trade-off, not an oversight. The alternative is to not retry writes on timeout,
which turns every transient blip on a solve into a failed turn. With a ten-second read timeout
and Mugloar answering in well under one, the double-apply window is narrow, and the next
successful read corrects the lag. I have not seen it happen. It would be the first thing I would
suspect if a run's local turn counter ever disagreed with Mugloar's.

## The longer list of rough edges

- The urgency weight and the floors are hand-tuned, not swept.
- The SSE reconnect replays a run's whole history on every reconnect. Correct because the store
  drops seen sequence numbers, but there is no `Last-Event-ID` handling.
- `RunRegistry` evicts finished-first then oldest-started, not least-recently-viewed.
- The board only animates in manual mode, for the reason above.
- The Playwright run stubs the backend with `page.route`. A real game takes dozens of turns and a
  rate-limited third party, which should not gate a push.
- `AutoPlayer`, the SSE fan-out and registry eviction have no direct tests.
- The benchmark prints to a log rather than writing results anywhere.
- Runs live in memory. A restart drops them; a second instance cannot see the first's.
