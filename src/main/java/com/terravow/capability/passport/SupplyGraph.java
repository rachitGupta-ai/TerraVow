package com.terravow.capability.passport;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A directed supply-chain dependency graph over {@link TierId} nodes, used for cascade-risk
 * propagation (Req 7.6) and substitution ripple (Req 7.7).
 *
 * <p>Each {@link Dependency} edge {@code upstream -> downstream} means the downstream tier depends on
 * the upstream tier (the downstream tier consumes what the upstream tier supplies). A degraded
 * condition at an upstream tier cascades to <em>every</em> tier that transitively depends on it
 * (Req 7.6); a substitution at a tier ripples to <em>every</em> connected tier (Req 7.7). Traversal
 * is deterministic: adjacency is stored in insertion-ordered maps/sets and reachability uses a FIFO
 * breadth-first search, so identical graphs always yield identically ordered results.
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 7.6, 7.7</a>
 */
public final class SupplyGraph {

    /**
     * A directed dependency edge: {@code downstream} depends on {@code upstream}.
     *
     * @param upstream   the supplying tier; never {@code null}
     * @param downstream the dependent tier; never {@code null} and distinct from {@code upstream}
     */
    public record Dependency(TierId upstream, TierId downstream) {

        /**
         * Validates both endpoints are present and distinct.
         *
         * @throws NullPointerException     if either endpoint is {@code null}
         * @throws IllegalArgumentException if {@code upstream} equals {@code downstream}
         */
        public Dependency {
            Objects.requireNonNull(upstream, "upstream must not be null");
            Objects.requireNonNull(downstream, "downstream must not be null");
            if (upstream.equals(downstream)) {
                throw new IllegalArgumentException(
                        "a dependency edge must connect two distinct tiers, got: " + upstream);
            }
        }
    }

    private final Map<TierId, Set<TierId>> downstreamOf = new LinkedHashMap<>();
    private final Map<TierId, Set<TierId>> neighboursOf = new LinkedHashMap<>();

    private SupplyGraph(List<Dependency> edges) {
        for (Dependency edge : edges) {
            downstreamOf
                    .computeIfAbsent(edge.upstream(), k -> new LinkedHashSet<>())
                    .add(edge.downstream());
            neighboursOf
                    .computeIfAbsent(edge.upstream(), k -> new LinkedHashSet<>())
                    .add(edge.downstream());
            neighboursOf
                    .computeIfAbsent(edge.downstream(), k -> new LinkedHashSet<>())
                    .add(edge.upstream());
        }
    }

    /**
     * Builds a graph from a list of dependency edges.
     *
     * @param edges the directed dependency edges; never {@code null}, defensively copied, may be
     *              empty
     * @return a new {@code SupplyGraph}
     * @throws NullPointerException if {@code edges} is {@code null} or contains a {@code null} edge
     */
    public static SupplyGraph of(List<Dependency> edges) {
        Objects.requireNonNull(edges, "edges must not be null");
        return new SupplyGraph(List.copyOf(edges));
    }

    /**
     * Returns, in deterministic breadth-first order, every tier that transitively depends on the
     * given upstream tier (its downstream reachable set, excluding the tier itself) (Req 7.6).
     *
     * @param upstream the tier a degraded condition occurs at; never {@code null}
     * @return the ordered set of transitively dependent downstream tiers
     * @throws NullPointerException if {@code upstream} is {@code null}
     */
    public Set<TierId> dependentDownstreamTiers(TierId upstream) {
        Objects.requireNonNull(upstream, "upstream must not be null");
        return traverse(upstream, downstreamOf);
    }

    /**
     * Returns, in deterministic breadth-first order, every tier connected to the given tier through
     * any dependency edge, in either direction, transitively (excluding the tier itself) (Req 7.7).
     *
     * @param tier the tier a substitution is applied at; never {@code null}
     * @return the ordered set of connected tiers
     * @throws NullPointerException if {@code tier} is {@code null}
     */
    public Set<TierId> connectedTiers(TierId tier) {
        Objects.requireNonNull(tier, "tier must not be null");
        return traverse(tier, neighboursOf);
    }

    private static Set<TierId> traverse(TierId start, Map<TierId, Set<TierId>> adjacency) {
        Set<TierId> visited = new LinkedHashSet<>();
        Deque<TierId> queue = new ArrayDeque<>();
        queue.add(start);
        Set<TierId> seen = new LinkedHashSet<>();
        seen.add(start);
        while (!queue.isEmpty()) {
            TierId current = queue.removeFirst();
            for (TierId next : adjacency.getOrDefault(current, Set.of())) {
                if (seen.add(next)) {
                    visited.add(next);
                    queue.addLast(next);
                }
            }
        }
        return visited;
    }
}
