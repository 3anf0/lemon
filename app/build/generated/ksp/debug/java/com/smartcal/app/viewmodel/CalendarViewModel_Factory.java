package com.smartcal.app.viewmodel;

import com.smartcal.app.data.EventRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class CalendarViewModel_Factory implements Factory<CalendarViewModel> {
  private final Provider<EventRepository> eventRepoProvider;

  public CalendarViewModel_Factory(Provider<EventRepository> eventRepoProvider) {
    this.eventRepoProvider = eventRepoProvider;
  }

  @Override
  public CalendarViewModel get() {
    return newInstance(eventRepoProvider.get());
  }

  public static CalendarViewModel_Factory create(Provider<EventRepository> eventRepoProvider) {
    return new CalendarViewModel_Factory(eventRepoProvider);
  }

  public static CalendarViewModel newInstance(EventRepository eventRepo) {
    return new CalendarViewModel(eventRepo);
  }
}
