import '@stoplight/elements/web-components.min.js'
import { createElement, useEffect, useRef } from 'react'

type StoplightApiProps = {
  apiDescriptionUrl: string
}

export function StoplightApi({ apiDescriptionUrl }: StoplightApiProps) {
  const elementRef = useRef<HTMLElement | null>(null)

  useEffect(() => {
    const element = elementRef.current
    if (!element) {
      return
    }

    element.apiDescriptionUrl = apiDescriptionUrl
    element.layout = 'responsive'
    element.router = 'hash'
    element.hideTryIt = true
    element.hideTryItPanel = true
    element.hideExport = true
  }, [apiDescriptionUrl])

  return createElement('elements-api', { ref: elementRef })
}
