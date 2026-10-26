import { App as AntApp } from 'antd'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { platformApi } from '../api/platformApi'
import i18n from '../i18n'
import type { ApiOperationRecord, OperationProcessingSettings, SaveCaseInput } from '../types/platform'
import { parseDsl, stringifyDsl } from '../utils/caseDsl'
import { CaseDesigner } from './CaseDesigner'

vi.mock('../api/platformApi', () => ({ platformApi: {
  getOperationProcessing: vi.fn(), getApiSpecContract: vi.fn(), saveOperationProcessing: vi.fn(), useImportedProcessing: vi.fn(),
} }))

const operation: ApiOperationRecord = {
  id: 'secure', projectId: 'project', apiSpecId: 'spec', method: 'POST', path: '/orders',
  summary: 'Create order', requestBody: {}, responses: {}, createdAt: '',
}
let settings: OperationProcessingSettings

beforeEach(async () => {
  await i18n.changeLanguage('en-US')
  settings = {
    source: 'IMPORTED', revision: 4, needsReview: false, importedConfiguration: null,
    configuration: {
      version: 1,
      request: { protection: 'pgp-sign-encrypt', ciphertext: { location: 'json-pointer', pointer: '/payload' } },
      responses: {
        '200': { protection: 'pgp-decrypt-verify', ciphertext: { location: 'json-pointer', pointer: '/result/encryptdata' } },
        '400': { protection: 'none' },
      },
    },
  }
  vi.mocked(platformApi.getOperationProcessing).mockImplementation(async () => structuredClone(settings))
  vi.mocked(platformApi.getApiSpecContract).mockResolvedValue({ schemaRegistry: {}, operations: [] } as any)
  vi.mocked(platformApi.saveOperationProcessing).mockImplementation(async (_project, _operation, _revision, configuration) => {
    settings = { ...settings, configuration, revision: 5, source: 'MANUAL' }
    return structuredClone(settings)
  })
})
afterEach(() => { cleanup(); vi.clearAllMocks() })

function show(mode = 'automatic', beforeType = 'body.buildJson') {
  const onChange = vi.fn()
  const initial: SaveCaseInput = {
    name: 'Order', code: 'order', description: '', tags: [], dslVersion: 'v1', status: 'ACTIVE',
    content: stringifyDsl({ dslVersion: 'v1', name: 'Order', runtime: {}, variables: {}, steps: [{
      name: 'Create order', type: 'http', processingMode: mode,
      operation: { id: operation.id, apiSpecId: operation.apiSpecId, method: operation.method, path: operation.path },
      runtime: {}, request: { body: { orderId: '42' } },
      actions: {
        beforeSend: [{ type: beforeType, name: 'Prepare business data', template: { orderId: '42' }, output: { target: 'request.body' } }],
        afterRequestProcessing: [{ type: 'data.encodeBase64', name: 'Adjust wire body', input: { source: 'request.body', format: 'json' }, output: { target: 'request.body' } }],
        afterReceive: [],
      }, response: { status: { operator: 'equals', expected: 200 }, assertions: [], extract: {} },
    }] }),
  }
  function Harness() {
    const [draft, setDraft] = useState(initial)
    return <CaseDesigner draft={draft} operations={[operation]} view="step" activeStepIndex={0}
      onChange={(next) => { setDraft(next); onChange(next) }} onSelectCaseOverview={() => undefined}
      onSelectStep={() => undefined} onOpenRaw={() => undefined} />
  }
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const view = render(<QueryClientProvider client={client}><AntApp><Harness /></AntApp></QueryClientProvider>)
  return { ...view, onChange }
}

