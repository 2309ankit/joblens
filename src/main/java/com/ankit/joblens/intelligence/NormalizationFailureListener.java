package com.ankit.joblens.intelligence;

import org.springframework.batch.core.listener.ItemProcessListener;
import org.springframework.batch.core.listener.SkipListener;

public class NormalizationFailureListener
    implements ItemProcessListener<RawJobPosting, NormalizedJob>,
        SkipListener<RawJobPosting, NormalizedJob> {

  private final NormalizationStatusService statusService;

  public NormalizationFailureListener(NormalizationStatusService statusService) {
    this.statusService = statusService;
  }

  @Override
  public void onProcessError(RawJobPosting item, Exception exception) {
    if (!(exception instanceof NormalizationRejectedException)) {
      statusService.failed(item.id(), exception);
    }
  }

  @Override
  public void onSkipInProcess(RawJobPosting item, Throwable throwable) {
    String reason =
        throwable.getMessage() == null ? "Normalization rejected" : throwable.getMessage();
    statusService.rejected(item.id(), reason);
  }
}
