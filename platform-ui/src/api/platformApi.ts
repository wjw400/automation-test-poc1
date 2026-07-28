import { apiClient } from './client'
import type {
  ApiOperationRecord,
  ApiSpecRecord,
  ApiSpecReferenceRecord,
  CaseRecord,
  Environment,
  FolderRecord,
  Profile,
  Project,
  RunRecord,
  ResourceReferenceRecord,
  SaveCaseInput,
  SaveEnvironmentInput,
  SaveFolderInput,
  SaveProfileInput,
  SaveProjectInput,
  SaveSuiteInput,
  StartRunInput,
  StartSuiteRunInput,
  SuiteRecord,
  SuiteRunRecord,
} from '../types/platform'

export const platformApi = {
  async listProjects() {
    const { data } = await apiClient.get<Project[]>('/projects')
    return data
  },

  async createProject(input: SaveProjectInput) {
    const { data } = await apiClient.post<Project>('/projects', input)
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

  async listFolders(projectId: string) {
    const { data } = await apiClient.get<FolderRecord[]>(`/projects/${projectId}/folders`)
    return data
  },

  async createFolder(projectId: string, payload: SaveFolderInput) {
    const { data } = await apiClient.post<FolderRecord>(`/projects/${projectId}/folders`, payload)
    return data
  },

  async updateFolder(projectId: string, folderId: string, payload: SaveFolderInput) {
    const { data } = await apiClient.put<FolderRecord>(`/projects/${projectId}/folders/${folderId}`, payload)
    return data
  },

  async deleteFolder(folderId: string) {
    await apiClient.delete(`/folders/${folderId}`)
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

  async updateEnvironment(projectId: string, environmentId: string, payload: SaveEnvironmentInput) {
    const { data } = await apiClient.put<Environment>(
      `/projects/${projectId}/environments/${environmentId}`,
      payload,
    )
    return data
  },

  async listEnvironmentReferences(projectId: string, environmentId: string) {
    const { data } = await apiClient.get<ResourceReferenceRecord[]>(
      `/projects/${projectId}/environments/${environmentId}/references`,
    )
    return data
  },

  async deleteEnvironment(projectId: string, environmentId: string) {
    await apiClient.delete(`/projects/${projectId}/environments/${environmentId}`)
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

  async updateProfile(projectId: string, profileId: string, payload: SaveProfileInput) {
    const { data } = await apiClient.put<Profile>(
      `/projects/${projectId}/profiles/${profileId}`,
      payload,
    )
    return data
  },

  async listProfileReferences(projectId: string, profileId: string) {
    const { data } = await apiClient.get<ResourceReferenceRecord[]>(
      `/projects/${projectId}/profiles/${profileId}/references`,
    )
    return data
  },

  async deleteProfile(projectId: string, profileId: string) {
    await apiClient.delete(`/projects/${projectId}/profiles/${profileId}`)
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

  async listSuiteRuns() {
    const { data } = await apiClient.get<SuiteRunRecord[]>('/suite-runs')
    return data
  },

  async getSuiteRun(suiteRunId: string) {
    const { data } = await apiClient.get<SuiteRunRecord>(`/suite-runs/${suiteRunId}`)
    return data
  },

  async startSuiteRun(payload: StartSuiteRunInput) {
    const { data } = await apiClient.post<SuiteRunRecord>('/suite-runs', payload)
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

  async listApiSpecReferences(projectId: string, apiSpecId: string) {
    const { data } = await apiClient.get<ApiSpecReferenceRecord[]>(
      `/projects/${projectId}/api-specs/${apiSpecId}/references`,
    )
    return data
  },

  async deleteApiSpec(projectId: string, apiSpecId: string) {
    await apiClient.delete(`/projects/${projectId}/api-specs/${apiSpecId}`)
  },

  async listOperations(projectId: string) {
    const { data } = await apiClient.get<ApiOperationRecord[]>(`/projects/${projectId}/operations`)
    return data
  },

  getApiSpecContentUrl(projectId: string, apiSpecId: string) {
    return `/api/projects/${encodeURIComponent(projectId)}/api-specs/${encodeURIComponent(apiSpecId)}/content`
  },
}
