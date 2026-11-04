# Additional targeted actual-worker evidence

No tracked product changes and no Maven repeat. The root 40-request stress test
measures coalescing and bounds, then uses emergency test cleanup. This additional
probe separately observes actual thread exit BEFORE any forced cleanup while a
real 500-node ELK layout is pending, followed by a canary that suppresses the
private transport termination call. Original production source and the exact
packaged worker bytes are used.

The original v1 setup tried suppressing dispose AFTER a completed layout. The
worker had already retired on completion, so its expected-negative assertion
correctly failed and no qualification was issued. Those original files remain.
The v2 setup disposes a provably pending actual worker and separately suppresses
its private transport termination. The positive must exit0, the negative must
exit1 specifically with LIVE_WORKER_AFTER_DISPOSE, before emergency cleanup.
This is implementer-supplied supplemental evidence, not either of the required
new independent reviewer countercases.

The v2 pending-worker probe observed the correct exit before cleanup, but its
additional exact count incorrectly expected one terminate call. Inspection of
unchanged LayoutService shows cancel plus finally each call terminateWorker;
there are exactly two idempotent transport termination calls and one actual
thread exit. The v2 failure is retained. V3 checks exactly TWO calls, one exit,
zero live workers before cleanup, and detects suppressed termination as a real
live worker. No product assertions or product source were changed.
