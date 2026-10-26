export type Project = {
  id: string
  code: string
  name: string
  description: string
  status: string
  createdAt: string
  updatedAt: string
}

export type SaveProjectInput = {
  name: string
  description?: string
}

export type Environment = {
  id: string
  projectId: string
  name: string
  baseUrl: string
  endpoints: Record<string, string>
  variables: Record<string, string>
  status: string
}

export type Profile = {
  id: string
  projectId: string
  name: string
  type: string
  verificationPublicKey?: string | null
  signingPrivateKey?: string | null
  signingPrivateKeyPassphrase?: string | null
  encryptionPublicKey?: string | null
  decryptionPrivateKey?: string | null
  decryptionPrivateKeyPassphrase?: string | null
  environmentIds: string[]
  enabled: boolean
}

export type ResourceReferenceRecord = {
  type: string
  id: string
  name: string
}

export type CaseRecord = {
  id: string
  projectId: string
  name: string
  code: string
  description: string
  folderId?: string | null
  defaultEnvironmentId?: string | null
  defaultProfileId?: string | null
  tags: string[]
  dslVersion: string
  content: string
  status: string
  revision: number
  createdAt: string
  updatedAt: string
}

export type FolderRecord = {
  id: string
  projectId: string
  parentFolderId?: string | null
  name: string
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export type SuiteCaseRecord = {
  id: string
  caseId: string
  caseName: string
  caseCode: string
  displayName?: string | null
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
  revision: number
  cases: SuiteCaseRecord[]
  createdAt: string
  updatedAt: string
}

export type SuiteDraftRecord = {
  id: string
  projectId: string
  suiteId: string
  sourceRevision: number
  revision: number
  content: string
  status: string
  createdBy?: string | null
  agentTaskId?: string | null
  document: {
    name: string
    description: string
    status: string
    entries: Array<{
      id: string
      scenarioId: string
      displayName?: string | null
      enabled: boolean
      variableOverrides: Record<string, unknown>
      environmentId?: string | null
      profileId?: string | null
    }>
  }
  validation: ScenarioDraftValidation
  diff: { items: ScenarioDraftDiffItem[] }
  createdAt?: string
  updatedAt?: string
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
  stage: string
  origin?: string
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
  processingSnapshot?: Record<string, unknown>
  businessRequest?: unknown
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
  suiteRunId?: string | null
  suiteCaseId?: string | null
  suiteDisplayName?: string | null
  suiteCaseOrder?: number | null
  environmentId?: string | null
  environmentName?: string | null
  profileId?: string | null
  profileName?: string | null
  triggeredBy: string
  status: string
  startedAt: string
  finishedAt: string
  durationMs: number
  errorMessage?: string | null
  steps: StepRun[]
}

export type SuiteRunRecord = {
  id: string
  projectId: string
  suiteId: string
  suiteName: string
  triggeredBy: string
  status: string
  startedAt: string
  finishedAt: string
  durationMs: number
  errorMessage?: string | null
  runs: RunRecord[]
}

export type SaveCaseInput = {
  name: string
  code: string
  description: string
  folderId?: string
  defaultEnvironmentId?: string
  defaultProfileId?: string
  tags: string[]
  dslVersion: string
  content: string
  status: string
}

export type SaveFolderInput = {
  name: string
  parentFolderId?: string
}

export type SaveSuiteCaseInput = {
  id?: string
  caseId: string
  displayName?: string
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
  endpoints: Record<string, string>
  variables: Record<string, string>
  status: string
}

export type SaveProfileInput = {
  name: string
  type: string
  verificationPublicKey: string
  signingPrivateKey: string
  signingPrivateKeyPassphrase: string
  encryptionPublicKey: string
  decryptionPrivateKey: string
  decryptionPrivateKeyPassphrase: string
  environmentIds: string[]
  enabled: boolean
}

export type StartRunInput = {
  caseId: string
  environmentId?: string
  profileId?: string
  triggeredBy?: string
}

export type StartSuiteRunInput = {
  suiteId: string
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

export type ApiSpecReferenceRecord = {
  caseId: string
  caseName: string
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

export type ContractDiagnosticRecord = {
  severity: 'INFO' | 'WARNING' | 'ERROR' | string
  code: string
  message: string
  location: string
}

export type ContentContractRecord = {
  mediaType?: string | null
  schema?: Record<string, unknown> | null
  schemaRef?: string | null
  example?: unknown
}

export type PipelinePortRecord = {
  type: string
  defaultLocation?: string | null
  description?: string | null
}

export type PipelineParameterRecord = {
  type: string
  defaultValue?: unknown
  enumValues: unknown[]
  description?: string | null
}

export type PipelineStepRecord = {
  id: string
  uses: string
  displayName: string
  description?: string | null
}

export type PipelineFlowEdgeRecord = {
  from: string
  to: string
}

export type PipelineCatalogRecord = {
  id: string
  version: number
  displayName: string
  description?: string | null
  direction: string
  requiresRuntimeProfile: boolean
  inputs: Record<string, PipelinePortRecord>
  outputs: Record<string, PipelinePortRecord>
  parameters: Record<string, PipelineParameterRecord>
  steps: PipelineStepRecord[]
  flow: PipelineFlowEdgeRecord[]
  reference: string
}

export type ProcessingContractRecord = {
  pipelineReference: string
  pipeline?: PipelineCatalogRecord | null
  inputs: Record<string, unknown>
  outputs: Record<string, unknown>
  parameters: Record<string, unknown>
}

export type PayloadContractRecord = {
  business?: ContentContractRecord | null
  wire?: ContentContractRecord | null
  processing?: ProcessingContractRecord | null
}

export type ResponseContractRecord = {
  status: string
  description?: string | null
  business?: ContentContractRecord | null
  wire?: ContentContractRecord | null
  processing?: ProcessingContractRecord | null
}

export type ParameterContractRecord = {
  name?: string | null
  location?: string | null
  required: boolean
  schema?: Record<string, unknown> | null
  example?: unknown
  description?: string | null
}

export type OperationCapabilitiesRecord = {
  hasBusinessRequest: boolean
  hasBusinessResponse: boolean
  hasRequestProcessing: boolean
  hasResponseProcessing: boolean
  encrypted: boolean
  canGenerateScenario: boolean
}

export type OperationContractRecord = {
  processingSettings?: OperationProcessingSettings | null
  id?: string | null
  method: string
  path: string
  operationId?: string | null
  summary?: string | null
  description?: string | null
  tags: string[]
  parameters: ParameterContractRecord[]
  request: PayloadContractRecord
  responses: ResponseContractRecord[]
  capabilities: OperationCapabilitiesRecord
  rawOperation: Record<string, unknown>
  diagnostics: ContractDiagnosticRecord[]
}

export type MessageProcessingRule = {
  protection: 'none' | 'pgp-encrypt' | 'pgp-sign-encrypt' | 'pgp-decrypt' | 'pgp-decrypt-verify'
  ciphertext?: { location: 'body' | 'json-pointer'; pointer?: string | null }
  envelope?: unknown
  businessSchema?: unknown
}

export type OperationProcessingConfiguration = {
  version: number
  request: MessageProcessingRule
  responses: Record<string, MessageProcessingRule>
}

export type OperationProcessingSettings = {
  configuration: OperationProcessingConfiguration | null
  importedConfiguration: OperationProcessingConfiguration | null
  source: string
  revision: number
  needsReview: boolean
  importError?: string | null
}

export type ApiSpecContractRecord = {
  apiSpecId: string
  title?: string | null
  version?: string | null
  operations: OperationContractRecord[]
  schemaRegistry: Record<string, Record<string, unknown>>
  diagnostics: ContractDiagnosticRecord[]
}

export type TestKnowledgeScope = 'PROJECT' | 'API_SPEC' | 'OPERATION' | 'SCENARIO' | 'SUITE'
export type TestKnowledgeStatus = 'DRAFT' | 'PUBLISHED'

export type TestKnowledgeRecord = {
  id: string
  projectId: string
  name: string
  description: string
  scope: TestKnowledgeScope
  targetId?: string | null
  tags: string[]
  content: string
  status: TestKnowledgeStatus
  version: number
  createdAt: string
  updatedAt: string
}

export type SaveTestKnowledgeInput = {
  name: string
  description: string
  scope: TestKnowledgeScope
  targetId?: string
  tags: string[]
  content: string
  status: TestKnowledgeStatus
}

export type AgentRuntimeCapabilities = {
  available: boolean
  streaming: boolean
  cancellation: boolean
  tools: boolean
  defaultModel: string
  models: string[]
}

export type AgentRuntimeDescriptor = {
  type: string
  capabilities: AgentRuntimeCapabilities
}

export type AgentTaskStatus = 'READY' | 'RUNNING' | 'WAITING_FOR_USER' | 'COMPLETED' | 'FAILED' | 'CANCELLED'

export type AgentContextType = 'SCENARIO' | 'SUITE' | 'OPERATION' | 'EXECUTION' | 'TEST_KNOWLEDGE'

export type AgentContextReference = {
  type: AgentContextType
  id: string
}

export type AgentContextSelection = {
  included: AgentContextReference[]
  excluded: AgentContextReference[]
}

export type AgentPermissionMode = 'READ_ONLY' | 'REVIEW_REQUIRED' | 'AUTO_APPLY'

export type AgentTaskRecord = {
  id: string
  projectId: string
  title: string
  status: AgentTaskStatus
  targetType?: string | null
  targetId?: string | null
  permissionMode: AgentPermissionMode
  permissionModeRevision: number
  runtimeType: string
  modelId: string
  createdBy: string
  iterationCount: number
  tokenBudget: number
  context: AgentContextSelection
  revision: number
  createdAt: string
  updatedAt: string
  completedAt?: string | null
}

export type CreateAgentTaskInput = {
  title: string
  targetType?: 'SCENARIO' | 'SUITE'
  targetId?: string
  runtimeType?: string
  modelId?: string
  tokenBudget?: number
  context?: AgentContextSelection
  permissionMode?: AgentPermissionMode
}

export type AgentMessageRecord = {
  id: string
  taskId: string
  sequence: number
  role: 'USER' | 'ASSISTANT' | 'SYSTEM'
  content: Record<string, unknown>
  createdAt: string
}

export type AgentEventRecord = {
  id: string
  taskId: string
  sequence: number
  eventType: string
  payload: Record<string, unknown>
  createdAt: string
}

export type AgentArtifactRecord = {
  id: string
  taskId: string
  artifactType: string
  targetType?: string | null
  targetId?: string | null
  baseRevision?: number | null
  status: string
  content: Record<string, unknown>
  createdAt: string
  updatedAt: string
}

export type AgentAuditRecord = {
  id: string
  taskId: string
  projectId: string
  actorId: string
  eventType: string
  payload: Record<string, unknown>
  createdAt: string
}

export type AgentAuditSummary = {
  taskCount: number
  completedCount: number
  failedCount: number
  validationPassedCount: number
  validationFailedCount: number
  appliedCount: number
  savedCount: number
  manuallyModifiedCount: number
  positiveFeedbackCount: number
  negativeFeedbackCount: number
  successRate: number
  validationFailureRate: number
  adoptionRate: number
}

export type ScenarioDraftValidationIssue = {
  severity: 'ERROR' | 'WARNING' | 'INFORMATION'
  code: string
  path: string
  message: string
}

export type ScenarioDraftValidation = {
  valid: boolean
  issues: ScenarioDraftValidationIssue[]
}

export type ScenarioDraftDiffItem = {
  type: 'ADD' | 'REMOVE' | 'MOVE' | 'CHANGE'
  path: string
  stepId?: string | null
  before?: unknown
  after?: unknown
}

export type ScenarioDraftRecord = {
  id: string
  projectId: string
  scenarioId: string | null
  sourceRevision: number
  revision: number
  content: string
  status: string
  createdBy?: string | null
  agentTaskId?: string | null
  steps: Array<{ id: string, index: number, name: string }>
  validation: ScenarioDraftValidation
  diff: { items: ScenarioDraftDiffItem[] }
  createdAt: string
  updatedAt: string
  sourceSuiteId?: string | null
}

export type PublishScenarioDraftResult = {
  scenario: CaseRecord
  suite?: SuiteRecord | null
  created: boolean
}
