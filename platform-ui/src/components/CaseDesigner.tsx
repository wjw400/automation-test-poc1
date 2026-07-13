import {
  Alert,
  App,
  Button,
  Input,
  Select,
  Tag,
  Tabs,
  Typography,
} from 'antd'
import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  DeleteOutlined,
  DownOutlined,
  PlusOutlined,
  RightOutlined,
} from '@ant-design/icons'
import { useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import type { ApiOperationRecord, SaveCaseInput } from '../types/platform'
import {
  parseDsl,
  parseStructuredText,
  stringifyDsl,
  stringifyStructuredValue,
  type DslObject,
} from '../utils/caseDsl'

const { Paragraph, Text } = Typography

type CaseDesignerProps = {
  draft: SaveCaseInput
  onChange: (next: SaveCaseInput) => void
  operations: ApiOperationRecord[]
  view: 'case' | 'step'
  activeStepIndex: number
  onSelectCaseOverview: () => void
  onSelectStep: (stepIndex: number) => void
  onActiveStepChange?: (stepIndex: number) => void
}

type AssertionRule = {
  source?: string
  path?: string
  operator?: string
  expected?: unknown
}

type ExtractRule = {
  source?: string
  path?: string
}

type CaseAction = {
  type?: string
  name?: string
  profile?: string
  input?: unknown
  output?: unknown
}

const assertionOperators = [
  'equals',
  'notEquals',
  'exists',
  'notExists',
  'contains',
  'greaterThan',
  'lessThan',
]

const actionTypes = [
  'crypto.encrypt',
  'crypto.decrypt',
  'crypto.sign',
  'auth.oauth2.clientCredentials',
  'body.wrapJsonField',
  'body.parseJson',
  'body.decodeBase64',
  'response.normalize',
]

const emptyStep = () => ({
  name: 'New API step',
  type: 'http',
  operation: {},
  runtime: {},
  request: {
    query: {},
    headers: {
      'Content-Type': 'application/json',
    },
    body: {},
  },
  actions: {
    beforeSend: [],
    afterReceive: [],
  },
  response: {
    status: 200,
    assertions: [],
    extract: {},
  },
})

export function CaseDesigner({
  draft,
  onChange,
  operations,
  view,
  activeStepIndex,
  onSelectCaseOverview,
  onSelectStep,
  onActiveStepChange,
}: CaseDesignerProps) {
  const { modal } = App.useApp()
  const parsed = useMemo(() => parseDsl(draft.content), [draft.content])
  const [activeTab, setActiveTab] = useState('params')

  useEffect(() => {
    setActiveTab('params')
  }, [draft.code, draft.name, view, activeStepIndex])

  useEffect(() => {
    if (view === 'step') {
      onActiveStepChange?.(activeStepIndex + 1)
    }
  }, [activeStepIndex, onActiveStepChange, view])

  const dsl = parsed.data
  const schemaErrors = useMemo(() => (dsl ? validateCaseDslV1(dsl) : []), [dsl])

  if (parsed.error || !dsl) {
    return (
      <section className="case-workbench scenario-flow-designer">
        <SchemaErrorPanel
          title="Case YAML is not valid YAML"
          errors={[parsed.error ?? 'Case DSL root must be an object.']}
          draft={draft}
          onChange={onChange}
        />
      </section>
    )
  }

  if (schemaErrors.length) {
    return (
      <section className="case-workbench scenario-flow-designer">
        <SchemaErrorPanel
          title="Case YAML does not match Case DSL v1"
          errors={schemaErrors}
          draft={draft}
          onChange={onChange}
        />
      </section>
    )
  }

  const steps = dsl.steps as any[]
  const selectedStep = steps[activeStepIndex]
  const selectedOperation = resolveOperation(selectedStep, operations)

  const updateDsl = (recipe: (next: DslObject) => void) => {
    const clone = structuredClone(dsl)
    recipe(clone)
    onChange({ ...draft, content: stringifyDsl(clone) })
  }

  const updateSelectedStep = (recipe: (step: any) => void) => {
    updateDsl((next) => {
      next.steps = Array.isArray(next.steps) ? next.steps : []
      if (!next.steps[activeStepIndex]) {
        next.steps[activeStepIndex] = emptyStep()
      }
      recipe(next.steps[activeStepIndex])
    })
  }

  const addStep = () => {
    updateDsl((next) => {
      next.steps.push(emptyStep())
    })
    onSelectStep(steps.length)
  }

  const moveStep = (fromIndex: number, toIndex: number) => {
    if (toIndex < 0 || toIndex >= steps.length || fromIndex === toIndex) {
      return
    }
    updateDsl((next) => {
      const [movedStep] = next.steps.splice(fromIndex, 1)
      next.steps.splice(toIndex, 0, movedStep)
    })
    if (view === 'step') {
      onSelectStep(indexAfterMove(activeStepIndex, fromIndex, toIndex))
    }
  }

  const removeStep = (stepIndex: number) => {
    updateDsl((next) => {
      next.steps.splice(stepIndex, 1)
    })
    if (view !== 'step') {
      return
    }
    if (steps.length <= 1) {
      onSelectCaseOverview()
      return
    }
    if (activeStepIndex === stepIndex) {
      onSelectStep(Math.min(stepIndex, steps.length - 2))
    } else if (stepIndex < activeStepIndex) {
      onSelectStep(activeStepIndex - 1)
    }
  }

  const confirmDeleteStep = (stepIndex: number) => {
    const step = steps[stepIndex]
    modal.confirm({
      title: 'Delete step?',
      content: `Remove ${stepMethod(step)} ${operationPath(step)} from this scenario?`,
      okText: 'Delete',
      okButtonProps: { danger: true },
      cancelText: 'Cancel',
      onOk: () => removeStep(stepIndex),
    })
  }

  return (
    <section className="case-workbench scenario-flow-designer">
      <CaseOverview
        activeStepIndex={view === 'step' && selectedStep ? activeStepIndex : undefined}
        draft={draft}
        dsl={dsl}
        steps={steps}
        stepEditor={view === 'step' && selectedStep ? (
          <StepEditor
            activeTab={activeTab}
            afterReceiveActions={asActions(selectedStep?.actions?.afterReceive)}
            assertions={asAssertions(selectedStep?.response?.assertions)}
            beforeSendActions={asActions(selectedStep?.actions?.beforeSend)}
            extractEntries={asExtractEntries(selectedStep?.response?.extract)}
            operations={operations}
            selectedOperation={selectedOperation}
            selectedStep={selectedStep}
            onTabChange={setActiveTab}
            setSelectedOperation={(value) => {
              const nextOperation = operations.find((item) => item.id === value)
              updateSelectedStep((step) => {
                step.operation = nextOperation
                  ? {
                      id: nextOperation.id,
                      apiSpecId: nextOperation.apiSpecId,
                      apiSpecName: nextOperation.apiSpecName,
                      operationId: nextOperation.operationId,
                      summary: nextOperation.summary,
                      service: nextOperation.tagName || nextOperation.apiSpecName || 'defaultService',
                      method: nextOperation.method,
                      path: nextOperation.path,
                    }
                  : {}
              })
            }}
            updateSelectedStep={updateSelectedStep}
          />
        ) : null}
        onAddStep={addStep}
        onChange={onChange}
        onCollapseStep={onSelectCaseOverview}
        onDeleteStep={confirmDeleteStep}
        onMoveStep={moveStep}
        onSelectStep={onSelectStep}
        updateDsl={updateDsl}
      />
    </section>
  )
}

function CaseOverview({
  activeStepIndex,
  draft,
  dsl,
  steps,
  stepEditor,
  onAddStep,
  onChange,
  onCollapseStep,
  onDeleteStep,
  onMoveStep,
  onSelectStep,
  updateDsl,
}: {
  activeStepIndex?: number
  draft: SaveCaseInput
  dsl: DslObject
  steps: any[]
  stepEditor: ReactNode
  onAddStep: () => void
  onChange: (next: SaveCaseInput) => void
  onCollapseStep: () => void
  onDeleteStep: (stepIndex: number) => void
  onMoveStep: (fromIndex: number, toIndex: number) => void
  onSelectStep: (stepIndex: number) => void
  updateDsl: (recipe: (next: DslObject) => void) => void
}) {
  const [isAddingTag, setIsAddingTag] = useState(false)
  const [tagInput, setTagInput] = useState('')
  const stepItemRefs = useRef(new Map<number, HTMLDivElement>())

  useEffect(() => {
    if (activeStepIndex == null) {
      return
    }
    const frame = window.requestAnimationFrame(() => {
      stepItemRefs.current.get(activeStepIndex)?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    })
    return () => window.cancelAnimationFrame(frame)
  }, [activeStepIndex])

  const addTag = () => {
    const nextTag = tagInput.trim()
    if (nextTag && !draft.tags.includes(nextTag)) {
      onChange({ ...draft, tags: [...draft.tags, nextTag] })
    }
    setTagInput('')
    setIsAddingTag(false)
  }

  return (
    <div className="case-overview-panel">
      <section className="case-overview-section">
        <ScenarioSectionHeader title="Tags" />
        <div className="case-overview-tags">
          <div className="case-overview-tags-list">
            {draft.tags.map((tag) => (
              <Tag
                key={tag}
                closable
                className="case-overview-tag"
                onClose={(event) => {
                  event.preventDefault()
                  onChange({ ...draft, tags: draft.tags.filter((value) => value !== tag) })
                }}
              >
                {tag}
              </Tag>
            ))}
            {isAddingTag ? (
              <Input
                autoFocus
                className="case-overview-tag-input"
                size="small"
                value={tagInput}
                placeholder="Add tag"
                onBlur={addTag}
                onChange={(event) => setTagInput(event.target.value)}
                onPressEnter={(event) => event.currentTarget.blur()}
              />
            ) : (
              <Button
                aria-label="Add tag"
                className="case-overview-add-tag"
                icon={<PlusOutlined />}
                size="small"
                title="Add tag"
                type="text"
                onClick={() => setIsAddingTag(true)}
              />
            )}
          </div>
        </div>
      </section>

      <section className="case-overview-section">
        <ScenarioSectionHeader
          title="Scenario Steps"
          actions={<Button icon={<PlusOutlined />} onClick={onAddStep}>Add Step</Button>}
        />
        {steps.length ? (
          <div className="case-flow-sequence">
            {steps.map((step, index) => {
              const isExpanded = activeStepIndex === index
              const toggleStep = () => isExpanded ? onCollapseStep() : onSelectStep(index)
              return (
                <div
                  key={`overview-step-${index}`}
                  ref={(element) => {
                    if (element) {
                      stepItemRefs.current.set(index, element)
                    } else {
                      stepItemRefs.current.delete(index)
                    }
                  }}
                  className="case-flow-step-item"
                >
                  <div className={isExpanded ? 'case-flow-step-header expanded' : 'case-flow-step-header'}>
                    <button
                      type="button"
                      aria-expanded={isExpanded}
                      aria-controls={`scenario-step-editor-${index}`}
                      className="case-flow-step"
                      onClick={toggleStep}
                    >
                      <span className="flow-node-index">{String(index + 1).padStart(2, '0')}</span>
                      <span className={`method-pill method-${stepMethod(step).toLowerCase()}`}>{stepMethod(step)}</span>
                      <span className="case-flow-step-copy">
                        <span className="case-flow-step-primary">
                          <span className="case-flow-step-name">{operationPath(step)}</span>
                        </span>
                      </span>
                    </button>
                    <div className="case-flow-step-actions">
                      <Button
                        aria-label={`Move step ${index + 1} up`}
                        disabled={index === 0}
                        icon={<ArrowUpOutlined />}
                        size="small"
                        title="Move up"
                        type="text"
                        onClick={() => onMoveStep(index, index - 1)}
                      />
                      <Button
                        aria-label={`Move step ${index + 1} down`}
                        disabled={index === steps.length - 1}
                        icon={<ArrowDownOutlined />}
                        size="small"
                        title="Move down"
                        type="text"
                        onClick={() => onMoveStep(index, index + 1)}
                      />
                      <Button
                        aria-label={`Delete step ${index + 1}`}
                        danger
                        icon={<DeleteOutlined />}
                        size="small"
                        title="Delete step"
                        type="text"
                        onClick={() => onDeleteStep(index)}
                      />
                    </div>
                    <button
                      type="button"
                      aria-label={isExpanded ? `Collapse step ${index + 1}` : `Expand step ${index + 1}`}
                      aria-expanded={isExpanded}
                      aria-controls={`scenario-step-editor-${index}`}
                      className="case-flow-step-chevron"
                      title={isExpanded ? 'Collapse step' : 'Expand step'}
                      onClick={toggleStep}
                    >
                      {isExpanded ? <DownOutlined /> : <RightOutlined />}
                    </button>
                  </div>
                  {isExpanded && stepEditor ? (
                    <div id={`scenario-step-editor-${index}`} className="case-flow-step-expanded">{stepEditor}</div>
                  ) : null}
                </div>
              )
            })}
          </div>
        ) : (
          <div className="flow-empty-state">
            <Text strong>No flow steps yet.</Text>
            <Text type="secondary">Add a step to start composing this API scenario.</Text>
          </div>
        )}
      </section>

      <section className="case-overview-section">
        <ScenarioSectionHeader title="Description" />
        <StructuredEditor
          label=""
          rows={4}
          stringMode
          value={draft.description}
          onCommit={(value) => onChange({ ...draft, description: String(value ?? '') })}
        />
      </section>

      <section className="case-overview-section">
        <ScenarioSectionHeader title="Variables" />
        <StructuredEditor
          label=""
          value={dsl.variables ?? {}}
          onCommit={(value) => updateDsl((next) => {
            next.variables = value
          })}
        />
      </section>
    </div>
  )
}

function ScenarioSectionHeader({
  title,
  actions,
}: {
  title: string
  actions?: ReactNode
}) {
  return (
    <div className="scenario-section-header">
      <h3 className="scenario-section-title">{title}</h3>
      {actions ? <div className="scenario-section-actions">{actions}</div> : null}
    </div>
  )
}

function StepEditor({
  activeTab,
  afterReceiveActions,
  assertions,
  beforeSendActions,
  extractEntries,
  operations,
  selectedOperation,
  selectedStep,
  onTabChange,
  setSelectedOperation,
  updateSelectedStep,
}: {
  activeTab: string
  afterReceiveActions: CaseAction[]
  assertions: AssertionRule[]
  beforeSendActions: CaseAction[]
  extractEntries: Array<[string, ExtractRule]>
  operations: ApiOperationRecord[]
  selectedOperation: ApiOperationRecord | null
  selectedStep: any
  onTabChange: (nextTab: string) => void
  setSelectedOperation: (value?: string) => void
  updateSelectedStep: (recipe: (step: any) => void) => void
}) {
  return (
    <div className="step-editor-panel embedded-step-editor">
      <div className="request-compose-row">
        <div className="request-operation-composer">
          <span className={`request-method-segment method-${stepMethod(selectedStep).toLowerCase()}`}>
            {stepMethod(selectedStep)}
          </span>
          <Select
            showSearch
            allowClear
            className="request-operation-select"
            placeholder="Choose an API operation"
            optionFilterProp="label"
            variant="borderless"
            value={selectedOperation?.id}
            options={operations.map((operation) => ({
              label: `${operation.path} - ${operation.summary || operation.operationId || 'Operation'}`,
              value: operation.id,
            }))}
            onChange={setSelectedOperation}
          />
        </div>
      </div>

      <Tabs
        className="inspector-tabs"
        activeKey={activeTab}
        onChange={onTabChange}
        items={[
          {
            key: 'params',
            label: <span className="step-tab-label">Params</span>,
            children: (
              <div className="designer-band">
                <StructuredEditor
                  label="Query Params"
                  value={selectedStep.request?.query ?? {}}
                  onCommit={(value) => updateSelectedStep((step) => {
                    step.request = step.request ?? {}
                    step.request.query = value
                  })}
                />
              </div>
            ),
          },
          {
            key: 'headers',
            label: <span className="step-tab-label">Headers</span>,
            children: (
              <div className="designer-band">
                <StructuredEditor
                  label="Request Headers"
                  value={selectedStep.request?.headers ?? {}}
                  onCommit={(value) => updateSelectedStep((step) => {
                    step.request = step.request ?? {}
                    step.request.headers = value
                  })}
                />
              </div>
            ),
          },
          {
            key: 'body',
            label: <span className="step-tab-label">Body</span>,
            children: (
              <div className="designer-band">
                <StructuredEditor
                  label="Request Body"
                  value={selectedStep.request?.body ?? {}}
                  onCommit={(value) => updateSelectedStep((step) => {
                    step.request = step.request ?? {}
                    step.request.body = value
                  })}
                />
              </div>
            ),
          },
          {
            key: 'before',
            label: <span className="step-tab-label group-start">Before</span>,
            children: (
              <div className="designer-band">
                <ActionSection
                  title="Before Send"
                  description="Prepare the request before it is sent."
                  actions={beforeSendActions}
                  defaultAction={defaultBeforeSendAction}
                  onAdd={(action) => updateActionList(updateSelectedStep, 'beforeSend', (items) => {
                    items.push(action)
                  })}
                  onDelete={(index) => updateActionList(updateSelectedStep, 'beforeSend', (items) => {
                    items.splice(index, 1)
                  })}
                  onChange={(index, action) => updateActionList(updateSelectedStep, 'beforeSend', (items) => {
                    items[index] = action
                  })}
                />
              </div>
            ),
          },
          {
            key: 'after',
            label: <span className="step-tab-label">After</span>,
            children: (
              <div className="designer-band">
                <ActionSection
                  title="After Receive"
                  description="Process response data before assertions and extraction."
                  actions={afterReceiveActions}
                  defaultAction={defaultAfterReceiveAction}
                  onAdd={(action) => updateActionList(updateSelectedStep, 'afterReceive', (items) => {
                    items.push(action)
                  })}
                  onDelete={(index) => updateActionList(updateSelectedStep, 'afterReceive', (items) => {
                    items.splice(index, 1)
                  })}
                  onChange={(index, action) => updateActionList(updateSelectedStep, 'afterReceive', (items) => {
                    items[index] = action
                  })}
                />
              </div>
            ),
          },
          {
            key: 'assertions',
            label: <span className="step-tab-label">Tests</span>,
            children: (
              <div className="designer-band">
                <div className="panel-title-row compact">
                  <div>
                    <h4 className="panel-subtitle">Assertions</h4>
                    <Text type="secondary">Keep test verdicts separate from response processors.</Text>
                  </div>
                  <Button
                    icon={<PlusOutlined />}
                    onClick={() => updateSelectedStep((step) => {
                      step.response = step.response ?? {}
                      const rules = asAssertions(step.response.assertions)
                      rules.push({
                        source: 'response.body',
                        path: '$.code',
                        operator: 'equals',
                        expected: 'SUCCESS',
                      })
                      step.response.assertions = rules
                    })}
                  >
                    Add
                  </Button>
                </div>

                <div className="rule-stack">
                  {assertions.map((rule, index) => (
                    <RuleCard
                      key={`assertion-${index}`}
                      title={`Assertion ${index + 1}`}
                      onDelete={() => updateSelectedStep((step) => {
                        const rules = asAssertions(step.response.assertions)
                        rules.splice(index, 1)
                        step.response.assertions = rules
                      })}
                    >
                      <div className="designer-grid">
                        <LabeledField label="Source">
                          <Input
                            value={rule.source ?? 'response.body'}
                            onChange={(event) => updateAssertionRule(updateSelectedStep, index, 'source', event.target.value)}
                          />
                        </LabeledField>
                        <LabeledField label="JSONPath">
                          <Input
                            value={rule.path ?? ''}
                            onChange={(event) => updateAssertionRule(updateSelectedStep, index, 'path', event.target.value)}
                          />
                        </LabeledField>
                      </div>
                      <div className="designer-grid">
                        <LabeledField label="Operator">
                          <Select
                            value={rule.operator ?? 'equals'}
                            options={assertionOperators.map((value) => ({ label: value, value }))}
                            onChange={(value) => updateAssertionRule(updateSelectedStep, index, 'operator', value)}
                          />
                        </LabeledField>
                        <LabeledField label="Expected Value">
                          <Input
                            value={stringifyInlineValue(rule.expected)}
                            onChange={(event) => updateAssertionRule(
                              updateSelectedStep,
                              index,
                              'expected',
                              parseInlineValue(event.target.value),
                            )}
                          />
                        </LabeledField>
                      </div>
                    </RuleCard>
                  ))}
                </div>
              </div>
            ),
          },
          {
            key: 'extract',
            label: <span className="step-tab-label">Extract</span>,
            children: (
              <div className="designer-band">
                <div className="panel-title-row compact">
                  <div>
                    <h4 className="panel-subtitle">Extracted Variables</h4>
                    <Text type="secondary">Promote response values into scenario context for later steps.</Text>
                  </div>
                  <Button
                    icon={<PlusOutlined />}
                    onClick={() => updateSelectedStep((step) => {
                      step.response = step.response ?? {}
                      step.response.extract = step.response.extract ?? {}
                      step.response.extract[nextExtractName(step.response.extract)] = {
                        source: 'response.body',
                        path: '$.id',
                      }
                    })}
                  >
                    Add
                  </Button>
                </div>

                <div className="rule-stack">
                  {extractEntries.map(([name, rule], index) => (
                    <RuleCard
                      key={`extract-${name}-${index}`}
                      title={name}
                      onDelete={() => updateSelectedStep((step) => {
                        delete step.response.extract[name]
                      })}
                    >
                      <LabeledField label="Variable Name">
                        <Input
                          value={name}
                          onChange={(event) => renameExtract(updateSelectedStep, name, event.target.value)}
                        />
                      </LabeledField>
                      <div className="designer-grid">
                        <LabeledField label="Source">
                          <Input
                            value={rule.source ?? 'response.body'}
                            onChange={(event) => updateExtractRule(updateSelectedStep, name, 'source', event.target.value)}
                          />
                        </LabeledField>
                        <LabeledField label="JSONPath">
                          <Input
                            value={rule.path ?? ''}
                            onChange={(event) => updateExtractRule(updateSelectedStep, name, 'path', event.target.value)}
                          />
                        </LabeledField>
                      </div>
                    </RuleCard>
                  ))}
                </div>
              </div>
            ),
          },
        ]}
      />
    </div>
  )
}

function SchemaErrorPanel({
  title,
  errors,
  draft,
  onChange,
}: {
  title: string
  errors: string[]
  draft: SaveCaseInput
  onChange: (next: SaveCaseInput) => void
}) {
  return (
    <div className="designer-shell">
      <Alert
        type="error"
        showIcon
        message={title}
        description={(
          <ul className="schema-error-list">
            {errors.map((error) => (
              <li key={error}>{error}</li>
            ))}
          </ul>
        )}
      />
      <Paragraph type="secondary">
        This editor only supports Case DSL v1. Legacy fields such as top-level request, expect, extract, request.transform, and expect.decrypt are intentionally not migrated here.
      </Paragraph>
      <StructuredEditor
        label="Case YAML"
        value={draft.content}
        rows={18}
        onCommit={(value) => {
          onChange({ ...draft, content: typeof value === 'string' ? value : stringifyStructuredValue(value) })
        }}
        stringMode
      />
    </div>
  )
}

function ActionSection({
  title,
  description,
  actions,
  defaultAction,
  onAdd,
  onDelete,
  onChange,
}: {
  title: string
  description: string
  actions: CaseAction[]
  defaultAction: () => CaseAction
  onAdd: (action: CaseAction) => void
  onDelete: (index: number) => void
  onChange: (index: number, action: CaseAction) => void
}) {
  return (
    <section className="action-section">
      <div className="panel-title-row compact">
        <div>
          <h4 className="panel-subtitle">{title}</h4>
          <Text type="secondary">{description}</Text>
        </div>
        <Button icon={<PlusOutlined />} onClick={() => onAdd(defaultAction())}>
          Add Action
        </Button>
      </div>
      <div className="rule-stack">
        {actions.map((action, index) => (
          <RuleCard key={`${title}-${index}`} title={action.name || action.type || `Action ${index + 1}`} onDelete={() => onDelete(index)}>
            <div className="designer-grid narrow">
              <LabeledField label="Type">
                <Select
                  showSearch
                  value={action.type}
                  options={actionTypes.map((value) => ({ label: value, value }))}
                  onChange={(value) => onChange(index, { ...action, type: value })}
                />
              </LabeledField>
              <LabeledField label="Name">
                <Input
                  value={action.name ?? ''}
                  onChange={(event) => onChange(index, { ...action, name: event.target.value })}
                />
              </LabeledField>
              <LabeledField label="Profile">
                <Input
                  value={action.profile ?? ''}
                  onChange={(event) => onChange(index, { ...action, profile: event.target.value })}
                />
              </LabeledField>
            </div>
            <div className="designer-grid">
              <StructuredEditor
                label="Input"
                value={action.input ?? {}}
                onCommit={(value) => onChange(index, { ...action, input: value })}
              />
              <StructuredEditor
                label="Output"
                value={action.output ?? {}}
                onCommit={(value) => onChange(index, { ...action, output: value })}
              />
            </div>
          </RuleCard>
        ))}
      </div>
    </section>
  )
}

function StructuredEditor({
  label,
  value,
  onCommit,
  rows = 6,
  stringMode = false,
}: {
  label?: string
  value: unknown
  onCommit: (next: unknown) => void
  rows?: number
  stringMode?: boolean
}) {
  const [text, setText] = useState(stringMode && typeof value === 'string' ? value : stringifyStructuredValue(value))
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setText(stringMode && typeof value === 'string' ? value : stringifyStructuredValue(value))
    setError(null)
  }, [stringMode, value])

  return (
    <LabeledField label={label}>
      <Input.TextArea
        rows={rows}
        value={text}
        onChange={(event) => {
          const next = event.target.value
          setText(next)
          try {
            const parsed = stringMode ? next : parseStructuredText(next)
            onCommit(parsed)
            setError(null)
          } catch (commitError) {
            setError(commitError instanceof Error ? commitError.message : 'Invalid structured content')
          }
        }}
      />
      {error ? <Text type="danger">{error}</Text> : null}
    </LabeledField>
  )
}

