import { describe, expect, it } from 'vitest'
import { validateCaseDslV1 } from './caseDslValidation'

describe('validateCaseDslV1', () => {
  it('keeps field locations for renderable response errors', () => {
    const issues = validateCaseDslV1({
      dslVersion: 'v1',
      name: 'Invalid extraction',
      variables: {},
      steps: [{
        name: 'Get order',
        type: 'http',
        operation: { operationId: 'getOrder' },
        request: {},
        actions: { beforeSend: [], afterReceive: [] },
        response: {
          status: { operator: 'oneOf', expected: [200] },
          assertions: [],
          extract: { orderId: { source: 'raw', path: '' } },
        },
      }],
    })

    expect(issues).toEqual(expect.arrayContaining([
      expect.objectContaining({
        code: 'EXTRACTION_SOURCE_INVALID',
        path: '$.steps[0].response.extract.orderId.source',
        stepIndex: 0,
        section: 'extract',
        field: 'source',
        mapped: true,
      }),
      expect.objectContaining({
        code: 'EXTRACTION_PATH_INVALID',
        path: '$.steps[0].response.extract.orderId.path',
      }),
    ]))
  })

  it('identifies unsupported configuration that needs a Raw fallback', () => {
    const issues = validateCaseDslV1({
      dslVersion: 'v1',
      name: 'Legacy scenario',
      variables: {},
      expect: { status: 200 },
      steps: [],
    })

    expect(issues).toContainEqual(expect.objectContaining({
      code: 'LEGACY_ROOT_FIELDS',
      path: '$',
      mapped: false,
    }))
  })

  it('accepts request path parameters defined by Case DSL v1', () => {
    const issues = validateCaseDslV1({
      dslVersion: 'v1',
      name: 'Get order by id',
      variables: { orderId: 'ORDER-1' },
      steps: [{
        name: 'Get order',
        type: 'http',
        operation: { method: 'GET', path: '/orders/{orderId}' },
        request: {
          path: { orderId: '${orderId}' },
          query: {},
          headers: {},
        },
        actions: { beforeSend: [], afterReceive: [] },
        response: { status: { operator: 'oneOf', expected: [200] }, assertions: [], extract: {} },
      }],
    })

    expect(issues).not.toEqual(expect.arrayContaining([
      expect.objectContaining({ path: '$.steps[0].request.path' }),
    ]))
  })

  it('maps invalid request path parameters to the Params editor', () => {
    const issues = validateCaseDslV1({
      dslVersion: 'v1',
      name: 'Invalid path parameters',
      steps: [{
        name: 'Get order',
        type: 'http',
        operation: { method: 'GET', path: '/orders/{orderId}' },
        request: { path: 'ORDER-1' },
        actions: { beforeSend: [], afterReceive: [] },
        response: { status: { operator: 'oneOf', expected: [200] }, assertions: [], extract: {} },
      }],
    })

    expect(issues).toContainEqual(expect.objectContaining({
      code: 'PATH_PARAMS_INVALID',
      path: '$.steps[0].request.path',
      section: 'params',
      field: 'path',
      mapped: true,
    }))
  })

  it('maps an invalid response status to the Tests editor', () => {
    const issues = validateCaseDslV1({
      dslVersion: 'v1',
      name: 'Invalid response status',
      steps: [{
        name: 'Get order',
        type: 'http',
        operation: { method: 'GET', path: '/orders' },
        request: {},
        actions: { beforeSend: [], afterReceive: [] },
        response: { status: { operator: 'range', min: 300, max: 200 }, assertions: [], extract: {} },
      }],
    })

    expect(issues).toContainEqual(expect.objectContaining({
      code: 'RESPONSE_STATUS_INVALID',
      path: '$.steps[0].response.status',
      section: 'assertions',
      field: 'status',
      mapped: true,
    }))
  })

  it('accepts equals, oneOf, and range response status rules', () => {
    const statuses = [
      { operator: 'equals', expected: 201 },
      { operator: 'oneOf', expected: [200, 202, 204] },
      { operator: 'range', min: 200, max: 299 },
    ]

    statuses.forEach((status) => {
      const issues = validateCaseDslV1(scenarioWithStatus(status))
      expect(issues).not.toEqual(expect.arrayContaining([
        expect.objectContaining({ code: 'RESPONSE_STATUS_INVALID' }),
      ]))
    })
  })

  it('rejects the legacy integer response status', () => {
    const issues = validateCaseDslV1(scenarioWithStatus(200))

    expect(issues).toContainEqual(expect.objectContaining({
      code: 'RESPONSE_STATUS_INVALID',
      path: '$.steps[0].response.status',
      mapped: true,
    }))
  })
})

function scenarioWithStatus(status: unknown) {
  return {
    dslVersion: 'v1',
    name: 'Status validation',
    steps: [{
      name: 'Get order',
      type: 'http',
      operation: { method: 'GET', path: '/orders' },
      request: {},
      actions: { beforeSend: [], afterReceive: [] },
      response: { status, assertions: [], extract: {} },
    }],
  }
}
