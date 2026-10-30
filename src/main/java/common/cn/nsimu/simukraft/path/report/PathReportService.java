package common.cn.nsimu.simukraft.path.report;

import common.cn.nsimu.simukraft.path.PathReportSink;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 开关寻路报告，并把结果写到存档目录。
 */
public final class PathReportService {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static PathReportSession session;

    private PathReportService() {
    }

    public static boolean running() {
        return session != null;
    }

    public static void start() {
        session = new PathReportSession();
        PathReportSink.setListener(session);
    }

    /**
     * stop：卸下监听并写出 CSV 与 HTML。没开过报告时返回 null。
     */
    public static PathReportWriter.Written stop(MinecraftServer server) throws IOException {
        PathReportSession current = session;
        if (current == null || server == null) {
            return null;
        }
        Path directory = server.getWorldPath(LevelResource.ROOT).resolve("simukraft").resolve("path-reports");
        PathReportWriter.Written written = PathReportWriter.write(directory, LocalDateTime.now().format(STAMP), current);
        session = null;
        PathReportSink.setListener(null);
        return written;
    }
}
