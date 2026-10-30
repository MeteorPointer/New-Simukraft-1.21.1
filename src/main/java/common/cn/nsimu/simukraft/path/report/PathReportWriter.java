package common.cn.nsimu.simukraft.path.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * 把一次采样写成中英对照的 CSV，以及可切换时段的 HTML 折线。
 */
public final class PathReportWriter {
    private static final double CAPTURE_SHARE = 0.25D;
    private static final double SATURATED_CAPTURE_SHARE = 0.10D;
    private static final double SAMPLE_WALK_SHARE = 0.30D;
    private static final double FRESH_SATURATED = 0.75D;
    private static final double FRESH_QUIET = 0.50D;
    private static final double TICK_SPIKE_MS = 50.0D;
    private static final List<Range> RANGES = List.of(
            new Range("all", "全程", "All", 0L),
            new Range("30s", "近30秒", "Last 30s", 30_000L),
            new Range("2m", "近2分钟", "Last 2 min", 120_000L),
            new Range("10m", "近10分钟", "Last 10 min", 600_000L),
            new Range("30m", "近30分钟", "Last 30 min", 1_800_000L));
    private static final List<Series> SERIES = series();

    private PathReportWriter() {
    }

    public static Written write(Path directory, String stamp, PathReportSession session) throws IOException {
        Files.createDirectories(directory);
        Path csv = directory.resolve("path-report-" + stamp + ".csv");
        Path html = directory.resolve("path-report-" + stamp + ".html");
        List<PathReportSession.Row> rows = session.rows();
        List<PathReportSession.Sample> samples = session.samples();
        long freshBudget = gaugeOf(rows, "state/freshBudget", 48L);
        long requestBudget = gaugeOf(rows, "state/requestBudget", 16L);
        long examineCap = gaugeOf(rows, "state/examineCap", 24L);
        Files.writeString(csv, csv(rows, samples), StandardCharsets.UTF_8);
        Files.writeString(html, html(stamp, session, rows, samples, freshBudget, requestBudget, examineCap), StandardCharsets.UTF_8);
        return new Written(csv, html);
    }

    /**
     * window：取记录末尾的一段时间。0 表示全程。折线和结论用同一段。
     */
    static List<PathReportSession.Sample> window(List<PathReportSession.Sample> samples, long rangeMs) {
        if (samples.isEmpty() || rangeMs <= 0L) {
            return samples;
        }
        long start = Math.max(0L, samples.get(samples.size() - 1).offsetMs() - rangeMs);
        List<PathReportSession.Sample> view = new ArrayList<>();
        for (PathReportSession.Sample sample : samples) {
            if (sample.offsetMs() >= start) {
                view.add(sample);
            }
        }
        return view;
    }

    private static List<PathReportSession.Sample> inDimension(List<PathReportSession.Sample> samples, String dimension) {
        List<PathReportSession.Sample> view = new ArrayList<>();
        for (PathReportSession.Sample sample : samples) {
            if (dimensionId(sample).equals(dimension)) {
                view.add(sample);
            }
        }
        return view;
    }

    /**
     * dimensions：队列最长的维度排在前面，页面打开时不会被空世界拉低均值。
     */
    private static List<String> dimensions(List<PathReportSession.Sample> samples) {
        Map<String, Long> peak = new LinkedHashMap<>();
        for (PathReportSession.Sample sample : samples) {
            peak.merge(dimensionId(sample), sample.queueSize(), Math::max);
        }
        if (peak.isEmpty()) {
            peak.put("", 0L);
        }
        List<String> ids = new ArrayList<>(peak.keySet());
        ids.sort(Comparator.comparingLong((String id) -> peak.getOrDefault(id, 0L)).reversed());
        return ids;
    }

    private static String dimensionId(PathReportSession.Sample sample) {
        String id = sample.dimension();
        return id == null ? "" : id;
    }

    private static String dimensionsJson(List<String> dimensionIds) {
        StringBuilder out = new StringBuilder();
        out.append('[');
        for (int i = 0; i < dimensionIds.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(jsonString(dimensionIds.get(i)));
        }
        out.append(']');
        return out.toString();
    }

    /**
     * diagnose：主线程卡在哪，以及人为什么还站着。两句分开，避免只留一个赢家。
     */
    static String diagnose(List<PathReportSession.Sample> samples, long freshBudget, long requestBudget, long examineCap) {
        if (samples.isEmpty()) {
            return "无数据 / No data";
        }
        Totals totals = Totals.of(samples);
        return mainLine(totals, freshBudget) + "\n" + waitLine(totals, freshBudget, requestBudget, examineCap) + "\n" + numbersLine(totals);
    }

