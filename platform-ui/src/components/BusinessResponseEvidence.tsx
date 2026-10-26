import { Typography } from 'antd'
import { useTranslation } from 'react-i18next'
import type { StepRun } from '../types/platform'
import { JsonPane } from './JsonPane'

export function BusinessResponseEvidence({ step }: { step: Pick<StepRun, 'decryptedPayloads' | 'response'> }) {
  const { t } = useTranslation('execution')
  const payloads = step.decryptedPayloads ?? {}
  // Match business.response.body in ApiCaseRunner: select the named payload
  // once, preserving null and any real "business" field inside the response.
  const business = Object.prototype.hasOwnProperty.call(payloads, 'business')
    ? payloads.business : step.response?.body
  const named = Object.entries(payloads).filter(([name]) => name !== 'business')

  return <div className="tab-stack">
    <section className="tab-stack">
      <Typography.Text strong>{t('businessResponse')}</Typography.Text>
      <Typography.Text type="secondary">{t('businessResponseHelp')}</Typography.Text>
      <code>business.response.body</code>
      <JsonPane value={business} emptyLabel={t('noBusinessResponse')} />
    </section>
    {named.length > 0 ? <section className="tab-stack">
      <Typography.Text strong>{t('namedDecrypted')}</Typography.Text>
      {named.map(([name, value]) => <div className="tab-stack" key={name}>
        <Typography.Text code>{`decrypted.${name}`}</Typography.Text>
        <Typography.Text type="secondary">{t('namedDecryptedHelp')}</Typography.Text>
        <JsonPane value={value} emptyLabel={t('noDecrypted')} />
      </div>)}
    </section> : null}
  </div>
}
