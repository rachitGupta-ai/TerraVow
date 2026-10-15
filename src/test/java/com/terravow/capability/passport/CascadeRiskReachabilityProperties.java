package com.terravow.capability.passport;

// Feature: terravow, Property 25: Cascade-risk reaches every dependent downstream tier

import static org.assertj.core.api.Assertions.assertThat;

import com.terravow.capability.passport.SupplyGraph.Dependency;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based test for cascade-risk reachability (Property 25).
 *
 * <p>When a degraded condition is detected at an upstream tier, cascade-risk is propagated to
 * <em>every</em> tier that transitively depends on it — no dependent tier is omitted and no
 * non-dependent tier is included (Req 7.6). This property generates an arbitrary directed
 * supply-chain graph and a degraded upstream tier, then checks that
 * {@link PassportService#propagateCascade(SupplyGraph, DegradedCondition)} reports exactly the set
 * of tiers reachable by following dependency edges downstream from the degraded tier, computed by
 * an independent reference traversal.
 *
 * <p><strong>Validates: Requirements 7.6</strong>
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 7.6</a>
 */
class CascadeRiskReachabilityProperties {

    private final PassportService service = new DefaultPassportService();

    /**
     * Property 25: the cascade report names exactly the transitively dependent downstream tiers of
     * the degraded upstream tier, each with the source severity attributed (Req 7.6).
     */
    @Property(tries = 100)
    void cascadeReportContainsExactlyTheDependentDownstreamTiers(
            @ForAll("graphAndDegraded") GraphAndDegraded scenario) {
        CascadeRisk report = service.propagateCascade(scenario.graph(), scenario.condition());

        Set<TierId> reported = new HashSet<>();
        for (CascadeRisk.Entry entry : report.entries()) {
            reported.add(entry.tier());
        }

        Set<TierId> expected =
                downstreamReachable(scenario.edges(), scenario.condition().upstream());

        // Exactly the transitively dependent downstream tiers — none missing, none extra (Req 7.6).
        assertThat(reported).isEqualTo(expected);
        assertThat(report.origin()).isEqualTo(scenario.condition().upstream());
        // The degraded tier never cascades risk to itself.
        assertThat(reported).doesNotContain(scenario.condition().upstream());
        // Every dependent tier carries the source severity (Req 7.6).
        assertThat(report.entries())
                .allMatch(e -> e.attributedRisk() == scenario.condition().severity());
    }

    /** Independent reference BFS over outgoing (downstream) edges, excluding the start tier. */
    private static Set<TierId> downstreamReachable(List<Dependency> edges, TierId start) {
        Map<TierId, List<TierId>> outgoing = new HashMap<>();
        for (Dependency edge : edges) {
            outgoing.computeIfAbsent(edge.upstream(), k -> new ArrayList<>()).add(edge.downstream());
        }
        Set<TierId> reachable = new HashSet<>();
        Set<TierId> seen = new HashSet<>();
        Deque<TierId> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            TierId current = queue.removeFirst();
            for (TierId next : outgoing.getOrDefault(current, List.of())) {
                if (seen.add(next)) {
                    reachable.add(next);
                    queue.addLast(next);
                }
            }
        }
        return reachable;
    }

    /** A generated supply graph, its edge list, and the degraded condition applied to it. */
    record GraphAndDegraded(SupplyGraph graph, List<Dependency> edges, DegradedCondition condition) {}

    /**
     * Generates an arbitrary directed supply graph over up to eight tiers together with a degraded
     * upstream tier and severity. Edges connect distinct tiers; the degraded tier is drawn from the
     * tier universe so both connected and isolated origins are exercised.
     *
     * @return a generator of graph-and-degraded scenarios
     */
    @Provide
    Arbitrary<GraphAndDegraded> graphAndDegraded() {
        Arbitrary<Integer> tierCounts = Arbitraries.integers().between(1, 8);
        return tierCounts.flatMap(tierCount -> {
            List<TierId> tiers = new ArrayList<>(tierCount);
            for (int i = 0; i < tierCount; i++) {
                tiers.add(new TierId("tier-" + i));
            }
            Arbitrary<Dependency> edgeArb = distinctEdge(tiers);
            Arbitrary<List<Dependency>> edgesArb = edgeArb.list().ofMinSize(0).ofMaxSize(tierCount * 2);
            Arbitrary<TierId> originArb = Arbitraries.of(tiers);
            Arbitrary<Double> severityArb = Arbitraries.doubles().between(0.0, 1.0).ofScale(3);
            return Combinators.combine(edgesArb, originArb, severityArb)
                    .as((edges, origin, severity) -> {
                        SupplyGraph graph = SupplyGraph.of(edges);
                        return new GraphAndDegraded(
                                graph, edges, new DegradedCondition(origin, severity));
                    });
        });
    }

    /** Builds an edge between two distinct tiers drawn from the universe. */
    private static Arbitrary<Dependency> distinctEdge(List<TierId> tiers) {
        if (tiers.size() < 2) {
            // Not enough tiers for an edge; produce none by picking an impossible pair filtered out.
            return Arbitraries.of(tiers)
                    .map(t -> (Dependency) null)
                    .filter(d -> false);
        }
        Arbitrary<TierId> from = Arbitraries.of(tiers);
        Arbitrary<TierId> to = Arbitraries.of(tiers);
        return Combinators.combine(from, to)
                .as((u, v) -> u.equals(v) ? null : new Dependency(u, v))
                .filter(d -> d != null);
    }
}
