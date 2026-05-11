package com.smartcal.app.viewmodel;

import android.content.Context;
import com.smartcal.app.data.ClaudeApiClient;
import com.smartcal.app.data.EventRepository;
import com.smartcal.app.data.FinanceRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class VoiceViewModel_Factory implements Factory<VoiceViewModel> {
  private final Provider<Context> contextProvider;

  private final Provider<ClaudeApiClient> geminiClientProvider;

  private final Provider<EventRepository> eventRepositoryProvider;

  private final Provider<FinanceRepository> financeRepositoryProvider;

  public VoiceViewModel_Factory(Provider<Context> contextProvider,
      Provider<ClaudeApiClient> geminiClientProvider,
      Provider<EventRepository> eventRepositoryProvider,
      Provider<FinanceRepository> financeRepositoryProvider) {
    this.contextProvider = contextProvider;
    this.geminiClientProvider = geminiClientProvider;
    this.eventRepositoryProvider = eventRepositoryProvider;
    this.financeRepositoryProvider = financeRepositoryProvider;
  }

  @Override
  public VoiceViewModel get() {
    return newInstance(contextProvider.get(), geminiClientProvider.get(), eventRepositoryProvider.get(), financeRepositoryProvider.get());
  }

  public static VoiceViewModel_Factory create(Provider<Context> contextProvider,
      Provider<ClaudeApiClient> geminiClientProvider,
      Provider<EventRepository> eventRepositoryProvider,
      Provider<FinanceRepository> financeRepositoryProvider) {
    return new VoiceViewModel_Factory(contextProvider, geminiClientProvider, eventRepositoryProvider, financeRepositoryProvider);
  }

  public static VoiceViewModel newInstance(Context context, ClaudeApiClient geminiClient,
      EventRepository eventRepository, FinanceRepository financeRepository) {
    return new VoiceViewModel(context, geminiClient, eventRepository, financeRepository);
  }
}
