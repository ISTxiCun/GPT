package com.istxicun.backend.common.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class PageQuery {
  @Min(1)
  private long page = 1;

  @Min(1)
  @Max(100)
  private long size = 20;

  public long getPage() {
    return page;
  }

  public void setPage(long page) {
    this.page = page;
  }

  public long getSize() {
    return size;
  }

  public void setSize(long size) {
    this.size = size;
  }
}
