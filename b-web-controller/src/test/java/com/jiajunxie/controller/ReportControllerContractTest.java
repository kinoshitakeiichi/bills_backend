package com.jiajunxie.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.request.ReportQueryRequest;
import com.jiajunxie.service.ReportService;
import com.jiajunxie.vo.ReportOverviewVo;
import com.jiajunxie.vo.ReportQueryVo;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class ReportControllerContractTest {

  @Test
  void controllerUsesStableV1ReportBasePath() {
    RequestMapping mapping = ReportController.class.getAnnotation(RequestMapping.class);
    assertNotNull(mapping);
    assertEquals("/api/v1/reports", mapping.value()[0]);
  }

  @Test
  void overviewPassesExplicitTimezoneToReportService() throws Exception {
    Method method = ReportController.class.getMethod("overview", ReportQueryRequest.class);
    PostMapping mapping = method.getAnnotation(PostMapping.class);
    assertNotNull(mapping);
    assertEquals("/overview", mapping.value()[0]);

    RecordingReportService service = new RecordingReportService();
    ReportController controller = new ReportController(service);

    controller.overview(request("Asia/Tokyo"));

    assertNotNull(service.query);
    assertEquals(ZoneId.of("Asia/Tokyo"), service.query.timezone());
  }

  @Test
  void missingTimezoneDefaultsToTokyo() {
    RecordingReportService service = new RecordingReportService();
    ReportController controller = new ReportController(service);

    controller.overview(request(null));

    assertNotNull(service.query);
    assertEquals(ZoneId.of("Asia/Tokyo"), service.query.timezone());
  }

  @Test
  void invalidTimezoneFailsBeforeCallingService() {
    RecordingReportService service = new RecordingReportService();
    ReportController controller = new ReportController(service);

    assertThrows(BusinessException.class, () -> controller.overview(request("Mars/Olympus")));
    assertEquals(null, service.query);
  }

  private ReportQueryRequest request(String timezone) {
    return new ReportQueryRequest(
        LocalDate.of(2026, 6, 29),
        LocalDate.of(2026, 6, 30),
        "DAY",
        "JPY",
        List.of(),
        List.of(),
        List.of(),
        timezone);
  }

  private static final class RecordingReportService implements ReportService {
    private ReportQueryVo query;

    @Override
    public ReportOverviewVo overview(ReportQueryVo query) {
      this.query = query;
      return null;
    }
  }
}
