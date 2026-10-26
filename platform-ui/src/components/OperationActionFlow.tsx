import { LockOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Drawer, Select, Space, Spin, Tag, Typography } from 'antd'
import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { platformApi } from '../api/platformApi'
import type { ApiOperationRecord, MessageProcessingRule } from '../types/platform'
import { OperationProcessingEditor } from './OperationProcessingEditor'

type FlowContent = { inherited: ReactNode; managesCrypto: boolean }
type Props = {
  operation: ApiOperationRecord | null
  direction: 'before' | 'after'
  mode: string
  onModeChange: (mode: string) => void
  children: (content: FlowContent) => ReactNode
}

export function OperationActionFlow(props: Props) {
  if (!props.operation) return <div className="operation-action-flow">{props.children({ inherited: null, managesCrypto: false })}</div>
  return <ConfiguredActionFlow {...props} operation={props.operation} />
}

function ConfiguredActionFlow({ operation, direction, mode, onModeChange, children }: Props & { operation: ApiOperationRecord }) {
  const { t } = useTranslation('scenario')
  const [editing, setEditing] = useState(false)
  const query = useQuery({
    queryKey: ['operation-processing', operation.projectId, operation.id],
    queryFn: () => platformApi.getOperationProcessing(operation.projectId, operation.id),
  })
  const configuration = query.data?.configuration
  const request = configuration?.request
  const requestManaged = mode === 'automatic' && request && request.protection !== 'none'
  const responses = Object.entries(configuration?.responses ?? {})
  const responseManaged = mode !== 'manual' && responses.some(([, rule]) => rule.protection !== 'none')
  const managesCrypto = Boolean(direction === 'before' ? requestManaged : responseManaged)
  const inherited = direction === 'before' ? requestManaged && request ? <>
    <InheritedAction title={t(request.protection === 'pgp-sign-encrypt' ? 'editor.actionFlow.signEncrypt' : 'editor.actionFlow.encrypt')}
      type={request.protection === 'pgp-sign-encrypt' ? 'crypto.pgp.signAndEncrypt' : 'crypto.pgp.encrypt'}>
      {t('editor.actionFlow.requestCryptoDetail')}
    </InheritedAction>
    <InheritedAction title={t('editor.actionFlow.envelope')} type="body.buildJson">
      {t('editor.actionFlow.envelopeDetail', { location: location(request) })}
    </InheritedAction>
  </> : null : mode !== 'manual' && responses.length ? <div className="rule-card inherited-action" aria-label={t('editor.actionFlow.responseRules')}>
    <div className="panel-title-row compact">
      <Typography.Text strong>{t('editor.actionFlow.responseRules')}</Typography.Text>
      <Tag icon={<LockOutlined />}>{t('editor.actionFlow.fromApi')}</Tag>
    </div>
    <Typography.Text type="secondary">{t('editor.actionFlow.responseSelection')}</Typography.Text>
    <div className="inherited-response-rules">
      {responses.map(([status, rule]) => <div className="inherited-response-rule" key={status}>
        <Tag>HTTP {status}</Tag>
        <div>
          <Typography.Text strong>{t(rule.protection === 'none' ? 'editor.actionFlow.plainResponse'
            : rule.protection === 'pgp-decrypt-verify' ? 'editor.actionFlow.decryptVerify' : 'editor.actionFlow.decrypt')}</Typography.Text>
          <Typography.Text type="secondary">{rule.protection === 'none' ? t('editor.actionFlow.plainResponseDetail')
            : t('editor.actionFlow.responseCryptoDetail', { location: location(rule) })}</Typography.Text>
          {rule.protection !== 'none' ? <code>{rule.protection === 'pgp-decrypt-verify' ? 'crypto.pgp.decryptAndVerify' : 'crypto.pgp.decrypt'}</code> : null}
        </div>
      </div>)}
    </div>
  </div> : null

  return <div className="operation-action-flow">
    <div className="action-flow-toolbar">
      <Space wrap>
        <Typography.Text>{t('editor.actionFlow.mode')}</Typography.Text>
        <Select aria-label={t('editor.actionFlow.mode')} value={mode} options={[
          { value: 'automatic', label: t('editor.actionFlow.automatic') },
          { value: 'wire', label: t('editor.actionFlow.wire') },
          { value: 'manual', label: t('editor.actionFlow.manual') },
        ]} onChange={onModeChange} />
      </Space>
      <Button size="small" onClick={() => setEditing(true)}>{t('editor.actionFlow.editApi')}</Button>
    </div>
    <Typography.Text type="secondary">{t(mode === 'manual' ? 'editor.actionFlow.manualHelp'
      : mode === 'wire' && direction === 'before' ? 'editor.actionFlow.wireHelp' : 'editor.actionFlow.orderHelp')}</Typography.Text>
    {query.isPending ? <Spin size="small" aria-label={t('editor.actionFlow.loading')} /> : null}
    {query.isError ? <Alert type="error" showIcon title={t('editor.actionFlow.loadError')} /> : null}
    {mode !== 'manual' && query.data?.needsReview ? <Alert type="warning" showIcon title={t('editor.actionFlow.reviewRequired')} /> : null}
    {mode !== 'manual' && query.data?.importError && query.data.source !== 'MANUAL' ? <Alert type="error" showIcon title={t('editor.actionFlow.importError')} description={query.data.importError} /> : null}
    {query.isSuccess && !configuration && mode !== 'manual' ? <Typography.Text type="secondary">{t('editor.actionFlow.noInherited')}</Typography.Text> : null}
    {children({ inherited, managesCrypto })}
    <Drawer title={t('editor.actionFlow.editApi')} open={editing} onClose={() => setEditing(false)} size={760} destroyOnHidden>
      <Alert className="action-flow-shared-warning" type="info" showIcon title={t('editor.actionFlow.sharedConfiguration')} />
      {editing ? <ApiProcessingConfiguration operation={operation} /> : null}
    </Drawer>
  </div>
}

function InheritedAction({ title, type, children }: { title: string; type: string; children: ReactNode }) {
  const { t } = useTranslation('scenario')
  return <section className="rule-card inherited-action" aria-label={title}>
    <div className="panel-title-row compact">
      <Typography.Text strong>{title}</Typography.Text>
      <Tag icon={<LockOutlined />}>{t('editor.actionFlow.fromApi')}</Tag>
    </div>
    <Typography.Text type="secondary">{children}</Typography.Text>
    <code>{type}</code>
  </section>
}

function ApiProcessingConfiguration({ operation }: { operation: ApiOperationRecord }) {
  const { t } = useTranslation('scenario')
  const query = useQuery({
    queryKey: ['api-spec-contract', operation.projectId, operation.apiSpecId],
    queryFn: () => platformApi.getApiSpecContract(operation.projectId, operation.apiSpecId),
  })
  if (query.isPending) return <Spin />
  if (query.isError) return <Alert type="error" showIcon title={t('editor.actionFlow.loadError')} />
  return <OperationProcessingEditor projectId={operation.projectId} operationId={operation.id} registry={query.data.schemaRegistry} />
}

function location(rule: MessageProcessingRule) {
  return rule.ciphertext?.location === 'body' ? 'HTTP body' : rule.ciphertext?.pointer ?? ''
}
