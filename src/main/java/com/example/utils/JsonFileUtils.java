package com.example.utils;

import com.example.exception.BusinessException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public final class JsonFileUtils {
  private static final String tmpDirName = "ship_data";

  private JsonFileUtils() {}

  public static File createTempJsonFile() throws BusinessException {
    try {
      Path tmpPath = Paths.get(
          org.apache.commons.io.FileUtils.getTempDirectory().getAbsolutePath(), tmpDirName);
      File tmpDir = Files.createDirectories(tmpPath).toFile();
      Path tmpJsonFilePath =
          Paths.get(tmpDir.getAbsolutePath(), UUID.randomUUID() + "_ship_data" + ".json");
      var file = Files.createFile(tmpJsonFilePath).toFile();
      file.setReadable(true);
      file.setWritable(true);
      return file;
    } catch (IOException e) {
      throw new BusinessException(e);
    }
  }
}
