package com.fiahub.nam.autosmartotp.service.api

import android.content.Context
import com.fiahub.nam.autosmartotp.BuildConfig
import io.reactivex.Single
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit

interface ApiService {

    companion object {

        val apiService by lazy {

            Retrofit.Builder()
                .baseUrl("https://www.coingiatot.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .client(provideOkHttpClient())
                .build().create(ApiService::class.java)
        }
    }

    @GET("https://www.coingiatot.com/api/v1/bank_otps")
    fun getPendingTransaction(): Single<PendingTransaction>

}

private fun provideOkHttpClient(): OkHttpClient {

    val cookieManager = CookieManager()
    CookieHandler.setDefault(cookieManager)
    cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL)

    val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)

    if (BuildConfig.DEBUG) {
        httpClient.addInterceptor(
            HttpLoggingInterceptor()
                .setLevel(HttpLoggingInterceptor.Level.BODY))
    }

    return httpClient.build()
}


