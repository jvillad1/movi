package com.jvillada.movi.data

import com.jvillada.movi.shared.repository.WalletRepository

object Repositories {
    val wallets: WalletRepository by lazy { createRepository() }
}
