import type { CaseRecord, ScenarioDraftRecord } from '../types/platform'

export type AgentDraftApplyInspection = {
  allowed: boolean
  reason?: 'MISSING_SCENARIO' | 'STALE_REVISION'
  requiresDiscardConfirmation: boolean
}

export function inspectAgentDraftApply(
  scenario: CaseRecord | undefined,
  draft: ScenarioDraftRecord,
  targetDirty: boolean,
): AgentDraftApplyInspection {
  if (!scenario || scenario.id !== draft.scenarioId) {
    return { allowed: false, reason: 'MISSING_SCENARIO', requiresDiscardConfirmation: false }
  }
  if (scenario.revision !== draft.sourceRevision) {
    return { allowed: false, reason: 'STALE_REVISION', requiresDiscardConfirmation: false }
  }
  return { allowed: true, requiresDiscardConfirmation: targetDirty }
}
