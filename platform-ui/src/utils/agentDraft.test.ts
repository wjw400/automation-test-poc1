import { describe, expect, it } from 'vitest'
import type { CaseRecord, ScenarioDraftRecord } from '../types/platform'
import { inspectAgentDraftApply } from './agentDraft'

const scenario = {
  id: 'scenario-1', projectId: 'project-1', name: 'Scenario', code: 'scenario', description: '', tags: [],
  dslVersion: 'v1', content: 'dslVersion: v1', status: 'ACTIVE', revision: 4,
  createdAt: '2026-08-01T00:00:00Z', updatedAt: '2026-08-01T00:00:00Z',
} satisfies CaseRecord

const draft = {
  id: 'draft-1', projectId: 'project-1', scenarioId: scenario.id, sourceRevision: 4, revision: 1,
  content: 'dslVersion: v1', status: 'ACTIVE', steps: [], validation: { valid: true, issues: [] },
  diff: { items: [] }, createdAt: scenario.createdAt, updatedAt: scenario.updatedAt,
} satisfies ScenarioDraftRecord

describe('inspectAgentDraftApply', () => {
  it('requires confirmation before replacing a dirty editor', () => {
    expect(inspectAgentDraftApply(scenario, draft, true)).toEqual({
      allowed: true,
      requiresDiscardConfirmation: true,
    })
  })

  it('rejects missing and stale Scenario targets', () => {
    expect(inspectAgentDraftApply(undefined, draft, false).reason).toBe('MISSING_SCENARIO')
    expect(inspectAgentDraftApply({ ...scenario, revision: 5 }, draft, false).reason).toBe('STALE_REVISION')
  })
})