function LabeledField({
  label,
  children,
}: {
  label?: string
  children: ReactNode
}) {
  return (
    <div className="designer-field">
      {label ? <Text className="designer-label">{label}</Text> : null}
      {children}
    </div>
  )
}

function RuleCard({
  title,
  children,
  onDelete,
}: {
  title: string
  children: ReactNode
  onDelete: () => void
}) {
  return (
    <section className="rule-card">
      <div className="panel-title-row compact">
        <Text strong>{title}</Text>
        <Button type="text" danger icon={<DeleteOutlined />} onClick={onDelete} />
      </div>
      {children}
    </section>
  )
}

function validateCaseDslV1(dsl: DslObject) {
  const errors: string[] = []
  if (dsl.dslVersion !== 'v1') {
    errors.push('dslVersion must be v1.')
  }
  if ('request' in dsl || 'expect' in dsl || 'extract' in dsl) {
    errors.push('Top-level request, expect, and extract are legacy fields. Use steps[].request and steps[].response.')
  }
  if (!Array.isArray(dsl.steps)) {
    errors.push('steps must be an array.')
    return errors
  }
  dsl.steps.forEach((step: any, index: number) => {
    const prefix = `steps[${index}]`
    if (!step || typeof step !== 'object' || Array.isArray(step)) {
      errors.push(`${prefix} must be an object.`)
      return
    }
    if (step.type !== 'http') {
      errors.push(`${prefix}.type must be http.`)
    }
    if (!step.operation || typeof step.operation !== 'object' || Array.isArray(step.operation)) {
      errors.push(`${prefix}.operation is required.`)
    }
    if (!step.request || typeof step.request !== 'object' || Array.isArray(step.request)) {
      errors.push(`${prefix}.request is required.`)
    }
    if (!step.response || typeof step.response !== 'object' || Array.isArray(step.response)) {
      errors.push(`${prefix}.response is required.`)
    }
    if ('expect' in step || 'extract' in step || 'profile' in step || 'continueOnFailure' in step) {
      errors.push(`${prefix} contains legacy fields. Use runtime, actions, and response instead.`)
    }
    if (step.request && ('method' in step.request || 'path' in step.request || 'baseUrl' in step.request || 'transform' in step.request)) {
      errors.push(`${prefix}.request contains legacy method/path/baseUrl/transform fields. Use operation and actions.beforeSend.`)
    }
    if (step.actions && typeof step.actions === 'object') {
      if (step.actions.beforeSend && !Array.isArray(step.actions.beforeSend)) {
        errors.push(`${prefix}.actions.beforeSend must be an array.`)
      }
      if (step.actions.afterReceive && !Array.isArray(step.actions.afterReceive)) {
        errors.push(`${prefix}.actions.afterReceive must be an array.`)
      }
    }
    if (step.response?.assertions && !Array.isArray(step.response.assertions)) {
      errors.push(`${prefix}.response.assertions must be an array.`)
    }
    if (step.response?.extract && (typeof step.response.extract !== 'object' || Array.isArray(step.response.extract))) {
      errors.push(`${prefix}.response.extract must be an object.`)
    }
  })
  return errors
}

