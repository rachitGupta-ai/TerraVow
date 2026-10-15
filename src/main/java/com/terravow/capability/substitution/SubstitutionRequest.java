package com.terravow.capability.substitution;

import com.terravow.domain.MachineId;

import java.util.Objects;
import java.util.Optional;

/**
 * A request to synthesise a substitution analysis for a machine or process step.
 *
 * <p>A user requests a substitution analysis for a machine or process step (Req 5.1). The request
 * identifies the subject either by a registered {@link MachineId} or by a free-text process-step
 * descriptor, and may carry an optional functional-role hint. When no explicit role is supplied, the
 * service infers the functional role of the subject before generating candidates (Req 5.1).
 *
 * <p>Exactly one of {@code machineId} or {@code processStep} must be present — a request identifies
 * one subject.
 *
 * @param machineId         the registered machine to analyse, when the subject is a machine; never
 *                         {@code null}
 * @param processStep       the free-text process-step descriptor, when the subject is a process
 *                         step; never {@code null}
 * @param functionalRoleHint an optional caller-supplied functional-role hint; never {@code null}
 *
 * @see <a href="file:../../../../../../.kiro/specs/terravow/requirements.md">Req 5.1</a>
 */
public record SubstitutionRequest(
        Optional<MachineId> machineId,
        Optional<String> processStep,
        Optional<String> functionalRoleHint) {

    /**
     * Validates that exactly one subject is identified (Req 5.1).
     *
     * @throws NullPointerException     if any component is {@code null}
     * @throws IllegalArgumentException if neither or both of {@code machineId} and
     *                                  {@code processStep} are present, or if a present
     *                                  {@code processStep} is blank
     */
    public SubstitutionRequest {
        Objects.requireNonNull(machineId, "machineId must not be null");
        Objects.requireNonNull(processStep, "processStep must not be null");
        Objects.requireNonNull(functionalRoleHint, "functionalRoleHint must not be null");
        if (machineId.isPresent() == processStep.isPresent()) {
            throw new IllegalArgumentException(
                    "Exactly one of machineId or processStep must identify the subject");
        }
        if (processStep.isPresent() && processStep.get().isBlank()) {
            throw new IllegalArgumentException("processStep descriptor must not be blank");
        }
    }

    /**
     * Creates a request for a registered machine.
     *
     * @param machineId the machine to analyse
     * @return a machine-subject request
     */
    public static SubstitutionRequest forMachine(MachineId machineId) {
        return new SubstitutionRequest(
                Optional.of(Objects.requireNonNull(machineId, "machineId")),
                Optional.empty(),
                Optional.empty());
    }

    /**
     * Creates a request for a free-text process step.
     *
     * @param processStep the non-blank process-step descriptor
     * @return a process-step-subject request
     */
    public static SubstitutionRequest forProcessStep(String processStep) {
        return new SubstitutionRequest(
                Optional.empty(),
                Optional.of(Objects.requireNonNull(processStep, "processStep")),
                Optional.empty());
    }

    /**
     * Returns a human-readable identifier of the subject for diagnostics and inferred-role
     * descriptions.
     *
     * @return the machine id value or the process-step descriptor
     */
    public String subjectDescriptor() {
        return machineId.map(MachineId::value).orElseGet(processStep::get);
    }
}
