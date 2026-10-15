package com.terravow.ingest;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A deterministic in-memory {@link PubSubPort} for local-first operation and tests (Req 3.1).
 *
 * <p>TerraVow is local-first and has no live Pub/Sub in the dev/test environment, so this stub
 * records every published event, in publish order, in an in-memory list. It fabricates nothing and
 * drops nothing: {@link #publish} simply appends, and {@link #published()} exposes the recorded
 * events for assertions and for the continuous-query triage to drain. The backing list is a
 * {@link CopyOnWriteArrayList} so the stub is safe to share across concurrent publishers, matching
 * the multi-replica safety expectation of the streaming front door.
 *
 * @see PubSubPort
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 3.1</a>
 */
public final class InMemoryPubSubPort implements PubSubPort {

    private final List<StreamEvent> published = new CopyOnWriteArrayList<>();

    /** {@inheritDoc} */
    @Override
    public void publish(StreamEvent event) {
        published.add(Objects.requireNonNull(event, "event must not be null"));
    }

    /**
     * Returns the events published so far, in publish order (Req 3.1).
     *
     * @return an immutable snapshot of the published events; never {@code null}
     */
    public List<StreamEvent> published() {
        return List.copyOf(published);
    }
}
