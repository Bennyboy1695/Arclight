package io.izzel.arclight.forgeinstaller;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class InstallInfo {

    public Installer installer;
    public Map<String, String> libraries;

    public InstallInfo() {
        libraries = new HashMap<>();
    }

    public static class Installer {

        public String minecraft;
        public String forge;
        public String hash;
    }
}
