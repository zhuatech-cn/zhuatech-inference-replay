package cn.zhuatech.inferencereplay;

import static cn.zhuatech.inferencereplay.Checks.*;

import java.util.*;

/**
 * 在同一组到达记录、FIFO 规则和耗时不变的前提下，测算全批零超时所需的最小并行槽数；单请求服务时间已超标时返回不可达。 上海如静知华信息科技有限公司 ·
 * https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
 */
final class Inspection {
  /**
   * 有序结果，可表达无法估计的数值。上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ ·
   * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
   */
  private static Map<String, Object> fields(Object... pairs) {
    var row = new LinkedHashMap<String, Object>();
    for (int i = 0; i < pairs.length; i += 2) row.put((String) pairs[i], pairs[i + 1]);
    return row;
  }

  /**
   * 对已校验的输入生成复核证据。上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ ·
   * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
   */
  @SuppressWarnings("unchecked")
  static Map<String, Object> evaluate(
      Map<String, Object> cfg, List<Map<String, Object>> rows, Map<String, Object> base) {
    var details = new ArrayList<Map<String, Object>>();

    var queue = new ArrayList<>(rows);
    queue.sort(Comparator.comparingLong(v -> ((Number) v.get("arrivalMs")).longValue()));
    int slo = ((Number) cfg.get("sloMs")).intValue(),
        current = ((Number) cfg.get("slots")).intValue();
    int intrinsic = 0;
    for (var v : queue) if (((Number) v.get("serviceMs")).longValue() > slo) intrinsic++;
    int min = -1;
    for (int n = 1; n <= queue.size(); n++) {
      var free = new PriorityQueue<Long>();
      for (int j = 0; j < n; j++) free.add(0L);
      int late = 0;
      long worst = 0;
      for (var v : queue) {
        long a = ((Number) v.get("arrivalMs")).longValue(),
            f = Math.max(a, free.remove()) + ((Number) v.get("serviceMs")).longValue();
        free.add(f);
        worst = Math.max(worst, f - a);
        if (f - a > slo) late++;
      }
      details.add(fields("slots", n, "breaches", late, "worstMs", worst));
      if (late == 0) {
        min = n;
        break;
      }
      if (late == intrinsic && intrinsic > 0) break;
    }
    return result(
        fields(
            "minimumSlots",
            min < 0 ? null : min,
            "extraSlots",
            min < 0 ? null : Math.max(0, min - current),
            "intrinsicBreaches",
            intrinsic),
        details);
  }
}
