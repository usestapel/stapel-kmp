package stapel.analytics

import stapel.runtime.PlatformContext
import stapel.runtime.StapelDispatchers
import stapel.runtime.application
import java.io.File

public actual fun platformAnalyticsStorage(
    platform: PlatformContext,
    dispatchers: StapelDispatchers,
    namespace: String,
): AnalyticsStorage = FileAnalyticsStorage(File(platform.application.noBackupFilesDir, namespace).absolutePath, dispatchers.io)
