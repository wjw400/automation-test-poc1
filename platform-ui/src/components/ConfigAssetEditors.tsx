import { SaveOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Button, Form, Input, Select, Space, Switch, Typography } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { platformApi } from '../api/platformApi'
import type {
  Environment,
  Profile,
  SaveEnvironmentInput,
  SaveProfileInput,
} from '../types/platform'
import { JsonObjectEditor } from './JsonObjectEditor'

const { Text } = Typography

type EditorCallbacks<T> = {
  projectId: string
  onDirtyChange?: (dirty: boolean) => void
  onTitleChange?: (title: string) => void
  onSaveReady?: (save?: () => Promise<void>) => void
  onSaved?: (record: T) => void
}

const emptyEnvironment: SaveEnvironmentInput = {
  name: '',
  baseUrl: '',
  endpoints: {},
  variables: {},
  status: 'ACTIVE',
}

export function EnvironmentAssetEditor({
  projectId,
  environment,
  onDirtyChange,
  onTitleChange,
  onSaveReady,
  onSaved,
}: EditorCallbacks<Environment> & { environment?: Environment }) {
  const { t } = useTranslation(['common', 'config'])
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const initialValue = useMemo(() => environmentDraft(environment), [environment])
  const [draft, setDraft] = useState<SaveEnvironmentInput>(initialValue)
  const [baseline, setBaseline] = useState<SaveEnvironmentInput>(initialValue)
  const [variablesError, setVariablesError] = useState<string | null>(null)

  const apiSpecsQuery = useQuery({
    queryKey: ['api-specs', projectId],
    queryFn: () => platformApi.listApiSpecs(projectId),
  })

  useEffect(() => {
    setDraft(initialValue)
    setBaseline(initialValue)
    setVariablesError(null)
  }, [initialValue])

  const dirty = JSON.stringify(draft) !== JSON.stringify(baseline)

  useEffect(() => {
    onDirtyChange?.(dirty || Boolean(variablesError))
  }, [dirty, onDirtyChange, variablesError])

  useEffect(() => {
    onTitleChange?.(draft.name || t('config:newEnvironment'))
  }, [draft.name, onTitleChange, t])

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (variablesError) {
        throw new Error(`Fix Variables JSON before saving: ${variablesError}`)
      }
      if (!draft.name.trim() || !draft.baseUrl.trim()) {
        throw new Error(t('config:environmentRequired'))
      }
      return environment
        ? platformApi.updateEnvironment(projectId, environment.id, draft)
        : platformApi.createEnvironment(projectId, draft)
    },
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: ['environments', projectId] })
      const next = environmentDraft(saved)
      setDraft(next)
      setBaseline(next)
      onSaved?.(saved)
      void message.success(t('config:environmentSaved'))
    },
  })

  useEffect(() => {
    onSaveReady?.(async () => {
      await saveMutation.mutateAsync()
    })
    return () => onSaveReady?.(undefined)
  }, [onSaveReady, saveMutation])

  return (
    <div className="asset-editor-page">
      <header className="asset-editor-header">
        <div>
          <h1>{draft.name || t('config:newEnvironment')}</h1>
          <Text type="secondary">{t('config:environment')}</Text>
        </div>
        <Space>
          <Text className={dirty ? 'scenario-draft-state dirty' : 'scenario-draft-state'}>
            {dirty ? t('config:unsaved') : t('config:saved')}
          </Text>
          <Button
            type="primary"
            icon={<SaveOutlined />}
            disabled={!dirty || Boolean(variablesError) || !draft.name.trim() || !draft.baseUrl.trim()}
            loading={saveMutation.isPending}
            onClick={() => void saveMutation.mutateAsync()}
          >
            {t('common:actions.save')}
          </Button>
        </Space>
      </header>
      {saveMutation.isError ? <Alert type="error" showIcon message={t('config:environmentSaveFailed')} /> : null}
      <div className="asset-editor-scroll">
        <Form className="asset-editor-form" layout="vertical">
          <Form.Item label={t('common:fields.name')}>
            <Input value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} />
          </Form.Item>
          <Form.Item label={t('config:defaultBaseUrl')}>
            <Input value={draft.baseUrl} onChange={(event) => setDraft({ ...draft, baseUrl: event.target.value })} />
          </Form.Item>
          <Form.Item label={t('common:fields.status')}>
            <Select
              value={draft.status}
              options={[
                { value: 'ACTIVE', label: 'ACTIVE' },
                { value: 'INACTIVE', label: 'INACTIVE' },
              ]}
              onChange={(status) => setDraft({ ...draft, status })}
            />
          </Form.Item>
          <section className="asset-editor-section">
            <h2>{t('config:apiEndpointBindings')}</h2>
            <div className="endpoint-binding-list">
              {(apiSpecsQuery.data ?? []).map((apiSpec) => (
                <div className="endpoint-binding-row" key={apiSpec.id}>
                  <div className="endpoint-binding-name">
                    <strong>{apiSpec.title || apiSpec.name}</strong>
                    <Text type="secondary">{apiSpec.name}</Text>
                  </div>
                  <Input
                    aria-label={`${apiSpec.name} base URL`}
                    placeholder={draft.baseUrl || 'https://api.example.com'}
                    value={draft.endpoints[apiSpec.id] ?? ''}
                    onChange={(event) => {
                      const endpoints = { ...draft.endpoints }
                      if (event.target.value) {
                        endpoints[apiSpec.id] = event.target.value
                      } else {
                        delete endpoints[apiSpec.id]
                      }
                      setDraft({ ...draft, endpoints })
                    }}
                  />
                </div>
              ))}
            </div>
          </section>
          <section className="asset-editor-section">
            <h2>{t('common:fields.variables')}</h2>
            <JsonObjectEditor
              ariaLabel={t('config:environmentVariablesEditor')}
              value={draft.variables}
              onValidationChange={setVariablesError}
              onCommit={(variables) => setDraft({
                ...draft,
                variables: variables as Record<string, string>,
              })}
            />
          </section>
        </Form>
      </div>
    </div>
  )
}

