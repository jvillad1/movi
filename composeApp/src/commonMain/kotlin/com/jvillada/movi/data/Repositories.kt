package com.jvillada.movi.data

import com.jvillada.movi.shared.repository.WalletRepository
import com.jvillada.movi.shared.repository.WalletRepositoryImpl

object Repositories {
    val wallets: WalletRepository by lazy {
        WalletRepositoryImpl(createHttpClient(), apiBaseUrl)
    }
}
