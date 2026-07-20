package com.aliablip.hytik.data.api

import com.aliablip.hytik.data.api.models.TikLyResponse
import com.aliablip.hytik.data.api.models.TikWmResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface TikTokApiService {

    @GET("https://www.tikwm.com/api/")
    suspend fun fetchTikWm(
        @Query("url") url: String,
        @Query("hd") hd: Int = 1
    ): Response<TikWmResponse>

    @GET("https://api.tiklydown.eu.org/api/download")
    suspend fun fetchTikLyDown(
        @Query("url") url: String
    ): Response<TikLyResponse>
}
