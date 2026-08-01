import {
  ApiOutlined,
  CheckCircleFilled,
  ClockCircleOutlined,
  CloseCircleFilled,
  CloseOutlined,
  CompressOutlined,
  DeleteOutlined,
  EditOutlined,
  ExperimentOutlined,
  ExpandOutlined,
  FileTextOutlined,
  HistoryOutlined,
  LikeOutlined,
  DislikeOutlined,
  LoadingOutlined,
  LockOutlined,
  MoreOutlined,
  PushpinFilled,
  PushpinOutlined,
  PlusOutlined,
  ProfileOutlined,
  ReadOutlined,
  RobotOutlined,
  SafetyCertificateOutlined,
  SendOutlined,
  StopOutlined,
  ToolOutlined,
  WarningFilled,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Dropdown, Empty, Input, Modal, Popover, Select, Spin, Tooltip, Typography } from 'antd'
import { useCallback, useEffect, useMemo, useRef, useState, type PointerEvent as ReactPointerEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import type { TFunction } from 'i18next'
import { platformApi } from '../api/platformApi'
import { formatDateTime } from '../i18n'
import type {
  AgentArtifactRecord,
  AgentContextReference,
  AgentContextSelection,
  AgentEventRecord,
  AgentTaskRecord,
  ApiOperationRecord,
  CaseRecord,
  RunRecord,
  ScenarioDraftDiffItem,
  ScenarioDraftRecord,
  ScenarioDraftValidation,
  SuiteRecord,
  TestKnowledgeRecord,
} from '../types/platform'
import { emptyAgentContextSelection, resolveAgentContext, type ResolvedAgentContext } from '../utils/agentContext'

const { Text } = Typography
const eventTypes = [
  'task.started',
  'task.updated',
  'context.snapshot.created',
  'skill.loaded',
  'tool.started',
  'tool.completed',
  'tool.failed',
  'message.delta',
  'message.completed',
  'artifact.created',
  'task.completed',
  'task.failed',
  'task.cancelled',
]

type AgentPanelProps = {
  projectId: string
  activeScenario?: CaseRecord
  scenarios: CaseRecord[]
  suites: SuiteRecord[]
  operations: ApiOperationRecord[]
  runs: RunRecord[]
  knowledge: TestKnowledgeRecord[]
  pinned: boolean
  onPinnedChange: (pinned: boolean) => void
  onClose: () => void
  onOpenScenario: (scenarioId: string) => void
  onOpenKnowledge: (knowledgeId: string) => void
  onApplyDraft: (draft: ScenarioDraftRecord) => Promise<void> | void
}

export function AgentPanel({
  projectId,
  activeScenario,
  scenarios,
  suites,
  operations,
  runs,
  knowledge,
  pinned,
  onPinnedChange,
  onClose,
  onOpenScenario,
  onOpenKnowledge,
  onApplyDraft,
}: AgentPanelProps) {
  const { t } = useTranslation('agent')
  const { message, modal } = App.useApp()
  const queryClient = useQueryClient()
  const [selectedTaskId, setSelectedTaskId] = useState<string>()
  const [newChatMode, setNewChatMode] = useState(false)
  const [composer, setComposer] = useState('')
  const [runtimeType, setRuntimeType] = useState<string>()
  const [contextSelection, setContextSelection] = useState<AgentContextSelection>(emptyAgentContextSelection)
  const [events, setEvents] = useState<AgentEventRecord[]>([])
  const [streamingText, setStreamingText] = useState('')
  const [streamConnected, setStreamConnected] = useState(true)
  const [historyOpen, setHistoryOpen] = useState(false)
  const [panelWidth, setPanelWidth] = useState(420)
  const [expanded, setExpanded] = useState(false)
  const [renameOpen, setRenameOpen] = useState(false)
  const [renameTitle, setRenameTitle] = useState('')
  const [feedback, setFeedback] = useState<'positive' | 'negative'>()
  const messageEndRef = useRef<HTMLDivElement>(null)

  useEffect(() => setFeedback(undefined), [selectedTaskId])

  const tasksQuery = useQuery({
    queryKey: ['agent-tasks', projectId],
    queryFn: () => platformApi.listAgentTasks(projectId),
    refetchInterval: 10000,
  })
  const runtimesQuery = useQuery({
    queryKey: ['agent-runtimes'],
    queryFn: platformApi.listAgentRuntimes,
  })
  const taskQuery = useQuery({
    queryKey: ['agent-task', selectedTaskId],
    queryFn: () => platformApi.getAgentTask(selectedTaskId!),
    enabled: Boolean(selectedTaskId),
    refetchInterval: (query) => query.state.data?.status === 'RUNNING' ? 1500 : false,
  })
  const messagesQuery = useQuery({
    queryKey: ['agent-messages', selectedTaskId],
    queryFn: () => platformApi.listAgentMessages(selectedTaskId!),
    enabled: Boolean(selectedTaskId),
  })
  const artifactsQuery = useQuery({
    queryKey: ['agent-artifacts', selectedTaskId],
    queryFn: () => platformApi.listAgentArtifacts(selectedTaskId!),
    enabled: Boolean(selectedTaskId),
  })

  const selectedTask = taskQuery.data
    ?? tasksQuery.data?.find((task) => task.id === selectedTaskId)
  const taskScenario = scenarios.find((record) => record.id === selectedTask?.targetId)
  const contextScenario = selectedTask?.targetType === 'SCENARIO' ? taskScenario : activeScenario
  const effectiveContextSelection = selectedTask?.context ?? contextSelection
  const isRunning = selectedTask?.status === 'RUNNING'
  const resolvedContext = useMemo(() => resolveAgentContext({
    targetScenario: contextScenario,
    selection: effectiveContextSelection,
    scenarios,
    suites,
    operations,
    runs,
    knowledge,
  }), [contextScenario, effectiveContextSelection, knowledge, operations, runs, scenarios, suites])

  useEffect(() => {
    const available = runtimesQuery.data?.filter((runtime) => runtime.capabilities.available) ?? []
    if (runtimeType && available.some((runtime) => runtime.type === runtimeType)) return
    setRuntimeType(
      available.find((runtime) => runtime.type === 'DEEPSEEK')?.type
      ?? available.find((runtime) => runtime.type === 'FAKE')?.type
      ?? available[0]?.type,
    )
  }, [runtimeType, runtimesQuery.data])

  useEffect(() => {
    setNewChatMode(false)
    setSelectedTaskId(undefined)
    setComposer('')
    setContextSelection(emptyAgentContextSelection())
  }, [projectId])

  useEffect(() => {
    if (!selectedTaskId) setContextSelection(emptyAgentContextSelection())
  }, [activeScenario?.id, selectedTaskId])

  useEffect(() => {
    if (newChatMode || !tasksQuery.data?.length) return
    if (selectedTaskId && tasksQuery.data.some((task) => task.id === selectedTaskId)) return
    const stored = window.localStorage.getItem(`agent-task:${projectId}`)
    const next = tasksQuery.data.find((task) => task.targetId === activeScenario?.id)
      ?? tasksQuery.data.find((task) => task.id === stored)
      ?? tasksQuery.data[0]
    setSelectedTaskId(next?.id)
  }, [activeScenario?.id, newChatMode, projectId, selectedTaskId, tasksQuery.data])

  useEffect(() => {
    if (selectedTaskId) window.localStorage.setItem(`agent-task:${projectId}`, selectedTaskId)
  }, [projectId, selectedTaskId])

  useEffect(() => {
    setEvents([])
    setStreamingText('')
    setStreamConnected(true)
    if (!selectedTaskId) return

    let disposed = false
    let streamFinished = false
    let source: EventSource | undefined
    const seen = new Set<number>()
    let lastSequence = 0

    const ingest = (event: AgentEventRecord) => {
      if (disposed || seen.has(event.sequence)) return
      seen.add(event.sequence)
      lastSequence = Math.max(lastSequence, event.sequence)
      setEvents((current) => [...current, event].sort((left, right) => left.sequence - right.sequence))
      if (event.eventType === 'message.delta') {
        const delta = event.payload.delta
        if (typeof delta === 'string') setStreamingText((current) => current + delta)
      }
      if (event.eventType === 'message.completed') {
        setStreamingText('')
        void queryClient.invalidateQueries({ queryKey: ['agent-messages', selectedTaskId] })
      }
      if (event.eventType === 'artifact.created') {
        void queryClient.invalidateQueries({ queryKey: ['agent-artifacts', selectedTaskId] })
      }
      if (event.eventType.startsWith('task.')) {
        void queryClient.invalidateQueries({ queryKey: ['agent-task', selectedTaskId] })
        void queryClient.invalidateQueries({ queryKey: ['agent-tasks', projectId] })
      }
    }

    const connect = async () => {
      try {
        const history = await platformApi.listAgentEvents(selectedTaskId, 0)
        history.forEach(ingest)
        if (disposed) return
        if (selectedTask?.status !== 'RUNNING') return
        source = new EventSource(platformApi.getAgentEventStreamUrl(selectedTaskId, lastSequence))
        eventTypes.forEach((type) => {
          source?.addEventListener(type, (rawEvent) => {
            try {
              const event = JSON.parse((rawEvent as MessageEvent<string>).data) as AgentEventRecord
              ingest(event)
              setStreamConnected(true)
              if (isTerminalAgentEvent(event.eventType)) {
                streamFinished = true
                source?.close()
              }
            } catch {
              // Ignore malformed provider events; persisted events remain the source of truth.
            }
          })
        })
        source.onopen = () => setStreamConnected(true)
        source.onerror = () => {
          if (!streamFinished) setStreamConnected(false)
        }
      } catch {
        if (!disposed) setStreamConnected(false)
      }
    }
    void connect()
    return () => {
      disposed = true
      source?.close()
    }
  }, [projectId, queryClient, selectedTask?.status, selectedTaskId])

  useEffect(() => {
    messageEndRef.current?.scrollIntoView({ block: 'end' })
  }, [artifactsQuery.data, messagesQuery.data, streamingText])

  const createTaskMutation = useMutation({
    mutationFn: async () => {
      if (!activeScenario || !runtimeType) throw new Error(t('noScenario'))
      return platformApi.createAgentTask(projectId, {
        title: activeScenario.name,
        targetType: 'SCENARIO',
        targetId: activeScenario.id,
        runtimeType,
        context: contextSelection,
      })
    },
    onSuccess: async (task) => {
      setNewChatMode(false)
      setSelectedTaskId(task.id)
      await queryClient.invalidateQueries({ queryKey: ['agent-tasks', projectId] })
    },
  })

  const sendMutation = useMutation({
    mutationFn: async (content: string) => {
      let taskId = selectedTaskId
      if (!taskId) taskId = (await createTaskMutation.mutateAsync()).id
      const task = await platformApi.sendAgentMessage(taskId, content)
      return { taskId, task }
    },
    onSuccess: async ({ taskId, task }) => {
      setComposer('')
      queryClient.setQueryData(['agent-task', taskId], task)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['agent-task', taskId] }),
        queryClient.invalidateQueries({ queryKey: ['agent-messages', taskId] }),
        queryClient.invalidateQueries({ queryKey: ['agent-tasks', projectId] }),
      ])
    },
    onError: (error) => {
      void message.error(error instanceof Error ? error.message : t('sendFailed'))
    },
  })

  const cancelMutation = useMutation({
    mutationFn: () => platformApi.cancelAgentTask(selectedTaskId!),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['agent-task', selectedTaskId] }),
        queryClient.invalidateQueries({ queryKey: ['agent-tasks', projectId] }),
      ])
    },
  })

  const deleteMutation = useMutation({
    mutationFn: platformApi.deleteAgentTask,
    onSuccess: async (_result, taskId) => {
      if (selectedTaskId === taskId) {
        setSelectedTaskId(undefined)
        setNewChatMode(true)
      }
      await queryClient.invalidateQueries({ queryKey: ['agent-tasks', projectId] })
      void message.success(t('sessionDeleted'))
    },
  })

  const renameMutation = useMutation({
    mutationFn: () => platformApi.renameAgentTask(selectedTaskId!, renameTitle.trim()),
    onSuccess: async () => {
      setRenameOpen(false)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['agent-task', selectedTaskId] }),
        queryClient.invalidateQueries({ queryKey: ['agent-tasks', projectId] }),
      ])
    },
  })

  const feedbackMutation = useMutation({
    mutationFn: (value: 'positive' | 'negative') => platformApi.recordAgentDecision(
      selectedTaskId!,
      value === 'positive' ? 'FEEDBACK_POSITIVE' : 'FEEDBACK_NEGATIVE',
    ),
    onSuccess: (_result, value) => setFeedback(value),
    onError: () => void message.error(t('feedbackFailed')),
  })

  const applyDraft = useCallback(async (draftId: string) => {
    try {
      const draft = await platformApi.getScenarioDraft(draftId)
      if (!draft.validation.valid) throw new Error(t('invalid'))
      await onApplyDraft(draft)
      if (selectedTaskId) {
        await platformApi.recordAgentDecision(selectedTaskId, 'DRAFT_APPLIED_TO_EDITOR', { draftId })
      }
      void message.success(t('applied'))
      return true
    } catch (error) {
      void message.error(error instanceof Error ? error.message : t('applyFailed'))
      return false
    }
  }, [message, onApplyDraft, selectedTaskId, t])

  const submit = (content = composer) => {
    const value = content.trim()
    if (!value || isRunning || sendMutation.isPending) return
    void sendMutation.mutateAsync(value)
  }

  const retry = () => {
    const lastUserMessage = [...(messagesQuery.data ?? [])].reverse().find((item) => item.role === 'USER')
    const text = lastUserMessage?.content.text
    if (typeof text === 'string') submit(text)
  }

  const startResize = (event: ReactPointerEvent<HTMLDivElement>) => {
    event.preventDefault()
    const startX = event.clientX
    const startWidth = panelWidth
    const move = (pointerEvent: PointerEvent) => {
      const maximum = Math.min(760, window.innerWidth - 340)
      setPanelWidth(Math.max(340, Math.min(maximum, startWidth + startX - pointerEvent.clientX)))
      setExpanded(false)
    }
    const stop = () => {
      window.removeEventListener('pointermove', move)
      window.removeEventListener('pointerup', stop)
    }
    window.addEventListener('pointermove', move)
    window.addEventListener('pointerup', stop)
  }

  const toggleExpanded = () => {
    if (expanded) {
      setPanelWidth(420)
      setExpanded(false)
    } else {
      setPanelWidth(Math.min(720, Math.max(420, window.innerWidth - 420)))
      setExpanded(true)
    }
  }

  const activityEvents = events.filter((event) => (
    event.eventType === 'context.snapshot.created'
    || event.eventType === 'skill.loaded'
    || event.eventType.startsWith('tool.')
  ))
  const failedEvent = [...events].reverse().find((event) => event.eventType === 'task.failed')

  return (
    <aside className="agent-panel" style={{ width: panelWidth }} aria-label={t('title')}>
      <div className="agent-panel-resize" role="separator" aria-orientation="vertical" onPointerDown={startResize} />
      <header className="agent-panel-header">
        <div className="agent-panel-title">
          <RobotOutlined />
          <span>{t('title')}</span>
          <Tooltip title={t('safetyDescription')}>
            <span className="agent-safety-mode"><SafetyCertificateOutlined />{t('draftOnly')}</span>
          </Tooltip>
        </div>
        <div className="agent-panel-actions">
          <Tooltip title={t('newChat')}>
            <Button
              aria-label={t('newChat')}
              icon={<FileTextOutlined />}
              size="small"
              type="text"
              disabled={!activeScenario}
              onClick={() => {
                setNewChatMode(true)
                setSelectedTaskId(undefined)
                setComposer('')
                setContextSelection(emptyAgentContextSelection())
              }}
            />
          </Tooltip>
          <Popover
            arrow={false}
            open={historyOpen}
            placement="bottomRight"
            trigger="click"
            onOpenChange={setHistoryOpen}
            content={(
              <AgentHistory
                tasks={tasksQuery.data ?? []}
                selectedTaskId={selectedTaskId}
                onSelect={(taskId) => {
                  setNewChatMode(false)
                  setSelectedTaskId(taskId)
                  setHistoryOpen(false)
                }}
              />
            )}
          >
            <Tooltip title={t('history')}>
              <Button aria-label={t('history')} icon={<HistoryOutlined />} size="small" type="text" />
            </Tooltip>
          </Popover>
          <Tooltip title={pinned ? t('unpin') : t('pin')}>
            <Button
              aria-label={pinned ? t('unpin') : t('pin')}
              icon={pinned ? <PushpinFilled /> : <PushpinOutlined />}
              size="small"
              type="text"
              onClick={() => onPinnedChange(!pinned)}
            />
          </Tooltip>
          <Tooltip title={expanded ? t('collapse') : t('expand')}>
            <Button
              aria-label={expanded ? t('collapse') : t('expand')}
              icon={expanded ? <CompressOutlined /> : <ExpandOutlined />}
              size="small"
              type="text"
              onClick={toggleExpanded}
            />
          </Tooltip>
          <Tooltip title={t('close')}>
            <Button aria-label={t('close')} icon={<CloseOutlined />} size="small" type="text" onClick={onClose} />
          </Tooltip>
        </div>
      </header>

      <div className="agent-context-bar">
        <ExperimentOutlined />
        <div>
          <span>{contextScenario?.name ?? t('noScenario')}</span>
          <small>{t('contextSummary', {
            operations: resolvedContext.automaticOperations.length,
            knowledge: resolvedContext.visibleKnowledge.length,
            added: effectiveContextSelection.included.length,
          })}</small>
        </div>
        {contextScenario ? (
          <Popover
            arrow={false}
            placement="bottomRight"
            trigger="click"
            content={(
              <AgentContextEditor
                locked={Boolean(selectedTask)}
                targetScenario={contextScenario}
                selection={effectiveContextSelection}
                resolved={resolvedContext}
                scenarios={scenarios}
                suites={suites}
                operations={operations}
                runs={runs}
                onChange={setContextSelection}
              />
            )}
          >
            <Button
              aria-label={selectedTask ? t('viewContext') : t('editContext')}
              icon={selectedTask ? <LockOutlined /> : <PlusOutlined />}
              size="small"
              type="text"
            />
          </Popover>
        ) : null}
        {selectedTask ? (
          <Dropdown
            trigger={['click']}
            menu={{
              items: [
                { key: 'open', label: t('openScenario'), icon: <ExperimentOutlined />, disabled: !selectedTask.targetId },
                { key: 'rename', label: t('renameSession'), icon: <EditOutlined /> },
                { type: 'divider' },
                { key: 'delete', label: t('deleteSession'), icon: <DeleteOutlined />, danger: true, disabled: isRunning },
              ],
              onClick: ({ key }) => {
                if (key === 'open' && selectedTask.targetId) onOpenScenario(selectedTask.targetId)
                if (key === 'rename') {
                  setRenameTitle(selectedTask.title)
                  setRenameOpen(true)
                }
                if (key === 'delete') {
                  modal.confirm({
                    title: t('deleteConfirm'),
                    okText: t('deleteSession'),
                    okButtonProps: { danger: true },
                    onOk: () => deleteMutation.mutateAsync(selectedTask.id),
                  })
                }
              },
            }}
          >
            <Button aria-label={t('deleteSession')} icon={<MoreOutlined />} size="small" type="text" />
          </Dropdown>
        ) : null}
      </div>

      {!selectedTask ? (
        <div className="agent-runtime-row">
          <Text type="secondary">{t('runtime')}</Text>
          <Select
            size="small"
            value={runtimeType}
            loading={runtimesQuery.isLoading}
            options={(runtimesQuery.data ?? []).map((runtime) => ({
              value: runtime.type,
              disabled: !runtime.capabilities.available,
              label: runtime.capabilities.available
                ? `${runtime.type} / ${runtime.capabilities.defaultModel}`
                : `${runtime.type} / ${t('runtimeUnavailable')}`,
            }))}
            onChange={setRuntimeType}
          />
        </div>
      ) : null}

      <div className="agent-conversation">
        {tasksQuery.isLoading && !selectedTask ? <Spin size="small" /> : null}
        {!contextScenario && !selectedTask ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={(
            <div className="agent-empty-copy">
              <strong>{t('noScenario')}</strong>
              <span>{t('noScenarioDetail')}</span>
            </div>
          )} />
        ) : null}
        {contextScenario && !selectedTask && !messagesQuery.isLoading ? (
          <div className="agent-empty-state">
            <RobotOutlined />
            <strong>{t('emptyTitle')}</strong>
            <span>{t('emptyDescription')}</span>
          </div>
        ) : null}

        {(messagesQuery.data ?? []).map((item) => (
          <article key={item.id} className={`agent-message ${item.role.toLowerCase()}`}>
            <div className="agent-message-role">{item.role === 'USER' ? 'You' : t('title')}</div>
            <div className="agent-message-content">{messageText(item.content)}</div>
          </article>
        ))}
        {streamingText ? (
          <article className="agent-message assistant streaming">
            <div className="agent-message-role">{t('title')}</div>
            <div className="agent-message-content">{streamingText}<span className="agent-stream-caret" /></div>
          </article>
        ) : null}

        {activityEvents.length ? <AgentActivity events={activityEvents} /> : null}
        {artifactsQuery.data?.length ? (
          <AgentArtifacts artifacts={artifactsQuery.data} onApply={applyDraft} onOpenKnowledge={onOpenKnowledge} />
        ) : null}

        {selectedTask?.status === 'COMPLETED' ? (
          <div className="agent-feedback">
            <span>{feedback ? t('feedbackRecorded') : t('feedbackPrompt')}</span>
            <Tooltip title={t('feedbackPositive')}>
              <Button
                aria-label={t('feedbackPositive')}
                type={feedback === 'positive' ? 'primary' : 'text'}
                icon={<LikeOutlined />}
                size="small"
                disabled={Boolean(feedback)}
                loading={feedbackMutation.isPending && !feedback}
                onClick={() => feedbackMutation.mutate('positive')}
              />
            </Tooltip>
            <Tooltip title={t('feedbackNegative')}>
              <Button
                aria-label={t('feedbackNegative')}
                type={feedback === 'negative' ? 'primary' : 'text'}
                icon={<DislikeOutlined />}
                size="small"
                disabled={Boolean(feedback)}
                loading={feedbackMutation.isPending && !feedback}
                onClick={() => feedbackMutation.mutate('negative')}
              />
            </Tooltip>
          </div>
        ) : null}

        {failedEvent ? (
          <div className="agent-error-row">
            <CloseCircleFilled />
            <div>
              <strong>{t('failed')}</strong>
              <span>{agentFailureMessage(failedEvent, t)}</span>
            </div>
            {failedEvent.payload.retryable === true ? <Button size="small" onClick={retry}>{t('retry')}</Button> : null}
          </div>
        ) : null}
        {!streamConnected && selectedTaskId ? <div className="agent-stream-state">{t('connectionLost')}</div> : null}
        <div ref={messageEndRef} />
      </div>

      <footer className="agent-composer">
        <Input.TextArea
          autoSize={{ minRows: 2, maxRows: 6 }}
          value={composer}
          placeholder={t('composerPlaceholder')}
          disabled={(!selectedTask && !activeScenario) || isRunning}
          onChange={(event) => setComposer(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && !event.shiftKey) {
              event.preventDefault()
              submit()
            }
          }}
        />
        <div className="agent-composer-footer">
          <TaskStatus task={selectedTask} />
          {isRunning ? (
            <Tooltip title={t('stop')}>
              <Button
                aria-label={t('stop')}
                danger
                icon={<StopOutlined />}
                loading={cancelMutation.isPending}
                shape="circle"
                onClick={() => void cancelMutation.mutateAsync()}
              />
            </Tooltip>
          ) : (
            <Tooltip title={t('send')}>
              <Button
                aria-label={t('send')}
                type="primary"
                icon={<SendOutlined />}
                loading={sendMutation.isPending || createTaskMutation.isPending}
                shape="circle"
                disabled={!composer.trim() || (!selectedTask && (!activeScenario || !runtimeType))}
                onClick={() => submit()}
              />
            </Tooltip>
          )}
        </div>
      </footer>
      <Modal
        title={t('renameTitle')}
        open={renameOpen}
        okText={t('renameSession')}
        confirmLoading={renameMutation.isPending}
        okButtonProps={{ disabled: !renameTitle.trim() }}
        onCancel={() => setRenameOpen(false)}
        onOk={() => void renameMutation.mutateAsync()}
      >
        <Input
          autoFocus
          value={renameTitle}
          placeholder={t('titlePlaceholder')}
          onChange={(event) => setRenameTitle(event.target.value)}
          onPressEnter={() => {
            if (renameTitle.trim()) void renameMutation.mutateAsync()
          }}
        />
      </Modal>
    </aside>
  )
}

