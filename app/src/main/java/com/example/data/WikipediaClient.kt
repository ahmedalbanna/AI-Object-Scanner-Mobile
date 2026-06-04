package com.example.data

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WikipediaDesktopUrl(
    val page: String? = null
)

data class WikipediaContentUrls(
    val desktop: WikipediaDesktopUrl? = null
)

data class WikipediaSummaryResponse(
    val title: String? = null,
    val extract: String? = null,
    val content_urls: WikipediaContentUrls? = null
)

object WikipediaClient {
    private const val TAG = "WikipediaClient"
    
    private val client = OkHttpClient.Builder().build()
    
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    /**
     * Query Wikipedia for an object name.
     * Returns a Triple of (Title, Summary, URL) if found, or null otherwise.
     */
    suspend fun fetchSummaryAndUrl(query: String, useArabic: Boolean): Triple<String, String, String>? = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext null
        
        val lang = if (useArabic) "ar" else "en"
        val cleanQuery = query.trim()
        
        try {
            // Step 1: Query the traditional Action API Opensearch endpoint to find the exact Wikipedia title
            val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
            val searchUrl = "https://$lang.wikipedia.org/w/api.php?action=opensearch&search=$encodedQuery&limit=1&namespace=0&format=json&origin=*"
            
            Log.d(TAG, "Requesting Wikipedia search URL: $searchUrl")
            val searchRequest = Request.Builder()
                .url(searchUrl)
                .addHeader("User-Agent", "AIObjectScannerApp/1.0 (contact: ahmedalbannacode@gmail.com)")
                .build()
                
            client.newCall(searchRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Search request failed with status code ${response.code}")
                    return@withContext null
                }
                
                val bodyString = response.body?.string() ?: return@withContext null
                Log.d(TAG, "Wikipedia Search API raw output: $bodyString")
                
                // Parse opensearch response format: [ "query", ["Title"], ["Description"], ["URL"] ]
                val listType = Types.newParameterizedType(List::class.java, Any::class.java)
                val listAdapter = moshi.adapter<List<Any>>(listType)
                val parsed = listAdapter.fromJson(bodyString)
                
                if (parsed != null && parsed.size >= 4) {
                    val titles = parsed[1] as? List<*>
                    val urls = parsed[3] as? List<*>
                    
                    if (titles != null && titles.isNotEmpty() && urls != null && urls.isNotEmpty()) {
                        val matchedTitle = titles[0]?.toString() ?: ""
                        val matchedUrl = urls[0]?.toString() ?: ""
                        
                        if (matchedTitle.isNotBlank()) {
                            Log.d(TAG, "Matched Wikipedia Title: $matchedTitle, URL: $matchedUrl")
                            
                            // Step 2: Query the clean REST summary API endpoint for the matched page title
                            val encodedTitle = URLEncoder.encode(matchedTitle, "UTF-8")
                            val summaryUrl = "https://$lang.wikipedia.org/api/rest_v1/page/summary/$encodedTitle"
                            Log.d(TAG, "Requesting Wikipedia Summary URL: $summaryUrl")
                            
                            val summaryRequest = Request.Builder()
                                .url(summaryUrl)
                                .addHeader("User-Agent", "AIObjectScannerApp/1.0 (contact: ahmedalbannacode@gmail.com)")
                                .build()
                                
                            client.newCall(summaryRequest).execute().use { sumResponse ->
                                if (sumResponse.isSuccessful) {
                                    val sumBody = sumResponse.body?.string()
                                    if (sumBody != null) {
                                        Log.d(TAG, "Wikipedia Summary API raw output: $sumBody")
                                        val summaryAdapter = moshi.adapter(WikipediaSummaryResponse::class.java)
                                        val summaryData = summaryAdapter.fromJson(sumBody)
                                        
                                        val extractText = summaryData?.extract ?: ""
                                        val canonicalUrl = summaryData?.content_urls?.desktop?.page ?: matchedUrl
                                        
                                        if (extractText.isNotBlank()) {
                                            return@withContext Triple(matchedTitle, extractText, canonicalUrl)
                                        }
                                    }
                                } else {
                                    Log.e(TAG, "Summary request failed with code ${sumResponse.code} for $matchedTitle")
                                }
                            }
                            
                            // Step 3: Fallback if REST summary API fails but we have Opensearch descriptions
                            val descriptions = parsed[2] as? List<*>
                            val fallbackExtract = descriptions?.firstOrNull()?.toString() ?: ""
                            if (fallbackExtract.isNotBlank()) {
                                return@withContext Triple(matchedTitle, fallbackExtract, matchedUrl)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception encountered during Wikipedia API transaction: ", e)
        }
        
        return@withContext null
    }
}