function resolveOperation(step: any, operations: ApiOperationRecord[]) {
  const operationId = step?.operation?.id
  if (operationId) {
    return operations.find((item) => item.id === operationId) ?? null
  }
  const fallbackOperationId = step?.operation?.operationId
  if (fallbackOperationId) {
    return operations.find((item) => item.operationId === fallbackOperationId) ?? null
  }
  return null
}

function stepMethod(step: any) {
  return String(step?.operation?.method ?? 'HTTP').toUpperCase()
}

function operationPath(step: any) {
  return String(step?.operation?.path ?? 'Select operation')
}

function indexAfterMove(activeIndex: number, fromIndex: number, toIndex: number) {
  if (activeIndex === fromIndex) {
    return toIndex
  }
  if (fromIndex < toIndex && activeIndex > fromIndex && activeIndex <= toIndex) {
    return activeIndex - 1
  }
  if (toIndex < fromIndex && activeIndex >= toIndex && activeIndex < fromIndex) {
    return activeIndex + 1
  }
  return activeIndex
}

function asActions(value: unknown): CaseAction[] {
  return Array.isArray(value) ? value : []
}

function asAssertions(value: unknown): AssertionRule[] {
  return Array.isArray(value) ? value : []
}

function asExtractEntries(value: unknown): Array<[string, ExtractRule]> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return []
  }
  return Object.entries(value as Record<string, ExtractRule>)
}

