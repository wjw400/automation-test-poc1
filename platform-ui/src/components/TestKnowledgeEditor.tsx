import { EditOutlined, ReadOutlined, SaveOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Button, Form, Input, Segmented, Select, Space, Tag, Typography } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { platformApi } from '../api/platformApi'
import type { SaveTestKnowledgeInput, TestKnowledgeRecord, TestKnowledgeScope } from '../types/platform'

const { Text } = Typography

type Props = {
  projectId: string
  knowledge?: TestKnowledgeRecord
  onDirtyChange?: (dirty: boolean) => void
  onTitleChange?: (title: string) => void
  onSaveReady?: (save?: () => Promise<void>) => void
  onSaved?: (record: TestKnowledgeRecord) => void
}

const emptyDraft: SaveTestKnowledgeInput = {
  name: '', description: '', scope: 'PROJECT', tags: [], content: '# Test knowledge\n\n', status: 'DRAFT',
}

export function TestKnowledgeEditor({ projectId, knowledge, onDirtyChange, onTitleChange, onSaveReady, onSaved }: Props) {
  const { message } = App.useApp()
  const { t } = useTranslation('workspace')
  const queryClient = useQueryClient()
  const initial = useMemo(() => knowledge ? toDraft(knowledge) : emptyDraft, [knowledge])
  const [draft, setDraft] = useState<SaveTestKnowledgeInput>(initial)
  const [baseline, setBaseline] = useState<SaveTestKnowledgeInput>(initial)
  const [tagsInput, setTagsInput] = useState(initial.tags.join(', '))
  const [error, setError] = useState<string>()
  const [mode, setMode] = useState<'read' | 'edit'>(knowledge ? 'read' : 'edit')
  const specsQuery = useQuery({ queryKey: ['api-specs', projectId], queryFn: () => platformApi.listApiSpecs(projectId) })
  const operationsQuery = useQuery({ queryKey: ['operations', projectId], queryFn: () => platformApi.listOperations(projectId) })
  const casesQuery = useQuery({ queryKey: ['cases', projectId], queryFn: () => platformApi.listCases(projectId) })
  const suitesQuery = useQuery({ queryKey: ['suites', projectId], queryFn: () => platformApi.listSuites(projectId) })

  useEffect(() => {
    setDraft(initial)
    setBaseline(initial)
    setTagsInput(initial.tags.join(', '))
    setError(undefined)
    setMode(knowledge ? 'read' : 'edit')
  }, [initial, knowledge])
  const dirty = JSON.stringify(draft) !== JSON.stringify(baseline)
  useEffect(() => { onDirtyChange?.(dirty) }, [dirty, onDirtyChange])
  useEffect(() => { onTitleChange?.(draft.name || 'New Test Knowledge') }, [draft.name, onTitleChange])

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (!draft.name.trim() || !draft.content.trim()) throw new Error('Name and Markdown content are required.')
      if (draft.scope !== 'PROJECT' && !draft.targetId) throw new Error('Select the asset this document applies to.')
      return knowledge
        ? platformApi.updateTestKnowledge(projectId, knowledge.id, draft)
        : platformApi.createTestKnowledge(projectId, draft)
    },
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: ['test-knowledge', projectId] })
      const next = toDraft(saved)
      setDraft(next); setBaseline(next); setTagsInput(next.tags.join(', ')); setError(undefined)
      onSaved?.(saved)
      void message.success('Test knowledge saved')
    },
    onError: (saveError) => setError(saveError instanceof Error ? saveError.message : 'Unable to save test knowledge.'),
  })
  useEffect(() => {
    onSaveReady?.(async () => { await saveMutation.mutateAsync() })
    return () => onSaveReady?.(undefined)
  }, [onSaveReady, saveMutation])

  const setScope = (scope: TestKnowledgeScope) => setDraft({ ...draft, scope, targetId: undefined })
  const targetOptions = getTargetOptions(draft.scope, specsQuery.data ?? [], operationsQuery.data ?? [], casesQuery.data ?? [], suitesQuery.data ?? [])
  const targetLabel = draft.scope === 'PROJECT'
    ? t('knowledgeEditor.entireProject')
    : targetOptions.find((option) => option.value === draft.targetId)?.label ?? t('knowledgeEditor.noTarget')
  return (
    <div className="asset-editor-page knowledge-editor-page">
      <header className="asset-editor-header">
        <div><h1>{draft.name || 'New Test Knowledge'}</h1><Text type="secondary">{draft.description || t('knowledgeEditor.subtitle')}</Text></div>
        <Space>
          <Segmented
            value={mode}
            options={[
              { value: 'read', label: t('knowledgeEditor.read'), icon: <ReadOutlined /> },
              { value: 'edit', label: t('knowledgeEditor.edit'), icon: <EditOutlined /> },
            ]}
            onChange={(value) => setMode(value as 'read' | 'edit')}
          />
          <Text className={dirty ? 'scenario-draft-state dirty' : 'scenario-draft-state'}>{dirty ? 'Unsaved' : 'Saved'}</Text>
          {mode === 'edit' ? <Button type="primary" icon={<SaveOutlined />} disabled={!dirty || !draft.name.trim() || !draft.content.trim()} loading={saveMutation.isPending} onClick={() => void saveMutation.mutateAsync()}>Save</Button> : null}
        </Space>
      </header>
      {error ? <Alert type="error" showIcon message={error} /> : null}
      <div className="asset-editor-scroll">
        {mode === 'read' ? (
          <article className="knowledge-reader">
            <div className="knowledge-reader-meta">
              <Tag variant="filled" color={draft.status === 'PUBLISHED' ? 'green' : 'default'}>{draft.status === 'PUBLISHED' ? t('knowledgeEditor.published') : t('knowledgeEditor.draft')}</Tag>
              <Text type="secondary">{t(scopeLabelKeys[draft.scope])} · {targetLabel}</Text>
              {draft.tags.map((tag) => <Tag key={tag}>{tag}</Tag>)}
            </div>
            <div className="knowledge-reader-content">
              <ReactMarkdown remarkPlugins={[remarkGfm]}>{draft.content}</ReactMarkdown>
            </div>
          </article>
        ) : <Form className="asset-editor-form knowledge-editor-form" layout="vertical">
          <div className="knowledge-editor-meta">
            <Form.Item label="Name"><Input value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} /></Form.Item>
            <Form.Item label="Status"><Select value={draft.status} options={[{ value: 'DRAFT', label: 'Draft' }, { value: 'PUBLISHED', label: 'Published' }]} onChange={(status) => setDraft({ ...draft, status })} /></Form.Item>
            <Form.Item label="Applies to"><Select value={draft.scope} options={scopeOptions} onChange={setScope} /></Form.Item>
            {draft.scope !== 'PROJECT' ? <Form.Item label="Target"><Select value={draft.targetId} options={targetOptions} placeholder="Select an asset" onChange={(targetId) => setDraft({ ...draft, targetId })} /></Form.Item> : null}
          </div>
          <Form.Item label="Description"><Input value={draft.description} placeholder="What does this document explain?" onChange={(event) => setDraft({ ...draft, description: event.target.value })} /></Form.Item>
          <Form.Item label="Tags"><Input value={tagsInput} placeholder="encryption, protocol, order-flow" onChange={(event) => {
            const value = event.target.value
            setTagsInput(value)
            setDraft({ ...draft, tags: value.split(',').map((tag) => tag.trim()).filter(Boolean) })
          }} />
            {draft.tags.length ? <div className="knowledge-tag-list">{draft.tags.map((tag) => <Tag key={tag}>{tag}</Tag>)}</div> : null}
          </Form.Item>
          <Form.Item className="knowledge-editor-content" label="Markdown"><Input.TextArea className="knowledge-markdown-input" spellCheck={false} value={draft.content} onChange={(event) => setDraft({ ...draft, content: event.target.value })} /></Form.Item>
        </Form>}
      </div>
    </div>
  )
}

