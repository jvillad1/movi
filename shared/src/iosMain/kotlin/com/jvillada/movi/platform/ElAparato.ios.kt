package com.jvillada.movi.platform

import platform.UIKit.UIDevice
import platform.UIKit.UIUserInterfaceIdiomPhone

/** Ver el `expect`. Un iPad tiene Atajos pero no recibe SMS: ahí no se ofrece. */
internal actual fun esUnIphone(): Boolean =
    UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPhone

/** Ver el `expect`: la app lo lee del sistema («27.0.1» → 27), sin adivinar nada. */
internal actual fun versionDeIos(): Int? =
    UIDevice.currentDevice.systemVersion.substringBefore('.').toIntOrNull()
