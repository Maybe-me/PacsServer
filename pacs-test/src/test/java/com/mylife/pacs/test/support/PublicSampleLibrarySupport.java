package com.mylife.pacs.test.support;

import org.dcm4che3.data.Attributes;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PublicSampleLibrarySupport {

    private PublicSampleLibrarySupport() {
    }

    public static Path requireDownloadedSample(String sourceId, String fileName) {
        Path samplePath = resolveRepoRoot()
                .resolve("sample-library")
                .resolve("downloads")
                .resolve(sourceId)
                .resolve(fileName);
        Assumptions.assumeTrue(
                Files.exists(samplePath),
                () -> "Required sample is missing: " + samplePath
                        + ". Download it with .\\sample-library\\download-public-samples.ps1 -SourceId "
                        + sourceId
        );
        return samplePath;
    }

    public static byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read sample file: " + path, exception);
        }
    }

    public static Attributes readDataset(Path path) {
        return TestDicomObjects.readDataset(readBytes(path));
    }

    private static Path resolveRepoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) && Files.isDirectory(current.resolve("sample-library"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Failed to resolve repository root from current directory");
    }
}
