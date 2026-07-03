import type * as React from 'react'

declare module '@stoplight/elements/web-components.min.js'

declare global {
  namespace JSX {
    interface IntrinsicElements {
      'elements-api': React.DetailedHTMLProps<React.HTMLAttributes<HTMLElement>, HTMLElement>
    }
  }

  interface HTMLElement {
    apiDescriptionUrl?: string
    layout?: 'sidebar' | 'stacked' | 'responsive'
    router?: 'history' | 'memory' | 'hash' | 'static'
    hideTryIt?: boolean
    hideTryItPanel?: boolean
    hideExport?: boolean
  }
}

export {}
