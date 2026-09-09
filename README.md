# Dragons of Mugloar

A Spring Boot service that plays the Mugloar game, and a Vue app that either watches it play or
hands you the board so you can play it yourself. The backend owns the Mugloar integration and all
the decision-making; the browser only ever talks to the backend. Across 30 runs the strategy
averages a little over 3,600 points, which clears the 1,000 bar comfortably, though not every run
does.

## Running it

```bash
docker compose up --build
```

Then open http://localhost:8088. That builds both modules and puts nginx in front, serving the UI
and proxying `/api` to the backend, so everything is same-origin.

For local development, two terminals:

```bash
./gradlew :backend:bootRun
```

```bash
cd frontend && npm install && npm run dev
```

The UI is on http://localhost:5173 and Vite proxies `/api` to :8080.

To see the numbers for yourself:

```bash
./gradlew :backend:benchmark -Pgames=100
```

That plays 100 games headless and prints average, median, percentiles and the share clearing 1,000.
Add `-Pstrategy=reward-per-risk` to run the other one, or `-Pturnlog=true` to get a line per turn.
Tests are `./gradlew :backend:test` and, in `frontend/`, `npm run test` and `npm run e2e`.

## Reading the API first

The published docs at dragonsofmugloar.com/doc are incomplete, and I only found that out by calling
the thing. Two fields that decide the entire strategy — `probability` and `encrypted` — are not
documented at all. `GET /:gameId/messages` is documented as returning `{"messages": [...]}` and
actually returns a bare array. `reward` is documented as a String and arrives as a number. Solve
responses omit `level` and purchase responses omit `score`, so a caller has to carry those across
itself or quietly corrupt its own state.

The encoding was the part worth being careful about. Some ads arrive with `encrypted: 1` (Base64) or
`encrypted: 2` (ROT13), and the encoding covers `adId` as well as the text. I checked rather than
assumed: posting the encoded id to `/solve` returns 400 and posting the decoded one returns 200.
That happens in exactly one class, `AdDecoder`, so nothing above the adapter has ever seen an
encoded ad and no second implementation can drift from the first. Decoding also turned up an
eleventh probability label, "Impossible", which only ever appears on encrypted ads — if you only
read plaintext messages you will never know it exists.

## How the bot decides

The first version was reward divided by risk rank. It sounded reasonable and it lost most of its
runs, for a specific reason: it had no concept of time. Ads expire. A 250-gold notice would sit on
the board while the bot cleared 20-gold safe ones, and by the time it got round to the good one it
was gone. Adding an urgency term — `1 + weight / expiresIn`, so an ad about to vanish is worth more
than an identical one with six turns left — was the single change that made runs look like runs.

That got me to `reward × P(success) × urgency`, and then I stopped guessing at `P(success)`. I made
the bot log every attempt it made with the label and the outcome, played about thirty games, and
counted. Over roughly 440 attempts:

```
Piece of cake       80/88   0.909      Risky               13/26   0.500
Walk in the park    59/70   0.843      Rather detrimental   8/24   0.333
Sure thing          26/35   0.743      Playing with fire    5/16   0.312
Gamble              18/27   0.667      Suicide mission      1/44   0.023
Quite likely        35/53   0.660      Impossible           0/16   0.000
Hmmm....            22/43   0.512
```

Two things fell out of that. The scale I had guessed from the words was wrong — "Gamble" beats
"Quite likely" and "Rather detrimental" beats "Playing with fire", both by margins well inside the
noise at those counts. I left them where the measurement put them rather than where intuition
wanted them, because for one of those I have data and for the other I have a hunch.

The bigger one: the bottom two labels essentially never pay. One success in forty-four and zero in
sixteen, at a life apiece. And my urgency term was *actively steering into them*, because those ads
carry the biggest rewards and the shortest expiry, which is exactly the combination the formula was
built to chase. Sixty attempts, sixty dead lives, and it took a measurement to see it. `RiskLevel`
now knows which labels are never worth attempting, and the strategy drops them before it looks at a
reward, so a big number on a suicide mission never gets the chance to be tempting.

I had also built dragon level into the probability model, on the theory that a stronger dragon
succeeds more often. It does not. The success rate barely moves with level. What moves is the pay:
the same "Piece of cake" is worth about 50 gold at level 2 and about 195 at level 6. Level is a
multiplier on the whole board, not an edge on the next attempt. So I deleted the class that modelled
it as a probability, the shop policy got rewritten around "upgrades are where the score comes from",
and the strategy reads the reward it is given and nothing else.

The shop is three rules in a deliberate order. Healing when lives are low, because lives are the only
resource you cannot earn back and a potion is the cheapest thing on the shelf. Then the cheapest
upgrade whenever there is surplus, because every upgrade raises the level by one step regardless of
price — 100 gold buys the same progress as 300, which I checked. Then nothing, because buying costs
a turn even when the purchase fails.

Reputation I tracked and then switched off. `POST /:gameId/investigate/reputation` costs a turn — I
confirmed it by watching an ad's `expiresIn` tick down across the call — and a turn is worth
somewhere north of 100 gold once the dragon is levelled. Nothing in the strategy reads the three
numbers it returns. Paying a turn for information you do not act on is a bad trade, so
`investigate-reputation-every-turns` defaults to 0. Set it and the run records reputation and the UI
shows it. That is an honest "no", not a missing feature.

## What the numbers actually look like

RESULTS_BLOCK

Failures are worth naming rather than averaging away. Mugloar sits behind Cloudflare and answers a
burst of requests with `error code: 1015`, which is not a normal 429 and outlasts any sane retry
budget. Running games in parallel walks straight into it. Two things fixed that: pacing game starts
rather than retrying through the ban, and caching the shop listing per game — it is identical every
turn, and fetching it alongside the board was doubling my request count for nothing. Runs killed by
the upstream are counted in their own bucket in the benchmark output, not recorded as zero scores,
because folding them into the average would flatter it.