function AgentContextEditor({
  locked,
  targetScenario,
  selection,
  resolved,
  scenarios,
  suites,
  operations,
  runs,
  onChange,
}: {
  locked: boolean
  targetScenario: CaseRecord
  selection: AgentContextSelection
  resolved: ResolvedAgentContext
  scenarios: CaseRecord[]
  suites: SuiteRecord[]
  operations: ApiOperationRecord[]
  runs: RunRecord[]
  onChange: (selection: AgentContextSelection) => void
}) {
  const { t } = useTranslation('agent')
  const excludedKeys = new Set(selection.excluded.map(contextKey))
  const includedKeys = new Set(selection.included.map(contextKey))
  const automaticOperationKeys = new Set(
    resolved.availableAutomaticOperations.map((item) => `OPERATION:${item.id}`),
  )
  const options = [
    {
      label: t('contextScenarios'),
      options: scenarios
        .filter((item) => item.id !== targetScenario.id && !includedKeys.has(`SCENARIO:${item.id}`))
        .map((item) => ({ value: `SCENARIO:${item.id}`, label: item.name })),
    },
    {
      label: t('contextSuites'),
      options: suites
        .filter((item) => !includedKeys.has(`SUITE:${item.id}`))
        .map((item) => ({ value: `SUITE:${item.id}`, label: item.name })),
    },
    {
      label: t('contextOperations'),
      options: operations
        .filter((item) => !includedKeys.has(`OPERATION:${item.id}`) && !automaticOperationKeys.has(`OPERATION:${item.id}`))
        .map((item) => ({
          value: `OPERATION:${item.id}`,
          label: `${item.method} ${item.path}`,
        })),
    },
    {
      label: t('contextExecutions'),
      options: runs
        .filter((item) => !includedKeys.has(`EXECUTION:${item.id}`))
        .map((item) => ({
          value: `EXECUTION:${item.id}`,
          label: `${item.status} · ${formatDateTime(item.startedAt)}`,
        })),
    },
  ].filter((group) => group.options.length)

  const setExcluded = (references: AgentContextReference[], excluded: boolean) => {
    const keys = new Set(references.map(contextKey))
    const retained = selection.excluded.filter((item) => !keys.has(contextKey(item)))
    onChange({
      included: selection.included,
      excluded: excluded ? [...retained, ...references] : retained,
    })
  }

  return (
    <div className="agent-context-popover">
      <div className="agent-context-popover-head">
        <strong>{t('context')}</strong>
        <small>{locked ? t('contextLocked') : t('contextEditable')}</small>
      </div>
      <div className="agent-context-list">
        <ContextRow
          icon={<ExperimentOutlined />}
          title={targetScenario.name}
          detail={t('targetScenario')}
          action={<LockOutlined />}
        />
        {resolved.availableAutomaticOperations.map((operation) => {
          const reference: AgentContextReference = { type: 'OPERATION', id: operation.id }
          const excluded = excludedKeys.has(contextKey(reference))
          return (
            <ContextRow
              key={operation.id}
              icon={<ApiOutlined />}
              title={`${operation.method} ${operation.path}`}
              detail={excluded ? t('contextExcluded') : t('linkedOperation')}
              muted={excluded}
              action={!locked ? (
                <Button
                  aria-label={excluded ? t('restoreContext') : t('removeContext')}
                  icon={excluded ? <PlusOutlined /> : <CloseOutlined />}
                  size="small"
                  type="text"
                  onClick={() => setExcluded([reference], !excluded)}
                />
              ) : undefined}
            />
          )
        })}
        {resolved.matchingKnowledge.length ? (
          <ContextRow
            icon={<ReadOutlined />}
            title={t('knowledgeCount', { count: resolved.visibleKnowledge.length })}
            detail={resolved.visibleKnowledge.length ? t('matchedKnowledge') : t('contextExcluded')}
            muted={!resolved.visibleKnowledge.length}
            action={!locked ? (
              <Button
                aria-label={resolved.visibleKnowledge.length ? t('removeContext') : t('restoreContext')}
                icon={resolved.visibleKnowledge.length ? <CloseOutlined /> : <PlusOutlined />}
                size="small"
                type="text"
                onClick={() => setExcluded(
                  resolved.matchingKnowledge.map((item) => ({ type: 'TEST_KNOWLEDGE', id: item.id })),
                  Boolean(resolved.visibleKnowledge.length),
                )}
              />
            ) : undefined}
          />
        ) : null}
        {selection.included.map((reference) => (
          <ContextRow
            key={contextKey(reference)}
            icon={contextIcon(reference.type)}
            title={contextReferenceLabel(reference, scenarios, suites, operations, runs)}
            detail={t('addedContext')}
            action={!locked ? (
              <Button
                aria-label={t('removeContext')}
                icon={<CloseOutlined />}
                size="small"
                type="text"
                onClick={() => onChange({
                  included: selection.included.filter((item) => contextKey(item) !== contextKey(reference)),
                  excluded: selection.excluded,
                })}
              />
            ) : undefined}
          />
        ))}
      </div>
      {!locked && options.length ? (
        <Select
          className="agent-context-add"
          aria-label={t('addContext')}
          placeholder={t('addContext')}
          showSearch
          value={undefined}
          options={options}
          optionFilterProp="label"
          onChange={(value: string) => {
            const separator = value.indexOf(':')
            const reference = {
              type: value.slice(0, separator),
              id: value.slice(separator + 1),
            } as AgentContextReference
            onChange({
              included: [...selection.included, reference],
              excluded: selection.excluded.filter((item) => contextKey(item) !== contextKey(reference)),
            })
          }}
        />
      ) : null}
    </div>
  )
}

