package com.terravow.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.terravow.domain.Confidence;
import com.terravow.evidence.EvidenceChain;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the generic {@link Determination} abstraction (value + confidence +
 * Evidence_Chain) every capability result conforms to (Req 1.7, 10.1).
 */
class DeterminationTest {

    @Test
    void carriesValueConfidenceAndEvidence() {
        Determination<String> det =
                Determination.of("EID=0.42", new Confidence(0.9), EvidenceChain.EMPTY);

        assertThat(det.value()).isEqualTo("EID=0.42");
        assertThat(det.confidence()).isEqualTo(new Confidence(0.9));
        assertThat(det.evidence()).isEqualTo(EvidenceChain.EMPTY);
    }

    @Test
    void rejectsNullValue() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Determination<>(null, Confidence.FULL, EvidenceChain.EMPTY));
    }

    @Test
    void rejectsNullConfidence() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Determination<>("v", null, EvidenceChain.EMPTY));
    }

    @Test
    void rejectsNullEvidence() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Determination<>("v", Confidence.FULL, null));
    }
}
