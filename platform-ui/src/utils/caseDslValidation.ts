import type { DslObject } from './caseDsl'

export type DslIssueSeverity = 'ERROR' | 'WARNING'

export type DslIssueSection =
  | 'step'
  | 'operation'
  | 'request'
  | 'params'
  | 'headers'
  | 'body'
  | 'before'
  | 'after'
  | 'assertions'
  | 'extract'

export type DslValidationIssue = {
  code: string
  severity: DslIssueSeverity
  path: string
  message: string
  stepIndex?: number
  section?: DslIssueSection
  field?: string
  mapped: boolean
}

const beforeActionTypes = new Set([
  'crypto.pgp.signAndEncrypt',
  'crypto.pgp.encrypt',
  'data.encodeBase64',
  'body.buildJson',
])

const afterActionTypes = new Set([
  'crypto.pgp.decryptAndVerify',
  'crypto.pgp.decrypt',
])

const assertionOperators = new Set([
  'equals',
  'notEquals',
  'exists',
  'notExists',
  'contains',
  'greaterThan',
  'lessThan',
])

export function validateCaseDslV1(dsl: DslObject): DslValidationIssue[] {
  const issues: DslValidationIssue[] = []
  validateUnknownFields(dsl, new Set(['dslVersion', 'name', 'description', 'tags', 'runtime', 'variables', 'profiles', 'steps', 'request', 'expect', 'extract']), '$', issues)
  if (dsl.dslVersion !== 'v1') {
    addIssue(issues, 'DSL_VERSION_INVALID', '$.dslVersion', 'dslVersion must be v1.')
  }
  if (typeof dsl.name !== 'string' || !dsl.name.trim()) {
    addIssue(issues, 'SCENARIO_NAME_INVALID', '$.name', 'name must be a non-empty string.')
  }
  if (dsl.description !== undefined && typeof dsl.description !== 'string') {
    addIssue(issues, 'DESCRIPTION_INVALID', '$.description', 'description must be a string.', {
      field: 'description',
      mapped: true,
    })
  }
  if (dsl.tags !== undefined && (!Array.isArray(dsl.tags) || dsl.tags.some((tag) => typeof tag !== 'string'))) {
    addIssue(issues, 'TAGS_INVALID', '$.tags', 'tags must be an array of strings.', {
      field: 'tags',
      mapped: true,
    })
  }
  if (dsl.runtime !== undefined && !isRecord(dsl.runtime)) {
    addIssue(issues, 'RUNTIME_INVALID', '$.runtime', 'runtime must be an object.')
  }
  if (dsl.profiles !== undefined && !isRecord(dsl.profiles)) {
    addIssue(issues, 'PROFILES_INVALID', '$.profiles', 'profiles must be an object.')
  }
  if (dsl.variables !== undefined && !isRecord(dsl.variables)) {
    addIssue(issues, 'VARIABLES_INVALID', '$.variables', 'variables must be an object.', {
      field: 'variables',
      mapped: true,
    })
  }
  if ('request' in dsl || 'expect' in dsl || 'extract' in dsl) {
    addIssue(
      issues,
      'LEGACY_ROOT_FIELDS',
      '$',
      'Top-level request, expect, and extract are legacy fields. Use steps[].request and steps[].response.',
    )
  }
  if (!Array.isArray(dsl.steps)) {
    addIssue(issues, 'STEPS_INVALID', '$.steps', 'steps must be an array.')
    return issues
  }

  dsl.steps.forEach((step: unknown, stepIndex: number) => {
    const prefix = `$.steps[${stepIndex}]`
    if (!isRecord(step)) {
      addIssue(issues, 'STEP_INVALID', prefix, `${displayPath(prefix)} must be an object.`, {
        stepIndex,
        section: 'step',
        mapped: true,
      })
      return
    }
    validateUnknownFields(step, new Set(['name', 'type', 'operation', 'runtime', 'request', 'actions', 'response', 'expect', 'extract', 'profile', 'continueOnFailure', 'processingMode']), prefix, issues, stepIndex)
    if (step.processingMode !== undefined && !['automatic', 'manual', 'wire'].includes(String(step.processingMode))) addIssue(issues, 'PROCESSING_MODE_INVALID', `${prefix}.processingMode`, 'processingMode must be automatic, manual, or wire.', { stepIndex })
    if (typeof step.name !== 'string' || !step.name.trim()) {
      addIssue(issues, 'STEP_NAME_INVALID', `${prefix}.name`, `${displayPath(prefix)}.name must be a non-empty string.`, { stepIndex })
    }
    if (step.type !== 'http') {
      addIssue(issues, 'STEP_TYPE_INVALID', `${prefix}.type`, `${displayPath(prefix)}.type must be http.`, {
        stepIndex,
        section: 'step',
      })
    }
    if (!isRecord(step.operation)) {
      addIssue(issues, 'OPERATION_INVALID', `${prefix}.operation`, `${displayPath(prefix)}.operation is required.`, {
        stepIndex,
        section: 'operation',
        field: 'operation',
        mapped: true,
      })
    } else if (!stringValue(step.operation.method) || !stringValue(step.operation.path)) {
      addIssue(issues, 'OPERATION_REFERENCE_MISSING', `${prefix}.operation`, 'Select an API operation.', {
        stepIndex,
        section: 'operation',
        field: 'operation',
        mapped: true,
      })
    }
    if (!isRecord(step.request)) {
      addIssue(issues, 'REQUEST_INVALID', `${prefix}.request`, `${displayPath(prefix)}.request must be an object.`, {
        stepIndex,
        section: 'request',
        mapped: true,
      })
    } else {
      validateUnknownFields(step.request, new Set(['path', 'headers', 'query', 'body', 'method', 'baseUrl', 'transform']), `${prefix}.request`, issues, stepIndex)
      if (step.request.path !== undefined && !isRecord(step.request.path)) {
        addIssue(issues, 'PATH_PARAMS_INVALID', `${prefix}.request.path`, 'request.path must be an object.', {
          stepIndex,
          section: 'params',
          field: 'path',
          mapped: true,
        })
      }
      if (step.request.query !== undefined && !isRecord(step.request.query)) {
        addIssue(issues, 'QUERY_INVALID', `${prefix}.request.query`, 'request.query must be an object.', {
          stepIndex,
          section: 'params',
          mapped: true,
        })
      }
      if (step.request.headers !== undefined && !isRecord(step.request.headers)) {
        addIssue(issues, 'HEADERS_INVALID', `${prefix}.request.headers`, 'request.headers must be an object.', {
          stepIndex,
          section: 'headers',
          mapped: true,
        })
      }
    }
    if (!isRecord(step.response)) {
      addIssue(issues, 'RESPONSE_INVALID', `${prefix}.response`, `${displayPath(prefix)}.response must be an object.`, {
        stepIndex,
      })
    }
    if ('expect' in step || 'extract' in step || 'profile' in step || 'continueOnFailure' in step) {
      addIssue(
        issues,
        'LEGACY_STEP_FIELDS',
        prefix,
        `${displayPath(prefix)} contains legacy fields. Use runtime, actions, and response instead.`,
        { stepIndex },
      )
    }
    if (isRecord(step.request) && ('method' in step.request || 'baseUrl' in step.request || 'transform' in step.request)) {
      addIssue(
        issues,
        'LEGACY_REQUEST_FIELDS',
        `${prefix}.request`,
        `${displayPath(prefix)}.request contains legacy method/baseUrl/transform fields. Use operation and actions.beforeSend.`,
        { stepIndex, section: 'request' },
      )
    }

    if (!isRecord(step.actions)) {
      addIssue(issues, 'ACTIONS_INVALID', `${prefix}.actions`, 'actions must be an object.', { stepIndex })
    }
    const actions = isRecord(step.actions) ? step.actions : {}
    validateUnknownFields(actions, new Set(['beforeSend', 'afterReceive', 'afterRequestProcessing']), `${prefix}.actions`, issues, stepIndex)
    validateActions(actions.afterRequestProcessing, beforeActionTypes, `${prefix}.actions.afterRequestProcessing`, stepIndex, 'before', issues)
    validateActions(actions.beforeSend, beforeActionTypes, `${prefix}.actions.beforeSend`, stepIndex, 'before', issues)
    validateActions(actions.afterReceive, afterActionTypes, `${prefix}.actions.afterReceive`, stepIndex, 'after', issues)

    const response = isRecord(step.response) ? step.response : {}
    validateUnknownFields(response, new Set(['status', 'assertions', 'extract']), `${prefix}.response`, issues, stepIndex)
    validateHttpStatusRule(response.status, `${prefix}.response.status`, stepIndex, issues)
    if (response.assertions !== undefined && !Array.isArray(response.assertions)) {
      addIssue(issues, 'ASSERTIONS_INVALID', `${prefix}.response.assertions`, 'response.assertions must be an array.', {
        stepIndex,
        section: 'assertions',
        mapped: true,
      })
    }
    if (response.extract !== undefined && !isRecord(response.extract)) {
      addIssue(issues, 'EXTRACT_INVALID', `${prefix}.response.extract`, 'response.extract must be an object.', {
        stepIndex,
        section: 'extract',
        mapped: true,
      })
    }

    const responseSources = availableResponseSources(actions.afterReceive)
    if (Array.isArray(response.assertions)) {
      response.assertions.forEach((assertion, assertionIndex) => {
        validateResponseRule(
          assertion,
          responseSources,
          `${prefix}.response.assertions[${assertionIndex}]`,
          stepIndex,
          'assertions',
          issues,
          true,
        )
        if (isRecord(assertion) && !assertionOperators.has(String(assertion.operator ?? ''))) {
          addIssue(
            issues,
            'ASSERTION_OPERATOR_INVALID',
            `${prefix}.response.assertions[${assertionIndex}].operator`,
            `operator must be one of: ${Array.from(assertionOperators).join(', ')}.`,
            { stepIndex, section: 'assertions', field: 'operator', mapped: true },
          )
        }
      })
    }
    if (isRecord(response.extract)) {
      Object.entries(response.extract).forEach(([name, extraction]) => {
        validateResponseRule(
          extraction,
          responseSources,
          `${prefix}.response.extract.${name}`,
          stepIndex,
          'extract',
          issues,
          false,
        )
      })
    }
  })
  return issues
}

