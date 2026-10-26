import { App as AntApp } from 'antd'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { platformApi } from '../api/platformApi'
import type { OperationProcessingSettings } from '../types/platform'
import { OperationProcessingEditor } from './OperationProcessingEditor'
import { processingStages } from '../utils/operationProcessing'

vi.mock('../api/platformApi', () => ({ platformApi: {
  getOperationProcessing: vi.fn(), saveOperationProcessing: vi.fn(), useImportedProcessing: vi.fn(),
} }))

const settings: OperationProcessingSettings = {
  source: 'IMPORTED', revision: 4, needsReview: false, importedConfiguration: null,
  configuration: {
    version: 1,
    request: { protection: 'pgp-sign-encrypt', ciphertext: { location: 'json-pointer', pointer: '/payload' } },
    responses: {
      '200': { protection: 'pgp-decrypt-verify', ciphertext: { location: 'json-pointer', pointer: '/resultData' } },
      '400': { protection: 'none' },
    },
  },
}

beforeEach(() => {
  vi.mocked(platformApi.getOperationProcessing).mockResolvedValue(structuredClone(settings))
  vi.mocked(platformApi.saveOperationProcessing).mockResolvedValue({ ...settings, revision: 5, source: 'MANUAL' })
})
afterEach(() => { cleanup(); vi.clearAllMocks() })

function showEditor() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={client}><AntApp><OperationProcessingEditor projectId="project" operationId="operation" registry={{}} /></AntApp></QueryClientProvider>)
}

describe('OperationProcessingEditor', () => {
  it('edits a nested request pointer independently of the response mapping', async () => {
    const user = userEvent.setup()
    showEditor()
    const request = await screen.findByRole('textbox', { name: '请求密文路径' })
    await user.clear(request)
    await user.type(request, '/data/encryptdata')
    expect(screen.getByRole('textbox', { name: '响应 200密文路径' })).toHaveValue('/resultData')
    await user.click(screen.getByRole('button', { name: '保存接口配置' }))
    await waitFor(() => expect(platformApi.saveOperationProcessing).toHaveBeenCalledWith('project', 'operation', 4,
      expect.objectContaining({
        request: expect.objectContaining({ ciphertext: { location: 'json-pointer', pointer: '/data/encryptdata' } }),
        responses: expect.objectContaining({ '200': expect.objectContaining({ ciphertext: { location: 'json-pointer', pointer: '/resultData' } }) }),
      })))
  })

  it('blocks saving invalid envelope JSON instead of using stale valid values', async () => {
    const user = userEvent.setup()
    showEditor()
    await screen.findByRole('textbox', { name: '请求密文路径' })
    await user.click(screen.getByText('请求外围字段（可选）'))
    const input = screen.getByRole('textbox', { name: '请求外围结构' })
    await user.clear(input)
    await user.type(input, 'invalid-json')
    expect(screen.getByRole('button', { name: '保存接口配置' })).toBeDisabled()
    expect(platformApi.saveOperationProcessing).not.toHaveBeenCalled()
    await user.clear(input)
    await user.type(input, '{{}')
    expect(screen.getByRole('button', { name: '保存接口配置' })).toBeEnabled()
  })

  it('shows plaintext and encrypted response rules accurately in the execution preview', () => {
    expect(processingStages(settings.configuration!)).toEqual(expect.arrayContaining([
      '将密文写入 /payload',
      '响应 200：从 /resultData 提取密文，PGP 解密并验签',
      '响应 400：直接读取普通 JSON',
    ]))
  })
})