function ContextRow({
  icon,
  title,
  detail,
  action,
  muted = false,
}: {
  icon: ReactNode
  title: string
  detail: string
  action?: ReactNode
  muted?: boolean
}) {
  return (
    <div className={muted ? 'agent-context-row muted' : 'agent-context-row'}>
      {icon}
      <div><span>{title}</span><small>{detail}</small></div>
      <span className="agent-context-row-action">{action}</span>
    </div>
  )
}

function AgentHistory({
  tasks,
  selectedTaskId,
  onSelect,
}: {
  tasks: AgentTaskRecord[]
  selectedTaskId?: string
  onSelect: (taskId: string) => void
}) {
  const { t } = useTranslation('agent')
  return (
    <div className="agent-history">
      <strong>{t('history')}</strong>
      <div className="agent-history-list">
        {tasks.map((task) => (
          <button
            key={task.id}
            type="button"
            className={task.id === selectedTaskId ? 'agent-history-row active' : 'agent-history-row'}
            onClick={() => onSelect(task.id)}
          >
            <span>{task.title}</span>
            <small>{t(taskStatusKey(task.status))} · {formatDateTime(task.updatedAt)}</small>
            <i className={`agent-status-dot ${task.status.toLowerCase()}`} />
          </button>
        ))}
        {!tasks.length ? <Text type="secondary">{t('newChat')}</Text> : null}
      </div>
    </div>
  )
}