function validateHttpStatusRule(
  value: unknown,
  prefix: string,
  stepIndex: number,
  issues: DslValidationIssue[],
) {
  const invalid = (path: string, message: string, field = 'status') => addIssue(
    issues,
    'RESPONSE_STATUS_INVALID',
    path,
    message,
    { stepIndex, section: 'assertions', field, mapped: true },
  )
  if (!isRecord(value)) {
    invalid(prefix, 'response.status must be an object using equals, oneOf, or range.')
    return
  }
  validateUnknownFields(value, new Set(['operator', 'expected', 'min', 'max']), prefix, issues, stepIndex)
  const operator = value.operator
  if (operator === 'equals') {
    if (!isHttpStatus(value.expected)) invalid(`${prefix}.expected`, 'equals requires an integer expected status from 100 to 599.', 'expected')
    if ('min' in value || 'max' in value) invalid(prefix, 'equals does not support min or max.')
    return
  }
  if (operator === 'oneOf') {
    if (!Array.isArray(value.expected) || !value.expected.length || value.expected.some((status) => !isHttpStatus(status))) {
      invalid(`${prefix}.expected`, 'oneOf requires at least one integer status from 100 to 599.', 'expected')
    } else if (new Set(value.expected).size !== value.expected.length) {
      invalid(`${prefix}.expected`, 'oneOf status values must be unique.', 'expected')
    }
    if ('min' in value || 'max' in value) invalid(prefix, 'oneOf does not support min or max.')
    return
  }
  if (operator === 'range') {
    if (!isHttpStatus(value.min)) invalid(`${prefix}.min`, 'range min must be an integer from 100 to 599.', 'min')
    if (!isHttpStatus(value.max)) invalid(`${prefix}.max`, 'range max must be an integer from 100 to 599.', 'max')
    if (isHttpStatus(value.min) && isHttpStatus(value.max) && value.min > value.max) {
      invalid(prefix, 'range min must not exceed max.')
    }
    if ('expected' in value) invalid(prefix, 'range does not support expected.')
    return
  }
  invalid(`${prefix}.operator`, 'operator must be equals, oneOf, or range.', 'operator')
}

