package com.example.cmiyc.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "https://op.gg/"

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}

//object ApiClient {
//    val apiService: ApiService by lazy {
//        RetrofitClient.retrofit.create(ApiService::class.java)
//    }
//}

object ApiClient {
    val apiService: MockApiService = MockApiService();
}