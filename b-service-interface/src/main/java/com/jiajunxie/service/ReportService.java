package com.jiajunxie.service;

import com.jiajunxie.vo.ReportOverviewVo;
import com.jiajunxie.vo.ReportQueryVo;

public interface ReportService {
  ReportOverviewVo overview(ReportQueryVo query);
}
