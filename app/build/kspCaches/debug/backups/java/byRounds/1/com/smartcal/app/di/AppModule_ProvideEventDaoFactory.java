package com.smartcal.app.di;

import com.smartcal.app.data.local.AppDatabase;
import com.smartcal.app.data.local.EventDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class AppModule_ProvideEventDaoFactory implements Factory<EventDao> {
  private final Provider<AppDatabase> dbProvider;

  public AppModule_ProvideEventDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public EventDao get() {
    return provideEventDao(dbProvider.get());
  }

  public static AppModule_ProvideEventDaoFactory create(Provider<AppDatabase> dbProvider) {
    return new AppModule_ProvideEventDaoFactory(dbProvider);
  }

  public static EventDao provideEventDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideEventDao(db));
  }
}
