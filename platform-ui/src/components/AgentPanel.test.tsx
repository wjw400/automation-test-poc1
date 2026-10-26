import { App as AntApp } from 'antd'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ComponentProps } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { platformApi } from '../api/platformApi'
import type {
  AgentArtifactRecord,
  AgentEventRecord,
  AgentTaskRecord,
  ApiOperationRecord,
  CaseRecord,
  ScenarioDraftRecord,
  SuiteRecord,
  TestKnowledgeRecord,
} from '../types/platform'
import { AgentPanel } from './AgentPanel'

vi.mock('../api/platformApi', () => ({
  platformApi: {
    listAgentTasks: vi.fn(),
    listAgentRuntimes: vi.fn(),
    getAgentTask: vi.fn(),
    listAgentMessages: vi.fn(),
    listAgentArtifacts: vi.fn(),
    listAgentEvents: vi.fn(),
    getAgentEventStreamUrl: vi.fn((taskId: string, sequence: number) => `/events/${taskId}?after=${sequence}`),
    createAgentTask: vi.fn(),
    sendAgentMessage: vi.fn(),
    cancelAgentTask: vi.fn(),
    decideAgentContinuation: vi.fn(),
    deleteAgentTask: vi.fn(),
    renameAgentTask: vi.fn(),
    changeAgentTaskPermission: vi.fn(),
    changeAgentTaskModel: vi.fn(),
    recordAgentDecision: vi.fn(),
    getScenarioDraft: vi.fn(),
    publishScenarioDraft: vi.fn(),
  },
}))

const now = '2026-08-01T00:00:00Z'
const scenarioOne = scenario('scenario-1', 'Order lifecycle', 'operation-1')
const scenarioTwo = scenario('scenario-2', 'Refund lifecycle', 'operation-2')
const suiteOne = suite('suite-1', 'Order regression')
const operations = [operation('operation-1', '/orders'), operation('operation-2', '/refunds')]
const knowledge = knowledgeRecord('knowledge-1')

let tasks: AgentTaskRecord[]

class EventSourceStub {
  static instances: EventSourceStub[] = []
  readonly listeners = new Map<string, Array<(event: MessageEvent<string>) => void>>()
  readonly url: string
  closed = false
  onopen: (() => void) | null = null
  onerror: (() => void) | null = null

  constructor(url: string | URL) {
    this.url = String(url)
    EventSourceStub.instances.push(this)
  }

  addEventListener(type: string, listener: EventListenerOrEventListenerObject) {
    const callback = listener as (event: MessageEvent<string>) => void
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), callback])
  }

  emit(type: string, event: AgentEventRecord) {
    const message = new MessageEvent<string>(type, { data: JSON.stringify(event) })
    this.listeners.get(type)?.forEach((listener) => listener(message))
  }

  close() { this.closed = true }
}

