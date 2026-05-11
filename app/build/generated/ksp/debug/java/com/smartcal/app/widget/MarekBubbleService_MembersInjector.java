package com.smartcal.app.widget;

import com.smartcal.app.data.EventRepository;
import com.smartcal.app.data.FinanceRepository;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class MarekBubbleService_MembersInjector implements MembersInjector<MarekBubbleService> {
  private final Provider<EventRepository> eventRepositoryProvider;

  private final Provider<FinanceRepository> financeRepositoryProvider;

  public MarekBubbleService_MembersInjector(Provider<EventRepository> eventRepositoryProvider,
      Provider<FinanceRepository> financeRepositoryProvider) {
    this.eventRepositoryProvider = eventRepositoryProvider;
    this.financeRepositoryProvider = financeRepositoryProvider;
  }

  public static MembersInjector<MarekBubbleService> create(
      Provider<EventRepository> eventRepositoryProvider,
      Provider<FinanceRepository> financeRepositoryProvider) {
    return new MarekBubbleService_MembersInjector(eventRepositoryProvider, financeRepositoryProvider);
  }

  @Override
  public void injectMembers(MarekBubbleService instance) {
    injectEventRepository(instance, eventRepositoryProvider.get());
    injectFinanceRepository(instance, financeRepositoryProvider.get());
  }

  @InjectedFieldSignature("com.smartcal.app.widget.MarekBubbleService.eventRepository")
  public static void injectEventRepository(MarekBubbleService instance,
      EventRepository eventRepository) {
    instance.eventRepository = eventRepository;
  }

  @InjectedFieldSignature("com.smartcal.app.widget.MarekBubbleService.financeRepository")
  public static void injectFinanceRepository(MarekBubbleService instance,
      FinanceRepository financeRepository) {
    instance.financeRepository = financeRepository;
  }
}
