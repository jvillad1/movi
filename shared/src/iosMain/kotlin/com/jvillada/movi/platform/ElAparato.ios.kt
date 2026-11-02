package com.jvillada.movi.platform

import platform.UIKit.UIDevice
import platform.UIKit.UIUserInterfaceIdiomPhone

/** Ver el `expect`. Un iPad tiene Atajos pero no recibe SMS: ahí no se ofrece. */
internal actual fun esUnIphone(): Boolean =
    UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPhone
