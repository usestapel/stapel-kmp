package stapel.push

import stapel.runtime.PlatformContext
import stapel.runtime.StapelDispatchers

/** Desktop has no push: the record lives in memory for the process. */
public actual fun platformPushStore(platform: PlatformContext, dispatchers: StapelDispatchers): PushStore = InMemoryPushStore()
