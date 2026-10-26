import { App as AntApp } from 'antd'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import i18n from '../i18n'
import { platformApi } from '../api/platformApi'
import type { Profile } from '../types/platform'
import { ProfileAssetEditor } from './ConfigAssetEditors'

vi.mock('../api/platformApi', () => ({ platformApi: {
  listEnvironments: vi.fn(), updateProfile: vi.fn(),
} }))

const profile: Profile = {
  id: 'identity', projectId: 'project', name: 'Merchant', type: 'PGP', enabled: true,
  environmentIds: ['test'], signingPrivateKey: 'our-signing-key', signingPrivateKeyPassphrase: 'signing-passphrase',
  decryptionPrivateKey: 'our-decryption-key', decryptionPrivateKeyPassphrase: 'decryption-passphrase',
  encryptionPublicKey: 'their-encryption-key', verificationPublicKey: 'their-verification-key',
}

beforeEach(async () => {
  await i18n.changeLanguage('zh-CN')
  vi.mocked(platformApi.listEnvironments).mockResolvedValue([
    { id: 'test', projectId: 'project', name: 'Test', baseUrl: 'http://localhost', variables: {}, endpoints: {}, status: 'ACTIVE' },
  ])
  vi.mocked(platformApi.updateProfile).mockResolvedValue({ ...profile, signingPrivateKey: 'rotated-signing-key' })
})
afterEach(() => { cleanup(); vi.clearAllMocks() })

it('groups our private keys and their public keys and saves a signing-key change independently', async () => {
  const user = userEvent.setup()
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><AntApp>
    <ProfileAssetEditor projectId="project" profile={profile} />
  </AntApp></QueryClientProvider>)

  expect(screen.getByRole('heading', { name: '我方签名与解密' })).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: '对方加密与验签' })).toBeInTheDocument()
  expect(screen.getByLabelText('解密私钥（我方）')).toHaveValue('our-decryption-key')
  expect(screen.getByLabelText('加密公钥（对方）')).toHaveValue('their-encryption-key')
  expect(screen.getByLabelText('验签公钥（对方）')).toHaveValue('their-verification-key')
  expect(screen.getByLabelText('签名私钥口令')).toHaveValue('signing-passphrase')
  expect(screen.getByLabelText('解密私钥口令')).toHaveValue('decryption-passphrase')
  const signing = screen.getByLabelText('签名私钥（我方）')
  await user.clear(signing)
  await user.type(signing, 'rotated-signing-key')
  await user.click(screen.getByRole('button', { name: /保\s*存/ }))

  const { id: _id, projectId: _projectId, ...expected } = profile
  await waitFor(() => expect(platformApi.updateProfile).toHaveBeenCalledWith('project', 'identity', {
    ...expected, signingPrivateKey: 'rotated-signing-key',
  }))
})
