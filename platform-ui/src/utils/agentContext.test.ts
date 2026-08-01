import { describe, expect, it } from 'vitest'
import type { ApiOperationRecord, CaseRecord, RunRecord, TestKnowledgeRecord } from '../types/platform'
import { resolveAgentContext } from './agentContext'

const now = '2026-08-01T00:00:00Z'

function scenario(id: string, operationId: string): CaseRecord {
  return {
    id,
    projectId: 'project-1',
    name: id,
    code: id,
    description: '',
    tags: [],
    dslVersion: 'v1',
    content: `dslVersion: v1\nname: ${id}\nsteps:\n  - name: request\n    type: http\n    operation:\n      id: ${operationId}\n`,
    status: 'ACTIVE',
    revision: 0,
    createdAt: now,
    updatedAt: now,
  }
}

function operation(id: string, apiSpecId: string): ApiOperationRecord {
  return {
    id,
    projectId: 'project-1',
    apiSpecId,
    method: 'POST',
    path: `/${id}`,
    requestBody: {},
    responses: {},
    createdAt: now,
  }
}

function knowledge(id: string, scope: TestKnowledgeRecord['scope'], targetId?: string): TestKnowledgeRecord {
  return {
    id,
    projectId: 'project-1',
    name: id,
    description: '',
    scope,
    targetId,
    tags: [],
    content: '',
    status: 'PUBLISHED',
    version: 1,
    createdAt: now,
    updatedAt: now,
  }
}

describe('resolveAgentContext', () => {
  it('resolves linked context and applies explicit additions and exclusions', () => {
    const scenarios = [scenario('scenario-1', 'operation-1'), scenario('scenario-2', 'operation-2')]
    const operations = [operation('operation-1', 'spec-1'), operation('operation-2', 'spec-2')]
    const execution = {
      id: 'run-1', projectId: 'project-1', caseId: 'scenario-2', triggeredBy: 'tester',
      status: 'PASSED', startedAt: now, finishedAt: now, durationMs: 10, steps: [],
    } satisfies RunRecord
    const result = resolveAgentContext({
      targetScenario: scenarios[0],
      selection: {
        included: [{ type: 'EXECUTION', id: execution.id }],
        excluded: [
          { type: 'OPERATION', id: 'operation-1' },
          { type: 'TEST_KNOWLEDGE', id: 'knowledge-project' },
        ],
      },
      scenarios,
      suites: [],
      operations,
      runs: [execution],
      knowledge: [
        knowledge('knowledge-project', 'PROJECT'),
        knowledge('knowledge-scenario', 'SCENARIO', 'scenario-2'),
        knowledge('knowledge-operation', 'OPERATION', 'operation-2'),
        { ...knowledge('knowledge-draft', 'PROJECT'), status: 'DRAFT' },
      ],
    })

    expect(result.availableAutomaticOperations.map((item) => item.id)).toEqual(['operation-1', 'operation-2'])
    expect(result.automaticOperations.map((item) => item.id)).toEqual(['operation-2'])
    expect(result.matchingKnowledge.map((item) => item.id)).toEqual([
      'knowledge-project', 'knowledge-scenario', 'knowledge-operation',
    ])
    expect(result.visibleKnowledge.map((item) => item.id)).toEqual([
      'knowledge-scenario', 'knowledge-operation',
    ])
  })
})