function AgentActivity({ events }: { events: AgentEventRecord[] }) {
  const { t } = useTranslation('agent')
  return (
    <details className="agent-activity">
      <summary><ToolOutlined /><span>{t('activity')}</span><small>{events.length}</small></summary>
      <div className="agent-activity-list">
        {events.map((event) => (
          <div key={event.id} className="agent-activity-row">
            {activityIcon(event.eventType)}
            <span>{activityLabel(event)}</span>
          </div>
        ))}
      </div>
    </details>
  )
}

function AgentArtifacts({
  artifacts,
  onApply,
  onOpenKnowledge,
}: {
  artifacts: AgentArtifactRecord[]
  onApply: (draftId: string) => Promise<boolean>
  onOpenKnowledge: (knowledgeId: string) => void
}) {
  const { t } = useTranslation('agent')
  const [applyingId, setApplyingId] = useState<string>()
  const [appliedDraftIds, setAppliedDraftIds] = useState<Set<string>>(() => new Set())
  const drafts = artifacts.filter((artifact) => artifact.artifactType === 'SCENARIO_DRAFT' && artifact.targetId)

  if (!drafts.length) return null
  return (
    <section className="agent-artifacts">
      <div className="agent-artifacts-title"><FileTextOutlined /><strong>{t('artifacts')}</strong></div>
      {drafts.map((draft, index) => {
        const validationArtifact = findLatestArtifact(artifacts, 'SCENARIO_VALIDATION', draft.targetId!)
        const diffArtifact = findLatestArtifact(artifacts, 'SCENARIO_DIFF', draft.targetId!)
        const planArtifact = findLatestArtifact(artifacts, 'COMMAND_PLAN', draft.targetId!)
        const validation = nestedObject(validationArtifact?.content, 'validation') as ScenarioDraftValidation | undefined
        const diff = nestedObject(diffArtifact?.content, 'diff')
        const changes = Array.isArray(diff?.items) ? diff.items as ScenarioDraftDiffItem[] : []
        const commands = Array.isArray(planArtifact?.content.commands)
          ? planArtifact.content.commands as Array<Record<string, unknown>>
          : []
        const citations = Array.isArray(planArtifact?.content.citations) ? planArtifact.content.citations : []
        const canApply = validation?.valid === true
        const applied = appliedDraftIds.has(draft.targetId!)
        return (
          <div key={draft.id} className="agent-artifact">
            <div className="agent-artifact-head">
              <div>
                <strong>{t('draft')} {drafts.length > 1 ? index + 1 : ''}</strong>
                <span className={canApply ? 'valid' : 'invalid'}>
                  {canApply ? <CheckCircleFilled /> : <WarningFilled />}
                  {canApply ? t('valid') : t('invalid')}
                </span>
              </div>
              <Button
                size="small"
                type="primary"
                disabled={!canApply || applied}
                loading={applyingId === draft.targetId}
                onClick={async () => {
                  setApplyingId(draft.targetId!)
                  try {
                    if (await onApply(draft.targetId!)) {
                      setAppliedDraftIds((current) => new Set(current).add(draft.targetId!))
                    }
                  } finally {
                    setApplyingId(undefined)
                  }
                }}
              >
                {applied ? t('appliedShort') : t('applyToEditor')}
              </Button>
            </div>
            {commands.length ? (
              <details open className="agent-artifact-detail agent-plan">
                <summary>{t('plan')} ({commands.length})</summary>
                <ol>
                  {commands.map((command, commandIndex) => (
                    <li key={`${String(command.type)}-${commandIndex}`}>
                      <span>{commandSummary(command)}</span>
                    </li>
                  ))}
                </ol>
              </details>
            ) : null}
            <details open className="agent-artifact-detail">
              <summary>{t('changeCount', { count: changes.length })}</summary>
              {changes.length ? (
                <div className="agent-diff-list">
                  {changes.map((change, changeIndex) => (
                    <div key={`${change.path}-${changeIndex}`} className="agent-diff-row">
                      <span className={`agent-diff-kind ${change.type.toLowerCase()}`}>{change.type}</span>
                      <code>{change.path}</code>
                    </div>
                  ))}
                </div>
              ) : <Text type="secondary">{t('noChanges')}</Text>}
            </details>
            {validation?.issues?.length ? (
              <details className="agent-artifact-detail">
                <summary>{t('validation')} ({validation.issues.length})</summary>
                {validation.issues.map((issue, issueIndex) => (
                  <div key={`${issue.code}-${issueIndex}`} className="agent-validation-row">
                    <span>{issue.severity}</span>
                    <div><code>{issue.path}</code><small>{issue.message}</small></div>
                  </div>
                ))}
              </details>
            ) : null}
            {citations.length ? (
              <details className="agent-artifact-detail">
                <summary>{t('citations')} ({citations.length})</summary>
                <div className="agent-citation-list">
                  {citations.map((citation, citationIndex) => {
                    const knowledgeId = citationKnowledgeId(citation)
                    return knowledgeId ? (
                      <button key={citationIndex} type="button" onClick={() => onOpenKnowledge(knowledgeId)}>
                        <ReadOutlined />
                        <span>{citationLabel(citation)}</span>
                      </button>
                    ) : <code key={citationIndex}>{citationLabel(citation)}</code>
                  })}
                </div>
              </details>
            ) : null}
          </div>
        )
      })}
    </section>
  )
}

