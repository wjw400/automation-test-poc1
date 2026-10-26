import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Button, Collapse, Divider, Input, Select, Space, Spin, Tag, Typography } from 'antd'
import { createContext, useContext, useEffect, useRef, useState } from 'react'
import { platformApi } from '../api/platformApi'
import type { MessageProcessingRule, OperationProcessingConfiguration, OperationProcessingSettings } from '../types/platform'
import { JsonPane } from './JsonPane'
import { processingStages } from '../utils/operationProcessing'

const { Text } = Typography
const emptyRule = (): MessageProcessingRule => ({ protection: 'none', ciphertext: { location: 'json-pointer', pointer: '' } })
const InputValidity = createContext<(label: string, invalid: boolean) => void>(() => undefined)

export function OperationProcessingEditor({ projectId, operationId, registry }: {
  projectId: string
  operationId: string
  registry: Record<string, Record<string, unknown>>
}) {
  const query = useQuery({
    queryKey: ['operation-processing', projectId, operationId],
    queryFn: () => platformApi.getOperationProcessing(projectId, operationId),
  })
  if (query.isPending) return <Spin />
  if (query.isError || !query.data) return <Alert type="error" showIcon title="无法加载接口处理配置" />
  return <ProcessingForm key={`${operationId}:${query.data.revision}`} settings={query.data} projectId={projectId} operationId={operationId} registry={registry} />
}

function ProcessingForm({ settings, projectId, operationId, registry }: {
  settings: OperationProcessingSettings
  projectId: string
  operationId: string
  registry: Record<string, Record<string, unknown>>
}) {
  const { message } = App.useApp()
  const client = useQueryClient()
  const [configuration, setConfiguration] = useState<OperationProcessingConfiguration>(() => structuredClone(settings.configuration ?? {
    version: 1, request: emptyRule(), responses: { default: emptyRule() },
  }))
  const [newStatus, setNewStatus] = useState('')
  const [invalidInputs, setInvalidInputs] = useState<Record<string, boolean>>({})
  const inputValidity = (label: string, invalid: boolean) => setInvalidInputs((current) => current[label] === invalid ? current : { ...current, [label]: invalid })
  const refresh = async () => {
    await Promise.all([
      client.invalidateQueries({ queryKey: ['operation-processing', projectId, operationId] }),
      client.invalidateQueries({ queryKey: ['api-spec-contract', projectId] }),
      client.invalidateQueries({ queryKey: ['operation-contract', projectId, operationId] }),
    ])
  }
  const save = useMutation({
    mutationFn: () => platformApi.saveOperationProcessing(projectId, operationId, settings.revision, configuration),
    onSuccess: async () => { await refresh(); void message.success('接口处理配置已保存') },
    onError: (error: any) => void message.error(error.response?.data?.message ?? error.message),
  })
  const reset = useMutation({
    mutationFn: () => platformApi.useImportedProcessing(projectId, operationId, settings.revision),
    onSuccess: refresh,
    onError: (error: any) => void message.error(error.response?.data?.message ?? error.message),
  })
  const stages = processingStages(configuration)
  return <InputValidity.Provider value={inputValidity}><div className="tab-stack operation-processing-editor">
    <Space wrap>
      <Text strong>请求与响应处理</Text>
      <Tag>{settings.source === 'MANUAL' ? '平台手动配置' : 'OpenAPI 扩展导入'}</Tag>
      <Text type="secondary">版本 {settings.revision}</Text>
    </Space>
    <Text type="secondary">在这里配置一次，用例自动使用。PGP 由平台执行；请求与响应的密文位置分别设置。</Text>
    {settings.needsReview ? <Alert type="warning" showIcon title="OpenAPI 已更新，请复核处理配置" description="手动配置已保留。核对下方配置后保存，或选择使用本次导入配置。复核完成前将阻止自动处理的执行。" /> : null}
    {settings.importError ? <Alert type="warning" showIcon title="导入扩展需要处理" description={settings.importError} /> : null}
    <RuleEditor title="请求" rule={configuration.request} direction="request" registry={registry}
      onChange={(request) => setConfiguration({ ...configuration, request })} />
    <Divider titlePlacement="start">响应规则</Divider>
    <Text type="secondary">按实际 HTTP 状态匹配：精确状态码 → 范围（如 2XX）→ default。普通 JSON 错误响应选择“不处理”。</Text>
    {Object.entries(configuration.responses).map(([status, rule]) => <div className="processing-response-rule" key={status}>
      <Space><Text strong>HTTP {status}</Text><Button size="small" type="text" danger onClick={() => {
        const responses = { ...configuration.responses }; delete responses[status]; setConfiguration({ ...configuration, responses })
      }}>删除规则</Button></Space>
      <RuleEditor title={`响应 ${status}`} rule={rule} direction="response" registry={registry}
        onChange={(next) => setConfiguration({ ...configuration, responses: { ...configuration.responses, [status]: next } })} />
    </div>)}
    <Space>
      <Input aria-label="新增响应状态" placeholder="200、2XX 或 default" value={newStatus} onChange={(event) => setNewStatus(event.target.value)} />
      <Button disabled={!/^(?:[1-5][0-9]{2}|[1-5]XX|default)$/.test(newStatus) || newStatus in configuration.responses} onClick={() => {
        setConfiguration({ ...configuration, responses: { ...configuration.responses, [newStatus]: emptyRule() } }); setNewStatus('')
      }}>添加响应规则</Button>
    </Space>
    <Divider titlePlacement="start">实际执行顺序</Divider>
    <ol className="processing-execution-order">{stages.map((stage) => <li key={stage}>{stage}</li>)}</ol>
    <Space wrap>
      <Button type="primary" disabled={Object.values(invalidInputs).some(Boolean)} loading={save.isPending} onClick={() => save.mutate()}>保存接口配置</Button>
      <Button loading={reset.isPending} disabled={!!settings.importError} onClick={() => reset.mutate()}>{settings.importedConfiguration ? '使用导入配置' : '恢复为未配置'}</Button>
      <Button disabled={Object.values(invalidInputs).some(Boolean)} onClick={() => {
        const url = URL.createObjectURL(new Blob([JSON.stringify({ operationId, 'x-platform-processing': configuration }, null, 2)], { type: 'application/json' }))
        const anchor = document.createElement('a'); anchor.href = url; anchor.download = 'operation-processing.json'; anchor.click(); URL.revokeObjectURL(url)
      }}>导出当前配置</Button>
    </Space>
    {settings.needsReview || settings.source === 'MANUAL' ? <Collapse items={[{
      key: 'imported', label: '本次 OpenAPI 导入的配置（用于比较）', children: <JsonPane value={settings.importedConfiguration} emptyLabel="文档未声明处理配置" />,
    }]} /> : null}
  </div></InputValidity.Provider>
}

