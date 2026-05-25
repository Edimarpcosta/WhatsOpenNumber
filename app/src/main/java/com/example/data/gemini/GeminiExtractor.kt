package com.example.data.gemini

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ExtractedContact(
    val name: String,
    val phone: String
)

object GeminiExtractor {
    private const val TAG = "GeminiExtractor"
    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun extractContacts(rawText: String): List<ExtractedContact> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "API Key is missing or default. Skipping AI call.")
            return@withContext emptyList()
        }

        val systemPrompt = """
            Você é um assistente especializado em extração de informações de contato.
            Sua tarefa é analisar o texto enviado pelo usuário e identificar qualquer número de telefone junto ao nome da pessoa ou empresa correspondente (se houver).
            Instruções:
            1. Formate os números de telefone mantendo os dígitos numéricos (por exemplo, contendo DDI e DDD como 5511999999999). Remova traços, espaços e parênteses.
            2. Se um número de telefone não tiver DDI (código do país), mas parecer do Brasil, assuma o código do país 55.
            3. Se não houver nome associado, use um marcador descritivo como "Contato" ou o próprio número formatado.
            4. Retorne OBRIGATORIAMENTE um array JSON no seguinte formato exato:
            [
              {"name": "Nome do Contato", "phone": "5511999999999"}
            ]
            Retorne APENAS o JSON puro. Não adicione markdown como ```json ou comentários extras. Se nenhum telefone for encontrado, retorne um array vazio [].
        """.trimIndent()

        val prompt = "Texto para extração:\n$rawText"

        try {
            val requestBodyJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemPrompt) })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1) // Low temperature for higher consistency
                    put("responseMimeType", "application/json")
                })
            }

            val requestBody = requestBodyJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL/$MODEL_NAME:generateContent?key=$apiKey")
                .post(requestBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e(TAG, "Gemini API failed with code ${response.code}: $errBody")
                    throw Exception("API Error (Code ${response.code})")
                }

                val bodyString = response.body?.string() ?: ""
                val responseJson = JSONObject(bodyString)
                val candidates = responseJson.optJSONArray("candidates")
                val content = candidates?.optJSONObject(0)?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val rawJsonText = parts?.optJSONObject(0)?.optString("text")?.trim() ?: "[]"

                Log.d(TAG, "Raw AI Response: $rawJsonText")

                val resultList = mutableListOf<ExtractedContact>()
                val jsonArray = JSONArray(rawJsonText)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val name = obj.optString("name", "Contato").trim()
                    val phone = obj.optString("phone", "").replace(Regex("[^0-9]"), "").trim()
                    if (phone.isNotEmpty()) {
                        resultList.add(ExtractedContact(name, phone))
                    }
                }
                return@withContext resultList
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error Call to Gemini API", e)
            throw e
        }
    }
}