function updateActionList(
  updateSelectedStep: (recipe: (step: any) => void) => void,
  stage: 'beforeSend' | 'afterReceive',
  recipe: (actions: CaseAction[]) => void,
) {
  updateSelectedStep((step) => {
    step.actions = step.actions ?? {}
    const actions = asActions(step.actions[stage])
    recipe(actions)
    step.actions[stage] = actions
  })
}

function updateAssertionRule(
  updateSelectedStep: (recipe: (step: any) => void) => void,
  index: number,
  key: keyof AssertionRule,
  value: unknown,
) {
  updateSelectedStep((step) => {
    step.response = step.response ?? {}
    const rules = asAssertions(step.response.assertions)
    rules[index] = {
      ...rules[index],
      [key]: value,
    }
    step.response.assertions = rules
  })
}

function updateExtractRule(
  updateSelectedStep: (recipe: (step: any) => void) => void,
  name: string,
  key: keyof ExtractRule,
  value: unknown,
) {
  updateSelectedStep((step) => {
    step.response = step.response ?? {}
    step.response.extract = step.response.extract ?? {}
    step.response.extract[name] = {
      ...step.response.extract[name],
      [key]: value,
    }
  })
}

function renameExtract(
  updateSelectedStep: (recipe: (step: any) => void) => void,
  oldName: string,
  newName: string,
) {
  updateSelectedStep((step) => {
    const trimmed = newName.trim()
    if (!trimmed || trimmed === oldName) {
      return
    }
    step.response = step.response ?? {}
    step.response.extract = step.response.extract ?? {}
    step.response.extract[trimmed] = step.response.extract[oldName]
    delete step.response.extract[oldName]
  })
}

function nextExtractName(extract: Record<string, unknown>) {
  const base = 'extractedValue'
  if (!extract[base]) {
    return base
  }
  let index = 2
  while (extract[`${base}${index}`]) {
    index += 1
  }
  return `${base}${index}`
}

function defaultBeforeSendAction(): CaseAction {
  return {
    type: 'crypto.encrypt',
    name: 'Encrypt request body',
    input: {
      source: 'request.body',
      format: 'json',
    },
    output: {
      target: 'request.body',
      mode: 'jsonField',
      field: 'payload',
    },
  }
}

function defaultAfterReceiveAction(): CaseAction {
  return {
    type: 'crypto.decrypt',
    name: 'Decrypt response payload',
    input: {
      source: 'response.body',
      path: '$.payload',
    },
    output: {
      target: 'decrypted.securePayload',
      format: 'json',
    },
  }
}

function parseInlineValue(value: string) {
  const trimmed = value.trim()
  if (!trimmed) {
    return ''
  }
  try {
    return parseStructuredText(trimmed)
  } catch {
    return value
  }
}

function stringifyInlineValue(value: unknown) {
  if (typeof value === 'string') {
    return value
  }
  if (value === undefined || value === null) {
    return ''
  }
  return stringifyStructuredValue(value)
}
