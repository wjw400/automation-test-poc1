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
    deleteAgentTask: vi.fn(),
    renameAgentTask: vi.fn(),
    recordAgentDecision: vi.fn(),
    getScenarioDraft: vi.fn(),
  },
}))

const now = '2026-08-01T00:00:00Z'
const scenarioOne = scenario('scenario-1', 'Order lifecycle', 'operation-1')
const scenarioTwo = scenario('scenario-2', 'Refund lifecycle', 'operation-2')
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
  window.localStorage.clear()
  EventSourceStub.instances = []
  vi.stubGlobal('EventSource', EventSourceStub)
  tasks = []
  vi.mocked(platformApi.listAgentTasks).mockImplementation(async () => tasks)
  vi.mocked(platformApi.listAgentRuntimes).mockResolvedValue([{
    type: 'FAKE',
    capabilities: { available: true, streaming: true, cancellation: true, tools: true, defaultModel: 'fake-v1' },
  }])
  vi.mocked(platformApi.getAgentTask).mockImplementation(async (taskId) => tasks.find((task) => task.id === taskId)!)
  vi.mocked(platformApi.listAgentMessages).mockResolvedValue([])
  vi.mocked(platformApi.listAgentArtifacts).mockResolvedValue([])
  vi.mocked(platformApi.listAgentEvents).mockResolvedValue([])
  vi.mocked(platformApi.createAgentTask).mockImplementation(async (projectId, input) => {
    const created = task('task-created', 'READY', input.context, input.targetId)
    created.projectId = projectId
    tasks = [created]
    return created
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
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('AgentPanel', () => {
  it('supports pin, close, and editable Context before creating a chat', async () => {
    const user = userEvent.setup()
    const onPinnedChange = vi.fn()
    const onClose = vi.fn()
    renderPanel({ onPinnedChange, onClose })

    await user.click(await screen.findByLabelText('Keep panel open'))
    await user.click(screen.getByLabelText('Close Agent panel'))
    expect(onPinnedChange).toHaveBeenCalledWith(true)
    expect(onClose).toHaveBeenCalledOnce()

    await user.click(screen.getByLabelText('Edit chat context'))
    await user.click((await screen.findAllByLabelText('Remove from context'))[0])
    await user.click(screen.getByLabelText('Add workspace context'))
    await user.click(await screen.findByText(scenarioTwo.name))
    await user.type(screen.getByPlaceholderText('Describe the test change...'), 'Update the request')
    await user.click(screen.getByLabelText('Send message'))

    await waitFor(() => expect(platformApi.createAgentTask).toHaveBeenCalled())
    const input = vi.mocked(platformApi.createAgentTask).mock.calls[0][1]
    expect(input.context?.excluded).toContainEqual({ type: 'OPERATION', id: 'operation-1' })
    expect(input.context?.included).toContainEqual({ type: 'SCENARIO', id: scenarioTwo.id })
  })

  it('keeps Task Context fixed when the active Scenario changes', async () => {
    tasks = [task('task-1', 'COMPLETED', undefined, scenarioOne.id)]
    const view = renderPanel()
    expect(await screen.findByText(scenarioOne.name)).toBeInTheDocument()

    view.rerender(panelTree({ activeScenario: scenarioTwo }))
    expect(screen.getByText(scenarioOne.name)).toBeInTheDocument()
    expect(screen.queryByText(scenarioTwo.name)).not.toBeInTheDocument()
    expect(screen.getByLabelText('View chat context')).toBeInTheDocument()
  })

  it('restores and switches chats from workspace history', async () => {
    const user = userEvent.setup()
    tasks = [
      { ...task('task-1', 'COMPLETED', undefined, scenarioOne.id), title: 'Order chat' },
      { ...task('task-2', 'COMPLETED', undefined, scenarioTwo.id), title: 'Refund chat' },
    ]
    renderPanel()
    expect(await screen.findByText(scenarioOne.name)).toBeInTheDocument()

    await user.click(screen.getByLabelText('Chat history'))
    await user.click(await screen.findByText('Refund chat'))
    expect(await screen.findByText(scenarioTwo.name)).toBeInTheDocument()
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
    await user.click(await screen.findByRole('button', { name: 'Retry' }))
    expect(platformApi.sendAgentMessage).toHaveBeenCalledWith('task-failed', 'Try this change')
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
    await waitFor(() => expect(applyButton).toHaveTextContent('Applied'))
    expect(applyButton).toBeDisabled()
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
          activeScenario={scenarioOne}
          scenarios={[scenarioOne, scenarioTwo]}
          suites={[]}
          operations={operations}
          runs={[]}
          knowledge={[knowledge]}
          pinned={false}
          onPinnedChange={() => undefined}
          onClose={() => undefined}
          onOpenScenario={() => undefined}
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
    permissionMode: 'DRAFT_ONLY', runtimeType: 'FAKE', modelId: 'fake-v1', createdBy: 'tester',
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
