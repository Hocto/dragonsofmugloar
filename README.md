# Dragons of Mugloar

A Spring Boot service that plays the Mugloar game, and a Vue app that either watches it play or
hands you the board so you can play it yourself. The backend owns the Mugloar integration and all
the decision-making; the browser only ever talks to the backend. Across 30 runs the strategy
averages about 3,600 points, which clears the 1,000 bar comfortably, though not every run does.

The measurements behind these decisions are in [NOTES.md](NOTES.md): the probes, the tables, and
the experiments that went the wrong way. This file is the decisions.

## Running it

```bash
docker compose up --build
```

Then open http://localhost:8088. nginx serves the UI and proxies `/api` to the backend, so
everything is same-origin.

For local development, two terminals:

```bash
./gradlew :backend:bootRun
```

```bash
cd frontend && npm install && npm run dev
```

The UI is on http://localhost:5173 and Vite proxies `/api` to :8080. Tests are
`./gradlew :backend:test` and, in `frontend/`, `npm run test` and `npm run e2e`. To see the numbers
for yourself, `./gradlew :backend:benchmark -Pgames=100` plays a hundred games headless; add
`-Pstrategy=reward-per-risk` to run the other strategy.

## Reading the API first

The published docs are incomplete, and I only found that out by calling the thing. The two fields
the whole strategy turns on, `probability` and `encrypted`, are not documented at all.
`/messages` returns a bare array, not the documented object. Solve responses omit `level` and
purchase responses omit `score`, so a caller has to carry those across or quietly corrupt its own
state.

Some ads arrive Base64 (`encrypted: 1`) or ROT13 (`encrypted: 2`), and the encoding covers `adId`
too: posting the encoded id to `/solve` returns 400, the decoded one 200. That happens in exactly one
class, `AdDecoder`, so nothing above the adapter has ever seen an encoded ad. Decoding also turned up
an eleventh probability label, "Impossible", that only ever appears on encrypted ads.

## How the bot decides

The first version was reward divided by risk rank, and it had no concept of time. You could see the
failure in the turn log: a 250-gold notice would sit on the board for six turns while the bot
cleared 20-gold safe ones, then expire unattempted. What stopped that was an urgency term,
`1 + weight / expiresIn`, so that an ad about to vanish outranks an identical one with turns to spare.

That gave me `reward × P(success) × urgency`, and then I stopped guessing at `P(success)`. I made
the bot log every attempt with its label and outcome, played thirty-odd games, and counted about
440 of them. Two things fell out.

The two bottom labels essentially never pay: "Suicide mission" went 1 for 44 and "Impossible" 0 for
16, at a life apiece. And my urgency term was actively steering into them, because those ads carry
the biggest rewards and the shortest expiry. `RiskLevel` now knows which labels are never worth
attempting, and the strategy drops them before it looks at a reward.

And dragon level does not make ads easier. I had built it into the probability model on that
theory; the success rate barely moves with level. What moves is the pay: the same "Piece of cake"
is worth about 50 gold at level 2 and about 195 at level 6. So I deleted the class that modelled
level as a probability, and the shop policy got rewritten around upgrades being where the score
comes from: heal when lives are low, because lives are the one thing you cannot earn back; then buy
the cheapest upgrade whenever there is surplus, because every upgrade raises the level by one step
regardless of price; then nothing, because a purchase costs a turn even when it fails.

There is also a way to give up a turn. `investigate/reputation` costs one and risks nothing, and
the bot uses it when the whole board is below the survival floor and there is no gold for a potion.
It is a narrower move than it looks, because waiting adds nothing to the board; it only ages what is
there. Measured against real play it almost never fires. The details, and why I kept it anyway,
are in the notes. The player gets a stronger version of the same move on `POST /api/runs/{id}/wait`: one
click passes turns until a notice actually expires and the board changes, and the button says up
front how many turns that will cost.

## What the numbers look like

Thirty runs of the tuned strategy against fifteen of the naive one it was meant to replace:

```
                        expected-value      reward-per-risk
  Games                     30                  15
  Average                 3582                3744
  Median                  3934                4072
  Min / Max          505 / 5629         1832 / 5080
  Cleared 1000            93.3%              100.0%
  Aborted upstream           0                   0
```

All thirty expected-value scores, since the shape matters more than the average: 505, 769, 1031,
1511, 1642, 1819, 2882, 3215, 3720, 3739, 3771, 3795, 3802, 3851, 3877, 3991, 4045, 4057, 4090,
4102, 4255, 4282, 4301, 4320, 4379, 4559, 4983, 5106, 5423, 5629. Two are under 1,000 and I am not
rounding them off; both lost their first two attempts and never got the gold together for an
upgrade.

