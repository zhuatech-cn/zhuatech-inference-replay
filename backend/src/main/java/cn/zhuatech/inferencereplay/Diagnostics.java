package cn.zhuatech.inferencereplay;

import java.util.*;

/**
 * 以请求到达、开始、完成为事件重建半开时间段，计算峰值等待数、有排队的时长、槽位利用率。利用率的窗口为首个到达到最后完成，不含窗口外空闲时间。 上海如静知华信息科技有限公司 ·
 * https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
 */
final class Diagnostics {
  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private static Map<String, Object> map(Object... pairs) {
    var m = new LinkedHashMap<String, Object>();
    for (int i = 0; i < pairs.length; i += 2) m.put((String) pairs[i], pairs[i + 1]);
    return m;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private static double num(Map<String, Object> m, String k) {
    return ((Number) m.get(k)).doubleValue();
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  private static Double ratio(double a, double b) {
    return b == 0 ? null : a / b;
  }

  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  @SuppressWarnings("unchecked")
  static Map<String, Object> evaluate(
      Map<String, Object> cfg, List<Map<String, Object>> rows, Map<String, Object> base) {
    var metrics = (Map<String, Object>) base.get("metrics");
    var evidence = (List<Map<String, Object>>) base.get("rows");
    var details = new ArrayList<Map<String, Object>>();
    var inputs = CorrectionDiff.byId(rows);
    var e = new TreeMap<Long, int[]>();
    int slots = (int) num(cfg, "slots");
    for (var r : evidence) {
      var src = inputs.get(r.get("id"));
      long arrival = (long) num(src, "arrivalMs"),
          end = (long) num(r, "finishMs"),
          start = end - (long) num(src, "serviceMs");
      e.computeIfAbsent(arrival, k -> new int[2])[1]++;
      var begin = e.computeIfAbsent(start, k -> new int[2]);
      begin[1]--;
      begin[0]++;
      e.computeIfAbsent(end, k -> new int[2])[0]--;
    }
    int busy = 0, waiting = 0, peak = 0;
    long last = e.firstKey(), occupied = 0, queuedTime = 0;
    for (var event : e.entrySet()) {
      long at = event.getKey(), dt = at - last;
      if (dt > 0) {
        details.add(map("fromMs", last, "toMs", at, "running", busy, "waitingNow", waiting));
        occupied += dt * busy;
        if (waiting > 0) queuedTime += dt;
        peak = Math.max(peak, waiting);
      }
      busy += event.getValue()[0];
      waiting += event.getValue()[1];
      last = at;
    }
    long horizon = e.lastKey() - e.firstKey();
    return Checks.result(
        map(
            "windowMs",
            horizon,
            "queueActiveMs",
            queuedTime,
            "peakWaiting",
            peak,
            "slotUtilization",
            ratio(occupied, (double) slots * horizon)),
        details);
  }
}
