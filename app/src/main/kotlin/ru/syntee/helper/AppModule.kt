package ru.syntee.helper

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.syntee.helper.core.network.AppInfo

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** BuildConfig-значения для сетевого слоя (library-модули его не имеют). */
    @Provides
    fun provideAppInfo(@ApplicationContext context: Context): AppInfo = AppInfo(
        versionName = BuildConfig.VERSION_NAME,
        isDebug = BuildConfig.DEBUG,
        defaultApiUrl = BuildConfig.SYNTEE_API_URL,
        defaultWebUrl = BuildConfig.SYNTEE_WEB_URL,
    )
}
