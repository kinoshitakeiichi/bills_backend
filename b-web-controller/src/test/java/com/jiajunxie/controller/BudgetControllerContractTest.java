package com.jiajunxie.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jiajunxie.common.exception.BusinessException;
import com.jiajunxie.convert.BudgetConvertWebMapper;
import com.jiajunxie.request.BudgetRequest;
import com.jiajunxie.response.BudgetActualResponse;
import com.jiajunxie.response.BudgetResponse;
import com.jiajunxie.service.BudgetService;
import com.jiajunxie.vo.BudgetActualVo;
import com.jiajunxie.vo.BudgetSaveVo;
import com.jiajunxie.vo.BudgetVo;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class BudgetControllerContractTest {

  @Test
  void controllerUsesStableV1BudgetBasePath() {
    RequestMapping mapping = BudgetController.class.getAnnotation(RequestMapping.class);
    assertNotNull(mapping);
    assertEquals("/api/v1/budgets", mapping.value()[0]);
  }

  @Test
  void actualEndpointIsExplicitAndTimezoneAware() throws Exception {
    Method method =
        BudgetController.class.getMethod("actual", String.class, String.class);
    GetMapping mapping = method.getAnnotation(GetMapping.class);
    assertNotNull(mapping);
    assertEquals("/{budgetId}/actual", mapping.value()[0]);

    RecordingBudgetService service = new RecordingBudgetService();
    BudgetController controller = new BudgetController(service, new BudgetConvertWebMapper());

    var response = controller.actual("budget", "Asia/Tokyo");

    assertEquals("budget", service.actualBudgetId);
    assertEquals(ZoneId.of("Asia/Tokyo"), service.actualZoneId);
    assertEquals("budget", response.getBody().data().budgetId());
  }

  @Test
  void invalidTimezoneFailsBeforeCallingService() {
    RecordingBudgetService service = new RecordingBudgetService();
    BudgetController controller = new BudgetController(service, new BudgetConvertWebMapper());

    assertThrows(BusinessException.class, () -> controller.actual("budget", "Mars/Olympus"));
    assertEquals(null, service.actualBudgetId);
  }

  @Test
  void createMapsNestedRequestIntoServiceContract() {
    RecordingBudgetService service = new RecordingBudgetService();
    BudgetController controller = new BudgetController(service, new BudgetConvertWebMapper());

    BudgetRequest request =
        new BudgetRequest(
            "Food",
            "MONTHLY",
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31),
            "JPY",
            true,
            List.of(
                new BudgetRequest.Item(
                    new BigDecimal("50000"),
                    "JPY",
                    false,
                    10,
                    List.of(
                        new BudgetRequest.Target(
                            "CATEGORY", "food", null, true)))));

    var response = controller.create(request);

    assertEquals("created-budget", response.getBody().data());
    assertNotNull(service.created);
    assertEquals("Food", service.created.budgetName());
    assertEquals("food", service.created.items().get(0).targets().get(0).targetId());
  }

  private static class RecordingBudgetService implements BudgetService {
    private BudgetSaveVo created;
    private String actualBudgetId;
    private ZoneId actualZoneId;

    @Override
    public List<BudgetVo> listBudgets() {
      return List.of();
    }

    @Override
    public BudgetVo getBudget(String budgetId) {
      return null;
    }

    @Override
    public String createBudget(BudgetSaveVo request) {
      created = request;
      return "created-budget";
    }

    @Override
    public int updateBudget(String budgetId, BudgetSaveVo request) {
      return 1;
    }

    @Override
    public int removeBudget(String budgetId) {
      return 1;
    }

    @Override
    public BudgetActualVo getActual(String budgetId, ZoneId timezone) {
      actualBudgetId = budgetId;
      actualZoneId = timezone;
      return new BudgetActualVo(
          budgetId,
          "Food",
          LocalDate.of(2026, 8, 1),
          LocalDate.of(2026, 8, 31),
          "JPY",
          List.of(
              new BudgetActualVo.ItemActual(
                  "item",
                  new BigDecimal("1000"),
                  new BigDecimal("200"),
                  new BigDecimal("800"),
                  "JPY",
                  false,
                  10,
                  List.of())));
    }
  }
}
