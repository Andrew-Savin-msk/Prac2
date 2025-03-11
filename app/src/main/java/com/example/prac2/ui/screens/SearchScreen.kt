package com.example.prac2.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


// Игнорируем проверку SSL (для тестов, не для продакшена!)
fun getUnsafeOkHttpClient(): OkHttpClient {
    val trustAllCerts = arrayOf<TrustManager>(
        object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
    )

    val sslContext = SSLContext.getInstance("SSL").apply {
        init(null, trustAllCerts, java.security.SecureRandom())
    }

    return OkHttpClient.Builder()
        .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
        .hostnameVerifier { _, _ -> true }
        .build()
}

// API Service
interface QuoteApiService {
    @GET("search/quotes")
    fun searchQuotes(@Query("query") query: String, @Query("limit") limit: Int = 5): Call<QuoteResponse>
}

// Data model
data class QuoteResponse(val results: List<Quote>)
data class Quote(val content: String, val author: String)

// Retrofit instance
val retrofit: Retrofit = Retrofit.Builder()
    .baseUrl("https://api.quotable.io/")
    .client(getUnsafeOkHttpClient())
    .addConverterFactory(GsonConverterFactory.create())
    .build()

val apiService: QuoteApiService = retrofit.create(QuoteApiService::class.java)

@Composable
fun SearchScreen() {
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Quote>?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    fun fetchQuotes() {
        if (query.isBlank()) {
            Toast.makeText(context, "Введите запрос", Toast.LENGTH_SHORT).show()
            return
        }
        isLoading = true
        errorMessage = null

        apiService.searchQuotes(query).enqueue(object : Callback<QuoteResponse> {
            override fun onResponse(call: Call<QuoteResponse>, response: Response<QuoteResponse>) {
                isLoading = false
                if (response.isSuccessful) {
                    searchResults = response.body()?.results ?: emptyList()
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Неизвестная ошибка"
                    errorMessage = "Ошибка загрузки: $errorBody"
                    Log.e("API_ERROR", "Ошибка ответа: $errorBody")
                }
            }

            override fun onFailure(call: Call<QuoteResponse>, t: Throwable) {
                isLoading = false
                errorMessage = "Ошибка соединения: ${t.message}"
                Log.e("API_FAILURE", "Ошибка запроса", t)
            }
        })
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        SearchBar(
            query = query,
            onQueryChange = { query = it },
            onClear = { query = "" },
            onRefresh = { fetchQuotes() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        when {
            isLoading -> PlaceholderText("Загрузка...")
            errorMessage != null -> ErrorPlaceholder(errorMessage!!, onRetry = { fetchQuotes() })
            searchResults == null -> PlaceholderText("Введите запрос и нажмите обновить")
            searchResults!!.isEmpty() -> PlaceholderText("Ничего не найдено")
            else -> QuoteList(searchResults!!)
        }
    }
}

@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit, onRefresh: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Введите текст для поиска") },
        singleLine = true,
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onRefresh() }),
        leadingIcon = {
            IconButton(onClick = onRefresh) {
                Icon(imageVector = Icons.Default.Search, contentDescription = "Обновить")
            }
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Очистить")
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun PlaceholderText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Color.Gray)
    }
}

@Composable
fun ErrorPlaceholder(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, color = Color.Red, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.width(160.dp).height(48.dp)
        ) {
            Text("Обновить", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun QuoteList(quotes: List<Quote>) {
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        quotes.forEach { quote ->
            Card(modifier = Modifier.fillMaxWidth().padding(8.dp), elevation = CardDefaults.cardElevation(4.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(quote.content)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("- ${quote.author}", color = Color.Gray)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewSearchScreen() {
    SearchScreen()
}