beforeEach(() => {
  vi.clearAllMocks()
  window.localStorage.clear()
  EventSourceStub.instances = []
  vi.stubGlobal('EventSource', EventSourceStub)
  tasks = []
  vi.mocked(platformApi.listAgentTasks).mockImplementation(async () => tasks)
  vi.mocked(platformApi.listAgentRuntimes).mockResolvedValue([{
    type: 'FAKE',
    capabilities: { available: true, streaming: true, cancellation: true, tools: true, defaultModel: 'fake-v1', models: ['fake-v1', 'fake-v2'] },
  }])
  vi.mocked(platformApi.getAgentTask).mockImplementation(async (taskId) => tasks.find((task) => task.id === taskId)!)
  vi.mocked(platformApi.listAgentMessages).mockResolvedValue([])
  vi.mocked(platformApi.listAgentArtifacts).mockResolvedValue([])
  vi.mocked(platformApi.listAgentEvents).mockResolvedValue([])
  vi.mocked(platformApi.createAgentTask).mockImplementation(async (projectId, input) => {
    const created = task('task-created', 'READY', input.context, input.targetId)
    created.projectId = projectId
    created.targetType = input.targetType ?? null
    created.targetId = input.targetId ?? null
    tasks = [created]
    return created
  })
  vi.mocked(platformApi.changeAgentTaskPermission).mockImplementation(async (taskId, permissionMode) => {
    const updated = { ...tasks.find((task) => task.id === taskId)!, permissionMode, permissionModeRevision: 2 }
    tasks = [updated]
    return updated
  })
  vi.mocked(platformApi.changeAgentTaskModel).mockImplementation(async (taskId, modelId) => {
    const updated = { ...tasks.find((task) => task.id === taskId)!, modelId }
    tasks = [updated]
    return updated
  })
  vi.mocked(platformApi.sendAgentMessage).mockImplementation(async (taskId) => {
    const running = { ...tasks.find((item) => item.id === taskId)!, status: 'RUNNING' as const }
    tasks = [running]
    return running
  })
  vi.mocked(platformApi.cancelAgentTask).mockImplementation(async (taskId) => {
    const cancelled = { ...tasks.find((item) => item.id === taskId)!, status: 'CANCELLED' as const }
    tasks = [cancelled]
    return cancelled
  })
  vi.mocked(platformApi.decideAgentContinuation).mockImplementation(async (taskId, decision) => {
    const status = decision === 'CONTINUE' ? 'RUNNING' as const : 'CANCELLED' as const
    const updated = { ...tasks.find((item) => item.id === taskId)!, status }
    tasks = [updated]
    return updated
  })
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('AgentPanel', () => {
  it('supports closing and editable Context before creating a chat', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    renderPanel({ onClose })

    await user.click(screen.getByLabelText('Close Agent panel'))
    expect(onClose).toHaveBeenCalledOnce()

    await user.click(screen.getByLabelText('Edit chat context'))
    await user.click((await screen.findAllByLabelText('Remove from context'))[0])
    await user.click(screen.getByLabelText('Add workspace context'))
    await user.click(await screen.findByText(scenarioTwo.name))
    await user.type(screen.getByPlaceholderText('Describe the Scenario change...'), 'Update the request')
    await user.click(screen.getByLabelText('Send message'))

    await waitFor(() => expect(platformApi.createAgentTask).toHaveBeenCalled())
    const input = vi.mocked(platformApi.createAgentTask).mock.calls[0][1]
    expect(input).toMatchObject({ targetType: 'SCENARIO', targetId: scenarioOne.id })
    expect(input.permissionMode).toBeUndefined()
    expect(input.runtimeType).toBeUndefined()
    expect(input.modelId).toBeUndefined()
    expect(input.context?.excluded).toContainEqual({ type: 'OPERATION', id: 'operation-1' })
    expect(input.context?.included).toContainEqual({ type: 'SCENARIO', id: scenarioTwo.id })
  })

  it('creates a Project chat without binding an asset', async () => {
    const user = userEvent.setup()
    renderPanel({ scope: { type: 'PROJECT' } })

    expect(await screen.findByText('Project overview')).toBeInTheDocument()
    await user.type(screen.getByPlaceholderText('Ask about project coverage, APIs, or test strategy...'), 'Review coverage')
    await user.click(screen.getByLabelText('Send message'))

    await waitFor(() => expect(platformApi.createAgentTask).toHaveBeenCalled())
    const input = vi.mocked(platformApi.createAgentTask).mock.calls.at(-1)![1]
    expect(input).toMatchObject({
      title: 'Project overview',
    })
    expect(input.targetType).toBeUndefined()
    expect(input.targetId).toBeUndefined()
    expect(input.permissionMode).toBeUndefined()
  })

  it('creates a Suite-scoped chat from the current Suite tab', async () => {
    const user = userEvent.setup()
    renderPanel({ scope: { type: 'SUITE', id: suiteOne.id }, suites: [suiteOne] })

    await user.type(screen.getByPlaceholderText('Ask about Suite coverage, order, or execution results...'), 'Review suite coverage')
    await user.click(screen.getByLabelText('Send message'))

    await waitFor(() => expect(platformApi.createAgentTask).toHaveBeenCalled())
    expect(vi.mocked(platformApi.createAgentTask).mock.calls.at(-1)![1]).toMatchObject({
      title: suiteOne.name,
      targetType: 'SUITE',
      targetId: suiteOne.id,
    })
  })

  it('only lists conversations belonging to the current Scenario', async () => {
    const user = userEvent.setup()
    tasks = [
      { ...task('task-1', 'COMPLETED', undefined, scenarioOne.id), title: 'Order chat' },
      { ...task('task-2', 'COMPLETED', undefined, scenarioTwo.id), title: 'Refund chat' },
      { ...task('task-3', 'COMPLETED', undefined, suiteOne.id), title: 'Suite chat', targetType: 'SUITE' },
    ]
    renderPanel()
    expect(await screen.findByText(scenarioOne.name)).toBeInTheDocument()

    await user.click(screen.getByLabelText('Chat history'))
    expect(await screen.findByText('Order chat')).toBeInTheDocument()
    expect(screen.queryByText('Refund chat')).not.toBeInTheDocument()
    expect(screen.queryByText('Suite chat')).not.toBeInTheDocument()
  })

  it('renders Assistant Markdown while preserving User messages as plain text', async () => {
    tasks = [task('task-markdown', 'COMPLETED', undefined, scenarioOne.id)]
    vi.mocked(platformApi.listAgentMessages).mockResolvedValue([
      {
        id: 'message-user', taskId: 'task-markdown', sequence: 1, role: 'USER',
        content: { text: '**keep literal**' }, createdAt: now,
      },
      {
        id: 'message-assistant', taskId: 'task-markdown', sequence: 2, role: 'ASSISTANT',
        content: { text: '## Result\n\n- Added step\n\n`orderId`\n\n[Open docs](https://example.com)' }, createdAt: now,
      },
    ])

    renderPanel()

    expect(await screen.findByRole('heading', { name: 'Result', level: 2 })).toBeInTheDocument()
    expect(screen.getByText('Added step').closest('li')).toBeInTheDocument()
    expect(screen.getByText('orderId').tagName).toBe('CODE')
    expect(screen.getByRole('link', { name: 'Open docs' })).toHaveAttribute('target', '_blank')
    expect(screen.getByText('**keep literal**')).toBeInTheDocument()
  })

  it('does not restore a conversation from another scope', async () => {
    tasks = [{ ...task('task-2', 'COMPLETED', undefined, scenarioTwo.id), title: 'Refund chat' }]
    renderPanel()

    expect(await screen.findByLabelText('Edit chat context')).toBeInTheDocument()
    expect(screen.queryByLabelText('View chat context')).not.toBeInTheDocument()
  })

  it('keeps a new chat open when previous conversations exist', async () => {
    const user = userEvent.setup()
    tasks = [task('task-1', 'COMPLETED', undefined, scenarioOne.id)]
    renderPanel()
    expect(await screen.findByLabelText('View chat context')).toBeInTheDocument()

    await user.click(screen.getByLabelText('New chat'))

    expect(await screen.findByText(scenarioOne.name)).toBeInTheDocument()
    expect(screen.getByLabelText('Edit chat context')).toBeInTheDocument()
    expect(screen.queryByLabelText('View chat context')).not.toBeInTheDocument()
  })

  it('changes a completed chat model from its header selector', async () => {
    const user = userEvent.setup()
    tasks = [task('task-model', 'COMPLETED', undefined, scenarioOne.id)]
    renderPanel()

    await user.click(await screen.findByLabelText('Model'))
    await user.click(await screen.findByText('fake-v2'))

    await waitFor(() => expect(platformApi.changeAgentTaskModel).toHaveBeenCalledWith('task-model', 'fake-v2'))
  })

  it('streams unique events and closes the live connection at a terminal event', async () => {
    tasks = [task('task-stream', 'RUNNING', undefined, scenarioOne.id)]
    vi.mocked(platformApi.listAgentEvents).mockResolvedValue([
      event('event-1', 1, 'message.delta', { delta: 'Hello' }),
    ])
    renderPanel()

    await waitFor(() => expect(EventSourceStub.instances).toHaveLength(1))
    const source = EventSourceStub.instances[0]
    source.emit('message.delta', event('duplicate', 1, 'message.delta', { delta: ' duplicated' }))
    source.onerror?.()
    expect(await screen.findByText('Live updates are reconnecting.')).toBeInTheDocument()
    source.emit('message.delta', event('event-2', 2, 'message.delta', { delta: ' world' }))
    expect(await screen.findByText('Hello world')).toBeInTheDocument()
    expect(screen.queryByText('Live updates are reconnecting.')).not.toBeInTheDocument()

    source.emit('task.completed', event('event-3', 3, 'task.completed', {}))
    expect(source.closed).toBe(true)
    source.onerror?.()
    expect(screen.queryByText('Live updates are reconnecting.')).not.toBeInTheDocument()
  })

  it('collapses Tool lifecycle events into one completed activity row', async () => {
    tasks = [task('task-tools', 'COMPLETED', undefined, scenarioOne.id)]
    vi.mocked(platformApi.listAgentEvents).mockResolvedValue([
      event('tool-started', 1, 'tool.started', {
        turnId: 'turn-1', toolName: 'getCurrentScenario', callSequence: 1,
      }),
      event('tool-completed', 2, 'tool.completed', {
        turnId: 'turn-1', toolName: 'getCurrentScenario', callSequence: 1,
      }),
    ])

    const view = renderPanel()

    expect(await screen.findByText('getCurrentScenario / completed')).toBeInTheDocument()
    expect(view.container.querySelectorAll('.agent-activity-row')).toHaveLength(1)
    expect(view.container.querySelector('.agent-activity-row .anticon-loading')).not.toBeInTheDocument()
    expect(screen.queryByText('getCurrentScenario / started')).not.toBeInTheDocument()
  })

  it('does not keep an unmatched historical Tool call spinning after the Task ends', async () => {
    tasks = [task('task-interrupted-tool', 'FAILED', undefined, scenarioOne.id)]
    vi.mocked(platformApi.listAgentEvents).mockResolvedValue([
      event('tool-started', 1, 'tool.started', {
        turnId: 'turn-1', toolName: 'getCurrentScenario', callSequence: 1,
      }),
      event('task-failed', 2, 'task.failed', { message: 'Turn failed', retryable: false }),
    ])

    const view = renderPanel()

    expect(await screen.findByText('getCurrentScenario / unfinished')).toBeInTheDocument()
    expect(view.container.querySelector('.agent-activity-row .anticon-loading')).not.toBeInTheDocument()
  })

  it('only spins unmatched Tool calls from the active retry turn', async () => {
    tasks = [task('task-retry-tools', 'RUNNING', undefined, scenarioOne.id)]
    vi.mocked(platformApi.listAgentEvents).mockResolvedValue([
      event('old-task-started', 1, 'task.started', { turnId: 'turn-1' }),
      event('old-tool-started', 2, 'tool.started', {
        turnId: 'turn-1', toolName: 'getCurrentScenario', callSequence: 1,
      }),
      event('old-task-failed', 3, 'task.failed', { turnId: 'turn-1' }),
      event('new-task-started', 4, 'task.started', { turnId: 'turn-2' }),
      event('new-tool-started', 5, 'tool.started', {
        turnId: 'turn-2', toolName: 'listProjectOperations', callSequence: 1,
      }),
    ])

    const view = renderPanel()

    expect(await screen.findByText('getCurrentScenario / unfinished')).toBeInTheDocument()
    expect(screen.getByText('listProjectOperations / started')).toBeInTheDocument()
    expect(view.container.querySelectorAll('.agent-activity-row .anticon-loading')).toHaveLength(1)
  })

  it('stops a running Task and retries a retryable failure', async () => {
    const user = userEvent.setup()
    tasks = [task('task-running', 'RUNNING', undefined, scenarioOne.id)]
    const view = renderPanel()
    await user.click(await screen.findByLabelText('Stop generation'))
    expect(platformApi.cancelAgentTask).toHaveBeenCalledWith('task-running')

    tasks = [task('task-failed', 'FAILED', undefined, scenarioOne.id)]
    vi.mocked(platformApi.listAgentMessages).mockResolvedValue([{
      id: 'message-1', taskId: 'task-failed', sequence: 1, role: 'USER', content: { text: 'Try this change' }, createdAt: now,
    }])
    vi.mocked(platformApi.listAgentEvents).mockResolvedValue([
      event('failed', 1, 'task.failed', { message: 'Timed out', retryable: true }),
    ])
    view.unmount()
    renderPanel()
    expect(await screen.findByText('Timed out')).toBeInTheDocument()
    await user.click(await screen.findByRole('button', { name: 'Retry' }))
    expect(platformApi.sendAgentMessage).toHaveBeenCalledWith('task-failed', 'Try this change')
    await waitFor(() => expect(screen.queryByText('Timed out')).not.toBeInTheDocument())
  })

  it('shows a plain-language continuation choice for paused Tasks', async () => {
    const user = userEvent.setup()
    tasks = [task('task-paused', 'WAITING_FOR_USER', undefined, scenarioOne.id)]
    renderPanel()

    expect(await screen.findByText('Agent paused')).toBeInTheDocument()
    expect(screen.getByText(/avoid using resources continuously/i)).toBeInTheDocument()
    expect(screen.queryByText(/Tool call/i)).not.toBeInTheDocument()
    expect(screen.getByPlaceholderText('Choose Continue or Stop before sending another message.')).toBeDisabled()

    await user.click(screen.getByRole('button', { name: 'Continue' }))
    expect(platformApi.decideAgentContinuation).toHaveBeenCalledWith('task-paused', 'CONTINUE')
  })

  it('shows Plan and Citation artifacts and applies a valid Draft', async () => {
    const user = userEvent.setup()
    const onApplyDraft = vi.fn()
    const onOpenKnowledge = vi.fn()
    tasks = [task('task-artifact', 'COMPLETED', undefined, scenarioOne.id)]
    vi.mocked(platformApi.listAgentArtifacts).mockResolvedValue(artifacts())
    vi.mocked(platformApi.getScenarioDraft).mockResolvedValue(draft())
    renderPanel({ onApplyDraft, onOpenKnowledge })

    expect(await screen.findByText('SetRequestHeader · X-Correlation-Id')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /SCENARIO.*knowledge-1/ }))
    expect(onOpenKnowledge).toHaveBeenCalledWith('knowledge-1')

    const applyButton = screen.getByRole('button', { name: 'Apply to editor' })
    await user.click(applyButton)
    expect(onApplyDraft).toHaveBeenCalledWith(expect.objectContaining({ id: 'draft-1' }))
    await waitFor(() => expect(screen.getByText('Applied')).toBeInTheDocument())
    expect(screen.queryByText('SetRequestHeader 路 X-Correlation-Id')).not.toBeInTheDocument()
  })

  it('shows only the latest Draft result in a conversation', async () => {
    tasks = [task('task-artifact', 'COMPLETED', undefined, scenarioOne.id)]
    const oldDraft = { ...artifact('old-draft', 'SCENARIO_DRAFT', { name: 'Old Draft' }), targetId: 'draft-old' }
    const oldPlan = {
      ...artifact('old-plan', 'COMMAND_PLAN', {
        commands: [{ type: 'RenameStep', stepId: 'step-1', name: 'Historical step name' }],
      }),
      targetId: 'draft-old',
    }
    const latest = artifacts().map((item) => ({ ...item, targetId: 'draft-latest' }))
    vi.mocked(platformApi.listAgentArtifacts).mockResolvedValue([oldDraft, oldPlan, ...latest])

    const view = renderPanel()

    expect(await screen.findByText('SetRequestHeader · X-Correlation-Id')).toBeInTheDocument()
    expect(screen.queryByText(/Historical step name/)).not.toBeInTheDocument()
    expect(view.container.querySelectorAll('.agent-artifact')).toHaveLength(1)
  })

  it('restores an applied Draft as a compact record from Task history', async () => {
    tasks = [task('task-artifact', 'COMPLETED', undefined, scenarioOne.id)]
    vi.mocked(platformApi.listAgentArtifacts).mockResolvedValue(artifacts())
    vi.mocked(platformApi.listAgentEvents).mockResolvedValue([
      event('applied', 1, 'decision.draft_applied_to_editor', { draftId: 'draft-1' }),
    ])

    renderPanel()

    expect(await screen.findByText('Applied')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Apply to editor' })).not.toBeInTheDocument()
    expect(screen.queryByText('SetRequestHeader 路 X-Correlation-Id')).not.toBeInTheDocument()
  })

  it('publishes a new Scenario Draft and adds it to the current Suite after confirmation', async () => {
    const user = userEvent.setup()
    const suiteTask = task('task-artifact', 'COMPLETED', undefined, suiteOne.id)
    suiteTask.targetType = 'SUITE'
    tasks = [suiteTask]
    const createdScenario = scenario('scenario-created', 'Cancel order', 'operation-1')
    const updatedSuite: SuiteRecord = {
      ...suiteOne,
      revision: 1,
      cases: [{
        id: 'entry-created', caseId: createdScenario.id, caseName: createdScenario.name,
        caseCode: createdScenario.code, order: 1, enabled: true, variableOverrides: {},
      }],
    }
    vi.mocked(platformApi.listAgentArtifacts).mockResolvedValue([
      {
        ...artifact('new-scenario-draft', 'NEW_SCENARIO_DRAFT', {
          name: 'Cancel order',
          sourceSuiteId: suiteOne.id,
          steps: [{ id: 'step-1', name: 'Cancel order request' }],
          validation: { valid: true, issues: [] },
        }),
        status: 'ACTIVE',
      },
    ])
    vi.mocked(platformApi.publishScenarioDraft).mockResolvedValue({
      scenario: createdScenario,
      suite: updatedSuite,
      created: true,
    })

    renderPanel({ scope: { type: 'SUITE', id: suiteOne.id }, suites: [suiteOne] })

    expect(await screen.findByText('Cancel order')).toBeInTheDocument()
    expect(screen.getByText('1 steps')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Create and add' }))
    const confirmationButtons = await screen.findAllByRole('button', { name: 'Create and add' })
    await user.click(confirmationButtons[confirmationButtons.length - 1])

    await waitFor(() => expect(platformApi.publishScenarioDraft).toHaveBeenCalledWith(
      'draft-1',
      { addToSuite: true, expectedSuiteRevision: 0 },
    ))
    expect(await screen.findByText('Created')).toBeInTheDocument()
  })
})

function renderPanel(overrides: Partial<ComponentProps<typeof AgentPanel>> = {}) {
  return render(panelTree(overrides))
}

function panelTree(overrides: Partial<ComponentProps<typeof AgentPanel>> = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return (
    <QueryClientProvider client={client}>
      <AntApp>
        <AgentPanel
          projectId="project-1"
          scope={{ type: 'SCENARIO', id: scenarioOne.id }}
          scenarios={[scenarioOne, scenarioTwo]}
          suites={[]}
          operations={operations}
          runs={[]}
          knowledge={[knowledge]}
          onClose={() => undefined}
          onOpenScenario={() => undefined}
          onOpenSuite={() => undefined}
          onOpenKnowledge={() => undefined}
          onApplyDraft={() => undefined}
          {...overrides}
        />
      </AntApp>
    </QueryClientProvider>
  )
}

function scenario(id: string, name: string, operationId: string): CaseRecord {
  return {
    id, projectId: 'project-1', name, code: id, description: '', tags: [], dslVersion: 'v1', status: 'ACTIVE', revision: 0,
    content: `dslVersion: v1\nname: ${name}\nsteps:\n  - name: request\n    type: http\n    operation:\n      id: ${operationId}\n`,
    createdAt: now, updatedAt: now,
  }
}

function suite(id: string, name: string): SuiteRecord {
  return {
    id, projectId: 'project-1', name, description: '', status: 'ACTIVE', revision: 0, cases: [], createdAt: now, updatedAt: now,
  }
}

function operation(id: string, path: string): ApiOperationRecord {
  return {
    id, projectId: 'project-1', apiSpecId: 'spec-1', method: 'POST', path,
    requestBody: {}, responses: {}, createdAt: now,
  }
}

function knowledgeRecord(id: string): TestKnowledgeRecord {
  return {
    id, projectId: 'project-1', name: 'Order protocol', description: '', scope: 'SCENARIO', targetId: scenarioOne.id,
    tags: [], content: 'Protocol', status: 'PUBLISHED', version: 1, createdAt: now, updatedAt: now,
  }
}

function task(
  id: string,
  status: AgentTaskRecord['status'],
  context: AgentTaskRecord['context'] = { included: [], excluded: [] },
  targetId = scenarioOne.id,
): AgentTaskRecord {
  return {
    id, projectId: 'project-1', title: 'Agent task', status, targetType: 'SCENARIO', targetId,
    permissionMode: 'REVIEW_REQUIRED', permissionModeRevision: 1,
    runtimeType: 'FAKE', modelId: 'fake-v1', createdBy: 'tester',
    iterationCount: 1, tokenBudget: 4096, context, revision: 0, createdAt: now, updatedAt: now,
  }
}

function event(id: string, sequence: number, eventType: string, payload: Record<string, unknown>): AgentEventRecord {
  return { id, taskId: tasks[0]?.id ?? 'task-1', sequence, eventType, payload, createdAt: now }
}

function artifacts(): AgentArtifactRecord[] {
  return [
    artifact('draft-artifact', 'SCENARIO_DRAFT', { name: 'Draft' }),
    artifact('plan-artifact', 'COMMAND_PLAN', {
      commands: [{ type: 'SetRequestHeader', stepId: 'step-1', name: 'X-Correlation-Id' }],
      citations: [{ knowledgeId: 'knowledge-1', scope: 'SCENARIO', targetId: scenarioOne.id }],
    }),
    artifact('validation-artifact', 'SCENARIO_VALIDATION', { validation: { valid: true, issues: [] } }),
    artifact('diff-artifact', 'SCENARIO_DIFF', { diff: { items: [{ type: 'ADD', path: 'steps[0].request.headers.X-Correlation-Id' }] } }),
  ]
}

function artifact(id: string, artifactType: string, content: Record<string, unknown>): AgentArtifactRecord {
  return {
    id, taskId: 'task-artifact', artifactType, targetType: 'SCENARIO_DRAFT', targetId: 'draft-1',
    baseRevision: 0, status: 'READY', content, createdAt: now, updatedAt: now,
  }
}

function draft(): ScenarioDraftRecord {
  return {
    id: 'draft-1', projectId: 'project-1', scenarioId: scenarioOne.id, sourceRevision: 0, revision: 1,
    content: scenarioOne.content, status: 'ACTIVE', steps: [], validation: { valid: true, issues: [] },
    diff: { items: [] }, createdAt: now, updatedAt: now,
  }
}
