package common.cn.nsimu.simukraft.path.report;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathReportSessionTest {
    @Test
    void subtractsChildTimeFromParentSelfTime() {
        PathReportSession session = new PathReportSession();
        session.time("tick", 1_000_000L);
        session.time("tick/queue", 400_000L);
        session.time("tick/queue/sample", 250_000L);
        session.gauge("state/queue", 12L);

        List<PathReportSession.Row> rows = session.rows();
        PathReportSession.Row queue = rows.stream().filter(row -> row.name().equals("tick/queue")).findFirst().orElseThrow();
        PathReportSession.Row sample = rows.stream().filter(row -> row.name().equals("tick/queue/sample")).findFirst().orElseThrow();
        assertEquals(150_000L, queue.selfNanos());
        assertEquals(250_000L, sample.selfNanos());
        assertEquals(2, sample.depth());
        assertTrue(sample.percent() > 20.0D);
        assertEquals(1L, session.ticks());
    }

    @Test
    void writesHtmlWithoutTreatingCssPercentsAsFormat(@TempDir Path directory) throws Exception {
        PathReportSession session = new PathReportSession();
        session.time("tick", 1_000_000L);
        PathReportWriter.Written written = PathReportWriter.write(directory, "sample", session);
        String html = Files.readString(written.html());
        assertTrue(html.contains("width: 100%"));
        assertTrue(html.contains("sample"));
        assertTrue(html.contains("近30秒"));
        assertTrue(html.contains("50 ms"));
    }

    @Test
    void snapshotKeepsCountsRecordedBeforeTick() {
        PathReportSession session = new PathReportSession();
        session.time("tick/queue/sample/capture", 2_000_000L);
        session.time("tick/queue/sample", 3_000_000L);
        session.count("flow/started", 4);
        session.count("flow/freshSections", 8);
        session.gauge("state/queue", 9);
        session.count("flow/notTicking", 3);
        session.label("dimension", "minecraft:overworld");
        session.time("tick", 10_000_000L);

        PathReportSession.Sample sample = session.samples().get(0);
        assertEquals(10.0D, sample.tickMs(), 0.001D);
        assertEquals(2.0D, sample.captureMs(), 0.001D);
        assertEquals(3.0D, sample.sampleMs(), 0.001D);
        assertEquals(4L, sample.started());
        assertEquals(8L, sample.freshSections());
        assertEquals(9L, sample.queueSize());
        assertEquals(3L, sample.notTicking());
        assertEquals("minecraft:overworld", sample.dimension());
        assertTrue(session.rows().stream().anyMatch(row -> row.name().equals("flow/started")));
        assertEquals(1_000_000L, session.rows().stream()
                .filter(row -> row.name().equals("tick/queue/sample")).findFirst().orElseThrow().selfNanos());
    }

    @Test
    void reportNamesTheBottleneckForTheSelectedWindow(@TempDir Path directory) throws Exception {
        PathReportSession.Sample early = point(0L, 1, 0, 100, 0, 10, 0, 0, 0, 0, 0, 0);
        PathReportSession.Sample late = point(60_000L, 8, 5, 0, 0, 0, 8, 0, 0, 0, 0, 0);
        List<PathReportSession.Sample> both = List.of(early, late);

        String recent = PathReportWriter.diagnose(PathReportWriter.window(both, 30_000L), 8, 16, 24);
        String full = PathReportWriter.diagnose(both, 8, 16, 24);
        assertTrue(recent.contains("主线程卡在抄方块"));
        assertFalse(recent.contains("人不走是在等 A*"));
        assertTrue(full.contains("主线程卡在抄方块"));
        assertTrue(full.contains("人不走是在等 A*"));

        String budget = PathReportWriter.diagnose(List.of(point(0L, 1, 0, 0, 80, 0, 0, 0, 0, 2, 40, 0)), 8, 16, 24);
        assertTrue(budget.contains("放行预算"));
        assertTrue(budget.contains("New-path budget"));

        PathReportSession session = new PathReportSession();
        session.time("tick/queue/sample/capture", 5_000_000L);
        session.time("tick/queue/sample", 5_000_000L);
        session.count("flow/freshSections", 8);
        session.gauge("state/freshBudget", 8);
        session.time("tick", 8_000_000L);
        PathReportWriter.Written written = PathReportWriter.write(directory, "capture", session);
        String html = Files.readString(written.html());
        String csv = Files.readString(written.csv());
        assertTrue(html.contains("主线程卡在抄方块"));
        assertTrue(html.contains("Block capture"));
        assertTrue(html.contains("id=\"timeChart\""));
        assertTrue(html.contains("id=\"flowChart\""));
        assertTrue(html.contains("\"30s\""));
        assertTrue(csv.contains("all,capture_ms"));
        assertTrue(csv.contains("抄方块"));
        assertTrue(csv.contains("Block capture"));
        assertTrue(csv.contains("offset_ms,dimension,tick_ms,capture_ms"));
    }

    @Test
    void keepsDimensionsApartAndNamesOutsideSimulation(@TempDir Path directory) throws Exception {
        PathReportSession.Sample live = named(60_000L, "minecraft:overworld", 8, 5, 0, 2000, 8, 0, 24, 0);
        PathReportSession.Sample empty = named(60_000L, "minecraft:the_nether", 0.01, 0, 0, 0, 0, 0, 0, 0);
        String overworld = PathReportWriter.diagnose(List.of(live), 8, 16, 24);
        String nether = PathReportWriter.diagnose(List.of(empty), 8, 16, 24);
        assertTrue(overworld.contains("主线程卡在抄方块"));
        assertFalse(nether.contains("主线程卡在抄方块"));

        PathReportSession.Sample outside = named(0L, "minecraft:overworld", 1, 0, 0, 400, 0, 30, 0, 0);
        String standing = PathReportWriter.diagnose(List.of(outside), 8, 16, 24);
        assertTrue(standing.contains("模拟距离外"));
        assertTrue(standing.contains("Outside simulation"));

        PathReportSession session = new PathReportSession();
        session.gauge("state/queue", 10);
        session.label("dimension", "minecraft:the_nether");
        session.time("tick", 1_000_000L);
        session.gauge("state/queue", 900);
        session.label("dimension", "minecraft:overworld");
        session.count("flow/notTicking", 6);
        session.time("tick", 2_000_000L);
        String html = Files.readString(PathReportWriter.write(directory, "dims", session).html());
        assertTrue(html.contains("const dimensions=[\"minecraft:overworld\",\"minecraft:the_nether\"]"));
        assertTrue(html.contains("id=\"dimensions\""));
        assertTrue(html.contains("模拟距离外"));
    }

    private static PathReportSession.Sample point(long offsetMs, double tick, double capture, double search,
                                                  long queueSize, long pending, long fresh, long examined,
                                                  long failures, long started, long budgetHold, long blocked) {
        return new PathReportSession.Sample(offsetMs, tick, capture, capture, 0, search,
                0, 0, 0, 0,
                queueSize, 0, pending, 0,
                fresh, 0, examined, 0, failures,
                started, 0, budgetHold, blocked, 0, 0, 0, "");
    }

    private static PathReportSession.Sample named(long offsetMs, String dimension, double tick, double capture, double search,
                                                  long queueSize, long fresh, long notTicking, long sampleMiss, long started) {
        return new PathReportSession.Sample(offsetMs, tick, capture, capture, 0, search,
                0, 0, 0, 0,
                queueSize, 0, 0, 0,
                fresh, 0, 0, 0, 0,
                started, sampleMiss, 0, 0, 0, 0, notTicking, dimension);
    }
}
