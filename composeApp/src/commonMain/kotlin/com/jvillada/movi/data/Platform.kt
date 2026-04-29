package com.jvillada.movi.data

import io.ktor.client.HttpClient

expect fun createHttpClient(): HttpClient
expect val apiBaseUrl: String