function TaskStatus({ task }: { task?: AgentTaskRecord }) {
  const { t } = useTranslation('agent')
  if (!task) return <Text type="secondary">{t('ready')}</Text>
  const label = t(taskStatusKey(task.status))
  return <span className={`agent-task-status ${task.status.toLowerCase()}`}>{task.status === 'RUNNING' ? <LoadingOutlined spin /> : null}{label}</span>
}

function messageText(content: Record<string, unknown>) {
  const value = content.text
  return typeof value === 'string' ? value : JSON.stringify(content, null, 2)
}

function agentFailureMessage(event: AgentEventRecord, t: TFunction<'agent'>) {
  const code = String(event.payload.code ?? '')
  if (code === 'MODEL_RATE_LIMITED') return t('failureRateLimited')
  if (code === 'MODEL_TIMEOUT' || code === 'TURN_TIMEOUT') return t('failureTimeout')
  if (code === 'MODEL_AUTHENTICATION_FAILED' || code === 'MODEL_ACCESS_DENIED') return t('failureAuthentication')
  if (code === 'MODEL_BALANCE_EXHAUSTED') return t('failureBalance')
  if (code === 'MODEL_UNAVAILABLE' || code === 'SERVICE_RESTARTED' || code === 'SERVICE_SHUTDOWN') return t('failureUnavailable')
  if (code === 'TOKEN_BUDGET_EXCEEDED' || code === 'TURN_BUDGET_EXCEEDED' || code === 'TOOL_CALL_BUDGET_EXCEEDED') {
    return t('failureBudget')
  }
  if (code === 'REPEATED_TOOL_CALL' || code === 'NO_PROGRESS') return t('failureNoProgress')
  return String(event.payload.message ?? code ?? t('sendFailed'))
}

