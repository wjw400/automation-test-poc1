export type Project = {
  id: string
  code: string
  name: string
  description: string
  status: string
  createdAt: string
  updatedAt: string
}

export type Environment = {
  id: string
  projectId: string
  name: string
  baseUrl: string
  variables: Record<string, string>
  status: string
}

export type Profile = {
  id: string
  projectId: string
  name: string
  type: string
  senderPublicKey?: string | null
  senderPrivateKey?: string | null
  senderPrivateKeyPassphrase?: string | null
  recipientPublicKey?: string | null
  recipientPrivateKey?: string | null
  recipientPrivateKeyPassphrase?: string | null
  enabled: boolean
}

export type CaseRecord = {
  id: string
  projectId: string
  name: string
  code: string
  description: string
  folderPath?: string | null
  tags: string[]
  dslVersion: string
  content: string
  status: string
  createdAt: string
  updatedAt: string
}

export type SuiteCaseRecord = {
  id: string
  caseId: string
  caseName: string
  caseCode: string
  alias?: string | null
  order: number
  enabled: boolean
  variableOverrides: Record<string, unknown>
  environmentId?: string | null
  profileId?: string | null
}

export type SuiteRecord = {
  id: string
  projectId: string
  name: string
  description?: string | null
  status: string
  cases: SuiteCaseRecord[]
  createdAt: string
  updatedAt: string
}

export type ReportRequest = {
  method: string
  url: string
  headers: Record<string, string>
  body: unknown
}

export type ReportResponse = {
  statusCode: number
  headers: Record<string, string[]>
  body: string
}

export type ValidationReport = {
  status: string
  type: string
  target: string
  expected: unknown
  actual: unknown
  message: string
}

export type ActionExecutionReport = {
  stage: 'beforeSend' | 'afterReceive'
  index: number
  type: string
  name: string
  status: string
  durationMs: number
  input: unknown
  output: unknown
  errorMessage?: string | null
}

export type StepRun = {
  index: number
  name: string
  profile: string
  status: string
  errorMessage?: string | null
  request: ReportRequest | null
  response: ReportResponse | null
  actions: ActionExecutionReport[]
  decryptedPayloads: Record<string, unknown>
  validations: ValidationReport[]
  extractedValues: Record<string, unknown>
}

export type RunRecord = {
  id: string
  projectId: string
  caseId: string
  environmentId?: string | null
  profileId?: string | null
  triggeredBy: string
  status: string
  startedAt: string
  finishedAt: string
  durationMs: number
  errorMessage?: string | null
  steps: StepRun[]
}

export type SaveCaseInput = {
  name: string
  code: string
  description: string
  folderPath: string
  tags: string[]
  dslVersion: string
  content: string
  status: string
}

export type SaveSuiteCaseInput = {
  id?: string
  caseId: string
  alias?: string
  order: number
  enabled: boolean
  variableOverrides: Record<string, unknown>
  environmentId?: string
  profileId?: string
}

export type SaveSuiteInput = {
  name: string
  description: string
  status: string
  cases: SaveSuiteCaseInput[]
}

export type SaveEnvironmentInput = {
  name: string
  baseUrl: string
  variables: Record<string, string>
  status: string
}

export type SaveProfileInput = {
  name: string
  type: string
  senderPublicKey: string
  senderPrivateKey: string
  senderPrivateKeyPassphrase: string
  recipientPublicKey: string
  recipientPrivateKey: string
  recipientPrivateKeyPassphrase: string
  enabled: boolean
}

export type StartRunInput = {
  caseId: string
  environmentId?: string
  profileId?: string
  triggeredBy?: string
}

export type ApiSpecRecord = {
  id: string
  projectId: string
  name: string
  title?: string | null
  specVersion?: string | null
  sourceFormat?: string | null
  operationCount: number
  createdAt: string
  updatedAt: string
}

export type ApiOperationRecord = {
  id: string
  projectId: string
  apiSpecId: string
  apiSpecName?: string | null
  tagName?: string | null
  method: string
  path: string
  operationId?: string | null
  summary?: string | null
  description?: string | null
  requestBody: unknown
  responses: unknown
  createdAt: string
}
