package io.izzel.arclight.forgeinstaller;

import com.google.common.io.ByteStreams;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.json.JSONObject;
import org.json.XML;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

class Util {

    public static String mavenToPath(String maven) {
        String type;
        if (maven.matches(".*@\\w+$")) {
            int i = maven.lastIndexOf('@');
            type = maven.substring(i + 1);
            maven = maven.substring(0, i);
        } else {
            type = "jar";
        }
        String[] arr = maven.split(":");
        if (arr.length == 3) {
            String pkg = arr[0].replace('.', '/');
            return String.format("%s/%s/%s/%s-%s.%s", pkg, arr[1], arr[2], arr[1], arr[2], type);
        } else if (arr.length == 4) {
            String pkg = arr[0].replace('.', '/');
            return String.format("%s/%s/%s/%s-%s-%s.%s", pkg, arr[1], arr[2], arr[1], arr[2], arr[3], type);
        } else throw new RuntimeException("Wrong maven coordinate " + maven);
    }

    private static final String SHA_PAD = String.format("%040d", 0);

    public static String hash(String path) throws Exception {
        return hash(new File(path).toPath());
    }

    public static String hash(File path) throws Exception {
        return hash(path.toPath());
    }

    public static String hash(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        String hash = new BigInteger(1, digest.digest(Files.readAllBytes(path))).toString(16);
        return (SHA_PAD + hash).substring(hash.length());
    }

    public static String hash(InputStream is) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        String hash = new BigInteger(1, digest.digest(ByteStreams.toByteArray(is))).toString(16);
        return (SHA_PAD + hash).substring(hash.length());
    }

    private static String getXmlFromForge() {
        try {
            String uri = "https://files.minecraftforge.net/maven/net/minecraftforge/forge/maven-metadata.xml";

            URL url = new URL(uri);
            HttpURLConnection connection =
                    (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/xml");
            if (connection.getResponseCode() == 500) {
                return null;
            }
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();
            return content.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getForgeMavenRelease() {
        JSONObject jsonObject = XML.toJSONObject(getXmlFromForge());
        JsonObject object = new JsonParser().parse(jsonObject.toString()).getAsJsonObject();
        JsonArray array = object.getAsJsonObject("metadata").getAsJsonObject("versioning").getAsJsonObject("versions").getAsJsonArray("version");
        List<String> versions = new ArrayList<>();
        array.forEach(obj -> {
            if (obj.getAsString().startsWith("1.16.4")) {
                versions.add(obj.getAsString());
            }
        });
        return versions.get(versions.size() -1);
    }

}