function activityIcon(type: string) {
  if (type.endsWith('.completed')) return <CheckCircleFilled className="success" />
  if (type.endsWith('.failed')) return <CloseCircleFilled className="failed" />
  if (type.endsWith('.started')) return <LoadingOutlined spin />
  return <FileTextOutlined />
}

function activityLabel(event: AgentEventRecord) {
  if (event.eventType === 'skill.loaded') return `Skill: ${String(event.payload.skillName ?? '')}`
  if (event.eventType.startsWith('tool.')) return `${String(event.payload.toolName ?? 'Tool')} / ${event.eventType.split('.')[1]}`
  if (event.eventType === 'context.snapshot.created') return `Context snapshot / ${String(event.payload.sourceCount ?? 0)} sources`
  return event.eventType
}

function findLatestArtifact(artifacts: AgentArtifactRecord[], type: string, targetId: string) {
  return [...artifacts].reverse().find((artifact) => artifact.artifactType === type && artifact.targetId === targetId)
}

function nestedObject(content: Record<string, unknown> | undefined, key: string) {
  const value = content?.[key]
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : undefined
}

function citationLabel(value: unknown) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return String(value)
  const citation = value as Record<string, unknown>
  return [citation.scope, citation.targetId, citation.knowledgeId].filter(Boolean).join(' / ')
}

