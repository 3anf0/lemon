package com.smartcal.app.di;

import com.smartcal.app.data.local.AppDatabase;
import com.smartcal.app.data.local.TransactionDao;
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
public final class AppModule_ProvideTransactionDaoFactory implements Factory<TransactionDao> {
  private final Provider<AppDatabase> dbProvider;

  public AppModule_ProvideTransactionDaoFactory(Provider<AppDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public TransactionDao get() {
    return provideTransactionDao(dbProvider.get());
  }

  public static AppModule_ProvideTransactionDaoFactory create(Provider<AppDatabase> dbProvider) {
    return new AppModule_ProvideTransactionDaoFactory(dbProvider);
  }

  public static TransactionDao provideTransactionDao(AppDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideTransactionDao(db));
  }
}
