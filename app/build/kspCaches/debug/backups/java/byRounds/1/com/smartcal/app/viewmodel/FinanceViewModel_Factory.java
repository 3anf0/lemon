package com.smartcal.app.viewmodel;

import com.smartcal.app.data.FinanceRepository;
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
public final class FinanceViewModel_Factory implements Factory<FinanceViewModel> {
  private final Provider<FinanceRepository> repoProvider;

  public FinanceViewModel_Factory(Provider<FinanceRepository> repoProvider) {
    this.repoProvider = repoProvider;
  }

  @Override
  public FinanceViewModel get() {
    return newInstance(repoProvider.get());
  }

  public static FinanceViewModel_Factory create(Provider<FinanceRepository> repoProvider) {
    return new FinanceViewModel_Factory(repoProvider);
  }

  public static FinanceViewModel newInstance(FinanceRepository repo) {
    return new FinanceViewModel(repo);
  }
}
