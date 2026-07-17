package com.examingrity.cbt.network

import retrofit2.Response

class ExamRepository(private val apiService: ApiService) {

    suspend fun fetchCsrfToken() = apiService.getCsrfToken()

    suspend fun mulaiSesi(pin: String): Response<MulaiUjianResponse> =
        apiService.mulaiSesiUjian(pin)

    suspend fun getSoal(participantId: Int) = apiService.getDaftarSoal(participantId)

    suspend fun submitUjian(participantId: Int, request: SubmitUjianRequest): Response<SubmitResponse> =
        apiService.submitJawaban(participantId, request)

    suspend fun kirimLog(participantId: Int, request: LogRequest): Response<GeneralResponse> =
        apiService.sendLogPelanggaran(participantId, request)

    suspend fun autoSave(participantId: Int, request: AutoSaveRequest) =
        apiService.autoSaveJawaban(participantId, request)
}