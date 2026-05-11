package com.smartcal.app.data;

import com.smartcal.app.data.local.EventDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class EventRepository_Factory implements Factory<EventRepository> {
  private final Provider<EventDao> daoProvider;

  public EventRepository_Factory(Provider<EventDao> daoProvider) {
    this.daoProvider = daoProvider;
  }

  @Override
  public EventRepository get() {
    return newInstance(daoProvider.get());
  }

  public static EventRepository_Factory create(Provider<EventDao> daoProvider) {
    return new EventRepository_Factory(daoProvider);
  }

  public static EventRepository newInstance(EventDao dao) {
    return new EventRepository(dao);
  }
}
