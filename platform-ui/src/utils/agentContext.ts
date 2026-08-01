import type {
  AgentContextSelection,
  ApiOperationRecord,
  CaseRecord,
  RunRecord,
  SuiteRecord,
  TestKnowledgeRecord,
} from '../types/platform'
import { parseDsl } from './caseDsl'

export const emptyAgentContextSelection = (): AgentContextSelection => ({ included: [], excluded: [] })

export type ResolvedAgentContext = {
  availableAutomaticOperations: ApiOperationRecord[]
  automaticOperations: ApiOperationRecord[]
  matchingKnowledge: TestKnowledgeRecord[]
  visibleKnowledge: TestKnowledgeRecord[]
}

export function resolveAgentContext(options: {
  targetScenario?: CaseRecord
  selection: AgentContextSelection
  scenarios: CaseRecord[]
  suites: SuiteRecord[]
  operations: ApiOperationRecord[]
  runs: RunRecord[]
  knowledge: TestKnowledgeRecord[]
}): ResolvedAgentContext {
  const { targetScenario, selection, scenarios, operations, runs, knowledge } = options
  const scenarioIds = new Set<string>()
  const suiteIds = new Set<string>()
  const automaticOperationIds = new Set<string>()
  const availableAutomaticOperationIds = new Set<string>()
  const explicitOperationIds = new Set(
    selection.included.filter((item) => item.type === 'OPERATION').map((item) => item.id),
  )
  const excludedOperationIds = new Set(
    selection.excluded.filter((item) => item.type === 'OPERATION').map((item) => item.id),
  )
  const excludedKnowledgeIds = new Set(
    selection.excluded.filter((item) => item.type === 'TEST_KNOWLEDGE').map((item) => item.id),
  )

  if (targetScenario) scenarioIds.add(targetScenario.id)
  selection.included.forEach((item) => {
    if (item.type === 'SCENARIO') scenarioIds.add(item.id)
    if (item.type === 'SUITE') suiteIds.add(item.id)
    if (item.type === 'EXECUTION') {
      const run = runs.find((record) => record.id === item.id)
      if (run) scenarioIds.add(run.caseId)
    }
  })

  for (const scenarioId of scenarioIds) {
    const scenario = scenarios.find((record) => record.id === scenarioId)
    for (const operationId of scenarioOperationIds(scenario)) {
      availableAutomaticOperationIds.add(operationId)
      if (!excludedOperationIds.has(operationId)) automaticOperationIds.add(operationId)
    }
  }

  const operationIds = new Set([...automaticOperationIds, ...explicitOperationIds])
  const apiSpecIds = new Set(
    operations.filter((operation) => operationIds.has(operation.id)).map((operation) => operation.apiSpecId),
  )
  const matchingKnowledge = knowledge.filter((record) => {
    if (record.status !== 'PUBLISHED') return false
    if (record.scope === 'PROJECT') return true
    if (record.scope === 'SCENARIO') return Boolean(record.targetId && scenarioIds.has(record.targetId))
    if (record.scope === 'SUITE') return Boolean(record.targetId && suiteIds.has(record.targetId))
    if (record.scope === 'OPERATION') return Boolean(record.targetId && operationIds.has(record.targetId))
    if (record.scope === 'API_SPEC') return Boolean(record.targetId && apiSpecIds.has(record.targetId))
    return false
  })

  return {
    availableAutomaticOperations: operations.filter((operation) => (
      availableAutomaticOperationIds.has(operation.id) && !explicitOperationIds.has(operation.id)
    )),
    automaticOperations: operations.filter((operation) => (
      automaticOperationIds.has(operation.id) && !explicitOperationIds.has(operation.id)
    )),
    matchingKnowledge,
    visibleKnowledge: matchingKnowledge.filter((record) => !excludedKnowledgeIds.has(record.id)),
  }
}

export function scenarioOperationIds(scenario?: CaseRecord) {
  if (!scenario) return []
  const parsed = parseDsl(scenario.content).data
  if (!parsed || !Array.isArray(parsed.steps)) return []
  return [...new Set(parsed.steps.flatMap((step: unknown) => {
    if (!step || typeof step !== 'object' || Array.isArray(step)) return []
    const operation = (step as Record<string, unknown>).operation
    if (!operation || typeof operation !== 'object' || Array.isArray(operation)) return []
    const id = (operation as Record<string, unknown>).id
    return typeof id === 'string' && id ? [id] : []
  }))]
}
