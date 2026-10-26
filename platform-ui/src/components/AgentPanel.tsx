import {
  ApartmentOutlined,
  ApiOutlined,
  CheckCircleFilled,
  ClockCircleOutlined,
  CloseCircleFilled,
  CloseOutlined,
  DeleteOutlined,
  DownOutlined,
  EditOutlined,
  ExperimentOutlined,
  FileTextOutlined,
  GlobalOutlined,
  HistoryOutlined,
  LikeOutlined,
  DislikeOutlined,
  LoadingOutlined,
  LockOutlined,
  MoreOutlined,
  PlusOutlined,
  ProfileOutlined,
  ReadOutlined,
  RobotOutlined,
  SendOutlined,
  StopOutlined,
  ToolOutlined,
  WarningFilled,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Dropdown, Input, Modal, Popconfirm, Popover, Select, Spin, Tooltip, Typography } from 'antd'
import { useCallback, useEffect, useMemo, useRef, useState, type PointerEvent as ReactPointerEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import type { TFunction } from 'i18next'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { platformApi } from '../api/platformApi'
import { formatDateTime } from '../i18n'
import type {
  AgentArtifactRecord,
  AgentContextReference,
  AgentContextSelection,
  AgentEventRecord,
  AgentPermissionMode,
  AgentTaskRecord,
  ApiOperationRecord,
  CaseRecord,
  RunRecord,
  PublishScenarioDraftResult,
  ScenarioDraftDiffItem,
  ScenarioDraftRecord,
  SuiteDraftRecord,
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
  'task.paused',
  'task.failed',
  'task.cancelled',
  'continuation.started',
  'continuation.declined',
]

export type AgentScope =
  | { type: 'PROJECT' }
  | { type: 'SCENARIO', id: string }
  | { type: 'SUITE', id: string }

type AgentPanelProps = {
  projectId: string
  scope: AgentScope
  scenarios: CaseRecord[]
  suites: SuiteRecord[]
  operations: ApiOperationRecord[]
  runs: RunRecord[]
  knowledge: TestKnowledgeRecord[]
  presentation?: 'embedded' | 'workspace'
  targetDirty?: boolean
  onClose?: () => void
  onOpenScenario: (scenarioId: string) => void
  onOpenSuite: (suiteId: string) => void
  onOpenKnowledge: (knowledgeId: string) => void
  onApplyDraft: (draft: ScenarioDraftRecord | SuiteDraftRecord) => Promise<void> | void
}

export function AgentPanel({
  projectId,
  scope,
  scenarios,
  suites,
  operations,
  runs,
  knowledge,
  presentation = 'embedded',
  targetDirty = false,
  onClose,
  onOpenScenario,
  onOpenSuite,
  onOpenKnowledge,
  onApplyDraft,
}: AgentPanelProps) {
  const { t } = useTranslation('agent')
  const { message, modal } = App.useApp()
  const queryClient = useQueryClient()
  const storageScopeKey = `${projectId}:${scopeValue(scope)}`
  const [selectedTaskId, setSelectedTaskId] = useState<string>()
  const [newChatMode, setNewChatMode] = useState(false)
  const [composer, setComposer] = useState(
    () => window.localStorage.getItem(`agent-composer:${storageScopeKey}`) ?? '',
  )
  const [contextSelection, setContextSelection] = useState<AgentContextSelection>(emptyAgentContextSelection)
  const [events, setEvents] = useState<AgentEventRecord[]>([])
  const [streamingText, setStreamingText] = useState('')
  const [streamConnected, setStreamConnected] = useState(true)
  const [historyOpen, setHistoryOpen] = useState(false)
  const [panelWidth, setPanelWidth] = useState(420)
  const [renameOpen, setRenameOpen] = useState(false)
  const [renameTitle, setRenameTitle] = useState('')
  const [feedback, setFeedback] = useState<'positive' | 'negative'>()
  const messageEndRef = useRef<HTMLDivElement>(null)
  const skipAutoRestoreRef = useRef(false)
  const panelTitle = t('title')

  useEffect(() => setFeedback(undefined), [selectedTaskId])

  const tasksQuery = useQuery({
    queryKey: ['agent-tasks', projectId],
    queryFn: () => platformApi.listAgentTasks(projectId),
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

  const scopeTasks = useMemo(
    () => (tasksQuery.data ?? []).filter((task) => sameScope(taskScope(task), scope)),
    [scope, tasksQuery.data],
  )
  const selectedTaskCandidate = selectedTaskId
    ? taskQuery.data ?? scopeTasks.find((task) => task.id === selectedTaskId)
    : undefined
  const selectedTask = !newChatMode
    && selectedTaskCandidate
    && sameScope(taskScope(selectedTaskCandidate), scope)
    ? selectedTaskCandidate
    : undefined
  const activePermissionMode = selectedTask?.permissionMode ?? defaultPermissionMode(scope)
  const draftCapable = activePermissionMode === 'REVIEW_REQUIRED'
  const targetScenario = scope.type === 'SCENARIO'
    ? scenarios.find((record) => record.id === scope.id)
    : undefined
  const targetSuite = scope.type === 'SUITE'
    ? suites.find((record) => record.id === scope.id)
    : undefined
  const effectiveContextSelection = selectedTask?.context ?? contextSelection
  const isRunning = selectedTask?.status === 'RUNNING'
  const isWaitingForUser = selectedTask?.status === 'WAITING_FOR_USER'
  const resolvedContext = useMemo(() => resolveAgentContext({
    targetScenario,
    targetSuite,
    selection: effectiveContextSelection,
    scenarios,
    suites,
    operations,
    runs,
    knowledge,
  }), [effectiveContextSelection, knowledge, operations, runs, scenarios, suites, targetScenario, targetSuite])

  useEffect(() => {
    if (skipAutoRestoreRef.current || newChatMode || !scopeTasks.length) return
    if (selectedTaskId && scopeTasks.some((task) => task.id === selectedTaskId)) return
    const stored = window.localStorage.getItem(`agent-task:${storageScopeKey}`)
    const next = scopeTasks.find((task) => task.id === stored) ?? scopeTasks[0]
    setSelectedTaskId(next?.id)
  }, [newChatMode, scopeTasks, selectedTaskId, storageScopeKey])

  useEffect(() => {
    if (selectedTaskId) window.localStorage.setItem(`agent-task:${storageScopeKey}`, selectedTaskId)
  }, [selectedTaskId, storageScopeKey])

  useEffect(() => {
    const key = `agent-composer:${storageScopeKey}`
    if (composer) window.localStorage.setItem(key, composer)
    else window.localStorage.removeItem(key)
  }, [composer, storageScopeKey])

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
      const target = scopeTarget(scope)
      return platformApi.createAgentTask(projectId, {
        title: scopeLabel(scope, scenarios, suites, t),
        targetType: target?.type,
        targetId: target?.id,
        context: contextSelection,
      })
    },
    onSuccess: async (task) => {
      skipAutoRestoreRef.current = false
      setNewChatMode(false)
      setSelectedTaskId(task.id)
      await queryClient.invalidateQueries({ queryKey: ['agent-tasks', projectId] })
    },
  })

  const sendMutation = useMutation({
    mutationFn: async (content: string) => {
      let taskId = newChatMode ? undefined : selectedTaskId
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

  const continuationMutation = useMutation({
    mutationFn: (decision: 'CONTINUE' | 'STOP') => platformApi.decideAgentContinuation(selectedTaskId!, decision),
    onSuccess: async (task) => {
      queryClient.setQueryData(['agent-task', task.id], task)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['agent-task', task.id] }),
        queryClient.invalidateQueries({ queryKey: ['agent-events', task.id] }),
        queryClient.invalidateQueries({ queryKey: ['agent-messages', task.id] }),
        queryClient.invalidateQueries({ queryKey: ['agent-artifacts', task.id] }),
        queryClient.invalidateQueries({ queryKey: ['agent-tasks', projectId] }),
      ])
    },
    onError: (error) => {
      void message.error(error instanceof Error ? error.message : t('continuationFailed'))
    },
  })

  const deleteMutation = useMutation({
    mutationFn: platformApi.deleteAgentTask,
    onSuccess: async (_result, taskId) => {
      if (selectedTaskId === taskId) {
        skipAutoRestoreRef.current = true
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

  const permissionModeMutation = useMutation({
    mutationFn: (nextMode: AgentPermissionMode) => platformApi.changeAgentTaskPermission(selectedTaskId!, nextMode),
    onSuccess: async (task) => {
      queryClient.setQueryData(['agent-task', task.id], task)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['agent-task', task.id] }),
        queryClient.invalidateQueries({ queryKey: ['agent-tasks', projectId] }),
        queryClient.invalidateQueries({ queryKey: ['agent-events', task.id] }),
      ])
      void message.success(t('permissionUpdated'))
    },
    onError: () => void message.error(t('permissionUpdateFailed')),
  })

  const modelMutation = useMutation({
    mutationFn: (nextModelId: string) => platformApi.changeAgentTaskModel(selectedTaskId!, nextModelId),
    onSuccess: async (task) => {
      queryClient.setQueryData(['agent-task', task.id], task)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['agent-task', task.id] }),
        queryClient.invalidateQueries({ queryKey: ['agent-tasks', projectId] }),
        queryClient.invalidateQueries({ queryKey: ['agent-events', task.id] }),
      ])
      void message.success(t('modelUpdated'))
    },
    onError: () => void message.error(t('modelUpdateFailed')),
  })

  const feedbackMutation = useMutation({
    mutationFn: (value: 'positive' | 'negative') => platformApi.recordAgentDecision(
      selectedTaskId!,
      value === 'positive' ? 'FEEDBACK_POSITIVE' : 'FEEDBACK_NEGATIVE',
    ),
    onSuccess: (_result, value) => setFeedback(value),
    onError: () => void message.error(t('feedbackFailed')),
  })

  const applyDraft = useCallback(async (
    draftId: string,
    draftType: 'SCENARIO_DRAFT' | 'SUITE_DRAFT',
    artifactContent: Record<string, unknown>,
  ) => {
    try {
      const draft = draftType === 'SUITE_DRAFT'
        ? artifactContent as unknown as SuiteDraftRecord
        : await platformApi.getScenarioDraft(draftId)
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

  const publishNewScenario = useCallback(async (draftId: string, addToSuite: boolean) => {
    try {
      if (addToSuite && targetDirty) throw new Error(t('saveSuiteBeforeAdd'))
      const result = await platformApi.publishScenarioDraft(draftId, {
        addToSuite,
        expectedSuiteRevision: addToSuite ? targetSuite?.revision : undefined,
      })
      queryClient.setQueryData<CaseRecord[]>(['cases', projectId], (current = []) => {
        const withoutPublished = current.filter((item) => item.id !== result.scenario.id)
        return [result.scenario, ...withoutPublished]
      })
      if (result.suite) {
        queryClient.setQueryData<SuiteRecord[]>(['suites', projectId], (current = []) => current.map(
          (item) => item.id === result.suite?.id ? result.suite : item,
        ))
      }
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['cases', projectId] }),
        result.suite ? queryClient.invalidateQueries({ queryKey: ['suites', projectId] }) : Promise.resolve(),
      ])
      void message.success(addToSuite ? t('scenarioCreatedAndAdded') : t('scenarioCreated'))
      return result
    } catch (error) {
      void message.error(error instanceof Error ? error.message : t('scenarioCreateFailed'))
      throw error
    }
  }, [message, projectId, queryClient, t, targetDirty, targetSuite?.revision])

  const submit = (content = composer) => {
    const value = content.trim()
    if (!value || isRunning || sendMutation.isPending) return
    void sendMutation.mutateAsync(value)
  }

  const startNewChat = () => {
    skipAutoRestoreRef.current = true
    setNewChatMode(true)
    setSelectedTaskId(undefined)
    setComposer('')
    setContextSelection(emptyAgentContextSelection())
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
      const maximum = Math.min(760, Math.floor(window.innerWidth * 0.45))
      setPanelWidth(Math.max(340, Math.min(maximum, startWidth + startX - pointerEvent.clientX)))
    }
    const stop = () => {
      window.removeEventListener('pointermove', move)
      window.removeEventListener('pointerup', stop)
    }
    window.addEventListener('pointermove', move)
    window.addEventListener('pointerup', stop)
  }

  const activityEvents = events.filter((event) => (
    event.eventType === 'context.snapshot.created'
    || event.eventType === 'skill.loaded'
    || event.eventType.startsWith('tool.')
  ))
  const activeTurnId = isRunning
    ? [...events].reverse().find((event) => event.eventType === 'task.started')?.payload.turnId
    : undefined
  const latestTaskEvent = [...events].reverse().find((event) => event.eventType.startsWith('task.'))
  const failedEvent = !sendMutation.isPending
    && selectedTask?.status === 'FAILED'
    && latestTaskEvent?.eventType === 'task.failed'
    ? latestTaskEvent
    : undefined
  const persistedAppliedDraftIds = useMemo(() => new Set(events
    .filter((event) => event.eventType === 'decision.draft_applied_to_editor')
    .map((event) => event.payload.draftId)
    .filter((draftId): draftId is string => typeof draftId === 'string')), [events])
  const persistedPublishedScenarios = useMemo(() => new Map(events
    .filter((event) => event.eventType === 'decision.scenario_saved')
    .map((event) => [event.payload.draftId, event.payload.scenarioId] as const)
    .filter((entry): entry is readonly [string, string] => (
      typeof entry[0] === 'string' && typeof entry[1] === 'string'
    ))), [events])
  const modelOptions = useMemo(() => {
    if (!selectedTask) return []
    const runtime = (runtimesQuery.data ?? []).find((item) => item.type === selectedTask.runtimeType)
    const models = runtime?.capabilities.models?.length
      ? runtime.capabilities.models
      : [selectedTask.modelId]
    return models.map((modelId) => ({ key: modelId, label: modelId }))
  }, [runtimesQuery.data, selectedTask])
  return (
    <aside
      className={presentation === 'workspace'
        ? 'agent-panel agent-workspace-panel'
        : 'agent-panel agent-tab-pane'}
      style={presentation === 'embedded' ? { width: panelWidth } : undefined}
      aria-label={panelTitle}
    >
      {presentation === 'embedded' ? (
        <div className="agent-panel-resize" role="separator" aria-orientation="vertical" onPointerDown={startResize} />
      ) : null}
      <header className="agent-panel-header">
        <div className="agent-panel-title">
          <RobotOutlined />
          <span>{panelTitle}</span>
          {selectedTask ? (
            <>
              <Dropdown
                trigger={['click']}
                menu={{
                  selectable: true,
                  selectedKeys: [selectedTask.modelId],
                  items: modelOptions,
                  onClick: ({ key }) => modelMutation.mutate(String(key)),
                }}
              >
                <Button
                  aria-label={t('model')}
                  className="agent-model-trigger"
                  size="small"
                  type="text"
                  loading={modelMutation.isPending}
                  disabled={isRunning || isWaitingForUser || modelOptions.length < 2}
                >
                  {selectedTask.modelId}<DownOutlined />
                </Button>
              </Dropdown>
              <Dropdown
                trigger={['click']}
                menu={{
                  selectable: true,
                  selectedKeys: [selectedTask.permissionMode],
                  items: permissionOptions(scope, t).map((option) => ({ key: option.value, label: option.label })),
                  onClick: ({ key }) => permissionModeMutation.mutate(key as AgentPermissionMode),
                }}
              >
                <Button
                  aria-label={t('permission')}
                  className="agent-permission-trigger"
                  size="small"
                  type="text"
                  loading={permissionModeMutation.isPending}
                  disabled={isRunning || isWaitingForUser}
                >
                  {t(selectedTask.permissionMode === 'REVIEW_REQUIRED' ? 'reviewRequired' : 'readOnly')}<DownOutlined />
                </Button>
              </Dropdown>
            </>
          ) : null}
        </div>
        <div className="agent-panel-actions">
          <Tooltip title={t('newChat')}>
            <Button
              aria-label={t('newChat')}
              icon={<FileTextOutlined />}
              size="small"
              type="text"
              onClick={() => startNewChat()}
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
                tasks={scopeTasks}
                selectedTaskId={selectedTaskId}
                onSelect={(taskId) => {
                  skipAutoRestoreRef.current = false
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
          {presentation === 'embedded' ? (
            <>
              <Tooltip title={t('close')}>
                <Button aria-label={t('close')} icon={<CloseOutlined />} size="small" type="text" onClick={onClose} />
              </Tooltip>
            </>
          ) : null}
        </div>
      </header>

      <div className="agent-context-bar agent-scope-bar">
        {scopeIcon(scope.type)}
        <div className="agent-scope-copy">
          <span>{scopeLabel(scope, scenarios, suites, t)}</span>
          <small>{t('contextSummary', {
            operations: resolvedContext.automaticOperations.length,
            knowledge: resolvedContext.visibleKnowledge.length,
            added: effectiveContextSelection.included.length,
          })}</small>
        </div>
        <Popover
          arrow={false}
          placement="bottomRight"
          trigger="click"
          content={(
            <AgentContextEditor
              locked={Boolean(selectedTask)}
              scope={scope}
              targetScenario={targetScenario}
              targetSuite={targetSuite}
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
        {selectedTask ? (
          <Dropdown
            trigger={['click']}
            menu={{
              items: [
                {
                  key: 'open',
                  label: selectedTask.targetType === 'SUITE' ? t('openSuite') : t('openScenario'),
                  icon: selectedTask.targetType === 'SUITE' ? <ApartmentOutlined /> : <ExperimentOutlined />,
                  disabled: !selectedTask.targetId || !['SCENARIO', 'SUITE'].includes(selectedTask.targetType ?? ''),
                },
                { key: 'rename', label: t('renameSession'), icon: <EditOutlined /> },
                { type: 'divider' },
                { key: 'delete', label: t('deleteSession'), icon: <DeleteOutlined />, danger: true, disabled: isRunning },
              ],
              onClick: ({ key }) => {
                if (key === 'open' && selectedTask.targetId && selectedTask.targetType === 'SCENARIO') {
                  onOpenScenario(selectedTask.targetId)
                }
                if (key === 'open' && selectedTask.targetId && selectedTask.targetType === 'SUITE') {
                  onOpenSuite(selectedTask.targetId)
                }
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

      <div className="agent-conversation">
        {tasksQuery.isLoading && !selectedTask ? <Spin size="small" /> : null}
        {!selectedTask && !messagesQuery.isLoading ? (
          <div className="agent-empty-state">
            <RobotOutlined />
            <strong>{t(emptyTitleKey(scope.type))}</strong>
            <span>{t(emptyDescriptionKey(scope.type))}</span>
          </div>
        ) : null}

        {(messagesQuery.data ?? []).map((item) => (
          <article key={item.id} className={`agent-message ${item.role.toLowerCase()}`}>
            <div className="agent-message-role">{item.role === 'USER' ? 'You' : panelTitle}</div>
            {item.role === 'ASSISTANT'
              ? <AgentMarkdown content={messageText(item.content)} />
              : <div className="agent-message-content">{messageText(item.content)}</div>}
          </article>
        ))}
        {streamingText ? (
          <article className="agent-message assistant streaming">
            <div className="agent-message-role">{panelTitle}</div>
            <AgentMarkdown content={streamingText} streaming />
          </article>
        ) : null}

        {activityEvents.length ? <AgentActivity events={activityEvents} activeTurnId={activeTurnId} /> : null}
        {draftCapable && artifactsQuery.data?.length ? (
          <AgentArtifacts
            key={selectedTaskId}
            artifacts={artifactsQuery.data}
            persistedAppliedDraftIds={persistedAppliedDraftIds}
            persistedPublishedScenarios={persistedPublishedScenarios}
            canAddToSuite={!targetDirty}
            onApply={applyDraft}
            onPublishNewScenario={publishNewScenario}
            onOpenScenario={onOpenScenario}
            onOpenKnowledge={onOpenKnowledge}
          />
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
        {isWaitingForUser ? (
          <div className="agent-pause-row">
            <ClockCircleOutlined />
            <div>
              <strong>{t('paused')}</strong>
              <span>{t('pausedDescription')}</span>
            </div>
            <Button
              size="small"
              disabled={continuationMutation.isPending}
              onClick={() => void continuationMutation.mutateAsync('STOP')}
            >
              {t('stop')}
            </Button>
            <Button
              size="small"
              type="primary"
              loading={continuationMutation.isPending}
              onClick={() => void continuationMutation.mutateAsync('CONTINUE')}
            >
              {t('continue')}
            </Button>
          </div>
        ) : null}
        {!streamConnected && selectedTaskId ? <div className="agent-stream-state">{t('connectionLost')}</div> : null}
        <div ref={messageEndRef} />
      </div>

      <footer className="agent-composer">
        <Input.TextArea
          autoSize={{ minRows: 2, maxRows: 6 }}
          value={composer}
          disabled={isRunning || isWaitingForUser}
          placeholder={isWaitingForUser ? t('composerPaused') : t(composerPlaceholderKey(scope.type))}
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
                disabled={!composer.trim()}
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
  scope,
  targetScenario,
  targetSuite,
  selection,
  resolved,
  scenarios,
  suites,
  operations,
  runs,
  onChange,
}: {
  locked: boolean
  scope: AgentScope
  targetScenario?: CaseRecord
  targetSuite?: SuiteRecord
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
        .filter((item) => item.id !== targetScenario?.id && !includedKeys.has(`SCENARIO:${item.id}`))
        .map((item) => ({ value: `SCENARIO:${item.id}`, label: item.name })),
    },
    {
      label: t('contextSuites'),
      options: suites
        .filter((item) => item.id !== targetSuite?.id && !includedKeys.has(`SUITE:${item.id}`))
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
        {scope.type === 'PROJECT' ? (
          <ContextRow
            icon={<GlobalOutlined />}
            title={t('projectScope')}
            detail={t('targetProject')}
            action={<LockOutlined />}
          />
        ) : null}
        {targetScenario ? (
          <ContextRow
            icon={<ExperimentOutlined />}
            title={targetScenario.name}
            detail={t('targetScenario')}
            action={<LockOutlined />}
          />
        ) : null}
        {targetSuite ? (
          <ContextRow
            icon={<ApartmentOutlined />}
            title={targetSuite.name}
            detail={t('targetSuite')}
            action={<LockOutlined />}
          />
        ) : null}
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
            <small>{formatDateTime(task.updatedAt)}</small>
            <i className={`agent-status-dot ${task.status.toLowerCase()}`} />
          </button>
        ))}
        {!tasks.length ? <Text type="secondary">{t('newChat')}</Text> : null}
      </div>
    </div>
  )
}

function AgentActivity({ events, activeTurnId }: { events: AgentEventRecord[]; activeTurnId?: unknown }) {
  const { t } = useTranslation('agent')
  const items = collapseActivityEvents(events)
  return (
    <details className="agent-activity">
      <summary><ToolOutlined /><span>{t('activity')}</span><small>{items.length}</small></summary>
      <div className="agent-activity-list">
        {items.map((item) => (
          <div key={item.key} className="agent-activity-row">
            {activityIcon(item.event.eventType, isCurrentToolCall(item, activeTurnId))}
            <span>{activityLabel(item.event, isCurrentToolCall(item, activeTurnId))}</span>
          </div>
        ))}
      </div>
    </details>
  )
}

function AgentArtifacts({
  artifacts,
  persistedAppliedDraftIds,
  persistedPublishedScenarios,
  canAddToSuite,
  onApply,
  onPublishNewScenario,
  onOpenScenario,
  onOpenKnowledge,
}: {
  artifacts: AgentArtifactRecord[]
  persistedAppliedDraftIds: ReadonlySet<string>
  persistedPublishedScenarios: ReadonlyMap<string, string>
  canAddToSuite: boolean
  onApply: (draftId: string, draftType: 'SCENARIO_DRAFT' | 'SUITE_DRAFT', content: Record<string, unknown>) => Promise<boolean>
  onPublishNewScenario: (draftId: string, addToSuite: boolean) => Promise<PublishScenarioDraftResult>
  onOpenScenario: (scenarioId: string) => void
  onOpenKnowledge: (knowledgeId: string) => void
}) {
  const { t } = useTranslation('agent')
  const [applyingId, setApplyingId] = useState<string>()
  const [appliedDraftIds, setAppliedDraftIds] = useState<Set<string>>(() => new Set())
  const [publishedScenarios, setPublishedScenarios] = useState<Map<string, string>>(() => new Map())
  const draft = [...artifacts].reverse().find(
    (artifact) => (
      artifact.artifactType === 'SCENARIO_DRAFT'
      || artifact.artifactType === 'NEW_SCENARIO_DRAFT'
      || artifact.artifactType === 'SUITE_DRAFT'
    ) && artifact.targetId,
  )

  if (!draft?.targetId) return null
  const draftId = draft.targetId
  const suiteDraft = draft.artifactType === 'SUITE_DRAFT'
  const newScenarioDraft = draft.artifactType === 'NEW_SCENARIO_DRAFT'
  const validationArtifact = findLatestArtifact(artifacts, suiteDraft ? 'SUITE_VALIDATION' : 'SCENARIO_VALIDATION', draftId)
  const diffArtifact = findLatestArtifact(artifacts, suiteDraft ? 'SUITE_DIFF' : 'SCENARIO_DIFF', draftId)
  const planArtifact = findLatestArtifact(artifacts, suiteDraft ? 'SUITE_COMMAND_PLAN' : 'COMMAND_PLAN', draftId)
  const validation = (nestedObject(validationArtifact?.content, 'validation')
    ?? nestedObject(draft.content, 'validation')) as ScenarioDraftValidation | undefined
  const diff = nestedObject(diffArtifact?.content, 'diff')
  const changes = Array.isArray(diff?.items) ? diff.items as ScenarioDraftDiffItem[] : []
  const commands = Array.isArray(planArtifact?.content.commands)
    ? planArtifact.content.commands as Array<Record<string, unknown>>
    : []
  const citations = Array.isArray(planArtifact?.content.citations) ? planArtifact.content.citations : []
  const canApply = validation?.valid === true
  const applied = appliedDraftIds.has(draftId) || persistedAppliedDraftIds.has(draftId)
  const publishedScenarioId = publishedScenarios.get(draftId) ?? persistedPublishedScenarios.get(draftId)
  const draftName = typeof draft.content.name === 'string' && draft.content.name.trim()
    ? draft.content.name
    : t('newScenarioDraft')
  const draftSteps = Array.isArray(draft.content.steps)
    ? draft.content.steps as Array<{ id?: string, name?: string }>
    : []

  const publish = async (addToSuite: boolean) => {
    setApplyingId(draftId)
    try {
      const result = await onPublishNewScenario(draftId, addToSuite)
      setPublishedScenarios((current) => new Map(current).set(draftId, result.scenario.id))
    } finally {
      setApplyingId(undefined)
    }
  }
  return (
    <section className="agent-artifacts">
      <div className="agent-artifacts-title"><FileTextOutlined /><strong>{t('artifacts')}</strong></div>
          <div key={draft.id} className={(applied || publishedScenarioId) ? 'agent-artifact applied' : 'agent-artifact'}>
            <div className="agent-artifact-head">
              <div>
                <strong>{newScenarioDraft ? draftName : t('draft')}</strong>
                <span className={canApply ? 'valid' : 'invalid'}>
                  {canApply ? <CheckCircleFilled /> : <WarningFilled />}
                  {canApply ? t('valid') : t('invalid')}
                </span>
              </div>
              {publishedScenarioId ? (
                <div className="agent-artifact-actions">
                  <span className="agent-applied-status"><CheckCircleFilled />{t('created')}</span>
                  <Button size="small" type="link" onClick={() => onOpenScenario(publishedScenarioId)}>
                    {t('openScenario')}
                  </Button>
                </div>
              ) : newScenarioDraft ? (
                <div className="agent-artifact-actions">
                  <Popconfirm
                    title={t('confirmCreateScenarioTitle')}
                    description={t('confirmCreateScenarioDescription', { name: draftName })}
                    okText={t('createScenarioOnly')}
                    cancelText={t('cancel')}
                    onConfirm={() => publish(false)}
                  >
                    <Button size="small" disabled={!canApply} loading={applyingId === draftId}>
                      {t('createScenarioOnly')}
                    </Button>
                  </Popconfirm>
                  <Popconfirm
                    title={t('confirmCreateAndAddTitle')}
                    description={t('confirmCreateAndAddDescription', { name: draftName })}
                    okText={t('createAndAddToSuite')}
                    cancelText={t('cancel')}
                    onConfirm={() => publish(true)}
                  >
                    <Button
                      size="small"
                      type="primary"
                      disabled={!canApply || !canAddToSuite}
                      loading={applyingId === draftId}
                      title={!canAddToSuite ? t('saveSuiteBeforeAdd') : undefined}
                    >
                      {t('createAndAddToSuite')}
                    </Button>
                  </Popconfirm>
                </div>
              ) : applied ? (
                <span className="agent-applied-status"><CheckCircleFilled />{t('appliedShort')}</span>
              ) : (
                <Button
                  size="small"
                  type="primary"
                  disabled={!canApply}
                  loading={applyingId === draftId}
                  onClick={async () => {
                    setApplyingId(draftId)
                    try {
                      if (await onApply(
                        draftId,
                        draft.artifactType as 'SCENARIO_DRAFT' | 'SUITE_DRAFT',
                        draft.content,
                      )) {
                        setAppliedDraftIds((current) => new Set(current).add(draftId))
                      }
                    } finally {
                      setApplyingId(undefined)
                    }
                  }}
                >
                  {t('applyToEditor')}
                </Button>
              )}
            </div>
            {newScenarioDraft && !publishedScenarioId ? (
              <div className="agent-new-scenario-summary">
                <span>{t('stepCount', { count: draftSteps.length })}</span>
                {draftSteps.length ? (
                  <ol>{draftSteps.map((step, index) => <li key={step.id ?? index}>{step.name || t('unnamedStep')}</li>)}</ol>
                ) : null}
              </div>
            ) : null}
            {!applied && commands.length ? (
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
            {!applied ? <details open className="agent-artifact-detail">
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
            </details> : null}
            {!applied && validation?.issues?.length ? (
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
            {!applied && citations.length ? (
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

function AgentMarkdown({ content, streaming = false }: { content: string; streaming?: boolean }) {
  return (
    <div className="agent-message-content agent-markdown">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          a: ({ node: _node, ...props }) => <a {...props} target="_blank" rel="noreferrer noopener" />,
        }}
      >
        {content}
      </ReactMarkdown>
      {streaming ? <span className="agent-stream-caret" /> : null}
    </div>
  )
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

function activityIcon(type: string, active: boolean) {
  if (type.endsWith('.completed')) return <CheckCircleFilled className="success" />
  if (type.endsWith('.failed')) return <CloseCircleFilled className="failed" />
  if (type.endsWith('.started')) return active ? <LoadingOutlined spin /> : <ClockCircleOutlined />
  return <FileTextOutlined />
}

function activityLabel(event: AgentEventRecord, active = true) {
  if (event.eventType === 'skill.loaded') return `Skill: ${String(event.payload.skillName ?? '')}`
  if (event.eventType.startsWith('tool.')) {
    const state = event.eventType === 'tool.started' && !active ? 'unfinished' : event.eventType.split('.')[1]
    return `${String(event.payload.toolName ?? 'Tool')} / ${state}`
  }
  if (event.eventType === 'context.snapshot.created') return `Context snapshot / ${String(event.payload.sourceCount ?? 0)} sources`
  return event.eventType
}

type AgentActivityItem = {
  key: string
  event: AgentEventRecord
  pending: boolean
  fallbackKey?: string
}

function collapseActivityEvents(events: AgentEventRecord[]) {
  const items: AgentActivityItem[] = []
  const callsByKey = new Map<string, AgentActivityItem>()
  const pendingByFallback = new Map<string, AgentActivityItem[]>()

  for (const event of [...events].sort((left, right) => left.sequence - right.sequence)) {
    if (!event.eventType.startsWith('tool.')) {
      items.push({ key: event.id, event, pending: false })
      continue
    }

    const callKey = toolCallKey(event)
    const fallbackKey = toolFallbackKey(event)
    if (event.eventType === 'tool.started') {
      const item = { key: callKey ?? event.id, event, pending: true, fallbackKey }
      items.push(item)
      if (callKey) callsByKey.set(callKey, item)
      const pending = pendingByFallback.get(fallbackKey) ?? []
      pending.push(item)
      pendingByFallback.set(fallbackKey, pending)
      continue
    }

    const item = (callKey ? callsByKey.get(callKey) : undefined)
      ?? pendingByFallback.get(fallbackKey)?.find((candidate) => candidate.pending)
    if (item) {
      item.event = event
      item.pending = false
      continue
    }
    items.push({ key: callKey ?? event.id, event, pending: false, fallbackKey })
  }
  return items
}

function toolCallKey(event: AgentEventRecord) {
  const sequence = event.payload.callSequence
  if (typeof sequence !== 'number' && typeof sequence !== 'string') return undefined
  return `${String(event.payload.turnId ?? '')}:${String(sequence)}`
}

function toolFallbackKey(event: AgentEventRecord) {
  return `${String(event.payload.turnId ?? '')}:${String(event.payload.toolName ?? 'Tool')}`
}

function isCurrentToolCall(item: AgentActivityItem, activeTurnId: unknown) {
  return item.pending
    && (typeof activeTurnId === 'string' || typeof activeTurnId === 'number')
    && String(item.event.payload.turnId ?? '') === String(activeTurnId)
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
  if (status === 'WAITING_FOR_USER') return 'waiting'
  if (status === 'FAILED') return 'failed'
  if (status === 'CANCELLED') return 'cancelled'
  if (status === 'COMPLETED') return 'completed'
  return 'waiting'
}

function taskScope(task: AgentTaskRecord): AgentScope {
  if (task.targetType === 'SCENARIO' && task.targetId) return { type: 'SCENARIO', id: task.targetId }
  if (task.targetType === 'SUITE' && task.targetId) return { type: 'SUITE', id: task.targetId }
  return { type: 'PROJECT' }
}

function scopeTarget(scope: AgentScope) {
  if (scope.type === 'PROJECT') return undefined
  return { type: scope.type, id: scope.id } as const
}

function scopeValue(scope: AgentScope) {
  return scope.type === 'PROJECT' ? 'PROJECT' : `${scope.type}:${scope.id}`
}

function sameScope(left: AgentScope, right: AgentScope) {
  return scopeValue(left) === scopeValue(right)
}

function scopeLabel(
  scope: AgentScope,
  scenarios: CaseRecord[],
  suites: SuiteRecord[],
  t: TFunction<'agent'>,
) {
  if (scope.type === 'PROJECT') return t('projectScope')
  if (scope.type === 'SCENARIO') {
    return scenarios.find((item) => item.id === scope.id)?.name ?? t('missingScenario')
  }
  return suites.find((item) => item.id === scope.id)?.name ?? t('missingSuite')
}

function defaultPermissionMode(scope: AgentScope): AgentPermissionMode {
  return scope.type === 'SCENARIO' ? 'REVIEW_REQUIRED' : 'READ_ONLY'
}

function permissionOptions(scope: AgentScope, t: TFunction<'agent'>) {
  if (scope.type !== 'SCENARIO') return [{ value: 'READ_ONLY', label: t('readOnly') }]
  return [
    { value: 'READ_ONLY', label: t('readOnly') },
    { value: 'REVIEW_REQUIRED', label: t('reviewRequired') },
  ]
}

function scopeIcon(type: AgentScope['type']) {
  if (type === 'SCENARIO') return <ExperimentOutlined />
  if (type === 'SUITE') return <ApartmentOutlined />
  return <GlobalOutlined />
}

function emptyTitleKey(type: AgentScope['type']) {
  if (type === 'SCENARIO') return 'emptyTitleScenario' as const
  if (type === 'SUITE') return 'emptyTitleSuite' as const
  return 'emptyTitleProject' as const
}

function composerPlaceholderKey(type: AgentScope['type']) {
  if (type === 'SCENARIO') return 'composerPlaceholderScenario' as const
  if (type === 'SUITE') return 'composerPlaceholderSuite' as const
  return 'composerPlaceholderProject' as const
}

function emptyDescriptionKey(type: AgentScope['type']) {
  if (type === 'SCENARIO') return 'emptyDescriptionScenario' as const
  if (type === 'SUITE') return 'emptyDescriptionSuite' as const
  return 'emptyDescriptionProject' as const
}

function isTerminalAgentEvent(eventType: string) {
  return eventType === 'task.completed'
    || eventType === 'task.paused'
    || eventType === 'task.failed'
    || eventType === 'task.cancelled'
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
