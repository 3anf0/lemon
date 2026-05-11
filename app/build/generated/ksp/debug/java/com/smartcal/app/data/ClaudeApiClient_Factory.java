package com.smartcal.app.data;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import okhttp3.OkHttpClient;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class ClaudeApiClient_Factory implements Factory<ClaudeApiClient> {
  private final Provider<OkHttpClient> httpClientProvider;

  public ClaudeApiClient_Factory(Provider<OkHttpClient> httpClientProvider) {
    this.httpClientProvider = httpClientProvider;
  }

  @Override
  public ClaudeApiClient get() {
    return newInstance(httpClientProvider.get());
  }

  public static ClaudeApiClient_Factory create(Provider<OkHttpClient> httpClientProvider) {
    return new ClaudeApiClient_Factory(httpClientProvider);
  }

  public static ClaudeApiClient newInstance(OkHttpClient httpClient) {
    return new ClaudeApiClient(httpClient);
  }
}
