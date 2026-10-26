import {
  Alert,
  App,
  Button,
  Dropdown,
  Input,
  InputNumber,
  Segmented,
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
  ExclamationCircleOutlined,
  PlusOutlined,
  RightOutlined,
} from '@ant-design/icons'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { ReactNode } from 'react'
import type { ApiOperationRecord, SaveCaseInput } from '../types/platform'
import { JsonObjectEditor } from './JsonObjectEditor'
import { OperationActionFlow } from './OperationActionFlow'
import {
  parseDsl,
  parseStructuredText,
  stringifyDsl,
  stringifyStructuredValue,
  type DslObject,
} from '../utils/caseDsl'
import {
  issuesForPath,
  issuesForStep,
  validateCaseDslV1,
  type DslValidationIssue,
} from '../utils/caseDslValidation'

const { Text } = Typography

type CaseDesignerProps = {
  draft: SaveCaseInput
  onChange: (next: SaveCaseInput) => void
  operations: ApiOperationRecord[]
  view: 'case' | 'step'
  activeStepIndex: number
  onSelectCaseOverview: () => void
  onSelectStep: (stepIndex: number) => void
  onOpenRaw: () => void
  onActiveStepChange?: (stepIndex: number) => void
  onVariablesValidationChange?: (error: string | null) => void
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

type HttpStatusOperator = 'equals' | 'oneOf' | 'range'

type HttpStatusRule = {
  operator?: HttpStatusOperator
  expected?: unknown
  min?: number
  max?: number
}

type CaseAction = {
  type?: string
  name?: string
  enabled?: boolean
  input?: unknown
  output?: unknown
  template?: unknown
}

type ActionTypeOption = {
  value: string
  label: string
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

const beforeSendActionTypes: ActionTypeOption[] = [
  { value: 'crypto.pgp.encrypt', label: 'PGP Encrypt' },
  { value: 'crypto.pgp.signAndEncrypt', label: 'PGP Sign & Encrypt' },
  { value: 'data.encodeBase64', label: 'Encode Base64' },
  { value: 'body.buildJson', label: 'Build JSON Body' },
]

const afterReceiveActionTypes: ActionTypeOption[] = [
  { value: 'crypto.pgp.decrypt', label: 'PGP Decrypt' },
  { value: 'crypto.pgp.decryptAndVerify', label: 'PGP Decrypt & Verify' },
]

const emptyStep = () => ({
  name: 'New API step',
  type: 'http',
  operation: {},
  runtime: {},
  request: {
    path: {},
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
    status: {
      operator: 'oneOf',
      expected: [200],
    },
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
  onOpenRaw,
  onActiveStepChange,
  onVariablesValidationChange,
}: CaseDesignerProps) {
  const { modal } = App.useApp()
  const { t } = useTranslation(['common', 'scenario'])
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
  const validationIssues = useMemo(() => (dsl ? validateCaseDslV1(dsl) : []), [dsl])

  if (parsed.error || !dsl) {
    return (
      <section className="case-workbench scenario-flow-designer">
        <SchemaErrorPanel
          title={t('scenario:editor.caseYamlInvalid')}
          errors={[parsed.error ?? 'Case DSL root must be an object.']}
          onOpenRaw={onOpenRaw}
        />
      </section>
    )
  }

  const steps = Array.isArray(dsl.steps) ? dsl.steps : []
  const selectedStep = steps[activeStepIndex]
  const selectedStepIsObject = isRecord(selectedStep)
  const selectedOperation = resolveOperation(selectedStep, operations)

  const updateDsl = (recipe: (next: DslObject) => void) => {
    const clone = structuredClone(dsl)
    recipe(clone)
    onChange({ ...draft, content: stringifyDsl(clone) })
  }

  const updateSelectedStep = (recipe: (step: any) => void) => {
    updateDsl((next) => {
      next.steps = Array.isArray(next.steps) ? next.steps : []
      if (!isRecord(next.steps[activeStepIndex])) {
        next.steps[activeStepIndex] = emptyStep()
      }
      recipe(next.steps[activeStepIndex])
    })
  }

  const addStep = () => {
    updateDsl((next) => {
      next.steps = Array.isArray(next.steps) ? next.steps : []
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
      title: t('scenario:editor.deleteStepConfirm'),
      content: t('scenario:editor.deleteStepDescription', {
        method: stepMethod(step),
        path: operationPath(step),
      }),
      okText: t('common:actions.delete'),
      okButtonProps: { danger: true },
      cancelText: t('common:actions.cancel'),
      onOk: () => removeStep(stepIndex),
    })
  }

  return (
    <section className="case-workbench scenario-flow-designer">
      <CaseOverview
        activeStepIndex={view === 'step' && selectedStep ? activeStepIndex : undefined}
        draft={draft}
        dsl={dsl}
        issues={validationIssues}
        steps={steps}
        stepEditor={view === 'step' && selectedStep && selectedStepIsObject ? (
          <StepEditor
            activeTab={activeTab}
            afterReceiveActions={asActions(selectedStep?.actions?.afterReceive)}
            assertions={asAssertions(selectedStep?.response?.assertions)}
            beforeSendActions={asActions(selectedStep?.actions?.beforeSend)}
            extractEntries={asExtractEntries(selectedStep?.response?.extract)}
            operations={operations}
            selectedOperation={selectedOperation}
            selectedStep={selectedStep}
            stepIndex={activeStepIndex}
            issues={validationIssues}
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
        ) : view === 'step' && selectedStep ? (
          <UnsupportedConfiguration
            issues={issuesForStep(validationIssues, activeStepIndex)}
            onOpenRaw={onOpenRaw}
          />
        ) : null}
        onAddStep={addStep}
        onChange={onChange}
        onCollapseStep={onSelectCaseOverview}
        onDeleteStep={confirmDeleteStep}
        onMoveStep={moveStep}
        onSelectStep={onSelectStep}
        onOpenRaw={onOpenRaw}
        onVariablesValidationChange={onVariablesValidationChange}
        updateDsl={updateDsl}
      />
    </section>
  )
}

function CaseOverview({
  activeStepIndex,
  draft,
  dsl,
  issues,
  steps,
  stepEditor,
  onAddStep,
  onChange,
  onCollapseStep,
  onDeleteStep,
  onMoveStep,
  onSelectStep,
  onOpenRaw,
  onVariablesValidationChange,
  updateDsl,
}: {
  activeStepIndex?: number
  draft: SaveCaseInput
  dsl: DslObject
  issues: DslValidationIssue[]
  steps: any[]
  stepEditor: ReactNode
  onAddStep: () => void
  onChange: (next: SaveCaseInput) => void
  onCollapseStep: () => void
  onDeleteStep: (stepIndex: number) => void
  onMoveStep: (fromIndex: number, toIndex: number) => void
  onSelectStep: (stepIndex: number) => void
  onOpenRaw: () => void
  onVariablesValidationChange?: (error: string | null) => void
  updateDsl: (recipe: (next: DslObject) => void) => void
}) {
  const { t } = useTranslation(['common', 'scenario'])
  const [isAddingTag, setIsAddingTag] = useState(false)
  const [tagInput, setTagInput] = useState('')
  const stepItemRefs = useRef(new Map<number, HTMLDivElement>())
  const tags = draft.tags ?? []
  const tagsError = issues.find((issue) => issue.path === '$.tags')?.message
  const descriptionError = issues.find((issue) => issue.path === '$.description')?.message

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
    if (nextTag && !tags.includes(nextTag)) {
      updateScenarioMetadata({ tags: [...tags, nextTag] }, (next) => {
        next.tags = [...tags, nextTag]
      })
    }
    setTagInput('')
    setIsAddingTag(false)
  }

  const updateScenarioMetadata = (
    patch: Partial<SaveCaseInput>,
    recipe: (next: DslObject) => void,
  ) => {
    const nextDsl = structuredClone(dsl)
    recipe(nextDsl)
    onChange({ ...draft, ...patch, content: stringifyDsl(nextDsl) })
  }

  return (
    <div className="case-overview-panel">
      {issues.length ? (
        <ScenarioValidationSummary issues={issues} onOpenRaw={onOpenRaw} />
      ) : null}
      <section className="case-overview-section">
        <ScenarioSectionHeader title={t('common:fields.tags')} />
        <div className="case-overview-tags">
          <div className="case-overview-tags-list">
            {tags.map((tag) => (
              <Tag
                key={tag}
                closable
                className="case-overview-tag"
                onClose={(event) => {
                  event.preventDefault()
                  const nextTags = tags.filter((value) => value !== tag)
                  updateScenarioMetadata({ tags: nextTags }, (next) => {
                    next.tags = nextTags
                  })
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
                placeholder={t('scenario:actions.addTag')}
                onBlur={addTag}
                onChange={(event) => setTagInput(event.target.value)}
                onPressEnter={(event) => event.currentTarget.blur()}
              />
            ) : (
              <Button
                aria-label={t('scenario:actions.addTag')}
                className="case-overview-add-tag"
                icon={<PlusOutlined />}
                size="small"
                title={t('scenario:actions.addTag')}
                type="text"
                onClick={() => setIsAddingTag(true)}
              />
            )}
          </div>
          <FieldValidationMessage message={tagsError} />
        </div>
      </section>

      <section className="case-overview-section">
        <ScenarioSectionHeader
          title={t('scenario:steps')}
          actions={<Button icon={<PlusOutlined />} onClick={onAddStep}>{t('scenario:addStep')}</Button>}
        />
        {steps.length ? (
          <div className="case-flow-sequence">
            {steps.map((step, index) => {
              const isExpanded = activeStepIndex === index
              const stepIssues = issuesForStep(issues, index)
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
                          {stepIssues.length ? (
                            <span className="case-flow-step-validation" title={stepIssues[0].message}>
                              <ExclamationCircleOutlined /> {stepIssues.length}
                            </span>
                          ) : null}
                        </span>
                      </span>
                    </button>
                    <div className="case-flow-step-actions">
                      <Button
                        aria-label={t('scenario:editor.moveStepUp', { number: index + 1 })}
                        disabled={index === 0}
                        icon={<ArrowUpOutlined />}
                        size="small"
                        title={t('scenario:editor.moveStepUp', { number: index + 1 })}
                        type="text"
                        onClick={() => onMoveStep(index, index - 1)}
                      />
                      <Button
                        aria-label={t('scenario:editor.moveStepDown', { number: index + 1 })}
                        disabled={index === steps.length - 1}
                        icon={<ArrowDownOutlined />}
                        size="small"
                        title={t('scenario:editor.moveStepDown', { number: index + 1 })}
                        type="text"
                        onClick={() => onMoveStep(index, index + 1)}
                      />
                      <Button
                        aria-label={t('scenario:editor.deleteStep')}
                        danger
                        icon={<DeleteOutlined />}
                        size="small"
                        title={t('scenario:editor.deleteStep')}
                        type="text"
                        onClick={() => onDeleteStep(index)}
                      />
                    </div>
                    <button
                      type="button"
                      aria-label={isExpanded
                        ? t('scenario:editor.collapseStep', { number: index + 1 })
                        : t('scenario:editor.expandStep', { number: index + 1 })}
                      aria-expanded={isExpanded}
                      aria-controls={`scenario-step-editor-${index}`}
                      className="case-flow-step-chevron"
                      title={isExpanded
                        ? t('scenario:editor.collapseStep', { number: index + 1 })
                        : t('scenario:editor.expandStep', { number: index + 1 })}
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
            <Text strong>{t('scenario:editor.emptySteps')}</Text>
            <Text type="secondary">{t('scenario:editor.emptyStepsDescription')}</Text>
          </div>
        )}
      </section>

      <section className="case-overview-section">
        <ScenarioSectionHeader title={t('common:fields.description')} />
        <StructuredEditor
          label=""
          rows={4}
          stringMode
          value={draft.description}
          externalError={descriptionError}
          onCommit={(value) => {
            const description = String(value ?? '')
            updateScenarioMetadata({ description }, (next) => {
              next.description = description
            })
          }}
        />
      </section>

      <section className="case-overview-section">
        <ScenarioSectionHeader title={t('common:fields.variables')} />
        <JsonObjectEditor
          ariaLabel={t('scenario:editor.variablesJsonEditor')}
          value={dsl.variables ?? {}}
          onValidationChange={onVariablesValidationChange}
          onCommit={(value) => updateDsl((next) => {
            next.variables = value
          })}
        />
      </section>

      {issues.some((issue) => !issue.mapped) ? (
        <UnsupportedIssuesSection
          issues={issues.filter((issue) => !issue.mapped)}
          onOpenRaw={onOpenRaw}
        />
      ) : null}

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
  stepIndex,
  issues,
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
  stepIndex: number
  issues: DslValidationIssue[]
  onTabChange: (nextTab: string) => void
  setSelectedOperation: (value?: string) => void
  updateSelectedStep: (recipe: (step: any) => void) => void
}) {
  const { t } = useTranslation(['common', 'scenario'])
  const responseSourceOptions = availableResponseDataSources(afterReceiveActions)
  const stepPath = `$.steps[${stepIndex}]`
  const stepIssues = issuesForStep(issues, stepIndex)
  const fieldError = (path: string) => stepIssues.find((issue) => issue.path === path)?.message
  const sectionIssueCount = (...sections: Array<DslValidationIssue['section']>) => stepIssues
    .filter((issue) => issue.section && sections.includes(issue.section)).length
  const requestError = fieldError(`${stepPath}.request`)
  const operationError = fieldError(`${stepPath}.operation`)
  return (
    <div className="step-editor-panel embedded-step-editor">
      <div className="request-compose-row">
        <div className="request-operation-block">
          <div className="request-operation-composer">
            <span className={`request-method-segment method-${stepMethod(selectedStep).toLowerCase()}`}>
              {stepMethod(selectedStep)}
            </span>
            <Select
              showSearch
              allowClear
              className="request-operation-select"
              placeholder={t('scenario:editor.chooseOperation')}
              optionFilterProp="label"
              status={operationError ? 'error' : undefined}
              variant="borderless"
              value={selectedOperation?.id}
              options={operations.map((operation) => ({
                label: `${operation.path} - ${operation.summary || operation.operationId || t('scenario:editor.operation')}`,
                value: operation.id,
              }))}
              onChange={setSelectedOperation}
            />
          </div>
          {operationError ? <FieldValidationMessage message={operationError} /> : null}
        </div>
      </div>

      <Tabs
        className="inspector-tabs"
        activeKey={activeTab}
        onChange={onTabChange}
        items={[
          {
            key: 'params',
            label: <ValidationTabLabel label={t('scenario:editor.params')} count={sectionIssueCount('request', 'params')} />,
            children: (
              <div className="designer-band">
                <StructuredEditor
                  label={t('scenario:editor.pathParams')}
                  value={selectedStep.request?.path ?? {}}
                  rows={4}
                  externalError={requestError || fieldError(`${stepPath}.request.path`)}
                  onCommit={(value) => updateSelectedStep((step) => {
                    step.request = isRecord(step.request) ? step.request : {}
                    step.request.path = value
                  })}
                />
                <StructuredEditor
                  label={t('scenario:editor.queryParams')}
                  value={selectedStep.request?.query ?? {}}
                  rows={4}
                  externalError={requestError || fieldError(`${stepPath}.request.query`)}
                  onCommit={(value) => updateSelectedStep((step) => {
                    step.request = isRecord(step.request) ? step.request : {}
                    step.request.query = value
                  })}
                />
              </div>
            ),
          },
          {
            key: 'headers',
            label: <ValidationTabLabel label={t('scenario:editor.headers')} count={sectionIssueCount('request', 'headers')} />,
            children: (
              <div className="designer-band">
                <StructuredEditor
                  label={t('scenario:editor.requestHeaders')}
                  value={selectedStep.request?.headers ?? {}}
                  externalError={requestError || fieldError(`${stepPath}.request.headers`)}
                  onCommit={(value) => updateSelectedStep((step) => {
                    step.request = isRecord(step.request) ? step.request : {}
                    step.request.headers = value
                  })}
                />
              </div>
            ),
          },
          {
            key: 'body',
            label: <ValidationTabLabel label={t('scenario:editor.body')} count={sectionIssueCount('request', 'body')} />,
            children: (
              <div className="designer-band">
                <StructuredEditor
                  label={t('scenario:editor.requestBody')}
                  value={selectedStep.request?.body ?? {}}
                  externalError={requestError || fieldError(`${stepPath}.request.body`)}
                  onCommit={(value) => updateSelectedStep((step) => {
                    step.request = isRecord(step.request) ? step.request : {}
                    step.request.body = value
                  })}
                />
              </div>
            ),
          },
          {
            key: 'before',
            label: <ValidationTabLabel className="group-start" label={t('scenario:editor.before')} count={sectionIssueCount('before')} />,
            children: (
              <div className="designer-band">
                <OperationActionFlow operation={selectedOperation} direction="before" mode={selectedStep.processingMode ?? 'automatic'}
                  onModeChange={(mode) => updateSelectedStep((step) => { step.processingMode = mode })}>
                  {({ inherited, managesCrypto }) => <>
                    {managesCrypto && [...beforeSendActions, ...asActions(selectedStep?.actions?.afterRequestProcessing)].some(isEnabledCryptoAction)
                      ? <Alert type="warning" showIcon title={t('scenario:editor.actionFlow.duplicateCrypto')} /> : null}
                    <ActionSection
                      title={t(selectedStep.processingMode === 'wire' ? 'scenario:editor.actionFlow.wireBeforeActions' : 'scenario:editor.actionFlow.businessActions')}
                      description={t(selectedStep.processingMode === 'wire' ? 'scenario:editor.actionFlow.wireBeforeHelp' : 'scenario:editor.actionFlow.businessHelp')}
                      actions={beforeSendActions}
                      issues={stepIssues.filter((issue) => issue.section === 'before')}
                      path={`${stepPath}.actions.beforeSend`}
                      availableTypes={managesCrypto ? beforeSendActionTypes.filter((item) => !item.value.startsWith('crypto.pgp.')) : beforeSendActionTypes}
                      onAdd={(action) => updateActionList(updateSelectedStep, 'beforeSend', (items) => {
                        items.push(action)
                      })}
                      onDelete={(index) => updateActionList(updateSelectedStep, 'beforeSend', (items) => {
                        items.splice(index, 1)
                      })}
                      onChange={(index, action) => updateActionList(updateSelectedStep, 'beforeSend', (items) => {
                        items[index] = action
                      })}
                      onMove={(index, direction) => updateActionList(updateSelectedStep, 'beforeSend', (items) => {
                        moveItem(items, index, index + direction)
                      })}
                    />
                    {inherited}
                    <ActionSection
                      title={t('scenario:editor.actionFlow.wireActions')}
                      description={t('scenario:editor.actionFlow.wireActionsHelp')}
                      actions={asActions(selectedStep?.actions?.afterRequestProcessing)}
                      issues={stepIssues.filter((issue) => issue.path.includes('afterRequestProcessing'))}
                      path={`${stepPath}.actions.afterRequestProcessing`}
                      availableTypes={managesCrypto ? beforeSendActionTypes.filter((item) => !item.value.startsWith('crypto.pgp.')) : beforeSendActionTypes}
                      onAdd={(action) => updateActionList(updateSelectedStep, 'afterRequestProcessing', (items) => { items.push(action) })}
                      onDelete={(index) => updateActionList(updateSelectedStep, 'afterRequestProcessing', (items) => { items.splice(index, 1) })}
                      onChange={(index, action) => updateActionList(updateSelectedStep, 'afterRequestProcessing', (items) => { items[index] = action })}
                      onMove={(index, direction) => updateActionList(updateSelectedStep, 'afterRequestProcessing', (items) => {
                        const [item] = items.splice(index, 1); items.splice(index + direction, 0, item)
                      })}
                    />
                  </>}
                </OperationActionFlow>
              </div>
            ),
          },
          {
            key: 'after',
            label: <ValidationTabLabel label={t('scenario:editor.after')} count={sectionIssueCount('after')} />,
            children: (
              <div className="designer-band">
                <OperationActionFlow operation={selectedOperation} direction="after" mode={selectedStep.processingMode ?? 'automatic'}
                  onModeChange={(mode) => updateSelectedStep((step) => { step.processingMode = mode })}>
                  {({ inherited, managesCrypto }) => <>
                    {inherited}
                    {managesCrypto && afterReceiveActions.some(isEnabledCryptoAction)
                      ? <Alert type="warning" showIcon title={t('scenario:editor.actionFlow.duplicateCrypto')} /> : null}
                    <ActionSection
                      title={t('scenario:editor.afterReceive')}
                      description={t('scenario:editor.actionFlow.afterHelp')}
                      actions={afterReceiveActions}
                      issues={stepIssues.filter((issue) => issue.section === 'after')}
                      path={`${stepPath}.actions.afterReceive`}
                      availableTypes={managesCrypto ? afterReceiveActionTypes.filter((item) => !item.value.startsWith('crypto.pgp.')) : afterReceiveActionTypes}
                      onAdd={(action) => updateActionList(updateSelectedStep, 'afterReceive', (items) => {
                        items.push(action)
                      })}
                      onDelete={(index) => updateActionList(updateSelectedStep, 'afterReceive', (items) => {
                        items.splice(index, 1)
                      })}
                      onChange={(index, action) => updateActionList(updateSelectedStep, 'afterReceive', (items) => {
                        items[index] = action
                      })}
                      onMove={(index, direction) => updateActionList(updateSelectedStep, 'afterReceive', (items) => {
                        moveItem(items, index, index + direction)
                      })}
                    />
                  </>}
                </OperationActionFlow>
              </div>
            ),
          },
          {
            key: 'assertions',
            label: <ValidationTabLabel label={t('scenario:editor.tests')} count={sectionIssueCount('assertions')} />,
            children: (
              <div className="designer-band">
                <div className="http-status-test">
                  <div className="http-status-test-copy">
                    <Text strong>{t('scenario:editor.httpStatus')}</Text>
                    <Text type="secondary">{t('scenario:editor.httpStatusDescription')}</Text>
                  </div>
                  <HttpStatusEditor
                    value={selectedStep.response?.status}
                    error={fieldError(`${stepPath}.response.status`)}
                    fieldError={fieldError}
                    path={`${stepPath}.response.status`}
                    onChange={(value) => updateSelectedStep((step) => {
                      step.response = isRecord(step.response) ? step.response : {}
                      step.response.status = value
                    })}
                  />
                </div>

                <div className="panel-title-row compact">
                  <div>
                    <h4 className="panel-subtitle">{t('scenario:editor.assertions')}</h4>
                    <Text type="secondary">{t('scenario:editor.assertionsDescription')}</Text>
                  </div>
                  <Button
                    icon={<PlusOutlined />}
                    onClick={() => updateSelectedStep((step) => {
                      step.response = isRecord(step.response) ? step.response : {}
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
                    {t('common:actions.add')}
                  </Button>
                </div>
                <FieldValidationMessage message={fieldError(`${stepPath}.response.assertions`)} />

                <div className="rule-stack">
                  {assertions.map((rule, index) => isRecord(rule) ? (
                    <RuleCard
                      key={`assertion-${index}`}
                      title={t('scenario:editor.assertion', { number: index + 1 })}
                      onDelete={() => updateSelectedStep((step) => {
                        const rules = asAssertions(isRecord(step.response) ? step.response.assertions : undefined)
                        rules.splice(index, 1)
                        step.response = isRecord(step.response) ? step.response : {}
                        step.response.assertions = rules
                      })}
                    >
                      <div className="designer-grid">
                        <LabeledField label={t('scenario:editor.source')}>
                          <Select
                            value={rule.source ?? 'response.body'}
                            options={includeCurrentOption(responseSourceOptions, rule.source)}
                            status={fieldError(`${stepPath}.response.assertions[${index}].source`) ? 'error' : undefined}
                            onChange={(value) => updateAssertionRule(updateSelectedStep, index, 'source', value)}
                          />
                          <FieldValidationMessage message={fieldError(`${stepPath}.response.assertions[${index}].source`)} />
                        </LabeledField>
                        <LabeledField label="JSONPath">
                          <Input
                            status={fieldError(`${stepPath}.response.assertions[${index}].path`) ? 'error' : undefined}
                            value={rule.path ?? ''}
                            onChange={(event) => updateAssertionRule(updateSelectedStep, index, 'path', event.target.value)}
                          />
                          <Text type="secondary">{t('scenario:editor.jsonPathHelp')}</Text>
                          <FieldValidationMessage message={fieldError(`${stepPath}.response.assertions[${index}].path`)} />
                        </LabeledField>
                      </div>
                      <div className="designer-grid">
                        <LabeledField label={t('scenario:editor.operator')}>
                          <Select
                            value={rule.operator ?? 'equals'}
                            options={includeCurrentOption(
                              assertionOperators.map((value) => ({ label: value, value })),
                              rule.operator,
                            )}
                            status={fieldError(`${stepPath}.response.assertions[${index}].operator`) ? 'error' : undefined}
                            onChange={(value) => updateAssertionRule(updateSelectedStep, index, 'operator', value)}
                          />
                          <FieldValidationMessage message={fieldError(`${stepPath}.response.assertions[${index}].operator`)} />
                        </LabeledField>
                        <LabeledField label={t('scenario:editor.expectedValue')}>
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
                  ) : (
                    <InvalidRuleCard
                      key={`assertion-${index}`}
                      title={t('scenario:editor.assertion', { number: index + 1 })}
                      issues={issuesForPath(stepIssues, `${stepPath}.response.assertions[${index}]`)}
                      onDelete={() => updateSelectedStep((step) => {
                        const rules = asAssertions(step.response?.assertions)
                        rules.splice(index, 1)
                        step.response = isRecord(step.response) ? step.response : {}
                        step.response.assertions = rules
                      })}
                    />
                  ))}
                </div>
              </div>
            ),
          },
          {
            key: 'extract',
            label: <ValidationTabLabel label={t('scenario:editor.extract')} count={sectionIssueCount('extract')} />,
            children: (
              <div className="designer-band">
                <div className="panel-title-row compact">
                  <div>
                    <h4 className="panel-subtitle">{t('scenario:editor.extractedVariables')}</h4>
                    <Text type="secondary">{t('scenario:editor.extractedVariablesDescription')}</Text>
                  </div>
                  <Button
                    icon={<PlusOutlined />}
                    onClick={() => updateSelectedStep((step) => {
                      step.response = isRecord(step.response) ? step.response : {}
                      step.response.extract = isRecord(step.response.extract) ? step.response.extract : {}
                      step.response.extract[nextExtractName(step.response.extract)] = {
                        source: 'response.body',
                        path: '$.id',
                      }
                    })}
                  >
                    {t('common:actions.add')}
                  </Button>
                </div>
                <FieldValidationMessage message={fieldError(`${stepPath}.response.extract`)} />

                <div className="rule-stack">
                  {extractEntries.map(([name, rule], index) => isRecord(rule) ? (
                    <RuleCard
                      key={`extract-${name}-${index}`}
                      title={name}
                      onDelete={() => updateSelectedStep((step) => {
                        if (isRecord(step.response) && isRecord(step.response.extract)) {
                          delete step.response.extract[name]
                        }
                      })}
                    >
                      <LabeledField label={t('scenario:editor.variableName')}>
                        <Input
                          value={name}
                          onChange={(event) => renameExtract(updateSelectedStep, name, event.target.value)}
                        />
                      </LabeledField>
                      <div className="designer-grid">
                        <LabeledField label={t('scenario:editor.source')}>
                          <Select
                            value={rule.source ?? 'response.body'}
                            options={includeCurrentOption(responseSourceOptions, rule.source)}
                            status={fieldError(`${stepPath}.response.extract.${name}.source`) ? 'error' : undefined}
                            onChange={(value) => updateExtractRule(updateSelectedStep, name, 'source', value)}
                          />
                          <FieldValidationMessage message={fieldError(`${stepPath}.response.extract.${name}.source`)} />
                        </LabeledField>
                        <LabeledField label="JSONPath">
                          <Input
                            status={fieldError(`${stepPath}.response.extract.${name}.path`) ? 'error' : undefined}
                            value={rule.path ?? ''}
                            onChange={(event) => updateExtractRule(updateSelectedStep, name, 'path', event.target.value)}
                          />
                          <Text type="secondary">{t('scenario:editor.jsonPathHelp')}</Text>
                          <FieldValidationMessage message={fieldError(`${stepPath}.response.extract.${name}.path`)} />
                        </LabeledField>
                      </div>
                    </RuleCard>
                  ) : (
                    <InvalidRuleCard
                      key={`extract-${name}-${index}`}
                      title={name}
                      issues={issuesForPath(stepIssues, `${stepPath}.response.extract.${name}`)}
                      onDelete={() => updateSelectedStep((step) => {
                        if (isRecord(step.response) && isRecord(step.response.extract)) {
                          delete step.response.extract[name]
                        }
                      })}
                    />
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
  onOpenRaw,
}: {
  title: string
  errors: string[]
  onOpenRaw: () => void
}) {
  const { t } = useTranslation(['scenario'])
  return (
    <div className="designer-shell schema-error-shell">
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
        action={<Button onClick={onOpenRaw}>{t('scenario:openRaw')}</Button>}
      />
    </div>
  )
}

function ScenarioValidationSummary({
  issues,
  onOpenRaw,
}: {
  issues: DslValidationIssue[]
  onOpenRaw: () => void
}) {
  const { t } = useTranslation(['scenario'])
  return (
    <div className="scenario-validation-summary">
      <span><ExclamationCircleOutlined /> {t('scenario:validation.issueCount', { count: issues.length })}</span>
      <Button size="small" type="text" onClick={onOpenRaw}>{t('scenario:openRaw')}</Button>
    </div>
  )
}

function UnsupportedIssuesSection({
  issues,
  onOpenRaw,
}: {
  issues: DslValidationIssue[]
  onOpenRaw: () => void
}) {
  const { t } = useTranslation(['scenario'])
  return (
    <section className="case-overview-section scenario-unmapped-issues">
      <ScenarioSectionHeader
        title={t('scenario:validation.unmappedTitle')}
        actions={<Button size="small" type="text" onClick={onOpenRaw}>{t('scenario:openRaw')}</Button>}
      />
      <div className="scenario-issue-list">
        {issues.map((issue, index) => (
          <div className="scenario-issue-row" key={`${issue.code}-${issue.path}-${index}`}>
            <ExclamationCircleOutlined />
            <code>{issue.path}</code>
            <span>{issue.message}</span>
          </div>
        ))}
      </div>
    </section>
  )
}

function UnsupportedConfiguration({
  issues,
  onOpenRaw,
}: {
  issues: DslValidationIssue[]
  onOpenRaw: () => void
}) {
  const { t } = useTranslation(['scenario'])
  return (
    <div className="unsupported-configuration">
      <div>
        <Text strong>{t('scenario:validation.unsupportedConfiguration')}</Text>
        <FieldValidationMessage message={issues[0]?.message} />
      </div>
      <Button size="small" onClick={onOpenRaw}>{t('scenario:openRaw')}</Button>
    </div>
  )
}

function InvalidRuleCard({
  title,
  issues,
  onDelete,
}: {
  title: string
  issues: DslValidationIssue[]
  onDelete: () => void
}) {
  const { t } = useTranslation(['common', 'scenario'])
  return (
    <section className="rule-card invalid-rule-card">
      <div className="panel-title-row compact">
        <Text strong>{title}</Text>
        <Button
          danger
          icon={<DeleteOutlined />}
          title={t('common:actions.delete')}
          type="text"
          onClick={onDelete}
        />
      </div>
      <Text type="secondary">{t('scenario:validation.unsupportedConfiguration')}</Text>
      <FieldValidationMessage message={issues[0]?.message} />
    </section>
  )
}

function ActionSection({
  title,
  description,
  actions,
  issues,
  path,
  availableTypes,
  onAdd,
  onDelete,
  onChange,
  onMove,
}: {
  title: string
  description: string
  actions: CaseAction[]
  issues: DslValidationIssue[]
  path: string
  availableTypes: ActionTypeOption[]
  onAdd: (action: CaseAction) => void
  onDelete: (index: number) => void
  onChange: (index: number, action: CaseAction) => void
  onMove: (index: number, direction: -1 | 1) => void
}) {
  const { t } = useTranslation(['common', 'scenario'])
  return (
    <section className="action-section">
      <div className="panel-title-row compact">
        <div>
          <h4 className="panel-subtitle">{title}</h4>
          <Text type="secondary">{description}</Text>
        </div>
        {availableTypes.length > 0 ? <Dropdown
          trigger={['click']}
          menu={{
            items: availableTypes.map((item) => ({ key: item.value, label: item.label })),
            onClick: ({ key }) => onAdd(createAction(key)),
          }}
        >
          <Button icon={<PlusOutlined />}>{t('scenario:editor.addAction')} <DownOutlined /></Button>
        </Dropdown> : null}
      </div>
      <FieldValidationMessage message={issues.find((issue) => issue.path === path)?.message} />
      <div className="rule-stack">
        {actions.map((action, index) => isRecord(action) ? (
          <RuleCard
            key={`${title}-${index}`}
            title={action.name || action.type || `Action ${index + 1}`}
            onDelete={() => onDelete(index)}
            headerActions={(
              <>
                <Tag>{t('scenario:editor.actionFlow.fromCase')}</Tag>
                <Button
                  type="text"
                  icon={<ArrowUpOutlined />}
                  disabled={index === 0}
                  title={t('scenario:editor.moveActionUp')}
                  onClick={() => onMove(index, -1)}
                />
                <Button
                  type="text"
                  icon={<ArrowDownOutlined />}
                  disabled={index === actions.length - 1}
                  title={t('scenario:editor.moveActionDown')}
                  onClick={() => onMove(index, 1)}
                />
              </>
            )}
          >
            <div className="designer-grid narrow">
              <LabeledField label={t('scenario:editor.type')}>
                <Select
                  showSearch
                  value={action.type}
                  options={includeCurrentOption(availableTypes, action.type)}
                  status={issues.some((issue) => issue.path === `${path}[${index}].type`) ? 'error' : undefined}
                  onChange={(value) => onChange(index, createAction(value))}
                />
                <FieldValidationMessage message={issues.find((issue) => issue.path === `${path}[${index}].type`)?.message} />
              </LabeledField>
              <LabeledField label={t('scenario:editor.name')}>
                <Input
                  value={action.name ?? ''}
                  onChange={(event) => onChange(index, { ...action, name: event.target.value })}
                />
              </LabeledField>
            </div>
            <div className="designer-grid">
              {action.type === 'body.buildJson' ? (
                <StructuredEditor
                  label={t('scenario:editor.jsonTemplate')}
                  value={action.template ?? {}}
                  onCommit={(value) => onChange(index, { ...action, template: value })}
                />
              ) : (
                <StructuredEditor
                  label={t('scenario:editor.input')}
                  value={action.input ?? {}}
                  onCommit={(value) => onChange(index, { ...action, input: value })}
                />
              )}
              <StructuredEditor
                label={t('scenario:editor.output')}
                value={action.output ?? {}}
                onCommit={(value) => onChange(index, { ...action, output: value })}
              />
            </div>
          </RuleCard>
        ) : (
          <InvalidRuleCard
            key={`${title}-${index}`}
            title={`Action ${index + 1}`}
            issues={issuesForPath(issues, `${path}[${index}]`)}
            onDelete={() => onDelete(index)}
          />
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
  externalError,
}: {
  label?: string
  value: unknown
  onCommit: (next: unknown) => void
  rows?: number
  stringMode?: boolean
  externalError?: string
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
        status={error || externalError ? 'error' : undefined}
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
      {!error && externalError ? <FieldValidationMessage message={externalError} /> : null}
    </LabeledField>
  )
}

function HttpStatusEditor({
  value,
  error,
  fieldError,
  path,
  onChange,
}: {
  value: unknown
  error?: string
  fieldError: (path: string) => string | undefined
  path: string
  onChange: (next: HttpStatusRule) => void
}) {
  const { t } = useTranslation('scenario')
  const rule = isRecord(value) ? value as HttpStatusRule : {}
  const operator = isHttpStatusOperator(rule.operator) ? rule.operator : 'oneOf'
  const operatorError = fieldError(`${path}.operator`)
  const expectedError = fieldError(`${path}.expected`)
  const minError = fieldError(`${path}.min`)
  const maxError = fieldError(`${path}.max`)
  return (
    <div className="http-status-test-editor">
      <LabeledField label={t('editor.matchMode')}>
        <Segmented
          block
          value={operator}
          options={[
            { label: t('editor.statusEquals'), value: 'equals' },
            { label: t('editor.statusOneOf'), value: 'oneOf' },
            { label: t('editor.statusRange'), value: 'range' },
          ]}
          onChange={(next) => onChange(defaultHttpStatusRule(next as HttpStatusOperator))}
        />
        <FieldValidationMessage message={operatorError} />
      </LabeledField>
      {operator === 'equals' ? (
        <LabeledField label={t('editor.expectedStatus')}>
          <InputNumber
            min={100}
            max={599}
            precision={0}
            status={error || expectedError ? 'error' : undefined}
            value={typeof rule.expected === 'number' ? rule.expected : null}
            onChange={(expected) => onChange({ operator, expected })}
          />
          <FieldValidationMessage message={expectedError || error} />
        </LabeledField>
      ) : null}
      {operator === 'oneOf' ? (
        <LabeledField label={t('editor.acceptedStatuses')}>
          <Select
            mode="tags"
            tokenSeparators={[',', ' ']}
            status={error || expectedError ? 'error' : undefined}
            value={Array.isArray(rule.expected) ? rule.expected.map(String) : []}
            placeholder={t('editor.acceptedStatusesPlaceholder')}
            onChange={(values) => onChange({
              operator,
              expected: values.map((status) => /^\d+$/.test(status) ? Number(status) : status),
            })}
          />
          <FieldValidationMessage message={expectedError || error} />
        </LabeledField>
      ) : null}
      {operator === 'range' ? (
        <div className="http-status-range">
          <LabeledField label={t('editor.minimumStatus')}>
            <InputNumber
              min={100}
              max={599}
              precision={0}
              status={error || minError ? 'error' : undefined}
              value={typeof rule.min === 'number' ? rule.min : null}
              onChange={(min) => onChange({ operator, min: min ?? undefined, max: rule.max })}
            />
            <FieldValidationMessage message={minError} />
          </LabeledField>
          <LabeledField label={t('editor.maximumStatus')}>
            <InputNumber
              min={100}
              max={599}
              precision={0}
              status={error || maxError ? 'error' : undefined}
              value={typeof rule.max === 'number' ? rule.max : null}
              onChange={(max) => onChange({ operator, min: rule.min, max: max ?? undefined })}
            />
            <FieldValidationMessage message={maxError || error} />
          </LabeledField>
        </div>
      ) : null}
    </div>
  )
}

function defaultHttpStatusRule(operator: HttpStatusOperator): HttpStatusRule {
  if (operator === 'equals') return { operator, expected: 200 }
  if (operator === 'range') return { operator, min: 200, max: 299 }
  return { operator, expected: [200] }
}

function isHttpStatusOperator(value: unknown): value is HttpStatusOperator {
  return value === 'equals' || value === 'oneOf' || value === 'range'
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

function FieldValidationMessage({ message }: { message?: string }) {
  return message ? <Text className="designer-validation-message" type="danger">{message}</Text> : null
}

function ValidationTabLabel({
  label,
  count,
  className,
}: {
  label: string
  count: number
  className?: string
}) {
  return (
    <span className={`step-tab-label${className ? ` ${className}` : ''}`}>
      {label}
      {count ? <span className="step-tab-validation-count">{count}</span> : null}
    </span>
  )
}

function RuleCard({
  title,
  children,
  onDelete,
  headerActions,
}: {
  title: string
  children: ReactNode
  onDelete: () => void
  headerActions?: ReactNode
}) {
  const { t } = useTranslation(['common'])
  return (
    <section className="rule-card">
      <div className="panel-title-row compact">
        <Text strong>{title}</Text>
        <div className="rule-card-actions">
          {headerActions}
          <Button type="text" danger icon={<DeleteOutlined />} title={t('common:actions.delete')} onClick={onDelete} />
        </div>
      </div>
      {children}
    </section>
  )
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

function isEnabledCryptoAction(action: CaseAction) {
  return action?.enabled !== false && action?.type?.startsWith('crypto.pgp.')
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

function availableResponseDataSources(actions: CaseAction[]): ActionTypeOption[] {
  const options: ActionTypeOption[] = [
    { value: 'business.response.body', label: '业务响应（接口自动解密后）' },
    { value: 'response.body', label: 'response.body（原始响应）' },
    { value: 'wire.response.body', label: 'wire.response.body（原始响应）' },
  ]
  const names = new Set(options.map((option) => option.value))
  actions.forEach((action) => {
    if (!isRecord(action) || action.enabled === false || !isRecord(action.output)) {
      return
    }
    const target = action.output.target
    if (typeof target !== 'string' || !/^decrypted[.][A-Za-z][A-Za-z0-9_-]*$/.test(target) || names.has(target)) {
      return
    }
    names.add(target)
    options.push({ value: target, label: target })
  })
  return options
}

function includeCurrentOption(options: ActionTypeOption[], current: unknown): ActionTypeOption[] {
  if (typeof current !== 'string' || !current || options.some((option) => option.value === current)) {
    return options
  }
  return [{ value: current, label: current }, ...options]
}

function isRecord(value: unknown): value is Record<string, any> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function updateActionList(
  updateSelectedStep: (recipe: (step: any) => void) => void,
  stage: 'beforeSend' | 'afterReceive' | 'afterRequestProcessing',
  recipe: (actions: CaseAction[]) => void,
) {
  updateSelectedStep((step) => {
    step.actions = isRecord(step.actions) ? step.actions : {}
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
    step.response = isRecord(step.response) ? step.response : {}
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
    step.response = isRecord(step.response) ? step.response : {}
    step.response.extract = isRecord(step.response.extract) ? step.response.extract : {}
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
    step.response = isRecord(step.response) ? step.response : {}
    step.response.extract = isRecord(step.response.extract) ? step.response.extract : {}
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

function createAction(type: string): CaseAction {
  switch (type) {
    case 'crypto.pgp.encrypt':
    case 'crypto.pgp.signAndEncrypt':
      return {
        type,
        name: type === 'crypto.pgp.encrypt' ? 'Encrypt request payload' : 'Sign and encrypt request payload',
        input: { source: 'request.body', format: 'json' },
        output: { target: 'artifacts.encryptedPayload', format: 'text' },
      }
    case 'data.encodeBase64':
      return {
        type,
        name: 'Encode encrypted payload as Base64',
        input: { source: 'artifacts.encryptedPayload' },
        output: { target: 'artifacts.encryptedPayloadBase64' },
      }
    case 'body.buildJson':
      return {
        type,
        name: 'Build JSON request body',
        template: {
          meta: {},
          encryptBase64: '${artifacts.encryptedPayloadBase64}',
        },
        output: { target: 'request.body' },
      }
    case 'crypto.pgp.decrypt':
    case 'crypto.pgp.decryptAndVerify':
      return {
        type,
        name: type === 'crypto.pgp.decrypt' ? 'Decrypt response payload' : 'Decrypt and verify response payload',
        input: { source: 'response.body', path: '$.payload' },
        output: { target: 'decrypted.securePayload', format: 'json' },
      }
    default:
      return { type, name: type, input: {}, output: {} }
  }
}

function moveItem<T>(items: T[], fromIndex: number, toIndex: number) {
  if (toIndex < 0 || toIndex >= items.length || fromIndex === toIndex) {
    return
  }
  const [item] = items.splice(fromIndex, 1)
  items.splice(toIndex, 0, item)
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