    private static String csv(List<PathReportSession.Row> rows, List<PathReportSession.Sample> samples) {
        StringBuilder out = new StringBuilder();
        out.append("section,name_zh,name_en,depth,total_ms,self_ms,count,avg_ms,percent,gauge_avg\n");
        for (PathReportSession.Row row : rows) {
            long count = row.count();
            double totalMs = row.totalNanos() / 1_000_000.0D;
            double selfMs = row.selfNanos() / 1_000_000.0D;
            out.append("summary,").append(csvCell(zh(row.name()))).append(',').append(csvCell(en(row.name()))).append(',')
                    .append(row.depth()).append(',')
                    .append(num(totalMs)).append(',').append(num(selfMs)).append(',')
                    .append(count).append(',')
                    .append(num(count == 0L ? 0.0D : totalMs / count)).append(',')
                    .append(num(row.percent())).append(',')
                    .append(num(row.gaugeAverage())).append('\n');
        }
        out.append('\n');
        out.append("rollup,dimension,range,key,name_zh,name_en,unit,sum,avg,max,p95,pct_of_tick\n");
        for (String dimension : dimensions(samples)) {
            List<PathReportSession.Sample> scoped = inDimension(samples, dimension);
            for (Range range : RANGES) {
                List<PathReportSession.Sample> view = window(scoped, range.ms());
                double tick = sum(view, PathReportSession.Sample::tickMs);
                for (Series series : SERIES) {
                    Stats stats = Stats.of(view, series);
                    out.append("rollup,").append(csvCell(dimension)).append(',').append(range.id()).append(',')
                            .append(series.key()).append(',')
                            .append(csvCell(series.zh())).append(',').append(csvCell(series.en())).append(',')
                            .append(series.unit()).append(',')
                            .append(num(stats.sum())).append(',').append(num(stats.avg())).append(',')
                            .append(num(stats.max())).append(',').append(num(stats.p95())).append(',')
                            .append(series.tickShare() ? pct(stats.sum(), tick) : "").append('\n');
                }
            }
        }
        out.append('\n');
        out.append("dictionary,key,name_zh,name_en,unit,note_zh,note_en\n");
        for (Series series : SERIES) {
            out.append("dictionary,").append(series.key()).append(',')
                    .append(csvCell(series.zh())).append(',').append(csvCell(series.en())).append(',')
                    .append(series.unit()).append(',')
                    .append(csvCell(series.noteZh())).append(',').append(csvCell(series.noteEn())).append('\n');
        }
        out.append('\n');
        out.append("offset_ms,dimension");
        for (Series series : SERIES) {
            out.append(',').append(series.key());
        }
        out.append('\n');
        for (PathReportSession.Sample sample : samples) {
            out.append(sample.offsetMs()).append(',').append(csvCell(dimensionId(sample)));
            for (Series series : SERIES) {
                out.append(',').append(num(series.read(sample)));
            }
            out.append('\n');
        }
        return out.toString();
    }