const emptyProfile: SaveProfileInput = {
  name: '',
  type: 'PGP',
  senderPublicKey: '',
  senderPrivateKey: '',
  senderPrivateKeyPassphrase: '',
  recipientPublicKey: '',
  recipientPrivateKey: '',
  recipientPrivateKeyPassphrase: '',
  environmentIds: [],
  enabled: true,
}

export function ProfileAssetEditor({
  projectId,
  profile,
  onDirtyChange,
  onTitleChange,
  onSaveReady,
  onSaved,
}: EditorCallbacks<Profile> & { profile?: Profile }) {
  const { t } = useTranslation(['common', 'config'])
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const initialValue = useMemo(() => profileDraft(profile), [profile])
  const [draft, setDraft] = useState<SaveProfileInput>(initialValue)
  const [baseline, setBaseline] = useState<SaveProfileInput>(initialValue)

  const environmentsQuery = useQuery({
    queryKey: ['environments', projectId],
    queryFn: () => platformApi.listEnvironments(projectId),
  })

  useEffect(() => {
    setDraft(initialValue)
    setBaseline(initialValue)
  }, [initialValue])

  const dirty = JSON.stringify(draft) !== JSON.stringify(baseline)

  useEffect(() => {
    onDirtyChange?.(dirty)
  }, [dirty, onDirtyChange])

  useEffect(() => {
    onTitleChange?.(draft.name || t('config:newProfile'))
  }, [draft.name, onTitleChange, t])

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (!draft.name.trim() || draft.environmentIds.length === 0) {
        throw new Error(t('config:profileRequired'))
      }
      return profile
        ? platformApi.updateProfile(projectId, profile.id, draft)
        : platformApi.createProfile(projectId, draft)
    },
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: ['profiles', projectId] })
      const next = profileDraft(saved)
      setDraft(next)
      setBaseline(next)
      onSaved?.(saved)
      void message.success(t('config:profileSaved'))
    },
  })

  useEffect(() => {
    onSaveReady?.(async () => {
      await saveMutation.mutateAsync()
    })
    return () => onSaveReady?.(undefined)
  }, [onSaveReady, saveMutation])

  return (
    <div className="asset-editor-page">
      <header className="asset-editor-header">
        <div>
          <h1>{draft.name || t('config:newProfile')}</h1>
          <Text type="secondary">{t('config:profile')}</Text>
        </div>
        <Space>
          <Text className={dirty ? 'scenario-draft-state dirty' : 'scenario-draft-state'}>
            {dirty ? t('config:unsaved') : t('config:saved')}
          </Text>
          <Button
            type="primary"
            icon={<SaveOutlined />}
            disabled={!dirty || !draft.name.trim() || draft.environmentIds.length === 0}
            loading={saveMutation.isPending}
            onClick={() => void saveMutation.mutateAsync()}
          >
            {t('common:actions.save')}
          </Button>
        </Space>
      </header>
      {saveMutation.isError ? <Alert type="error" showIcon message={t('config:profileSaveFailed')} /> : null}
      <div className="asset-editor-scroll">
        <Form className="asset-editor-form" layout="vertical">
          <Form.Item label={t('common:fields.name')}>
            <Input value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} />
          </Form.Item>
          <Form.Item label={t('config:type')}>
            <Select value={draft.type} options={[{ value: 'PGP', label: 'PGP' }]} onChange={(type) => setDraft({ ...draft, type })} />
          </Form.Item>
          <Form.Item label={t('config:availableEnvironments')}>
            <Select
              mode="multiple"
              aria-label={t('config:availableEnvironments')}
              value={draft.environmentIds}
              options={(environmentsQuery.data ?? []).map((environment) => ({
                label: environment.name,
                value: environment.id,
                disabled: environment.status !== 'ACTIVE',
              }))}
              onChange={(environmentIds) => setDraft({ ...draft, environmentIds })}
            />
          </Form.Item>
          <Form.Item label={t('config:enabled')}>
            <Switch checked={draft.enabled} onChange={(enabled) => setDraft({ ...draft, enabled })} />
          </Form.Item>
          <section className="asset-editor-section">
            <h2>{t('config:senderKeys')}</h2>
            <Form.Item label={t('config:publicKey')}>
              <Input.TextArea rows={5} value={draft.senderPublicKey} onChange={(event) => setDraft({ ...draft, senderPublicKey: event.target.value })} />
            </Form.Item>
            <Form.Item label={t('config:privateKey')}>
              <Input.TextArea rows={5} value={draft.senderPrivateKey} onChange={(event) => setDraft({ ...draft, senderPrivateKey: event.target.value })} />
            </Form.Item>
            <Form.Item label={t('config:passphrase')}>
              <Input.Password value={draft.senderPrivateKeyPassphrase} onChange={(event) => setDraft({ ...draft, senderPrivateKeyPassphrase: event.target.value })} />
            </Form.Item>
          </section>
          <section className="asset-editor-section">
            <h2>{t('config:recipientKeys')}</h2>
            <Form.Item label={t('config:publicKey')}>
              <Input.TextArea rows={5} value={draft.recipientPublicKey} onChange={(event) => setDraft({ ...draft, recipientPublicKey: event.target.value })} />
            </Form.Item>
            <Form.Item label={t('config:privateKey')}>
              <Input.TextArea rows={5} value={draft.recipientPrivateKey} onChange={(event) => setDraft({ ...draft, recipientPrivateKey: event.target.value })} />
            </Form.Item>
            <Form.Item label={t('config:passphrase')}>
              <Input.Password value={draft.recipientPrivateKeyPassphrase} onChange={(event) => setDraft({ ...draft, recipientPrivateKeyPassphrase: event.target.value })} />
            </Form.Item>
          </section>
        </Form>
      </div>
    </div>
  )
}

function environmentDraft(environment?: Environment): SaveEnvironmentInput {
  return environment
    ? {
        name: environment.name,
        baseUrl: environment.baseUrl,
        endpoints: environment.endpoints ?? {},
        variables: environment.variables ?? {},
        status: environment.status,
      }
    : structuredClone(emptyEnvironment)
}

function profileDraft(profile?: Profile): SaveProfileInput {
  return profile
    ? {
        name: profile.name,
        type: profile.type,
        senderPublicKey: profile.senderPublicKey ?? '',
        senderPrivateKey: profile.senderPrivateKey ?? '',
        senderPrivateKeyPassphrase: profile.senderPrivateKeyPassphrase ?? '',
        recipientPublicKey: profile.recipientPublicKey ?? '',
        recipientPrivateKey: profile.recipientPrivateKey ?? '',
        recipientPrivateKeyPassphrase: profile.recipientPrivateKeyPassphrase ?? '',
        environmentIds: profile.environmentIds ?? [],
        enabled: profile.enabled,
      }
    : structuredClone(emptyProfile)
}