function citationKnowledgeId(value: unknown) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return undefined
  const id = (value as Record<string, unknown>).knowledgeId
  return typeof id === 'string' && id ? id : undefined
}

function commandSummary(command: Record<string, unknown>) {
  const type = typeof command.type === 'string' ? command.type : 'Command'
  const subject = [command.name, command.path, command.stepId]
    .find((value) => typeof value === 'string' && value)
  return subject ? `${type} · ${String(subject)}` : type
}

function taskStatusKey(status: AgentTaskRecord['status']) {
  if (status === 'RUNNING') return 'running'
  if (status === 'FAILED') return 'failed'
  if (status === 'CANCELLED') return 'cancelled'
  if (status === 'COMPLETED') return 'completed'
  return 'waiting'
}

function isTerminalAgentEvent(eventType: string) {
  return eventType === 'task.completed' || eventType === 'task.failed' || eventType === 'task.cancelled'
}

function contextKey(reference: AgentContextReference) {
  return `${reference.type}:${reference.id}`
}

function contextIcon(type: AgentContextReference['type']) {
  if (type === 'SCENARIO') return <ExperimentOutlined />
  if (type === 'SUITE') return <ProfileOutlined />
  if (type === 'OPERATION') return <ApiOutlined />
  if (type === 'EXECUTION') return <ClockCircleOutlined />
  return <ReadOutlined />
}

function contextReferenceLabel(
  reference: AgentContextReference,
  scenarios: CaseRecord[],
  suites: SuiteRecord[],
  operations: ApiOperationRecord[],
  runs: RunRecord[],
) {
  if (reference.type === 'SCENARIO') return scenarios.find((item) => item.id === reference.id)?.name ?? reference.id
  if (reference.type === 'SUITE') return suites.find((item) => item.id === reference.id)?.name ?? reference.id
  if (reference.type === 'OPERATION') {
    const operation = operations.find((item) => item.id === reference.id)
    return operation ? `${operation.method} ${operation.path}` : reference.id
  }
  if (reference.type === 'EXECUTION') {
    const run = runs.find((item) => item.id === reference.id)
    return run ? `${run.status} · ${formatDateTime(run.startedAt)}` : reference.id
  }
  return reference.id
}
