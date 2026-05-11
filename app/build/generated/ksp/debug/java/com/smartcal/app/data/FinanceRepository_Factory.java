package com.smartcal.app.data;

import com.smartcal.app.data.local.TransactionDao;
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
public final class FinanceRepository_Factory implements Factory<FinanceRepository> {
  private final Provider<TransactionDao> daoProvider;

  public FinanceRepository_Factory(Provider<TransactionDao> daoProvider) {
    this.daoProvider = daoProvider;
  }

  @Override
  public FinanceRepository get() {
    return newInstance(daoProvider.get());
  }

  public static FinanceRepository_Factory create(Provider<TransactionDao> daoProvider) {
    return new FinanceRepository_Factory(daoProvider);
  }

  public static FinanceRepository newInstance(TransactionDao dao) {
    return new FinanceRepository(dao);
  }
}
