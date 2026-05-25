package com.jvillada.movi.data

import com.jvillada.movi.shared.repository.WalletRepository
import io.ktor.client.HttpClient

expect fun createHttpClient(): HttpClient
expect val apiBaseUrl: String
expect fun createRepository(): WalletRepository
