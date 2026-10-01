package com.burton.slack.di

import android.content.Context
import coil.ImageLoader
import com.burton.slack.data.slack.TokenHolder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun okHttpClient(tokenHolder: TokenHolder): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(slackAuthInterceptor(tokenHolder))
            .build()

    @Provides
    @Singleton
    fun imageLoader(
        @ApplicationContext context: Context,
        client: OkHttpClient,
    ): ImageLoader =
        ImageLoader.Builder(context)
            .okHttpClient(client)
            .crossfade(true)
            .build()

    private fun slackAuthInterceptor(tokenHolder: TokenHolder): Interceptor = Interceptor { chain ->
        val request = chain.request()
        val path = request.url.encodedPath
        val skip = path.contains("oauth.v2") || path.contains("tooling.tokens")
        val host = request.url.host
        val token = tokenHolder.token
        val needsAuth = !skip &&
            token.isNotBlank() &&
            hostNeedsSlackAuth(host) &&
            request.header("Authorization").isNullOrBlank()
        val next = if (needsAuth) {
            request.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            request
        }
        chain.proceed(next)
    }

    private fun hostNeedsSlackAuth(host: String): Boolean =
        host == "files.slack.com" ||
            host == "slack-files.com" ||
            host.endsWith(".slack.com") ||
            host.endsWith(".slack-files.com")
}
