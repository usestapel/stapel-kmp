package stapel.cdn.di

import org.koin.core.module.Module
import org.koin.dsl.module
import stapel.cdn.api.CdnApi
import stapel.cdn.api.CdnApiClient
import stapel.cdn.media.CdnMediaUploader
import stapel.wire.ApiTransport
import stapel.wire.MediaUploader

/**
 * The media library's Koin module: [CdnApi] on the app's authenticated [ApiTransport] and stapel-cdn
 * as the app's [MediaUploader] (what the profiles brick stores avatars through). Install it with
 * `stapelApp { modules(cdnModule()) }`. Needs `networkModule()`.
 */
public fun cdnModule(): Module = module {
    single<CdnApi> { CdnApiClient(get<ApiTransport>()) }
    single<MediaUploader> { CdnMediaUploader(get()) }
}
