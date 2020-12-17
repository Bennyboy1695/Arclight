package io.izzel.arclight.forgeinstaller;

import io.izzel.arclight.api.Unsafe;
import io.izzel.arclight.i18n.LocalizedException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Supplier;

public class FileDownloader implements Supplier<Path> {

    private final String url;
    private final String target;

    public FileDownloader(String url, String target) {
        this.url = url;
        this.target = target;
    }

    @Override
    public Path get() {
        try {
            String hash;
            Path path = new File(target).toPath();
            if (!Files.exists(path) && path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            URL url = new URL(this.url);
            try (InputStream stream = redirect(url)) {
                Files.copy(stream, path, StandardCopyOption.REPLACE_EXISTING);
            } catch (SocketTimeoutException e) {
                throw LocalizedException.checked("downloader.timeout", e, url);
            }
            if (Files.exists(path)) {
                return path;
            } else {
                throw LocalizedException.checked("downloader.not-found", url);
            }
        } catch (AccessDeniedException e) {
            throw LocalizedException.unchecked("downloader.access-denied", e.getFile(), e);
        } catch (Exception e) {
            Unsafe.throwException(e);
            return null;
        }
    }

    InputStream redirect(URL url) throws IOException {
        return redirect(url, new HashSet<>());
    }

    private InputStream redirect(URL url, Set<String> history) throws IOException {
        if (history.contains(url.toString())) {
            StringJoiner joiner = new StringJoiner("\n        ");
            joiner.add("");
            history.forEach(joiner::add);
            throw LocalizedException.unchecked("downloader.redirect-error", joiner.toString());
        } else {
            history.add(url.toString());
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setReadTimeout(15000);
        connection.setConnectTimeout(15000);
        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            return connection.getInputStream();
        } else if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
            String location = URLDecoder.decode(connection.getHeaderField("Location"), "UTF-8");
            return redirect(new URL(url, location));
        } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND || responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
            throw LocalizedException.unchecked("downloader.not-found", url);
        } else {
            throw LocalizedException.unchecked("downloader.http-error", responseCode, url);
        }
    }
}
