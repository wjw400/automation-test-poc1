import { GlobalOutlined } from '@ant-design/icons'
import { Select } from 'antd'
import { useTranslation } from 'react-i18next'
import { normalizeLanguage } from '../i18n'
import type { SupportedLanguage } from '../i18n/resources'

export function LanguageSwitcher() {
  const { t, i18n } = useTranslation('common')
  const language = normalizeLanguage(i18n.resolvedLanguage)

  return (
    <Select<SupportedLanguage>
      aria-label={t('language.label')}
      className="language-switcher"
      size="small"
      suffixIcon={<GlobalOutlined />}
      value={language}
      options={[
        { value: 'en-US', label: t('language.english') },
        { value: 'zh-CN', label: t('language.simplifiedChinese') },
      ]}
      onChange={(nextLanguage) => void i18n.changeLanguage(nextLanguage)}
    />
  )
}
