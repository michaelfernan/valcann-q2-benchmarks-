
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * Q2 — Automação de Backups (Java)
 *
 * Lista arquivos da origem (name,size,created_at_utc,modified_at_utc) → backupsFrom.log (CSV),
 * remove arquivos com criação > N dias,
 * copia arquivos com criação ≤ N dias para o destino → backupsTo.log (CSV).
 *
 * Uso:
 *   java BackupJob --from /home/valcann/backupsFrom --to /home/valcann/backupsTo --log-dir /home/valcann --days 3
 *   (opcional) --dry-run
 */
public class BackupJob {
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_INSTANT;

    private static class Args {
        Path from;
        Path to;
        Path logDir;
        int days = 3;
        boolean dryRun = false;
    }

    public static void main(String[] args) {
        try {
            Args a = parseArgs(args);
            validateArgs(a);

            Path fromLog = a.logDir.resolve("backupsFrom.log");
            Path toLog   = a.logDir.resolve("backupsTo.log");
            Files.createDirectories(a.logDir);
            Files.createDirectories(a.to);

            // 1) Listagem e gravação do backupsFrom.log
            listFilesCsv(a.from, fromLog);

            // 2) Remoção de arquivos com criação > N dias
            removeOldFiles(a.from, a.days, a.dryRun);

            // 3) Copiar arquivos com criação ≤ N dias e gerar backupsTo.log
            copyRecentFiles(a.from, a.to, a.days, toLog, a.dryRun);

            System.out.println("[OK] Processo concluído.");
            System.out.println(" - Listagem: " + fromLog);
            System.out.println(" - Log de cópias: " + toLog);
            if (a.dryRun) System.out.println(" (ATENÇÃO) Modo --dry-run: nenhuma remoção/cópia foi efetuada.");
        } catch (IllegalArgumentException e) {
            System.err.println("[ERRO] " + e.getMessage());
            System.exit(2);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static Args parseArgs(String[] argv) {
        Args a = new Args();
        for (int i = 0; i < argv.length; i++) {
            switch (argv[i]) {
                case "--from":
                    a.from = Paths.get(requireNext(argv, ++i, "--from requer um caminho"));
                    break;
                case "--to":
                    a.to = Paths.get(requireNext(argv, ++i, "--to requer um caminho"));
                    break;
                case "--log-dir":
                    a.logDir = Paths.get(requireNext(argv, ++i, "--log-dir requer um caminho"));
                    break;
                case "--days":
                    a.days = Integer.parseInt(requireNext(argv, ++i, "--days requer um inteiro"));
                    break;
                case "--dry-run":
                    a.dryRun = true;
                    break;
                case "--help":
                case "-h":
                    printHelpAndExit();
                default:
                    if (argv[i].startsWith("--"))
                        throw new IllegalArgumentException("Argumento desconhecido: " + argv[i]);
            }
        }
        return a;
    }

    private static String requireNext(String[] argv, int idx, String err) {
        if (idx >= argv.length) throw new IllegalArgumentException(err);
        return argv[idx];
    }

    private static void printHelpAndExit() {
        System.out.println("Uso: java BackupJob --from <origem> --to <destino> --log-dir <dir logs> [--days N] [--dry-run]");
        System.exit(0);
    }

    private static void validateArgs(Args a) {
        if (a.from == null || a.to == null || a.logDir == null)
            throw new IllegalArgumentException("Parâmetros obrigatórios: --from, --to, --log-dir");
        if (!Files.isDirectory(a.from))
            throw new IllegalArgumentException("Origem não existe ou não é diretório: " + a.from);
        if (a.days < 0)
            throw new IllegalArgumentException("--days deve ser >= 0");
    }

    private static void listFilesCsv(Path src, Path csvPath) throws IOException {
        try (PrintWriter out = csvWriter(csvPath)) {
            out.println("name,size_bytes,created_at_utc,modified_at_utc");
            try (Stream<Path> st = Files.list(src)) {
                st.filter(Files::isRegularFile)
                  .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                  .forEach(p -> {
                      try {
                          BasicFileAttributes at = Files.readAttributes(p, BasicFileAttributes.class);
                          long size = at.size();
                          Instant created  = bestEffortCreation(at, p);
                          Instant modified = at.lastModifiedTime().toInstant();
                          out.println(csvEscape(p.getFileName().toString()) + "," + size + "," + iso(created) + "," + iso(modified));
                      } catch (IOException e) {
                          out.println(csvEscape(p.getFileName().toString()) + ",,,," );
                      }
                  });
            }
        }
    }

    private static void removeOldFiles(Path src, int days, boolean dryRun) throws IOException {
        long now = System.currentTimeMillis();
        long cutoffMs = now - daysToMs(days);
        try (Stream<Path> st = Files.list(src)) {
            for (Path p : (Iterable<Path>) st.filter(Files::isRegularFile)::iterator) {
                BasicFileAttributes at = Files.readAttributes(p, BasicFileAttributes.class);
                Instant created = bestEffortCreation(at, p);
                if (created.toEpochMilli() < cutoffMs) {
                    if (dryRun) {
                        System.out.println("[dry-run] removeria: " + p);
                    } else {
                        Files.deleteIfExists(p);
                        System.out.println("removido: " + p);
                    }
                }
            }
        }
    }

    private static void copyRecentFiles(Path src, Path dst, int days, Path csvPath, boolean dryRun) throws IOException {
        long now = System.currentTimeMillis();
        long cutoffMs = now - daysToMs(days);
        try (PrintWriter out = csvWriter(csvPath)) {
            out.println("name,size_bytes,created_at_utc,modified_at_utc,action,status");
            try (Stream<Path> st = Files.list(src)) {
                for (Path p : (Iterable<Path>) st.filter(Files::isRegularFile).sorted(Comparator.comparing(q -> q.getFileName().toString()))::iterator) {
                    try {
                        BasicFileAttributes at = Files.readAttributes(p, BasicFileAttributes.class);
                        long size = at.size();
                        Instant created  = bestEffortCreation(at, p);
                        Instant modified = at.lastModifiedTime().toInstant();

                        String action = (created.toEpochMilli() >= cutoffMs) ? "copy" : "skip";
                        String status = "skipped";
                        if ("copy".equals(action)) {
                            Path target = dst.resolve(p.getFileName().toString());
                            if (dryRun) {
                                status = "copied(dry-run)";
                                System.out.println("[dry-run] copiaria: " + p + " -> " + target);
                            } else {
                                Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                                status = "copied";
                            }
                        }
                        out.println(csvEscape(p.getFileName().toString()) + "," + size + "," + iso(created) + "," + iso(modified) + "," + action + "," + status);
                    } catch (Exception e) {
                        out.println(csvEscape(p.getFileName().toString()) + ",,,,error," + csvEscape(e.toString()));
                    }
                }
            }
        }
    }

    private static long daysToMs(int days) {
        return (long) days * 24L * 60L * 60L * 1000L;
    }

    private static Instant bestEffortCreation(BasicFileAttributes at, Path p) throws IOException {
        // Tenta creationTime(); se não existir ou for zero/negativo, cai para lastModifiedTime()
        Instant creation = at.creationTime().toInstant();
        if (creation.toEpochMilli() <= 0) {
            creation = at.lastModifiedTime().toInstant();
        }
        return creation;
    }

    private static PrintWriter csvWriter(Path path) throws IOException {
        Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp.toFile()), StandardCharsets.UTF_8));
        return new PrintWriter(bw) {
            @Override public void close() {
                super.close();
                try {
                    Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException e) {
                    throw new RuntimeException("Falha ao mover log temporário para o destino final: " + e);
                }
            }
        };
    }

    private static String iso(Instant i) {
        return ISO_FMT.format(i.atOffset(ZoneOffset.UTC));
    }

    private static String csvEscape(String s) {
        if (s == null) return "";
        boolean mustQuote = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        String t = s.replace("\"", "\"\"");
        return mustQuote ? "\"" + t + "\"" : t;
    }
}
