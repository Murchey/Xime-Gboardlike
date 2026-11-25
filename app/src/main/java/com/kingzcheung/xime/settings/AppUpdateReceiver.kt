package com.kingzcheung.xime.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 应用升级完成后清理下载的安装包。 */
class AppUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            AppUpdateDownloader.cleanInstalledCachedApks(context)
        }
    }
}