const scopeOptions = [
  { value: 'PROJECT', label: 'Project' }, { value: 'API_SPEC', label: 'API Spec' }, { value: 'OPERATION', label: 'Operation' },
  { value: 'SCENARIO', label: 'Scenario' }, { value: 'SUITE', label: 'Suite' },
]
const scopeLabelKeys = {
  PROJECT: 'knowledgeEditor.scope.project', API_SPEC: 'knowledgeEditor.scope.apiSpec', OPERATION: 'knowledgeEditor.scope.operation',
  SCENARIO: 'knowledgeEditor.scope.scenario', SUITE: 'knowledgeEditor.scope.suite',
} as const satisfies Record<TestKnowledgeScope, string>
function toDraft(record: TestKnowledgeRecord): SaveTestKnowledgeInput { return { name: record.name, description: record.description, scope: record.scope, targetId: record.targetId ?? undefined, tags: record.tags, content: record.content, status: record.status } }
function getTargetOptions(scope: TestKnowledgeScope, specs: Awaited<ReturnType<typeof platformApi.listApiSpecs>>, operations: Awaited<ReturnType<typeof platformApi.listOperations>>, cases: Awaited<ReturnType<typeof platformApi.listCases>>, suites: Awaited<ReturnType<typeof platformApi.listSuites>>) {
  if (scope === 'API_SPEC') return specs.map((record) => ({ value: record.id, label: record.title || record.name }))
  if (scope === 'OPERATION') return operations.map((record) => ({ value: record.id, label: `${record.method} ${record.path}${record.summary ? ` - ${record.summary}` : ''}` }))
  if (scope === 'SCENARIO') return cases.map((record) => ({ value: record.id, label: record.name }))
  if (scope === 'SUITE') return suites.map((record) => ({ value: record.id, label: record.name }))
  return []
}
