package com.terravow.capability.passport;

// Feature: terravow, Property 26: Substitution ripple reaches every connected tier

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
 * Property-based test for substitution ripple reachability (Property 26).
 *
 * <p>When a substitution is applied at a tier, the delivery-requirement ripple reaches
 * <em>every</em> tier connected to the applied tier through any dependency edge in either direction,
 * transitively — no connected tier is omitted and no unconnected tier is included (Req 7.7). This
 * property generates an arbitrary directed supply-chain graph and an applied tier, then checks that
 * {@link PassportService#supplyChainRipple(SupplyGraph, TierId, Substitution)} reports exactly the
 * set of tiers reachable by treating every dependency edge as undirected, computed by an independent
 * reference traversal.
 *
 * <p><strong>Validates: Requirements 7.7</strong>
 *
 * @see <a href="file:../../../../../.kiro/specs/terravow/requirements.md">Req 7.7</a>
 */
class SubstitutionRippleReachabilityProperties {

    private final PassportService service = new DefaultPassportService();

    /**
     * Property 26: the ripple report names exactly the tiers transitively connected (either
     * direction) to the applied tier, each with the substitution's delivery-requirement delta
     * (Req 7.7).
     */
    @Property(tries = 100)
    void rippleReportContainsExactlyTheConnectedTiers(
            @ForAll("graphAndApplied") GraphAndApplied scenario) {
        RippleResult report =
                service.supplyChainRipple(
                        scenario.graph(), scenario.appliedTier(), scenario.substitution());

        Set<TierId> reported = new HashSet<>();
        for (RippleResult.Entry entry : report.entries()) {
            reported.add(entry.tier());
        }

        Set<TierId> expected = connectedReachable(scenario.edges(), scenario.appliedTier());

        // Exactly the transitively connected tiers — none missing, none extra (Req 7.7).
        assertThat(reported).isEqualTo(expected);
        assertThat(report.appliedTier()).isEqualTo(scenario.appliedTier());
        // The applied tier is never reported as a connected tier of itself.
        assertThat(reported).doesNotContain(scenario.appliedTier());
        // Every connected tier carries the substitution's delivery-requirement delta (Req 7.7).
        assertThat(report.entries())
                .allMatch(
                        e ->
                                e.deliveryRequirementChange()
                                        == scenario.substitution().deliveryRequirementDelta());
    }

    /** Independent reference BFS treating every edge as undirected, excluding the start tier. */
    private static Set<TierId> connectedReachable(List<Dependency> edges, TierId start) {
        Map<TierId, List<TierId>> adjacency = new HashMap<>();
        for (Dependency edge : edges) {
            adjacency.computeIfAbsent(edge.upstream(), k -> new ArrayList<>()).add(edge.downstream());
            adjacency.computeIfAbsent(edge.downstream(), k -> new ArrayList<>()).add(edge.upstream());
        }
        Set<TierId> reachable = new HashSet<>();
        Set<TierId> seen = new HashSet<>();
        Deque<TierId> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            TierId current = queue.removeFirst();
            for (TierId next : adjacency.getOrDefault(current, List.of())) {
                if (seen.add(next)) {
                    reachable.add(next);
                    queue.addLast(next);
                }
            }
        }
        return reachable;
    }

    /** A generated supply graph, its edge list, the applied tier, and the substitution. */
    record GraphAndApplied(
            SupplyGraph graph, List<Dependency> edges, TierId appliedTier, Substitution substitution) {}

    /**
     * Generates an arbitrary directed supply graph over up to eight tiers together with an applied
     * tier and a substitution. Edges connect distinct tiers; the applied tier is drawn from the tier
     * universe so both connected and isolated applied tiers are exercised.
     *
     * @return a generator of graph-and-applied scenarios
     */
    @Provide
    Arbitrary<GraphAndApplied> graphAndApplied() {
        Arbitrary<Integer> tierCounts = Arbitraries.integers().between(1, 8);
        return tierCounts.flatMap(tierCount -> {
            List<TierId> tiers = new ArrayList<>(tierCount);
            for (int i = 0; i < tierCount; i++) {
                tiers.add(new TierId("tier-" + i));
            }
            Arbitrary<Dependency> edgeArb = distinctEdge(tiers);
            Arbitrary<List<Dependency>> edgesArb = edgeArb.list().ofMinSize(0).ofMaxSize(tierCount * 2);
            Arbitrary<TierId> appliedArb = Arbitraries.of(tiers);
            Arbitrary<Double> deltaArb = Arbitraries.doubles().between(-1.0, 1.0).ofScale(3);
            return Combinators.combine(edgesArb, appliedArb, deltaArb)
                    .as((edges, applied, delta) -> {
                        SupplyGraph graph = SupplyGraph.of(edges);
                        return new GraphAndApplied(
                                graph, edges, applied, new Substitution("substitute", delta));
                    });
        });
    }

    /** Builds an edge between two distinct tiers drawn from the universe. */
    private static Arbitrary<Dependency> distinctEdge(List<TierId> tiers) {
        if (tiers.size() < 2) {
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