function isHttpStatus(value: unknown): value is number {
  return Number.isInteger(value) && Number(value) >= 100 && Number(value) <= 599
}

export function issuesForStep(issues: DslValidationIssue[], stepIndex: number) {
  return issues.filter((issue) => issue.stepIndex === stepIndex)
}

export function issuesForPath(issues: DslValidationIssue[], path: string) {
  return issues.filter((issue) => issue.path === path || issue.path.startsWith(`${path}.`) || issue.path.startsWith(`${path}[`))
}

function validateActions(
  value: unknown,
  allowedTypes: Set<string>,
  prefix: string,
  stepIndex: number,
  section: 'before' | 'after',
  issues: DslValidationIssue[],
) {
  if (value === undefined) {
    return
  }
  if (!Array.isArray(value)) {
    addIssue(issues, 'ACTION_LIST_INVALID', prefix, `${displayPath(prefix)} must be an array.`, {
      stepIndex,
      section,
      mapped: true,
    })
    return
  }
  value.forEach((action, actionIndex) => {
    const actionPath = `${prefix}[${actionIndex}]`
    if (!isRecord(action)) {
      addIssue(issues, 'ACTION_INVALID', actionPath, `${displayPath(actionPath)} must be an object.`, {
        stepIndex,
        section,
        mapped: true,
      })
      return
    }
    validateUnknownFields(action, new Set(['type', 'name', 'enabled', 'input', 'output', 'template', 'profile']), actionPath, issues, stepIndex)
    if (!allowedTypes.has(String(action.type ?? ''))) {
      addIssue(issues, 'ACTION_TYPE_UNSUPPORTED', `${actionPath}.type`, 'Action type is not supported in this stage.', {
        stepIndex,
        section,
        field: 'type',
        mapped: true,
      })
    }
    if ('profile' in action) {
      addIssue(issues, 'ACTION_PROFILE_FORBIDDEN', `${actionPath}.profile`, 'Action cannot define profile. Use Step or Scenario runtime.profile.', {
        stepIndex,
        section,
      })
    }
  })
}

