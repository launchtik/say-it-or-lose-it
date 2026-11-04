package dev.sayit.client.speech;

import dev.sayit.SayItMod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.function.IntConsumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads a Vosk model zip from the official host (alphacephei.com) the first time a language is needed,
 * so players don't have to install models by hand. Disable with "autoDownloadModels": false.
 */
final class ModelDownloader {
    private ModelDownloader() {}

    static final String BASE_URL = "https://alphacephei.com/vosk/models/";

    /** Downloads and unpacks {@code modelName} into {@code modelsDir}. Reports 0-100 progress. */
    static Path download(String modelName, Path modelsDir, IntConsumer progress) throws SpeechUnavailableException {
        if (!modelName.matches("[A-Za-z0-9._-]+")) throw new SpeechUnavailableException("Bad model name " + modelName);
        Path target = modelsDir.resolve(modelName);
        Path zip = modelsDir.resolve(modelName + ".zip.part");
        Path tmp = modelsDir.resolve(".unpack-" + modelName);
        try {
            Files.createDirectories(modelsDir);
            SayItMod.LOG.info("Downloading Vosk model {}{}.zip", BASE_URL, modelName);
            HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(20)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(BASE_URL + modelName + ".zip"))
                    .header("User-Agent", "SayIt-Minecraft-Mod").GET().build();
            HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode());
            long total = res.headers().firstValueAsLong("Content-Length").orElse(-1);
            try (InputStream in = res.body(); OutputStream out = Files.newOutputStream(zip)) {
                byte[] buf = new byte[1 << 16];
                long done = 0;
                int last = -1;
                for (int n; (n = in.read(buf)) > 0; ) {
                    out.write(buf, 0, n);
                    done += n;
                    int pct = total > 0 ? (int) (done * 90 / total) : 0; // 90% download, 10% unpack
                    if (pct != last) { progress.accept(pct); last = pct; }
                }
            }
            deleteTree(tmp);
            unzip(zip, tmp);
            Path root = findModelRoot(tmp);
            if (root == null) throw new IOException("zip does not contain a Vosk model");
            deleteTree(target);
            Files.move(root, target, StandardCopyOption.ATOMIC_MOVE);
            progress.accept(100);
            SayItMod.LOG.info("Installed Vosk model {}", target);
            return target;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SpeechUnavailableException("Model download interrupted");
        } catch (IOException e) {
            throw new SpeechUnavailableException("Could not download the speech model (" + e.getMessage()
                    + "). Install " + modelName + " by hand - see README", e);
        } finally {
            try { Files.deleteIfExists(zip); } catch (IOException ignored) {}
            try { deleteTree(tmp); } catch (IOException ignored) {}
        }
    }

    private static void unzip(Path zip, Path dest) throws IOException {
        Path base = dest.toAbsolutePath().normalize();
        Files.createDirectories(base);
        try (ZipInputStream z = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) {
                Path p = base.resolve(e.getName()).normalize();
                if (!p.startsWith(base)) throw new IOException("unsafe path in zip: " + e.getName()); // zip-slip
                if (e.isDirectory()) Files.createDirectories(p);
                else {
                    Files.createDirectories(p.getParent());
                    Files.copy(z, p, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static Path findModelRoot(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir, 3)) {
            return s.filter(Files::isDirectory)
                    .filter(p -> Files.isDirectory(p.resolve("am")) || Files.exists(p.resolve("final.mdl")))
                    .min(Comparator.comparingInt(Path::getNameCount)).orElse(null);
        }
    }

    private static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (Stream<Path> s = Files.walk(p)) {
            for (Path x : s.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(x);
        }
    }
}