That table is not the result I wanted, and it is the most useful thing I measured. The strategy I
spent the time on does not beat the naive one. Once the risk scale is ordered by measurement,
dividing reward by rank already buries the labels that never pay, so my explicit guard was solving a
problem the baseline solved by accident; what is left of the difference is the survival floor,
which makes the tuned strategy play smaller when it is behind. The samples are unequal and the gap
is inside the spread of either, so I cannot show which is better. What I can show is that measuring
the labels is what moved the numbers: the same strategy with my guessed scale had a median of 2,323.

Where the points actually come from is levelling. Four runs with upgrades switched off scored 791,
865, 1,905 and 1,945 against a median of 3,934 with them on. Four runs is not a benchmark, but it
lines up with the reward-by-level numbers.

## Why the browser never calls Mugloar

Every game decision lives on the server, and that is the point rather than a side effect. The
scoring, the risk scale and the decoding exist once; a frontend that called Mugloar directly would
need its own copies and they would drift the first time I tuned one. The upstream URL stays out of
a bundle anyone can read. And the only cross-origin call in the system is made from Java, so CORS
is a five-line class that does nothing in Docker and exists only so `npm run dev` works.

The split follows: the backend decides, the frontend renders decisions. The board arrives already
scored, with the strategy's chance, its pick and a flag on the ads it refuses, so `AdCard.vue` never
needs to know what a survival floor is. Difficulty arrives as a rank out of eleven rather than a
colour, because the requirement is that the scale reads without colour.

If this grew, the first thing I would move is run storage. Runs live in a map, so a restart drops
them and a second instance cannot see the first one's games. That is fine for one container and
wrong for two.

## The look

The reflex for a game API is a dark dashboard, and that is what I was reacting to. The original site
is a photograph, a red title and plain body text, with torn parchment sheets and pencil sketches set
into it; I took the parchment and built the whole interface out of it. Warm paper, ink text, one wax
red for anything urgent or lost, one gold for money, and nothing else gets a colour, which is what
keeps the red meaningful when an ad is about to expire. Type is system serif stacks rather than a
web font, so it works with no network inside a container. The dragon behind the start screen is an
inline SVG I plotted from a handful of curves, so the repository ships no image files and
nothing in it is borrowed.

## Rough edges

- **The risk scale is fit to about 440 attempts.** The top and bottom I would defend anywhere; the
  middle four labels are a few points apart on samples in the twenties, and a bigger sample could
  reorder them.
- **The survival floor probably costs more than it saves.** It is the main thing separating the two
  strategies and the benchmark does not show it paying for itself. Relaxing it is the first
  experiment I would run.
- **`expected-value` is the default and I cannot justify that on score.** I keep it because its
  numbers mean something to the UI and it refuses hopeless ads on purpose rather than by accident.
  Those are design reasons, and the benchmark is right there.
- **The bot's waiting move is very nearly dead code.** Zero triggers in 154 measured turns. If the
  floor is relaxed, it can probably go.
- **Two testing gaps.** `AutoPlayer`'s thread, the SSE fan-out and registry eviction are only
  exercised by hand. And the Playwright run stubs the backend at the network boundary, so it
  covers the frontend rather than the whole stack.

The longer list is in the notes.

## Dependencies

Backend: `spring-boot-starter-web` gives `RestClient` for the Mugloar calls, MVC for my own API and
`SseEmitter` for the turn stream. `starter-validation` for request bodies. `starter-actuator` only
for `/actuator/health`, which Compose waits on. `starter-test` and `wiremock-standalone` for the
tests. Retry is thirty lines in `Backoff` rather than Spring Retry or Resilience4j, because one
policy on six methods does not need a circuit breaker library.

Frontend: `vue` and `pinia`; `vite`, `typescript` and `vue-tsc` to build and typecheck; `vitest`,
`@vue/test-utils`, `jsdom` and `msw` for unit tests; `@playwright/test` for the browser run. No
component library, because the design is half the exercise. No router: the run id lives in the URL
hash so a refresh, a shared link and the back button all work, and one hash pattern is not enough
routing to justify the dependency.

## On AI assistance

I built this with Claude Code doing a lot of the typing: the DTO layer, most of the CSS, the first
pass of the tests and a draft of this file. The parts I would want to be judged on went the way they
did because I pushed in that direction: probing the live API instead of trusting the docs, measuring
the risk labels rather than guessing them, and taking seriously what those measurements said even
when it was that my own strategy did not win. Everything the model wrote, I read.
