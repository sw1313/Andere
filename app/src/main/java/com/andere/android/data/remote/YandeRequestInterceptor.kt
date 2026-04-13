package com.andere.android.data.remote

import okhttp3.Interceptor
import okhttp3.Response

class YandeRequestInterceptor(
    private val host: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Andere/1.0 Safari/537.36")
            .header("Referer", host)
            .build()
        return chain.proceed(request)
    }
}