function RuleEditor({ title, rule, direction, registry, onChange }: {
  title: string
  rule: MessageProcessingRule
  direction: 'request' | 'response'
  registry: Record<string, Record<string, unknown>>
  onChange: (rule: MessageProcessingRule) => void
}) {
  const request = direction === 'request'
  const choices = request
    ? [{ value: 'none', label: '不处理' }, { value: 'pgp-encrypt', label: 'PGP 加密' }, { value: 'pgp-sign-encrypt', label: 'PGP 签名并加密' }]
    : [{ value: 'none', label: '普通 JSON，不解密' }, { value: 'pgp-decrypt', label: 'PGP 解密' }, { value: 'pgp-decrypt-verify', label: 'PGP 解密并要求有效签名' }]
  const schema = rule.businessSchema as Record<string, unknown> | null
  const inline = !!schema && !schema.$ref
  return <div className="processing-rule-fields">
    <label><Text>{title}处理方式</Text><Select aria-label={`${title}处理方式`} value={rule.protection} options={choices}
      onChange={(protection) => onChange({ ...rule, protection })} /></label>
    {rule.protection !== 'none' ? <>
      <label><Text>密文{request ? '写入' : '读取'}位置</Text><Select aria-label={`${title}密文位置`} value={rule.ciphertext?.location ?? 'json-pointer'} options={[
        { value: 'json-pointer', label: 'JSON 字段（支持嵌套）' }, { value: 'body', label: '整个 HTTP body（ASCII Armor 文本）' },
      ]} onChange={(location) => onChange({ ...rule, ciphertext: { ...rule.ciphertext, location } })} /></label>
      {rule.ciphertext?.location !== 'body' ? <label><Text>字段路径（JSON Pointer）</Text><Input aria-label={`${title}密文路径`} placeholder="/data/encryptdata" value={rule.ciphertext?.pointer ?? ''}
        onChange={(event) => onChange({ ...rule, ciphertext: { location: 'json-pointer', pointer: event.target.value } })} />
        <Text type="secondary">例如 /payload 或 /data/encryptdata；字段名中的 / 写成 ~1，~ 写成 ~0。</Text></label> : null}
      {request && rule.ciphertext?.location !== 'body' ? <Collapse items={[{
        key: 'envelope', label: '请求外围字段（可选）', children: <JsonInput value={rule.envelope ?? {}} onChange={(envelope) => onChange({ ...rule, envelope })} label="请求外围结构" />,
      }]} /> : null}
    </> : null}
    <label><Text>明文业务结构（可选）</Text><Select aria-label={`${title}业务结构`} allowClear placeholder="选择 OpenAPI 中的 Schema" value={inline ? '__inline' : schema?.$ref as string | undefined}
      options={[...Object.keys(registry).map((ref) => ({ value: ref, label: ref.split('/').at(-1) ?? ref })), { value: '__inline', label: '自定义 Schema' }]}
      onChange={(ref) => onChange({ ...rule, businessSchema: ref === '__inline' ? { type: 'object' } : ref ? { $ref: ref } : undefined })} /></label>
    {inline ? <JsonInput value={rule.businessSchema} label={`${title}自定义 Schema`} onChange={(businessSchema) => onChange({ ...rule, businessSchema })} /> : null}
  </div>
}

function JsonInput({ value, label, onChange }: { value: unknown; label: string; onChange: (value: unknown) => void }) {
  const reportValidity = useContext(InputValidity)
  const validityRef = useRef(reportValidity)
  validityRef.current = reportValidity
  useEffect(() => () => validityRef.current(label, false), [label])
  const [text, setText] = useState(() => JSON.stringify(value, null, 2))
  const [error, setError] = useState('')
  return <div><Input.TextArea aria-label={label} rows={5} value={text} status={error ? 'error' : undefined} onChange={(event) => {
    setText(event.target.value)
    try { const parsed = JSON.parse(event.target.value); if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error('请输入 JSON 对象'); onChange(parsed); setError(''); reportValidity(label, false) }
    catch (issue) { setError(issue instanceof Error ? issue.message : 'JSON 格式不正确'); reportValidity(label, true) }
  }} />{error ? <Text type="danger">{error}；请修正后再保存。</Text> : null}</div>
}

