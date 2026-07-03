import YAML from 'yaml'

export type DslObject = Record<string, any>

export function parseDsl(content: string): { data: DslObject | null; error: string | null } {
  try {
    const parsed = YAML.parse(content)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return { data: null, error: 'Case DSL root must be an object.' }
    }
    return { data: parsed as DslObject, error: null }
  } catch (error) {
    return {
      data: null,
      error: error instanceof Error ? error.message : 'Failed to parse case DSL.',
    }
  }
}

export function stringifyDsl(data: DslObject) {
  return YAML.stringify(data, {
    indent: 2,
    lineWidth: 0,
    minContentWidth: 0,
  })
}

export function parseStructuredText(value: string) {
  if (!value.trim()) {
    return {}
  }
  return YAML.parse(value)
}

export function stringifyStructuredValue(value: unknown) {
  if (value === null || value === undefined) {
    return ''
  }
  if (typeof value === 'string') {
    return value
  }
  return YAML.stringify(value, {
    indent: 2,
    lineWidth: 0,
    minContentWidth: 0,
  }).trim()
}
