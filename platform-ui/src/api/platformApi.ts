import { apiClient } from './client'
import type {
  ApiOperationRecord,
  ApiSpecRecord,
  CaseRecord,
  Environment,
  Profile,
  Project,
  RunRecord,
  SaveCaseInput,
  SaveEnvironmentInput,
  SaveProfileInput,
  SaveSuiteInput,
  StartRunInput,
  SuiteRecord,
} from '../types/platform'

export const platformApi = {
  async listProjects() {
    const { data } = await apiClient.get<Project[]>('/projects')
    return data
  },

  async listCases(projectId: string) {
    const { data } = await apiClient.get<CaseRecord[]>(`/projects/${projectId}/cases`)
    return data
  },

  async getCase(caseId: string) {
    const { data } = await apiClient.get<CaseRecord>(`/cases/${caseId}`)
    return data
  },

  async createCase(projectId: string, payload: SaveCaseInput) {
    const { data } = await apiClient.post<CaseRecord>(`/projects/${projectId}/cases`, payload)
    return data
  },

  async updateCase(projectId: string, caseId: string, payload: SaveCaseInput) {
    const { data } = await apiClient.put<CaseRecord>(
      `/projects/${projectId}/cases/${caseId}`,
      payload,
    )
    return data
  },

  async deleteCase(caseId: string) {
    await apiClient.delete(`/cases/${caseId}`)
  },

  async listSuites(projectId: string) {
    const { data } = await apiClient.get<SuiteRecord[]>(`/projects/${projectId}/suites`)
    return data
  },

  async createSuite(projectId: string, payload: SaveSuiteInput) {
    const { data } = await apiClient.post<SuiteRecord>(`/projects/${projectId}/suites`, payload)
    return data
  },

  async updateSuite(projectId: string, suiteId: string, payload: SaveSuiteInput) {
    const { data } = await apiClient.put<SuiteRecord>(
      `/projects/${projectId}/suites/${suiteId}`,
      payload,
    )
    return data
  },

  async deleteSuite(suiteId: string) {
    await apiClient.delete(`/suites/${suiteId}`)
  },

  async listEnvironments(projectId: string) {
    const { data } = await apiClient.get<Environment[]>(
      `/projects/${projectId}/environments`,
    )
    return data
  },

  async createEnvironment(projectId: string, payload: SaveEnvironmentInput) {
    const { data } = await apiClient.post<Environment>(
      `/projects/${projectId}/environments`,
      payload,
    )
    return data
  },

  async listProfiles(projectId: string) {
    const { data } = await apiClient.get<Profile[]>(`/projects/${projectId}/profiles`)
    return data
  },

  async createProfile(projectId: string, payload: SaveProfileInput) {
    const { data } = await apiClient.post<Profile>(
      `/projects/${projectId}/profiles`,
      payload,
    )
    return data
  },

  async listRuns() {
    const { data } = await apiClient.get<RunRecord[]>('/runs')
    return data
  },

  async getRun(runId: string) {
    const { data } = await apiClient.get<RunRecord>(`/runs/${runId}`)
    return data
  },

  async startRun(payload: StartRunInput) {
    const { data } = await apiClient.post<RunRecord>('/runs', payload)
    return data
  },

  async listApiSpecs(projectId: string) {
    const { data } = await apiClient.get<ApiSpecRecord[]>(`/projects/${projectId}/api-specs`)
    return data
  },

  async importApiSpec(projectId: string, file: File) {
    const formData = new FormData()
    formData.append('file', file)
    const { data } = await apiClient.post<ApiSpecRecord>(
      `/projects/${projectId}/api-specs/import`,
      formData,
      {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      },
    )
    return data
  },

  async listOperations(projectId: string) {
    const { data } = await apiClient.get<ApiOperationRecord[]>(`/projects/${projectId}/operations`)
    return data
  },

  getApiSpecContentUrl(projectId: string, apiSpecId: string) {
    return `/api/projects/${encodeURIComponent(projectId)}/api-specs/${encodeURIComponent(apiSpecId)}/content`
  },
}