function validateResponseRule(
  value: unknown,
  availableSources: Set<string>,
  prefix: string,
  stepIndex: number,
  section: 'assertions' | 'extract',
  issues: DslValidationIssue[],
  assertion: boolean,
) {
  if (!isRecord(value)) {
    addIssue(issues, assertion ? 'ASSERTION_INVALID' : 'EXTRACTION_INVALID', prefix, `${displayPath(prefix)} must be an object.`, {
      stepIndex,
      section,
      mapped: true,
    })
    return
  }
  validateUnknownFields(
    value,
    assertion ? new Set(['source', 'path', 'operator', 'expected']) : new Set(['source', 'path']),
    prefix,
    issues,
    stepIndex,
  )
  if (typeof value.source !== 'string' || !availableSources.has(value.source)) {
    addIssue(
      issues,
      assertion ? 'ASSERTION_SOURCE_INVALID' : 'EXTRACTION_SOURCE_INVALID',
      `${prefix}.source`,
      `source must be one of: ${Array.from(availableSources).join(', ')}.`,
      { stepIndex, section, field: 'source', mapped: true },
    )
  }
  if (typeof value.path !== 'string' || !value.path.trim()) {
    addIssue(
      issues,
      assertion ? 'ASSERTION_PATH_INVALID' : 'EXTRACTION_PATH_INVALID',
      `${prefix}.path`,
      'path must be a non-empty JSONPath.',
      { stepIndex, section, field: 'path', mapped: true },
    )
  }
}

function availableResponseSources(value: unknown) {
  const sources = new Set(['response.body', 'wire.response.body', 'business.response.body'])
  if (!Array.isArray(value)) {
    return sources
  }
  value.forEach((action) => {
    if (!isRecord(action) || action.enabled === false || !isRecord(action.output)) {
      return
    }
    const target = action.output.target
    if (typeof target === 'string' && /^decrypted[.][A-Za-z][A-Za-z0-9_-]*$/.test(target)) {
      sources.add(target)
    }
  })
  return sources
}

function addIssue(
  issues: DslValidationIssue[],
  code: string,
  path: string,
  message: string,
  details: Partial<Omit<DslValidationIssue, 'code' | 'path' | 'message' | 'severity'>> = {},
) {
  issues.push({
    code,
    severity: 'ERROR',
    path,
    message,
    mapped: false,
    ...details,
  })
}

function validateUnknownFields(
  value: Record<string, any>,
  allowed: Set<string>,
  prefix: string,
  issues: DslValidationIssue[],
  stepIndex?: number,
) {
  Object.keys(value).forEach((field) => {
    if (allowed.has(field)) {
      return
    }
    addIssue(issues, 'UNKNOWN_FIELD', prefix === '$' ? `$.${field}` : `${prefix}.${field}`, `Field "${field}" is not supported by Case DSL v1.`, {
      stepIndex,
    })
  })
}

function isRecord(value: unknown): value is Record<string, any> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function stringValue(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

function displayPath(path: string) {
  return path.startsWith('$.') ? path.slice(2) : path
}
