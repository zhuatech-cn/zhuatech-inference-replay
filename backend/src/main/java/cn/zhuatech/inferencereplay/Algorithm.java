package cn.zhuatech.inferencereplay;

import static cn.zhuatech.inferencereplay.Checks.*;

import java.util.*;

/**
 * 使用已测到达时刻与服务耗时回放多槽 FIFO 队列；预测等待、完成时刻、端到端 P95 和超时请求。 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ ·
 * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
 */
final class Algorithm {
  /**
   * 离散事件模拟，不执行真实推理；假设各槽独立且耗时固定，不模拟 GPU 动态批处理。 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ ·
   * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
   */
  static Map<String, Object> compute(Map<String, Object> cfg, List<Map<String, Object>> rows) {
    var base = calculate(cfg, rows);
    var enriched = new LinkedHashMap<String, Object>(base);
    enriched.put("inspection", Inspection.evaluate(cfg, rows, base));
    enriched.put("diagnostics", Diagnostics.evaluate(cfg, rows, base));
    return enriched;
  }

  /**
   * 保留原始计算口径。上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
   */
  private static Map<String, Object> calculate(
      Map<String, Object> cfg, List<Map<String, Object>> rows) {
    must(rows != null && !rows.isEmpty() && rows.size() <= 200, "需要 1–200 行");
    unique(rows);
    var evidence = new ArrayList<Map<String, Object>>();
    int slots = integer(cfg, "slots", 1, 256), slo = integer(cfg, "sloMs", 1, 2000000000);
    var queue = new ArrayList<>(rows);
    for (var r : queue) {
      integer(r, "arrivalMs", 0, 2000000000);
      integer(r, "serviceMs", 1, 2000000000);
    }
    queue.sort(Comparator.comparingInt(r -> ((Number) r.get("arrivalMs")).intValue()));
    var free = new PriorityQueue<Long>();
    for (int i = 0; i < slots; i++) free.add(0L);
    int failures = 0;
    var lat = new ArrayList<Long>();
    for (var r : queue) {
      long at = ((Number) r.get("arrivalMs")).longValue(),
          begin = Math.max(at, free.remove()),
          end = begin + ((Number) r.get("serviceMs")).longValue(),
          latency = end - at;
      free.add(end);
      lat.add(latency);
      boolean bad = latency > slo;
      if (bad) failures++;
      evidence.add(
          Map.of(
              "id",
              r.get("id"),
              "waitMs",
              begin - at,
              "finishMs",
              end,
              "latencyMs",
              latency,
              "breach",
              bad));
    }
    Collections.sort(lat);
    return result(
        Map.of(
            "breaches",
            failures,
            "p95Ms",
            lat.get((95 * lat.size() + 99) / 100 - 1),
            "requests",
            lat.size()),
        evidence);
  }
}
