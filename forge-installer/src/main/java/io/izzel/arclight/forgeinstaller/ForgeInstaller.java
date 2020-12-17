package io.izzel.arclight.forgeinstaller;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.izzel.arclight.api.Unsafe;
import io.izzel.arclight.i18n.ArclightLocale;
import io.izzel.arclight.i18n.LocalizedException;
import org.json.JSONObject;
import org.json.XML;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class ForgeInstaller {

    private static final String FORGE_INSTALLER_URL = "https://files.minecraftforge.net/maven/net/minecraftforge/forge/%s/forge-%s-installer.jar";
    private static final String FORGE_RELEASE_VERSION = Util.getForgeMavenRelease();

    public static void install() throws Throwable {
        Path path = Paths.get(String.format("forge-%s.jar", FORGE_RELEASE_VERSION));
        if (!Files.exists(path)) {
            System.out.println("Forge not found, please install version: " + FORGE_RELEASE_VERSION);
            return;
        } else {
            classpath(path);
        }
/*        Path path = Paths.get(String.format("forge-%s-installer.jar", FORGE_RELEASE_VERSION));
        if (!Files.exists(path)) {
            ArclightLocale.info("downloader.info2");
            ExecutorService pool = Executors.newFixedThreadPool(8);
            if (!Files.exists(path)) {
                CompletableFuture<?>[] futures = installForge(pool);
                handleFutures(futures);
                ArclightLocale.info("downloader.forge-install");
                ProcessBuilder builder = new ProcessBuilder();
                builder.command("java", "-Djava.net.useSystemProxies=true", "-jar", String.format("forge-%s-installer.jar", FORGE_RELEASE_VERSION), "--installServer", ".");
                builder.inheritIO();
                Process process = builder.start();
                process.waitFor();
            }
            pool.shutdownNow();
        }*/
        /*classpath(path);*/
    }

    private static Function<Supplier<Path>, CompletableFuture<Path>> reportSupply(ExecutorService service) {
        return it -> CompletableFuture.supplyAsync(it, service).thenApply(path -> {
            ArclightLocale.info("downloader.complete", path);
            return path;
        });
    }

    private static CompletableFuture<?>[] installForge(ExecutorService pool) throws Exception {
        String url = String.format(FORGE_INSTALLER_URL, FORGE_RELEASE_VERSION, FORGE_RELEASE_VERSION);
        String dist = String.format("forge-%s-installer.jar", FORGE_RELEASE_VERSION);
        FileDownloader fd = new FileDownloader(url, dist);
        CompletableFuture<?> installerFuture = reportSupply(pool).apply(fd).thenAccept(path -> {
            try {
                FileSystem system = FileSystems.newFileSystem(path ,null);
                Map<String, Map.Entry<String, String>> map = new HashMap<>();
                Path profile = system.getPath("install_profile.json");
                map.putAll(profileLibraries(profile));
                Path version = system.getPath("version.json");
                map.putAll(profileLibraries(version));
                map.forEach((s, stringStringEntry) -> System.out.println("S: " + s + " SS: " + stringStringEntry));
                /*List<Supplier<Path>> suppliers = checkMaven(map);*/
/*                CompletableFuture<?>[] array = suppliers.stream().map(reportSupply(pool)).toArray(CompletableFuture[]::new);
                handleFutures(array);*/
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
/*        CompletableFuture<?> serverFuture = reportSupply(pool).apply(
                new FileDownloader(String.format(SERVER_URL, "1.16.4"),
                        String.format("minecraft_server.%s.jar", info.installer.minecraft), VERSION_HASH.get(info.installer.minecraft))
        );*/
        return new CompletableFuture<?>[]{installerFuture};
    }

    private static void handleFutures(CompletableFuture<?>... futures) {
        for (CompletableFuture<?> future : futures) {
            try {
                future.join();
            } catch (CompletionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof LocalizedException) {
                    LocalizedException local = (LocalizedException) cause;
                    ArclightLocale.error(local.node(), local.args());
                } else throw e;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static Map<String, Map.Entry<String, String>> profileLibraries(Path path) throws IOException {
        Map<String, Map.Entry<String, String>> ret = new HashMap<>();
        JsonArray array = new JsonParser().parse(Files.newBufferedReader(path)).getAsJsonObject().getAsJsonArray("libraries");
        for (JsonElement element : array) {
            String name = element.getAsJsonObject().get("name").getAsString();
            JsonObject artifact = element.getAsJsonObject().getAsJsonObject("downloads").getAsJsonObject("artifact");
            String hash = artifact.get("sha1").getAsString();
            String url = artifact.get("url").getAsString();
            if (url == null || url.trim().isEmpty()) continue;
            ret.put(name, new AbstractMap.SimpleImmutableEntry<>(hash, url));
        }
        return ret;
    }

    private static List<Supplier<Path>> checkMavenNoSource(Map<String, String> map) {
        LinkedHashMap<String, Map.Entry<String, String>> hashMap = new LinkedHashMap<>(map.size());
        for (Map.Entry<String, String> entry : map.entrySet()) {
            hashMap.put(entry.getKey(), new AbstractMap.SimpleImmutableEntry<>(entry.getValue(), null));
        }
        return checkMaven(hashMap);
    }

    private static List<Supplier<Path>> checkMaven(Map<String, Map.Entry<String, String>> map) {
        List<Supplier<Path>> incomplete = new ArrayList<>();
        for (Map.Entry<String, Map.Entry<String, String>> entry : map.entrySet()) {
            String maven = entry.getKey();
            String hash = entry.getValue().getKey();
            String url = entry.getValue().getValue();
            String path = "libraries/" + Util.mavenToPath(maven);
/*            if (new File(path).exists()) {
                try {
                    String fileHash = Util.hash(path);
                    if (!fileHash.equals(hash)) {
                        incomplete.add(new MavenDownloader(MAVEN_REPO, maven, path, hash, url));
                    }
                } catch (Exception e) {
                    incomplete.add(new MavenDownloader(MAVEN_REPO, maven, path, hash, url));
                }
            } else {
                incomplete.add(new MavenDownloader(MAVEN_REPO, maven, path, hash, url));
            }*/
        }
        return incomplete;
    }

    private static void classpath(Path path) throws Throwable {
        JarFile jarFile = new JarFile(path.toFile());
        Manifest manifest = jarFile.getManifest();
        String[] split = manifest.getMainAttributes().getValue("Class-Path").split(" ");
        for (String s : split) {
            addToPath(Paths.get(s));
        }
        List<String> libs = Arrays.asList("org.ow2.asm:asm-util:8.0.1","org.ow2.asm:asm-analysis:8.0.1","org.yaml:snakeyaml:1.26","org.xerial:sqlite-jdbc:3.32.3","mysql:mysql-connector-java:5.1.49","commons-lang:commons-lang:2.6","com.googlecode.json-simple:json-simple:1.1.1","org.apache.logging.log4j:log4j-jul:2.11.2","net.md-5:SpecialSource:1.8.6","org.jline:jline-terminal-jansi:3.12.1","org.fusesource.jansi:jansi:1.18","org.jline:jline-terminal:3.12.1","org.jline:jline-reader:3.12.1","jline:jline:2.12.1");
        for (String library : libs) {
            addToPath(Paths.get("libraries", Util.mavenToPath(library)));
        }
        addToPath(path);
    }

    private static void addToPath(Path path) throws Throwable {
        ClassLoader loader = ForgeInstaller.class.getClassLoader();
        Field ucpField = loader.getClass().getDeclaredField("ucp");
        long offset = Unsafe.objectFieldOffset(ucpField);
        Object ucp = Unsafe.getObject(loader, offset);
        Method method = ucp.getClass().getDeclaredMethod("addURL", URL.class);
        Unsafe.lookup().unreflect(method).invoke(ucp, path.toUri().toURL());
    }
}