it('places inherited Actions between business and wire Actions, keeps them read-only and out of authored DSL', async () => {
  const user = userEvent.setup()
  const { container, onChange } = show()
  await user.click(screen.getByRole('tab', { name: 'Before' }))
  await screen.findByRole('region', { name: 'PGP sign and encrypt' })
  const cards = [...container.querySelectorAll('.operation-action-flow .rule-card')]
  expect(cards.map((card) => card.textContent)).toEqual([
    expect.stringContaining('Prepare business data'), expect.stringContaining('PGP sign and encrypt'),
    expect.stringContaining('Build encrypted request body'), expect.stringContaining('Adjust wire body'),
  ])
  expect(within(cards[1] as HTMLElement).queryByRole('button')).not.toBeInTheDocument()
  expect(within(cards[2] as HTMLElement).queryByRole('button')).not.toBeInTheDocument()
  expect(within(cards[1] as HTMLElement).getByText('From API · fixed order')).toBeInTheDocument()
  const name = screen.getByDisplayValue('Prepare business data')
  await user.clear(name)
  await user.type(name, 'Prepare order')
  const latest = onChange.mock.calls.at(-1)![0] as SaveCaseInput
  const step = (parseDsl(latest.content).data!.steps as any[])[0]
  expect(step.actions.beforeSend).toHaveLength(1)
  expect(step.actions.afterRequestProcessing).toHaveLength(1)
  expect(step.actions.beforeSend[0].name).toBe('Prepare order')
  expect(latest.content).not.toContain('crypto.pgp.signAndEncrypt')
  expect(latest.content).not.toContain('processingSnapshot')
})

it('shows conditional response decoding before scenario Actions and skips it in manual mode', async () => {
  const user = userEvent.setup()
  show()
  await user.click(screen.getByRole('tab', { name: 'After' }))
  const rules = await screen.findByLabelText('Decode response by HTTP status')
  expect(within(rules).getByText('PGP decrypt and verify')).toBeInTheDocument()
  expect(within(rules).getByText('Read plain JSON')).toBeInTheDocument()
  expect(within(rules).getByText(/Only one matching rule runs/)).toBeInTheDocument()
  expect(within(rules).getByText(/\/result\/encryptdata/)).toBeInTheDocument()
  await user.click(screen.getByRole('combobox', { name: 'Action mode' }))
  await user.click(screen.getByText('Manual Actions; skip API processing'))
  expect(screen.queryByLabelText('Decode response by HTTP status')).not.toBeInTheDocument()
  expect(screen.getByText(/API processing is skipped/)).toBeInTheDocument()
})

it('skips inherited request Actions in wire mode while keeping response processing', async () => {
  const user = userEvent.setup()
  show('wire')
  await user.click(screen.getByRole('tab', { name: 'Before' }))
  await screen.findByText(/The body is already a wire message/)
  expect(screen.queryByRole('region', { name: 'PGP sign and encrypt' })).not.toBeInTheDocument()
  await user.click(screen.getByRole('tab', { name: 'After' }))
  expect(await screen.findByText('PGP decrypt and verify')).toBeInTheDocument()
})

it('edits the shared API configuration and refreshes inherited Actions without changing the scenario', async () => {
  const user = userEvent.setup()
  const { onChange } = show()
  await user.click(screen.getByRole('tab', { name: 'Before' }))
  await user.click(screen.getByRole('button', { name: 'Edit API processing' }))
  expect(await screen.findByText(/This configuration is shared/)).toBeInTheDocument()
  const pointer = await screen.findByRole('textbox', { name: '请求密文路径' })
  await user.clear(pointer)
  await user.type(pointer, '/data/encryptdata')
  await user.click(screen.getByRole('button', { name: '保存接口配置' }))
  await waitFor(() => expect(platformApi.saveOperationProcessing).toHaveBeenCalledWith('project', 'secure', 4,
    expect.objectContaining({ request: expect.objectContaining({ ciphertext: { location: 'json-pointer', pointer: '/data/encryptdata' } }) })))
  await waitFor(() => expect(screen.getByText(/Write ciphertext to \/data\/encryptdata/)).toBeInTheDocument())
  expect(onChange).not.toHaveBeenCalled()
})

it('warns about existing duplicate crypto and does not offer another inherited crypto action', async () => {
  const user = userEvent.setup()
  show('automatic', 'crypto.pgp.signAndEncrypt')
  await user.click(screen.getByRole('tab', { name: 'Before' }))
  await screen.findByText(/PGP is already inherited/)
  await user.click(screen.getAllByRole('button', { name: /Add Action/ })[0])
  expect(screen.queryByRole('menuitem', { name: 'PGP Sign & Encrypt' })).not.toBeInTheDocument()
  expect(screen.queryByRole('menuitem', { name: 'PGP Encrypt' })).not.toBeInTheDocument()
  expect(screen.getByRole('menuitem', { name: 'Build JSON Body' })).toBeInTheDocument()
})
