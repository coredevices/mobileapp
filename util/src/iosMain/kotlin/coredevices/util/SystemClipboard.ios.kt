package coredevices.util

import platform.UIKit.UIPasteboard

class IosSystemClipboard : SystemClipboard {
    override fun setText(text: String) {
        UIPasteboard.generalPasteboard.string = text
    }
}