    private static String html(String stamp, PathReportSession session, List<PathReportSession.Row> rows,
                               List<PathReportSession.Sample> samples, long freshBudget, long requestBudget, long examineCap) {
        List<String> dimensionIds = dimensions(samples);
        String verdict = diagnose(inDimension(samples, dimensionIds.get(0)), freshBudget, requestBudget, examineCap);
        StringBuilder table = new StringBuilder();
        for (PathReportSession.Row row : rows) {
            table.append("<tr><td class=\"name\" style=\"padding-left:").append(12 + row.depth() * 16)
                    .append("px\"><span class=\"bar\" style=\"width:").append(num(Math.min(100.0D, row.percent())))
                    .append("%\"></span><span>").append(escape(zh(row.name()))).append(" / ").append(escape(en(row.name())))
                    .append("</span></td><td>").append(num(row.totalNanos() / 1_000_000.0D))
                    .append("</td><td>").append(num(row.selfNanos() / 1_000_000.0D))
                    .append("</td><td>").append(row.count())
                    .append("</td><td>").append(row.name().startsWith("tick") ? num(row.percent()) + "%" : "")
                    .append("</td><td>").append(row.gaugeAverage() == 0.0D ? "" : num(row.gaugeAverage()))
                    .append("</td></tr>\n");
        }
        StringBuilder page = new StringBuilder();
        page.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\"><title>SimuKraft Path Report</title><style>");
        page.append("body{margin:0;background:#1b1d23;color:#e8e6e3;font:14px/1.45 Segoe UI,Microsoft YaHei,sans-serif}");
        page.append("header,section{padding:18px 28px}header{background:#242733;border-bottom:1px solid #343846}");
        page.append("h1{margin:0 0 6px;font-size:22px}h2{font-size:16px;margin:22px 0 8px}");
        page.append(".meta,.note{color:#a7adbd}.guide{margin:8px 0 0;padding-left:18px}.guide li{margin:4px 0}");
        page.append("#verdict{white-space:pre-wrap;background:#2a2e3a;border-radius:8px;padding:12px 14px;margin:12px 0}");
        page.append("button,label{margin:0 8px 8px 0}button{background:#3a4054;color:#fff;border:0;border-radius:4px;padding:6px 10px;cursor:pointer}");
        page.append("button.on{background:#e38c2d}label i{display:inline-block;width:10px;height:10px;border-radius:2px;margin:0 4px}");
        page.append("canvas{width:100%;background:#14161c;border-radius:8px;display:block;margin-bottom:8px}");
        page.append("#timeChart{height:280px}#searchChart{height:160px}#peopleChart,#flowChart{height:220px}");
        page.append("table{width: 100%;border-collapse:collapse}th,td{padding:7px 10px;text-align:right;border-bottom:1px solid #2c3040}");
        page.append("th{position:sticky;top:0;background:#20232d}td.name,th.name{text-align:left;position:relative}");
        page.append(".bar{position:absolute;left:0;top:0;bottom:0;background:rgba(227,140,45,.28)}td.name span:last-child{position:relative}");
        page.append("</style></head><body><header><h1>SimuKraft 寻路报告 / Path Report</h1><div class=\"meta\">");
        page.append(escape(stamp)).append(" · ").append(num(session.elapsedNanos() / 1_000_000_000.0D))
                .append(" s · ").append(session.ticks()).append(" ticks · 新区段上限 / fresh cap ")
                .append(freshBudget).append(" · 新请求上限 / start cap ").append(requestBudget)
                .append(" · 检查上限 / examine cap ").append(examineCap).append("</div></header><section>");
        page.append("<pre id=\"verdict\">").append(escape(verdict)).append("</pre>");
        page.append("<ul class=\"guide\">");
        page.append("<li>抄方块高，并且新采区段顶着上限：瓶颈在主线程采样。Block capture high and fresh sections at the cap: main-thread sampling.</li>");
        page.append("<li>后台搜索高、抄方块低：人不走是在等 A*。Search high and capture low: people are waiting on A*.</li>");
        page.append("<li>预算留队高、新采区段低：瓶颈在每拍放行人数。Budget holds high and fresh sections low: the per-tick start cap is the limit.</li>");
        page.append("<li>检查人数顶着上限、队列更长：后面的人还没被看到。Examine cap full and a longer queue: later people are not sampled yet.</li>");
        page.append("<li>路线不通高：这条路已被判死，加宽采样不会重试。Blocked routes stay dead until a block changes.</li>");
        page.append("<li>模拟距离外高：人在排队，但区块没在模拟，采样名额没花在他们身上。Outside simulation high: queued people are outside the ticking area.</li>");
        page.append("</ul><p class=\"note\">排队含采样，采样含抄方块，三项不能相加。后台寻路是工作线程耗时，记在结果回到主线程的那一拍，不计入 tick。</p>");
        page.append("<p class=\"note\">Queue includes sample, and sample includes block capture; do not add those three. Async search is worker time, stamped on the tick the result returns, and is not part of tick.</p>");
        page.append("<h2>维度 / Dimension</h2><p class=\"note\">空维度会把每拍均值拉低。页面默认打开队列最长的维度。Empty dimensions pull per-tick averages down. The page opens on the dimension with the longest queue.</p>");
        page.append("<div id=\"dimensions\"></div><div id=\"ranges\"></div>");
        page.append("<h2>主线程耗时 / Main-thread time</h2><p class=\"note\">折线是每个时间桶的峰值。红色虚线是 50 ms。Peak per bucket. The red dashed line is 50 ms.</p><div id=\"mainSeries\"></div><canvas id=\"timeChart\" width=\"1100\" height=\"280\"></canvas>");
        page.append("<h2>后台寻路 / Async search</h2><p class=\"note\">单独一条轴，避免工作线程耗时把主线程尖峰压扁。Its own axis, so worker time does not flatten main-thread spikes.</p><div id=\"searchSeries\"></div><canvas id=\"searchChart\" width=\"1100\" height=\"160\"></canvas>");
        page.append("<h2>人数 / People</h2><p class=\"note\">折线是每个时间桶的峰值。Peak per bucket.</p><div id=\"peopleSeries\"></div><canvas id=\"peopleChart\" width=\"1100\" height=\"220\"></canvas>");
        page.append("<h2>每拍流量 / Per-tick flow</h2><p class=\"note\">折线是每个时间桶的合计。Sum per bucket.</p><div id=\"flowSeries\"></div><canvas id=\"flowChart\" width=\"1100\" height=\"220\"></canvas>");
        page.append("<h2>时段汇总 / Window rollup</h2><table><thead><tr>");
        page.append("<th class=\"name\">中文 / Chinese</th><th class=\"name\">English</th><th>单位 / Unit</th><th>合计 / Sum</th><th>平均 / Avg</th><th>峰值 / Max</th><th>P95</th><th>占 tick / % of tick</th>");
        page.append("</tr></thead><tbody id=\"rollup\"></tbody></table>");
        page.append("<h2>占用表 / Occupancy</h2><table><thead><tr><th class=\"name\">名称 / Name</th><th>总耗时 / Total ms</th><th>自身 / Self ms</th><th>次数 / Count</th><th>占 tick / % of tick</th><th>瞬时均值 / Gauge avg</th></tr></thead><tbody>");
        page.append(table);
        page.append("</tbody></table></section><script>const samples=").append(jsonSamples(samples))
                .append(";const packs=").append(packsJson(samples, freshBudget, requestBudget, examineCap))
                .append(";const series=").append(seriesJson())
                .append(";const ranges=").append(rangesJson())
                .append(";const dimensions=").append(dimensionsJson(dimensionIds)).append(';');
        page.append(SCRIPT);
        page.append("</script></body></html>");
        return page.toString();
    }

    private static String mainLine(Totals totals, long freshBudget) {
        if (totals.tick() <= 0.0D) {
            return "这一段没有主线程耗时 / No main-thread time in this window.";
        }
        double captureShare = totals.capture() / totals.tick();
        if (captureShare >= CAPTURE_SHARE
                || (freshBudget > 0L && totals.freshPer() >= freshBudget * FRESH_SATURATED && captureShare >= SATURATED_CAPTURE_SHARE)) {
            return "主线程卡在抄方块 / Main thread is in block capture. 抄方块占 tick " + pct(totals.capture(), totals.tick())
                    + "，新采 " + num1(totals.freshPer()) + " /拍，上限 " + freshBudget + "。";
        }
        if (totals.sample() / totals.tick() >= SAMPLE_WALK_SHARE && captureShare < SATURATED_CAPTURE_SHARE) {
            return "主线程卡在采样遍历 / Main thread is walking cached sections. 采样占 tick " + pct(totals.sample(), totals.tick())
                    + "，抄方块只占 " + pct(totals.capture(), totals.tick()) + "。";
        }
        Part best = heaviest(totals);
        if (totals.tickPeak() >= TICK_SPIKE_MS) {
            return "主线程尖峰 " + num1(totals.tickPeak()) + " ms，超过 50 ms / Main-thread spike over 50 ms. 最重的是 "
                    + best.zh() + " / " + best.en() + "，占 tick " + pct(best.ms(), totals.tick()) + "。";
        }
        return "主线程没有超过 50 ms 的尖峰 / No main-thread spike over 50 ms. 最重的是 "
                + best.zh() + " / " + best.en() + "，占 tick " + pct(best.ms(), totals.tick()) + "。";
    }

    private static String waitLine(Totals totals, long freshBudget, long requestBudget, long examineCap) {
        if (totals.rejected() > 0 && totals.rejected() >= totals.started()) {
            return "线程池拒绝 / Worker pool rejected. 拒绝 " + totals.rejected() + " 次，开工 " + totals.started() + " 次。新搜索没有发出去。";
        }
        if (totals.blocked() > 0 && totals.blocked() >= totals.started() && totals.failures() > 0) {
            return "路线不通 / Route blocked. 被标成不通 " + totals.blocked() + " 人次，失败 " + totals.failures()
                    + " 次。16 格余量失败后要等方块变化才会重试。";
        }
        if (totals.budgetHold() > totals.started() && totals.queueMax() > requestBudget) {
            return "放行预算 / New-path budget. 因预算或活动上限留队 " + totals.budgetHold() + " 人次，开工 " + totals.started()
                    + " 次。每拍新请求上限 " + requestBudget + "，队列峰值 " + totals.queueMax() + "。";
        }
        if (totals.notTicking() > totals.started() && totals.notTicking() > totals.sampleMiss() && totals.queueMax() > 0L) {
            return "模拟距离外 / Outside simulation. 放回队列 " + totals.notTicking()
                    + " 人次，因为所在区块没有在模拟。采样名额没有花在他们身上。";
        }
        if (totals.sampleMiss() > totals.started() && (freshBudget <= 0L || totals.freshPer() < freshBudget * FRESH_QUIET)) {
            return "采样未齐 / Sample not ready. 没采齐就回队列 " + totals.sampleMiss() + " 人次，新采 " + num1(totals.freshPer())
                    + " /拍，上限 " + freshBudget + "。";
        }
        if (examineCap > 0L && totals.examinedMax() >= examineCap && totals.queueMax() > examineCap
                && (freshBudget <= 0L || totals.freshPer() < freshBudget * FRESH_QUIET)) {
            return "检查上限 / Examine cap. 每拍只检查最近 " + examineCap + " 人，这一段检查峰值 " + totals.examinedMax()
                    + "，队列峰值 " + totals.queueMax() + "。后面的人还没进入采样。";
        }
        if (totals.search() > totals.tick() && totals.pendingMax() >= 4L) {
            return "后台搜索 / Async search. 工作线程合计 " + num1(totals.search()) + " ms，超过主线程 tick 合计 "
                    + num1(totals.tick()) + " ms。等待搜索峰值 " + totals.pendingMax() + "。人不走是在等 A*，主线程不是被采样卡死。";
        }
        if (totals.started() > 0 && totals.failures() > totals.started()) {
            return "寻路失败 / Search failures. 失败 " + totals.failures() + " 次，开工 " + totals.started() + " 次。";
        }
        return "没有单一流量信号 / No single flow signal. 队列、采样预算和后台搜索都没有单独超标。";
    }

    private static String numbersLine(Totals totals) {
        return totals.n() + " 拍 / ticks · tick 合计 / total " + num1(totals.tick()) + " ms · 峰值 / peak " + num1(totals.tickPeak())
                + " ms · 抄方块 / capture " + num1(totals.capture()) + " ms (" + pct(totals.capture(), totals.tick()) + ")"
                + " · 采样 / sample " + num1(totals.sample()) + " ms · 后台搜索 / search " + num1(totals.search()) + " ms"
                + " · 新采 / fresh " + num1(totals.freshPer()) + "/拍 · 开工 / started " + num1(totals.startedPer()) + "/拍"
                + " · 队列峰值 / queue peak " + totals.queueMax() + " · 等待搜索峰值 / pending peak " + totals.pendingMax()
                + " · 预算留队 / budget holds " + totals.budgetHold() + " · 采样未齐 / sample misses " + totals.sampleMiss()
                + " · 路线不通 / blocked " + totals.blocked() + " · 失败 / failures " + totals.failures()
                + " · 线程拒绝 / rejected " + totals.rejected() + " · 模拟距离外 / outside sim " + totals.notTicking()
                + " · 余量峰值 / padding peak " + totals.paddingMax();
    }

    private static Part heaviest(Totals totals) {
        Part best = new Part("抄方块", "Block capture", totals.capture());
        best = maxPart(best, new Part("采样其余", "Other sampling", Math.max(0.0D, totals.sample() - totals.capture())));
        best = maxPart(best, new Part("排队其余", "Other queue", Math.max(0.0D, totals.queue() - totals.sample())));
        best = maxPart(best, new Part("活动移动", "Active movement", totals.active()));
        best = maxPart(best, new Part("开门", "Doors", totals.doors()));
        best = maxPart(best, new Part("距离场", "Distance field", totals.field()));
        return maxPart(best, new Part("收完搜索", "Apply completed", totals.completed()));
    }

    private static Part maxPart(Part best, Part next) {
        return next.ms() > best.ms() ? next : best;
    }

    private static String packsJson(List<PathReportSession.Sample> samples, long freshBudget, long requestBudget, long examineCap) {
        List<String> dimensionIds = dimensions(samples);
        StringBuilder out = new StringBuilder();
        out.append('{');
        for (int d = 0; d < dimensionIds.size(); d++) {
            if (d > 0) {
                out.append(',');
            }
            String dimension = dimensionIds.get(d);
            List<PathReportSession.Sample> scoped = inDimension(samples, dimension);
            out.append(jsonString(dimension)).append(":{");
            for (int i = 0; i < RANGES.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                Range range = RANGES.get(i);
                List<PathReportSession.Sample> view = window(scoped, range.ms());
                out.append(jsonString(range.id())).append(":{");
                out.append("\"verdict\":").append(jsonString(diagnose(view, freshBudget, requestBudget, examineCap)));
                out.append(",\"rows\":").append(rollupJson(view)).append('}');
            }
            out.append('}');
        }
        out.append('}');
        return out.toString();
    }

    private static String rollupJson(List<PathReportSession.Sample> samples) {
        StringBuilder out = new StringBuilder();
        double tick = sum(samples, PathReportSession.Sample::tickMs);
        out.append('[');
        for (int i = 0; i < SERIES.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            Series series = SERIES.get(i);
            Stats stats = Stats.of(samples, series);
            out.append('[')
                    .append(jsonString(series.zh())).append(',')
                    .append(jsonString(series.en())).append(',')
                    .append(jsonString(series.unit())).append(',')
                    .append(jsonString(num(stats.sum()))).append(',')
                    .append(jsonString(num(stats.avg()))).append(',')
                    .append(jsonString(num(stats.max()))).append(',')
                    .append(jsonString(num(stats.p95()))).append(',')
                    .append(jsonString(series.tickShare() ? pct(stats.sum(), tick) : "")).append(']');
        }
        out.append(']');
        return out.toString();
    }

    private static String jsonSamples(List<PathReportSession.Sample> samples) {
        StringBuilder out = new StringBuilder();
        out.append('[');
        for (int i = 0; i < samples.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            PathReportSession.Sample sample = samples.get(i);
            out.append('[').append(sample.offsetMs());
            for (Series series : SERIES) {
                out.append(',').append(num(series.read(sample)));
            }
            out.append(',').append(jsonString(dimensionId(sample))).append(']');
        }
        out.append(']');
        return out.toString();
    }

    private static String seriesJson() {
        StringBuilder out = new StringBuilder();
        out.append('[');
        boolean first = true;
        for (int i = 0; i < SERIES.size(); i++) {
            Series series = SERIES.get(i);
            if ("table".equals(series.chart())) {
                continue;
            }
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append("{\"i\":").append(i + 1)
                    .append(",\"key\":").append(jsonString(series.key()))
                    .append(",\"zh\":").append(jsonString(series.zh()))
                    .append(",\"en\":").append(jsonString(series.en()))
                    .append(",\"color\":").append(jsonString(series.color()))
                    .append(",\"group\":").append(jsonString(series.chart()))
                    .append(",\"on\":").append(series.shown()).append('}');
        }
        out.append(']');
        return out.toString();
    }

    private static String rangesJson() {
        StringBuilder out = new StringBuilder();
        out.append('[');
        for (int i = 0; i < RANGES.size(); i++) {
            Range range = RANGES.get(i);
            if (i > 0) {
                out.append(',');
            }
            out.append("{\"id\":").append(jsonString(range.id()))
                    .append(",\"zh\":").append(jsonString(range.zh()))
                    .append(",\"en\":").append(jsonString(range.en()))
                    .append(",\"ms\":").append(range.ms()).append('}');
        }
        out.append(']');
        return out.toString();
    }

    private static long gaugeOf(List<PathReportSession.Row> rows, String name, long fallback) {
        for (PathReportSession.Row row : rows) {
            if (name.equals(row.name()) && row.gaugeLast() > 0L) {
                return row.gaugeLast();
            }
        }
        return fallback;
    }

    private static double sum(List<PathReportSession.Sample> samples, ToDoubleFunction<PathReportSession.Sample> read) {
        double total = 0.0D;
        for (PathReportSession.Sample sample : samples) {
            total += read.applyAsDouble(sample);
        }
        return total;
    }

    private static String zh(String name) {
        return switch (name) {
            case "tick" -> "游戏刻";
            case "tick/applyCompleted" -> "收完搜索";
            case "tick/activePaths" -> "活动移动";
            case "tick/doors" -> "开门";
            case "tick/queue" -> "排队处理";
            case "tick/queue/sample" -> "区段采样";
            case "tick/queue/sample/capture" -> "抄方块";
            case "tick/field" -> "距离场";
            case "async/search" -> "后台寻路";
            case "state/queue" -> "队列长度";
            case "state/active" -> "活动路径";
            case "state/pending" -> "等待搜索";
            case "state/padding" -> "左右余量";
            case "state/freshBudget" -> "新区段上限";
            case "state/requestBudget" -> "新请求上限";
            case "state/examineCap" -> "检查上限";
            case "flow/freshSections" -> "新采区段";
            case "flow/cacheHits" -> "路径缓存命中";
            case "flow/examined" -> "检查人数";
            case "flow/waiting" -> "等待采样";
            case "flow/failures" -> "寻路失败";
            case "flow/started" -> "新开工";
            case "flow/sampleMiss" -> "采样未齐";
            case "flow/budgetHold" -> "预算留队";
            case "flow/blocked" -> "路线不通";
            case "flow/completed" -> "搜索完成";
            case "flow/rejected" -> "线程拒绝";
            case "flow/notTicking" -> "模拟距离外";
            case "flow/flatColumns" -> "平坦街区";
            default -> name;
        };
    }

    private static String en(String name) {
        return switch (name) {
            case "tick" -> "Tick";
            case "tick/applyCompleted" -> "Apply completed";
            case "tick/activePaths" -> "Active movement";
            case "tick/doors" -> "Doors";
            case "tick/queue" -> "Queue";
            case "tick/queue/sample" -> "Section sample";
            case "tick/queue/sample/capture" -> "Block capture";
            case "tick/field" -> "Distance field";
            case "async/search" -> "Async search";
            case "state/queue" -> "Queue size";
            case "state/active" -> "Active paths";
            case "state/pending" -> "Pending searches";
            case "state/padding" -> "Horizontal padding";
            case "state/freshBudget" -> "Fresh section cap";
            case "state/requestBudget" -> "New request cap";
            case "state/examineCap" -> "Examine cap";
            case "flow/freshSections" -> "Fresh sections";
            case "flow/cacheHits" -> "Path cache hits";
            case "flow/examined" -> "Examined";
            case "flow/waiting" -> "Waiting for sample";
            case "flow/failures" -> "Search failures";
            case "flow/started" -> "Searches started";
            case "flow/sampleMiss" -> "Sample not ready";
            case "flow/budgetHold" -> "Held by budget";
            case "flow/blocked" -> "Route blocked";
            case "flow/completed" -> "Searches finished";
            case "flow/rejected" -> "Worker rejects";
            case "flow/notTicking" -> "Outside simulation";
            case "flow/flatColumns" -> "Flat blocks";
            default -> name.substring(name.lastIndexOf('/') + 1);
        };
    }

    private static String num(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String num1(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String pct(double part, double whole) {
        if (whole <= 0.0D) {
            return "0%";
        }
        return String.format(Locale.ROOT, "%.1f%%", part * 100.0D / whole);
    }

    private static String csvCell(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String jsonString(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '<' -> out.append("\\u003c");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
        return out.toString();
    }

    private static List<Series> series() {
        return List.of(
                series("tick_ms", "游戏刻", "Tick", "ms", "main", "#e38c2d", true, true,
                        "这一拍寻路在主线程的总耗时", "Main-thread time of this path tick", PathReportSession.Sample::tickMs),
                series("capture_ms", "抄方块", "Block capture", "ms", "main", "#4aa3ff", true, true,
                        "新采区段的 getBlockState，含在采样里", "getBlockState for a fresh section; included in sample", PathReportSession.Sample::captureMs),
                series("sample_ms", "区段采样", "Section sample", "ms", "main", "#9ad0ff", true, true,
                        "采样总耗时，含抄方块和走过缓存", "Sample time, including capture and cache walks", PathReportSession.Sample::sampleMs),
                series("queue_ms", "排队处理", "Queue", "ms", "main", "#8bd17c", false, true,
                        "排队处理总耗时，含采样", "Queue processing, including sample", PathReportSession.Sample::queueMs),
                series("search_ms", "后台寻路", "Async search", "ms", "search", "#d96b6b", true, false,
                        "工作线程 A* 耗时，记在结果回到主线程的那一拍，不计入 tick", "Worker A* time, stamped when the result returns; not part of tick", PathReportSession.Sample::searchMs),
                series("active_ms", "活动移动", "Active movement", "ms", "main", "#c58bff", false, true,
                        "已有路径的移动步进", "Stepping citizens who already have a path", PathReportSession.Sample::activeMs),
                series("doors_ms", "开门", "Doors", "ms", "main", "#f2d16b", false, true,
                        "处理打开的门", "Open-door processing", PathReportSession.Sample::doorsMs),
                series("field_ms", "距离场", "Distance field", "ms", "main", "#5ec8c5", false, true,
                        "主线程推进共享距离场", "Main-thread distance-field step", PathReportSession.Sample::fieldMs),
                series("completed_ms", "收完搜索", "Apply completed", "ms", "main", "#e07bb0", false, true,
                        "把完成的搜索接回路", "Applying finished searches", PathReportSession.Sample::completedMs),
                series("queue_size", "队列长度", "Queue size", "人", "people", "#e38c2d", true, false,
                        "这一拍结束时还在排队的人", "People still queued at tick end", sample -> sample.queueSize()),
                series("active_count", "活动路径", "Active paths", "人", "people", "#8bd17c", true, false,
                        "正在沿路径移动的人", "People moving on a path", sample -> sample.active()),
                series("pending_count", "等待搜索", "Pending searches", "人", "people", "#d96b6b", true, false,
                        "已开工、还没出结果的人", "Searches started but not finished", sample -> sample.pending()),
                series("padding", "左右余量", "Horizontal padding", "格", "table", "", false, false,
                        "这一拍采样分级的左右余量", "Horizontal sample padding this tick", sample -> sample.padding()),
                series("fresh_sections", "新采区段", "Fresh sections", "段", "flow", "#4aa3ff", true, false,
                        "这一拍新抄的 16³ 区段，上限通常是 48", "Newly captured sections; cap is usually 48", sample -> sample.freshSections()),
                series("cache_hits", "路径缓存命中", "Path cache hits", "次", "flow", "#5ec8c5", false, false,
                        "直接复用已有路径、不再搜索", "Reused a finished path and skipped search", sample -> sample.cacheHits()),
                series("examined", "检查人数", "Examined", "人", "flow", "#d0d4e0", false, false,
                        "这一拍从队列里拿出来看的人，上限通常是 24", "People taken from the queue; cap is usually 24", sample -> sample.examined()),
                series("waiting", "等待采样", "Waiting for sample", "人", "flow", "#f2d16b", true, false,
                        "检查过、但没有命中路径缓存的人", "Examined people who missed the path cache", sample -> sample.waiting()),
                series("failures", "寻路失败", "Search failures", "次", "flow", "#ff6b6b", true, false,
                        "搜索失败或余量升级", "Search failed or padding was raised", sample -> sample.failures()),
                series("started", "新开工", "Searches started", "次", "flow", "#c58bff", true, false,
                        "这一拍真正提交到工作线程的搜索", "Searches submitted to workers this tick", sample -> sample.started()),
                series("sample_miss", "采样未齐", "Sample not ready", "次", "flow", "#ff9f43", true, false,
                        "快照没采齐，人回到队列", "Snapshot was incomplete, so the person was requeued", sample -> sample.sampleMiss()),
                series("budget_hold", "预算留队", "Held by budget", "次", "flow", "#a0a4b8", true, false,
                        "因每拍新请求上限或活动上限留在队列里", "Held by the per-tick start cap or the active cap", sample -> sample.budgetHold()),
                series("blocked", "路线不通", "Route blocked", "次", "flow", "#888888", false, false,
                        "这条分区已被判成不通，要等方块变化", "Partition marked impassable until a block changes", sample -> sample.blocked()),
                series("completed_searches", "搜索完成", "Searches finished", "次", "flow", "#e07bb0", false, false,
                        "这一拍取回结果的搜索次数", "Searches whose results were collected this tick", sample -> sample.completedSearches()),
                series("rejected", "线程拒绝", "Worker rejects", "次", "flow", "#ff4d6d", false, false,
                        "线程池拒绝了新搜索", "The worker pool rejected a new search", sample -> sample.rejected()),
                series("not_ticking", "模拟距离外", "Outside simulation", "次", "flow", "#f0a202", true, false,
                        "人不在模拟距离里，被放回队列，不占用新区段", "Requeued because the chunk was not entity-ticking", sample -> sample.notTicking()));
    }

    private static Series series(String key, String zh, String en, String unit, String chart, String color, boolean shown, boolean tickShare,
                                 String noteZh, String noteEn, ToDoubleFunction<PathReportSession.Sample> value) {
        return new Series(key, zh, en, unit, chart, color, shown, tickShare, noteZh, noteEn, value);
    }

    private static final String SCRIPT = """
            let range = ranges[0];
            let dimension = dimensions[0];
            const shown = {};
            series.forEach(item => { shown[item.key] = item.on; });
            function mount() {
              const dimBox = document.getElementById('dimensions');
              dimensions.forEach(id => {
                const button = document.createElement('button');
                button.type = 'button';
                button.textContent = id || '未标记 / Unlabeled';
                button.dataset.id = id;
                button.onclick = () => { dimension = id; paintDimensions(); renderPack(); drawAll(); };
                dimBox.appendChild(button);
              });
              const rangeBox = document.getElementById('ranges');
              ranges.forEach(item => {
                const button = document.createElement('button');
                button.type = 'button';
                button.textContent = item.zh + ' / ' + item.en;
                button.dataset.id = item.id;
                button.onclick = () => { range = item; paintRanges(); renderPack(); drawAll(); };
                rangeBox.appendChild(button);
              });
              mountSeries('mainSeries', 'main');
              mountSeries('searchSeries', 'search');
              mountSeries('peopleSeries', 'people');
              mountSeries('flowSeries', 'flow');
              paintDimensions();
              paintRanges();
              renderPack();
            }
            function paintDimensions() {
              document.querySelectorAll('#dimensions button').forEach(button => {
                button.classList.toggle('on', button.dataset.id === dimension);
              });
            }
            function mountSeries(id, group) {
              const box = document.getElementById(id);
              series.filter(item => item.group === group).forEach(item => {
                const label = document.createElement('label');
                const input = document.createElement('input');
                input.type = 'checkbox';
                input.checked = !!shown[item.key];
                input.onchange = () => { shown[item.key] = input.checked; drawAll(); };
                const swatch = document.createElement('i');
                swatch.style.background = item.color;
                label.appendChild(input);
                label.appendChild(swatch);
                label.append(' ' + item.zh + ' / ' + item.en);
                box.appendChild(label);
              });
            }
            function paintRanges() {
              document.querySelectorAll('#ranges button').forEach(button => {
                button.classList.toggle('on', button.dataset.id === range.id);
              });
            }
            function renderPack() {
              const pack = packs[dimension] ? packs[dimension][range.id] : null;
              document.getElementById('verdict').textContent = pack ? pack.verdict : '';
              const body = document.getElementById('rollup');
              body.replaceChildren();
              if (!pack) return;
              pack.rows.forEach(row => {
                const tr = document.createElement('tr');
                row.forEach((cell, index) => {
                  const td = document.createElement('td');
                  if (index < 2) td.className = 'name';
                  td.textContent = String(cell);
                  tr.appendChild(td);
                });
                body.appendChild(tr);
              });
            }
            function currentView() {
              const scoped = samples.filter(row => row[row.length - 1] === dimension);
              if (!scoped.length) return [];
              const end = scoped[scoped.length - 1][0];
              const start = range.ms ? Math.max(0, end - range.ms) : 0;
              return scoped.filter(row => row[0] >= start);
            }
            function drawAll() {
              draw(document.getElementById('timeChart'), 'main', 'max', true);
              draw(document.getElementById('searchChart'), 'search', 'max', false);
              draw(document.getElementById('peopleChart'), 'people', 'max', false);
              draw(document.getElementById('flowChart'), 'flow', 'sum', false);
            }
            function draw(canvas, group, mode, markTick) {
              const items = series.filter(item => item.group === group && shown[item.key]);
              const width = Math.max(640, canvas.clientWidth || 1100);
              if (canvas.width !== width) canvas.width = width;
              const ctx = canvas.getContext('2d');
              const w = canvas.width;
              const h = canvas.height;
              ctx.clearRect(0, 0, w, h);
              ctx.fillStyle = '#14161c';
              ctx.fillRect(0, 0, w, h);
              const view = currentView();
              ctx.font = '12px Segoe UI, Microsoft YaHei, sans-serif';
              if (!view.length || !items.length) {
                ctx.fillStyle = '#a7adbd';
                ctx.fillText('无数据 / No data', 56, 36);
                return;
              }
              const buckets = bucket(view, items, mode);
              let max = 0.001;
              buckets.forEach(b => items.forEach(item => { if (b[item.key] > max) max = b[item.key]; }));
              if (markTick) max = Math.max(max, 50);
              ctx.strokeStyle = '#343846';
              ctx.beginPath();
              ctx.moveTo(56, 16);
              ctx.lineTo(56, h - 28);
              ctx.lineTo(w - 12, h - 28);
              ctx.stroke();
              ctx.fillStyle = '#a7adbd';
              ctx.fillText(max >= 100 ? String(Math.round(max)) : max.toFixed(2), 4, 24);
              ctx.fillText('0', 40, h - 32);
              if (markTick) {
                const y = h - 28 - (h - 52) * (50 / max);
                ctx.save();
                ctx.strokeStyle = '#ff5a5a';
                ctx.setLineDash([4, 4]);
                ctx.beginPath();
                ctx.moveTo(56, y);
                ctx.lineTo(w - 12, y);
                ctx.stroke();
                ctx.restore();
                ctx.fillStyle = '#ff8d8d';
                ctx.fillText('50 ms', w - 58, Math.max(16, y - 4));
              }
              const t0 = buckets[0].t;
              const t1 = buckets[buckets.length - 1].t;
              ctx.fillStyle = '#a7adbd';
              ctx.fillText(formatOffset(t0), 56, h - 8);
              const endLabel = formatOffset(t1);
              ctx.fillText(endLabel, w - 12 - ctx.measureText(endLabel).width, h - 8);
              items.forEach(item => {
                ctx.strokeStyle = item.color;
                ctx.lineWidth = 1.6;
                ctx.beginPath();
                buckets.forEach((b, i) => {
                  const x = 56 + (w - 76) * (buckets.length === 1 ? 0.5 : i / (buckets.length - 1));
                  const y = h - 28 - (h - 52) * ((b[item.key] || 0) / max);
                  if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
                });
                ctx.stroke();
              });
            }
            function bucket(view, items, mode) {
              const span = Math.max(1, view[view.length - 1][0] - view[0][0]);
              const bucketMs = Math.max(50, Math.ceil(span / 480 / 50) * 50);
              const groups = [];
              let current = null;
              view.forEach(row => {
                const slot = Math.floor(row[0] / bucketMs);
                if (!current || current.slot !== slot) {
                  if (current) groups.push(current);
                  current = { slot: slot, t: row[0], values: {} };
                  items.forEach(item => { current.values[item.key] = 0; });
                }
                current.t = row[0];
                items.forEach(item => {
                  const value = row[item.i] || 0;
                  current.values[item.key] = mode === 'max' ? Math.max(current.values[item.key], value) : current.values[item.key] + value;
                });
              });
              if (current) groups.push(current);
              return groups.map(group => Object.assign({ t: group.t }, group.values));
            }
            function formatOffset(ms) {
              const total = Math.max(0, Math.floor(ms / 1000));
              const minutes = Math.floor(total / 60);
              const seconds = total % 60;
              return minutes + ':' + String(seconds).padStart(2, '0');
            }
            mount();
            drawAll();
            window.addEventListener('resize', drawAll);
            """;

    private record Range(String id, String zh, String en, long ms) {
    }

    private record Series(String key, String zh, String en, String unit, String chart, String color, boolean shown, boolean tickShare,
                          String noteZh, String noteEn, ToDoubleFunction<PathReportSession.Sample> value) {
        private double read(PathReportSession.Sample sample) {
            return value.applyAsDouble(sample);
        }
    }

    private record Part(String zh, String en, double ms) {
    }

    private record Stats(double sum, double avg, double max, double p95) {
        private static Stats of(List<PathReportSession.Sample> samples, Series series) {
            if (samples.isEmpty()) {
                return new Stats(0.0D, 0.0D, 0.0D, 0.0D);
            }
            double[] values = new double[samples.size()];
            double total = 0.0D;
            double max = 0.0D;
            for (int i = 0; i < samples.size(); i++) {
                double value = series.read(samples.get(i));
                values[i] = value;
                total += value;
                if (value > max) {
                    max = value;
                }
            }
            return new Stats(total, total / samples.size(), max, percentile(values, 0.95D));
        }

        private static double percentile(double[] values, double p) {
            double[] copy = Arrays.copyOf(values, values.length);
            Arrays.sort(copy);
            int index = (int) Math.ceil(p * values.length) - 1;
            return copy[Math.max(0, Math.min(copy.length - 1, index))];
        }
    }

    private record Totals(int n, double tick, double capture, double sample, double queue, double search, double active,
                          double doors, double field, double completed, double tickPeak, double freshPer, double startedPer,
                          long fresh, long started, long budgetHold, long blocked, long failures, long sampleMiss, long rejected,
                          long notTicking, long queueMax, long pendingMax, long examinedMax, long paddingMax) {
        private static Totals of(List<PathReportSession.Sample> samples) {
            double tick = 0, capture = 0, sampleMs = 0, queue = 0, search = 0, active = 0, doors = 0, field = 0, completed = 0;
            double tickPeak = 0;
            long fresh = 0, started = 0, budgetHold = 0, blocked = 0, failures = 0, sampleMiss = 0, rejected = 0, notTicking = 0;
            long queueMax = 0, pendingMax = 0, examinedMax = 0, paddingMax = 0;
            for (PathReportSession.Sample row : samples) {
                tick += row.tickMs();
                capture += row.captureMs();
                sampleMs += row.sampleMs();
                queue += row.queueMs();
                search += row.searchMs();
                active += row.activeMs();
                doors += row.doorsMs();
                field += row.fieldMs();
                completed += row.completedMs();
                tickPeak = Math.max(tickPeak, row.tickMs());
                fresh += row.freshSections();
                started += row.started();
                budgetHold += row.budgetHold();
                blocked += row.blocked();
                failures += row.failures();
                sampleMiss += row.sampleMiss();
                rejected += row.rejected();
                notTicking += row.notTicking();
                queueMax = Math.max(queueMax, row.queueSize());
                pendingMax = Math.max(pendingMax, row.pending());
                examinedMax = Math.max(examinedMax, row.examined());
                paddingMax = Math.max(paddingMax, row.padding());
            }
            int n = samples.size();
            return new Totals(n, tick, capture, sampleMs, queue, search, active, doors, field, completed, tickPeak,
                    fresh / (double) n, started / (double) n, fresh, started, budgetHold, blocked, failures, sampleMiss, rejected,
                    notTicking, queueMax, pendingMax, examinedMax, paddingMax);
        }
    }

    public record Written(Path csv, Path html) {
    }
}
