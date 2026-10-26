import { Form, Input, Typography } from 'antd'
import { useId } from 'react'
import { useTranslation } from 'react-i18next'
import type { SaveProfileInput } from '../types/platform'

export function PgpKeyFields({ value, onChange }: {
  value: SaveProfileInput
  onChange: (value: SaveProfileInput) => void
}) {
  const { t } = useTranslation('config')
  const id = useId()

  return <>
    <section className="asset-editor-section">
      <h2>{t('ourKeys')}</h2>
      <Typography.Paragraph type="secondary">{t('ourKeysHelp')}</Typography.Paragraph>
      <Form.Item label={t('signingPrivateKey')} htmlFor={`${id}-signing-key`}>
        <Input.TextArea id={`${id}-signing-key`} rows={5} value={value.signingPrivateKey}
          onChange={(event) => onChange({ ...value, signingPrivateKey: event.target.value })} />
      </Form.Item>
      <Form.Item label={t('signingPassphrase')} htmlFor={`${id}-signing-passphrase`}>
        <Input.Password id={`${id}-signing-passphrase`} value={value.signingPrivateKeyPassphrase}
          onChange={(event) => onChange({ ...value, signingPrivateKeyPassphrase: event.target.value })} />
      </Form.Item>
      <Form.Item label={t('decryptionPrivateKey')} htmlFor={`${id}-decryption-key`}>
        <Input.TextArea id={`${id}-decryption-key`} rows={5} value={value.decryptionPrivateKey}
          onChange={(event) => onChange({ ...value, decryptionPrivateKey: event.target.value })} />
      </Form.Item>
      <Form.Item label={t('decryptionPassphrase')} htmlFor={`${id}-decryption-passphrase`}>
        <Input.Password id={`${id}-decryption-passphrase`} value={value.decryptionPrivateKeyPassphrase}
          onChange={(event) => onChange({ ...value, decryptionPrivateKeyPassphrase: event.target.value })} />
      </Form.Item>
    </section>
    <section className="asset-editor-section">
      <h2>{t('counterpartyKeys')}</h2>
      <Typography.Paragraph type="secondary">{t('counterpartyKeysHelp')}</Typography.Paragraph>
      <Form.Item label={t('encryptionPublicKey')} htmlFor={`${id}-encryption-key`}>
        <Input.TextArea id={`${id}-encryption-key`} rows={5} value={value.encryptionPublicKey}
          onChange={(event) => onChange({ ...value, encryptionPublicKey: event.target.value })} />
      </Form.Item>
      <Form.Item label={t('verificationPublicKey')} htmlFor={`${id}-verification-key`}>
        <Input.TextArea id={`${id}-verification-key`} rows={5} value={value.verificationPublicKey}
          onChange={(event) => onChange({ ...value, verificationPublicKey: event.target.value })} />
      </Form.Item>
    </section>
  </>
}