The low runs are real. A run that loses its first two attempts is behind before it has 100 gold,
and the survival floor then keeps it playing small safe ads it cannot level out of. That is the
shape of most of the tail.

## Why the browser never calls Mugloar

Every game decision lives on the server, and that is the point rather than a side effect. The
scoring, the risk scale and the decoding exist once. A frontend that called Mugloar directly would
need its own copy of the decode step and its own opinion about what a "Suicide mission" is worth,
and the two would drift the first time I tuned one. The upstream base URL stays out of a bundle
anyone can read. And the only cross-origin call in the system is the one this service makes from
Java, which is why CORS is a five-line class that does nothing at all in the Docker setup and only
exists so `npm run dev` works from port 5173.

The split follows from that. The backend decides; the frontend renders decisions. The board arrives
already scored, with the strategy's chance, its pick, and a flag on the ads it refuses to touch, so
`AdCard.vue` never needs to know what a survival floor is. Difficulty arrives as a rank out of
eleven rather than a colour, because the requirement is that the scale reads without colour and the
cleanest way to guarantee that is to send a number.

If this grew, the first thing I would move is run storage. Runs live in a `ConcurrentHashMap` right
now, which means a restart drops them and a second instance cannot see the first one's games. That
is fine for one container and wrong for two. The second thing is the benchmark: it is a
`CommandLineRunner` behind a profile, and the moment anyone wants to compare strategies regularly it
should write results somewhere instead of printing them.

## The look

The reflex when you get handed a game API is a dark dashboard: KPI cards, a neon accent, a data
table of ads. That is what I was reacting to. The original dragonsofmugloar.com is hand-drawn ink on
parchment, so this is a tavern notice board. Warm paper, ink-black text, one wax red reserved for
anything urgent or lost, one gold reserved for money, and nothing else gets a colour — which is what
keeps the red meaningful when an ad is about to expire. Notices are pinned slightly askew, the paper
darkens as an ad ages, and expiring ones lift off the board rather than blinking out.

Type is system serif stacks — Iowan, Palatino, Georgia — rather than a web font. It costs one fewer
request, it works with no network inside a container, and those faces already have the slightly
literary feel the parchment wants. There is a dark theme, but it is candlelight rather than a
generic dark mode: the paper goes to soot and the ink to warm bone.

## Rough edges

- **The risk scale is fit to about 440 attempts.** The top and bottom of it I would defend anywhere.
  The middle four labels are separated by a few percentage points on samples in the twenties and
  thirties, and I would not be surprised if a 5,000-attempt sample reordered them. The strategy is
  not very sensitive to that, but the numbers in `RiskLevel` are more confident than the data is.
- **The urgency weight and the survival floors are hand-tuned, not swept.** I changed them, ran a
  benchmark, kept what helped. I never ran a proper sweep, so I do not know how close to a local
  optimum these are.
- **`RunService`'s auto-run loop has no direct test.** The turn loop underneath it does, against a
  scripted `MugloarApi`, but the thread that drives it, the SSE fan-out and the eviction policy are
  only exercised by hand. That is the next test I would write.
- **The Playwright run stubs the backend** with `page.route`. It covers the frontend end to end, not
  the stack. A real game takes dozens of turns and depends on dice and a rate-limited third party,
  which is not something I want gating a push.
- **The SSE reconnect is cruder than it looks.** `EventSource` retries on its own, and after three
  failures the composable stops and offers a manual retry. Because the server replays a run's
  history to every new subscriber and the store drops sequence numbers it has already seen,
  reconnecting is cheap and correct — but on a long run it replays the whole thing, and there is no
  `Last-Event-ID` handling to avoid that.
- **`RunRegistry` evicts by insertion order**, not by last use. With a 200-run cap and games that
  last minutes, that has never mattered, but it is the wrong policy if it ever does.
- **The benchmark's concurrency is tuned to my IP address.** Three games at a time with a 1.2s
  stagger is what stopped the 1015s here. On a different network that is guesswork.

## Dependencies, and why each one is here

Backend: `spring-boot-starter-web` gives me `RestClient` for the Mugloar calls, MVC for my own API
and `SseEmitter` for the turn stream — three needs, one dependency. `spring-boot-starter-validation`
for the request bodies. `spring-boot-starter-actuator` only for `/actuator/health`, which is what
Compose waits on so the first page load cannot land on a 502. `spring-boot-starter-test` and
`wiremock-standalone` for the tests. That is the whole list. Retry is thirty lines in `Backoff`
rather than Spring Retry or Resilience4j: those are the right answer when you need circuit breakers
and bulkheads and metrics, and here it is one policy applied to six methods.

Frontend: `vue` and `pinia`. `vite`, `@vitejs/plugin-vue`, `typescript` and `vue-tsc` to build and
typecheck. `vitest`, `@vue/test-utils` and `jsdom` for unit tests, `msw` to stub the backend at the
network layer so the store and the API client under test are the real ones, and `@playwright/test`
for the browser run. No component library, because a component library would make the design
decisions and the design is half the exercise. No router, because the three screens are phases of
one run rather than places you can link into, and a URL for "halfway through a game" would be a lie.

## On AI assistance

I built this with Claude Code doing a lot of the typing — the DTO layer, most of the CSS, the first
pass of the tests and a draft of this file. The parts I would want to be judged on went the way they
did because I pushed in that direction: probing the live API instead of trusting the docs, deciding
to measure the risk labels rather than guess them, and then taking seriously what those measurements
said about dragon level and about the two labels that never pay. Everything the model wrote, I read.
