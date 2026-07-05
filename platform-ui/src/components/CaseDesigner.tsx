import {
  Alert,
  Button,
  Input,
  Select,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import { useEffect, useMemo, useState } from 'react'
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

export function CaseDesigner({ draft, onChange, operations, onActiveStepChange }: CaseDesignerProps) {
  const parsed = useMemo(() => parseDsl(draft.content), [draft.content])
  const [activeTab, setActiveTab] = useState('request')
  const [activeStepIndex, setActiveStepIndex] = useState(0)

  useEffect(() => {
    setActiveStepIndex(0)
    setActiveTab('request')
  }, [draft.code, draft.name])

  useEffect(() => {
    onActiveStepChange?.(activeStepIndex)
  }, [activeStepIndex, onActiveStepChange])

  const dsl = parsed.data
  const schemaErrors = useMemo(() => (dsl ? validateCaseDslV1(dsl) : []), [dsl])

  if (parsed.error || !dsl) {
    return (
      <section className="case-workbench scenario-request-builder">
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
      <section className="case-workbench scenario-request-builder">
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

  const setSelectedOperation = (value?: string) => {
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
  }

  const beforeSendActions = asActions(selectedStep?.actions?.beforeSend)
  const afterReceiveActions = asActions(selectedStep?.actions?.afterReceive)
  const assertions = asAssertions(selectedStep?.response?.assertions)
  const extractEntries = asExtractEntries(selectedStep?.response?.extract)

  return (
    <section className="case-workbench scenario-request-builder">
      <div className="scenario-step-strip">
        <div className="step-rail">
          {steps.length ? steps.map((step: any, index) => (
            <button
              key={`step-${index}`}
              type="button"
              className={index === activeStepIndex ? 'step-chip active' : 'step-chip'}
              onClick={() => setActiveStepIndex(index)}
            >
              <span className={`method-pill method-${stepMethod(step).toLowerCase()}`}>{stepMethod(step)}</span>
              <span className="step-chip-title">{step.name || `Step ${index + 1}`}</span>
              <span className="step-chip-path">{operationPath(step)}</span>
              <Button
                type="text"
                danger
                size="small"
                icon={<DeleteOutlined />}
                onClick={(event) => {
                  event.stopPropagation()
                  updateDsl((next) => {
                    next.steps.splice(index, 1)
                  })
                  setActiveStepIndex((current) => Math.max(0, current - (current >= index ? 1 : 0)))
                }}
              />
            </button>
          )) : (
            <div className="scenario-muted-row">No steps yet.</div>
          )}
        </div>
        <Button
          className="scenario-add-step"
          type="text"
          icon={<PlusOutlined />}
          onClick={() => {
            updateDsl((next) => {
              next.steps.push(emptyStep())
            })
            setActiveStepIndex(steps.length)
            setActiveTab('request')
          }}
        >
          Add Step
        </Button>
      </div>

      {selectedStep ? (
        <div className="designer-shell">
          <section className="step-editor-shell">
            <div className="request-compose-row">
              <Tag className={`method-pill method-${stepMethod(selectedStep).toLowerCase()}`}>{stepMethod(selectedStep)}</Tag>
              <Select
                showSearch
                allowClear
                className="request-operation-select"
                placeholder="Select operation"
                optionFilterProp="label"
                value={selectedOperation?.id}
                options={operations.map((operation) => ({
                  label: `${operation.method} ${operation.path} - ${operation.summary || operation.operationId || 'Operation'}`,
                  value: operation.id,
                }))}
                onChange={setSelectedOperation}
              />
              <Input
                className="request-step-name-input"
                value={selectedStep.name ?? ''}
                placeholder="Step name"
                onChange={(event) => updateSelectedStep((step) => {
                  step.name = event.target.value
                })}
              />
            </div>

            <Tabs
              className="scenario-request-tabs"
              activeKey={activeTab}
              onChange={setActiveTab}
              items={[
                {
                  key: 'request',
                  label: 'Request',
                  children: (
                    <div className="designer-band">
                      <div className="designer-grid narrow">
                        <LabeledField label="Service">
                          <Input
                            value={selectedStep.operation?.service ?? ''}
                            onChange={(event) => updateSelectedStep((step) => {
                              step.operation = step.operation ?? {}
                              step.operation.service = event.target.value
                            })}
                          />
                        </LabeledField>
                        <LabeledField label="Expected Status">
                          <Input
                            value={String(selectedStep.response?.status ?? '')}
                            onChange={(event) => updateSelectedStep((step) => {
                              step.response = step.response ?? {}
                              const numeric = Number(event.target.value)
                              step.response.status = Number.isNaN(numeric) ? event.target.value : numeric
                            })}
                          />
                        </LabeledField>
                        <LabeledField label="Operation Snapshot">
                          <Input value={operationLabel(selectedStep)} disabled />
                        </LabeledField>
                      </div>

                      <div className="designer-grid">
                        <StructuredEditor
                          label="Query Params"
                          value={selectedStep.request?.query ?? {}}
                          onCommit={(value) => updateSelectedStep((step) => {
                            step.request = step.request ?? {}
                            step.request.query = value
                          })}
                        />
                        <StructuredEditor
                          label="Headers"
                          value={selectedStep.request?.headers ?? {}}
                          onCommit={(value) => updateSelectedStep((step) => {
                            step.request = step.request ?? {}
                            step.request.headers = value
                          })}
                        />
                      </div>

                      <StructuredEditor
                        label="Body"
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
                  key: 'actions',
                  label: 'Actions',
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
                  label: 'Tests',
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
                          Add Assertion
                        </Button>
                      </div>

                      <div className="rule-stack">
                        {assertions.length ? assertions.map((rule, index) => (
                          <RuleCard
                            key={`assertion-${index}`}
                            title={`Assertion ${index + 1}`}
                            onDelete={() => updateSelectedStep((step) => {
                              const rules = asAssertions(step.response.assertions)
                              rules.splice(index, 1)
                              step.response.assertions = rules
                            })}
                          >
                            <div className="designer-grid narrow">
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
                              <LabeledField label="Operator">
                                <Select
                                  value={rule.operator ?? 'equals'}
                                  options={assertionOperators.map((value) => ({ label: value, value }))}
                                  onChange={(value) => updateAssertionRule(updateSelectedStep, index, 'operator', value)}
                                />
                              </LabeledField>
                            </div>
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
                          </RuleCard>
                        )) : (
                          <div className="panel-empty">No assertions yet. Add one to define the test verdict.</div>
                        )}
                      </div>
                    </div>
                  ),
                },
                {
                  key: 'extract',
                  label: 'Extract',
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
                          Add Extract
                        </Button>
                      </div>

                      <div className="rule-stack">
                        {extractEntries.length ? extractEntries.map(([name, rule], index) => (
                          <RuleCard
                            key={`extract-${name}-${index}`}
                            title={name}
                            onDelete={() => updateSelectedStep((step) => {
                              delete step.response.extract[name]
                            })}
                          >
                            <div className="designer-grid narrow">
                              <LabeledField label="Variable Name">
                                <Input
                                  value={name}
                                  onChange={(event) => renameExtract(updateSelectedStep, name, event.target.value)}
                                />
                              </LabeledField>
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
                        )) : (
                          <div className="panel-empty">No extract rules yet. Add one to chain data into later steps.</div>
                        )}
                      </div>
                    </div>
                  ),
                },
                {
                  key: 'advanced',
                  label: 'Advanced',
                  children: (
                    <div className="designer-band">
                      <div className="designer-grid">
                        <StructuredEditor
                          label="Case Runtime"
                          value={dsl.runtime ?? {}}
                          onCommit={(value) => updateDsl((next) => {
                            next.runtime = value
                          })}
                        />
                        <StructuredEditor
                          label="Case Variables"
                          value={dsl.variables ?? {}}
                          onCommit={(value) => updateDsl((next) => {
                            next.variables = value
                          })}
                        />
                      </div>

                      <StructuredEditor
                        label="Step Runtime"
                        value={selectedStep.runtime ?? {}}
                        onCommit={(value) => updateSelectedStep((step) => {
                          step.runtime = value
                        })}
                      />

                      <StructuredEditor
                        label="Step Definition Preview"
                        value={selectedStep}
                        onCommit={(value) => updateSelectedStep((step) => {
                          const nextValue = value && typeof value === 'object' ? value : emptyStep()
                          Object.keys(step).forEach((key) => delete step[key])
                          Object.assign(step, nextValue)
                        })}
                      />

                      <StructuredEditor
                        label="Case YAML"
                        value={draft.content}
                        rows={16}
                        onCommit={(value) => {
                          onChange({ ...draft, content: typeof value === 'string' ? value : stringifyStructuredValue(value) })
                        }}
                        stringMode
                      />
                    </div>
                  ),
                },
              ]}
            />
          </section>
        </div>
      ) : null}
    </section>
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
        {actions.length ? actions.map((action, index) => (
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
        )) : (
          <div className="panel-empty">No actions configured.</div>
        )}
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
  label: string
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
  label: string
  children: React.ReactNode
}) {
  return (
    <div className="designer-field">
      <Text className="designer-label">{label}</Text>
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
  children: React.ReactNode
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

function operationLabel(step: any) {
  const method = step?.operation?.method ?? 'METHOD'
  const path = step?.operation?.path ?? 'Select operation'
  return `${method} ${path}`
}

function stepMethod(step: any) {
  return String(step?.operation?.method ?? 'HTTP').toUpperCase()
}

function operationPath(step: any) {
  return String(step?.operation?.path ?? 'Select operation')
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
