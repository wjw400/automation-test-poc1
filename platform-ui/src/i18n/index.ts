import dayjs from 'dayjs'
import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import 'dayjs/locale/zh-cn'
import { defaultNS, namespaces, resources, type SupportedLanguage } from './resources'

const languageStorageKey = 'platform-ui-language'
const supportedLanguages: SupportedLanguage[] = ['en-US', 'zh-CN']

function collectTranslationKeys(value: unknown, prefix = ''): string[] {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return prefix ? [prefix] : []
  }
  return Object.entries(value).flatMap(([key, child]) => (
    collectTranslationKeys(child, prefix ? `${prefix}.${key}` : key)
  ))
}

function validateTranslationResources() {
  const referenceKeys = new Set(collectTranslationKeys(resources['en-US']))
  const translatedKeys = new Set(collectTranslationKeys(resources['zh-CN']))
  const missing = [...referenceKeys].filter((key) => !translatedKeys.has(key))
  const extra = [...translatedKeys].filter((key) => !referenceKeys.has(key))
  if (missing.length || extra.length) {
    throw new Error([
      missing.length ? `Missing zh-CN translation keys: ${missing.join(', ')}` : '',
      extra.length ? `Unexpected zh-CN translation keys: ${extra.join(', ')}` : '',
    ].filter(Boolean).join('\n'))
  }
}

validateTranslationResources()

export function normalizeLanguage(language?: string | null): SupportedLanguage {
  if (language?.toLowerCase().startsWith('zh')) {
    return 'zh-CN'
  }
  return 'en-US'
}

function detectInitialLanguage() {
  const storedLanguage = window.localStorage.getItem(languageStorageKey)
  if (supportedLanguages.includes(storedLanguage as SupportedLanguage)) {
    return storedLanguage as SupportedLanguage
  }
  return normalizeLanguage(window.navigator.language)
}

void i18n
  .use(initReactI18next)
  .init({
    resources,
    lng: detectInitialLanguage(),
    fallbackLng: 'en-US',
    supportedLngs: supportedLanguages,
    ns: [...namespaces],
    defaultNS,
    interpolation: {
      escapeValue: false,
    },
    returnNull: false,
  })

function applyLanguage(language: string) {
  const normalizedLanguage = normalizeLanguage(language)
  window.localStorage.setItem(languageStorageKey, normalizedLanguage)
  document.documentElement.lang = normalizedLanguage
  dayjs.locale(normalizedLanguage === 'zh-CN' ? 'zh-cn' : 'en')
}

applyLanguage(i18n.resolvedLanguage ?? i18n.language)
i18n.on('languageChanged', applyLanguage)

export function formatDateTime(value: string | number | Date, language = i18n.resolvedLanguage) {
  return new Intl.DateTimeFormat(normalizeLanguage(language), {
    dateStyle: 'short',
    timeStyle: 'medium',
  }).format(new Date(value))
}

export default i18n
