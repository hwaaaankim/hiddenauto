package com.dev.HiddenBATHAuto.utils;

import java.io.File;

public class FileUtil {

	public static void deleteIfExists(String path) {
        if (path != null && !path.isBlank()) {
            File file = new File(path);
            if (file.exists() && file.isFile()) file.delete();
        }
    }

    public static void createDirIfNotExists(String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists()) dir.mkdirs();
    }
}
