import {
  Alert,
  Button,
  Divider,
  Flex,
  Input,
  List,
  Select,
  Switch,
  Tabs,
  Typography,
} from 'antd'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import { useEffect, useMemo, useState } from 'react'
import type { SaveCaseInput } from '../types/platform'
import {
  parseDsl,
  parseStructuredText,
  stringifyDsl,
  stringifyStructuredValue,
  type DslObject,
} from '../utils/caseDsl'

const { Text, Title } = Typography

type CaseDesignerProps = {
  draft: SaveCaseInput
  onChange: (next: SaveCaseInput) => void
}

const emptyStep = () => ({
  name: 'New step',
  profile: '',
  continueOnFailure: false,
  request: {
    method: 'POST',
    baseUrl: '${baseUrl}',
    path: '/',
    headers: {
      'Content-Type': 'application/json',
    },
    query: {},
    body: {},
  },
  expect: {
    status: 200,
    decrypt: [],
    validations: [],
  },
  extract: {},
})

export function CaseDesigner({ draft, onChange }: CaseDesignerProps) {
  const parsed = useMemo(() => parseDsl(draft.content), [draft.content])
  const [activeStepIndex, setActiveStepIndex] = useState(0)

  useEffect(() => {
    setActiveStepIndex(0)
  }, [draft.code, draft.name])

  const dsl = parsed.data
  const profileNames = useMemo(
    () => (dsl?.profiles && typeof dsl.profiles === 'object' ? Object.keys(dsl.profiles) : []),
    [dsl],
  )

  const steps = Array.isArray(dsl?.steps) ? dsl.steps : []
  const selectedStep = steps[activeStepIndex]

  if (parsed.error || !dsl) {
    return (
      <div className="designer-shell">
        <Alert
          type="warning"
          showIcon
          message="Guided editor is unavailable until the YAML is valid."
          description={parsed.error ?? 'Unknown parse error'}
        />
      </div>
    )
  }

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

  return (
    <Tabs
      defaultActiveKey="guided"
      items={[
        {
          key: 'guided',
          label: 'Guided Editor',
          children: (
            <div className="designer-shell">
              <section className="designer-band">
                <div>
                  <Title level={5}>Case Overview</Title>
                  <Text type="secondary">Common fields stay editable here while preserving the underlying DSL.</Text>
                </div>
                <div className="designer-grid">
                  <LabeledField label="Variables">
                    <Input.TextArea
                      rows={8}
                      value={stringifyStructuredValue(dsl.variables ?? {})}
                      onChange={(event) => {
                        try {
                          const variables = parseStructuredText(event.target.value)
                          updateDsl((next) => {
                            next.variables = variables
                          })
                        } catch {
                          // keep current value until input becomes valid
                        }
                      }}
                    />
                  </LabeledField>
                  <LabeledField label="Profiles">
                    <Input.TextArea
                      rows={8}
                      value={stringifyStructuredValue(dsl.profiles ?? {})}
                      onChange={(event) => {
                        try {
                          const profiles = parseStructuredText(event.target.value)
                          updateDsl((next) => {
                            next.profiles = profiles
                          })
                        } catch {
                          // keep current value until input becomes valid
                        }
                      }}
                    />
                  </LabeledField>
                </div>
              </section>

              <Divider />

              <div className="designer-detail-grid">
                <section className="designer-steps">
                  <div className="panel-title-row compact">
                    <div>
                      <h3 className="panel-subtitle">Steps</h3>
                      <Text type="secondary">Model the scenario as ordered API steps.</Text>
                    </div>
                    <Button
                      icon={<PlusOutlined />}
                      onClick={() => {
                        updateDsl((next) => {
                          next.steps = Array.isArray(next.steps) ? next.steps : []
                          next.steps.push(emptyStep())
                        })
                        setActiveStepIndex(steps.length)
                      }}
                    >
                      Add Step
                    </Button>
                  </div>
                  <List
                    dataSource={steps}
                    locale={{ emptyText: 'No steps yet.' }}
                    renderItem={(step: any, index) => (
                      <List.Item
                        className={index === activeStepIndex ? 'case-list-item active' : 'case-list-item'}
                        onClick={() => setActiveStepIndex(index)}
                        actions={[
                          <Button
                            key="delete"
                            type="text"
                            danger
                            icon={<DeleteOutlined />}
                            onClick={(event) => {
                              event.stopPropagation()
                              updateDsl((next) => {
                                next.steps = Array.isArray(next.steps) ? next.steps : []
                                next.steps.splice(index, 1)
                              })
                              setActiveStepIndex((current) => Math.max(0, current - (current >= index ? 1 : 0)))
                            }}
                          />,
                        ]}
                      >
                        <Flex vertical gap={4} className="step-list-meta">
                          <Text strong>{step.name || `Step ${index + 1}`}</Text>
                          <Text type="secondary">{step.request?.method ?? 'METHOD'} {step.request?.path ?? '/'}</Text>
                        </Flex>
                      </List.Item>
                    )}
                  />
                </section>

                <section className="designer-step-editor">
                  {selectedStep ? (
                    <>
                      <div className="panel-title-row compact">
                        <div>
                          <h3 className="panel-subtitle">Step Detail</h3>
                          <Text type="secondary">Edit the selected request, response expectations, and data extraction.</Text>
                        </div>
                      </div>

                      <div className="designer-grid narrow">
                        <LabeledField label="Step Name">
                          <Input
                            value={selectedStep.name ?? ''}
                            onChange={(event) => updateSelectedStep((step) => { step.name = event.target.value })}
                          />
                        </LabeledField>
                        <LabeledField label="Profile">
                          <Select
                            value={selectedStep.profile || undefined}
                            allowClear
                            options={profileNames.map((name) => ({ label: name, value: name }))}
                            onChange={(value) => updateSelectedStep((step) => { step.profile = value ?? '' })}
                          />
                        </LabeledField>
                        <LabeledField label="Continue On Failure">
                          <Switch
                            checked={Boolean(selectedStep.continueOnFailure)}
                            onChange={(checked) => updateSelectedStep((step) => { step.continueOnFailure = checked })}
                          />
                        </LabeledField>
                      </div>

                      <Divider />

                      <Title level={5}>Request</Title>
                      <div className="designer-grid narrow">
                        <LabeledField label="Method">
                          <Select
                            value={selectedStep.request?.method ?? 'POST'}
                            options={['GET', 'POST', 'PUT', 'PATCH', 'DELETE'].map((method) => ({
                              label: method,
                              value: method,
                            }))}
                            onChange={(value) => updateSelectedStep((step) => {
                              step.request = step.request ?? {}
                              step.request.method = value
                            })}
                          />
                        </LabeledField>
                        <LabeledField label="Base URL">
                          <Input
                            value={selectedStep.request?.baseUrl ?? ''}
                            onChange={(event) => updateSelectedStep((step) => {
                              step.request = step.request ?? {}
                              step.request.baseUrl = event.target.value
                            })}
                          />
                        </LabeledField>
                        <LabeledField label="Path">
                          <Input
                            value={selectedStep.request?.path ?? ''}
                            onChange={(event) => updateSelectedStep((step) => {
                              step.request = step.request ?? {}
                              step.request.path = event.target.value
                            })}
                          />
                        </LabeledField>
                      </div>

                      <div className="designer-grid">
                        <StructuredEditor
                          label="Query"
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

                      <StructuredEditor
                        label="Request Transform"
                        value={selectedStep.request?.transform ?? {}}
                        onCommit={(value) => updateSelectedStep((step) => {
                          step.request = step.request ?? {}
                          step.request.transform = value
                        })}
                      />

                      <Divider />

                      <Title level={5}>Expect</Title>
                      <div className="designer-grid narrow">
                        <LabeledField label="HTTP Status">
                          <Input
                            value={String(selectedStep.expect?.status ?? 200)}
                            onChange={(event) => updateSelectedStep((step) => {
                              step.expect = step.expect ?? {}
                              step.expect.status = Number(event.target.value || 0)
                            })}
                          />
                        </LabeledField>
                      </div>

                      <StructuredEditor
                        label="Decrypt Rules"
                        value={selectedStep.expect?.decrypt ?? []}
                        onCommit={(value) => updateSelectedStep((step) => {
                          step.expect = step.expect ?? {}
                          step.expect.decrypt = value
                        })}
                      />

                      <StructuredEditor
                        label="Validations"
                        value={selectedStep.expect?.validations ?? []}
                        onCommit={(value) => updateSelectedStep((step) => {
                          step.expect = step.expect ?? {}
                          step.expect.validations = value
                        })}
                      />

                      <StructuredEditor
                        label="Extract"
                        value={selectedStep.extract ?? {}}
                        onCommit={(value) => updateSelectedStep((step) => {
                          step.extract = value
                        })}
                      />
                    </>
                  ) : (
                    <div className="panel-empty">Add a step to start building the scenario.</div>
                  )}
                </section>
              </div>
            </div>
          ),
        },
        {
          key: 'yaml',
          label: 'YAML',
          children: (
            <div className="editor-shell">
              <div className="plain-editor-shell">
                <Input.TextArea
                  className="plain-editor-input"
                  value={draft.content}
                  onChange={(event) => onChange({ ...draft, content: event.target.value })}
                  autoSize={false}
                />
              </div>
            </div>
          ),
        },
      ]}
    />
  )
}

function StructuredEditor({
  label,
  value,
  onCommit,
}: {
  label: string
  value: unknown
  onCommit: (next: unknown) => void
}) {
  const [text, setText] = useState(stringifyStructuredValue(value))
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setText(stringifyStructuredValue(value))
    setError(null)
  }, [value])

  return (
    <LabeledField label={label}>
      <Input.TextArea
        rows={6}
        value={text}
        onChange={(event) => {
          const next = event.target.value
          setText(next)
          try {
            const parsed = parseStructuredText(next)
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
