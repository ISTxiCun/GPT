package com.istxicun.backend.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;

public record PageResult<T>(long page, long size, long total, List<T> records) {
  public static <T> PageResult<T> from(IPage<T> result) {
    return new PageResult<>(
        result.getCurrent(), result.getSize(), result.getTotal(), result.getRecords());
  }
}
