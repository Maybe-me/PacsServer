package com.mylife.pacs.boot.tool;

import com.mylife.pacs.common.config.DicomPacsProperties;
import com.mylife.pacs.infrastructure.dimse.Dcm4cheAssociationFactory;
import com.mylife.pacs.infrastructure.dimse.PreparedDicomObject;
import com.mylife.pacs.infrastructure.netty.DicomClientBootstrap;
import com.mylife.pacs.infrastructure.netty.message.DicomMessage;
import org.dcm4che3.net.Status;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public final class LocalDicomSender {

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 11112;
    private static final String DEFAULT_CALLED_AET = "MY_PACS";
    private static final String DEFAULT_CALLING_AET = "LOCAL_SEND";

    private LocalDicomSender() {
    }

    public static void main(String[] args) throws Exception {
        Arguments arguments;
        try {
            arguments = Arguments.parse(args);
        } catch (IllegalArgumentException exception) {
            System.err.println(exception.getMessage());
            printUsage(System.err);
            System.exit(2);
            return;
        }

        if (arguments.help()) {
            printUsage(System.out);
            return;
        }

        List<Path> files = resolveFiles(arguments.path());
        if (files.isEmpty()) {
            throw new IllegalArgumentException("No files were found under: " + arguments.path());
        }

        DicomPacsProperties properties = new DicomPacsProperties();
        DicomClientBootstrap clientBootstrap = new DicomClientBootstrap(properties, new Dcm4cheAssociationFactory(properties));

        int successCount = 0;
        int skippedCount = 0;
        List<String> failures = new ArrayList<>();
        System.out.printf(
                "Sending %d file(s) to %s@%s:%d using calling AET %s%n",
                files.size(),
                arguments.calledAet(),
                arguments.host(),
                arguments.port(),
                arguments.callingAet()
        );

        for (Path file : files) {
            try {
                byte[] payload = Files.readAllBytes(file);
                PreparedDicomObject dicomObject = PreparedDicomObject.fromStoredFile(payload, null, null, null);
                validateDicomObject(file, dicomObject);
                DicomMessage response = clientBootstrap.store(
                        arguments.host(),
                        arguments.port(),
                        arguments.callingAet(),
                        arguments.calledAet(),
                        Map.of(
                                "00080016", dicomObject.sopClassUid(),
                                "00080018", dicomObject.sopInstanceUid(),
                                "00020010", dicomObject.transferSyntaxUid()
                        ),
                        payload
                );
                if (response.status() == Status.Success) {
                    successCount++;
                    System.out.printf("OK    %s  %s%n", file, dicomObject.sopInstanceUid());
                } else if (isDuplicate(response)) {
                    skippedCount++;
                    System.out.printf("SKIP  %s  %s  already exists%n", file, dicomObject.sopInstanceUid());
                } else if (isSkippableResponse(response)) {
                    skippedCount++;
                    System.out.printf("SKIP  %s  %s%n", file, response.message());
                } else {
                    String message = "FAIL  " + file + "  status=0x" + Integer.toHexString(response.status()) + "  " + response.message();
                    failures.add(message);
                    System.err.println(message);
                }
            } catch (Exception exception) {
                String rootMessage = rootMessage(exception);
                if (isSkippableException(rootMessage)) {
                    skippedCount++;
                    System.out.printf("SKIP  %s  %s%n", file, rootMessage);
                } else {
                    String message = "FAIL  " + file + "  " + rootMessage;
                    failures.add(message);
                    System.err.println(message);
                }
            }
        }

        System.out.printf(
                "Completed: %d total, %d succeeded, %d skipped, %d failed.%n",
                files.size(),
                successCount,
                skippedCount,
                failures.size()
        );
        if (!failures.isEmpty()) {
            System.exit(1);
        }
    }

    private static List<Path> resolveFiles(Path inputPath) throws java.io.IOException {
        if (!Files.exists(inputPath)) {
            throw new IllegalArgumentException("Input path does not exist: " + inputPath);
        }
        if (Files.isRegularFile(inputPath)) {
            return List.of(inputPath);
        }
        try (Stream<Path> stream = Files.walk(inputPath)) {
            return stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    private static void printUsage(PrintStream stream) {
        stream.println("Built-in DICOM sender");
        stream.println("Usage:");
        stream.println("  --path <file-or-directory>   Required. DICOM file or directory to send.");
        stream.println("  --host <hostname>            Optional. Default: 127.0.0.1");
        stream.println("  --port <port>                Optional. Default: 11112");
        stream.println("  --called-aet <aet>           Optional. Default: MY_PACS");
        stream.println("  --calling-aet <aet>          Optional. Default: LOCAL_SEND");
        stream.println("  --help                       Show this help.");
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return Objects.toString(current.getMessage(), current.getClass().getSimpleName());
    }

    private static boolean isDuplicate(DicomMessage response) {
        String message = response.attributes().getOrDefault("errorComment", response.message());
        return response.status() == Status.ProcessingFailure
                && message != null
                && message.contains("Duplicate SOP Instance UID");
    }

    private static boolean isSkippableResponse(DicomMessage response) {
        String message = response.attributes().getOrDefault("errorComment", response.message());
        return message != null && (
                message.contains("No Presentation Context for Abstract Syntax")
                        || message.contains("Stored file is not a valid DICOM object")
                        || message.contains("EOFException")
        );
    }

    private static boolean isSkippableException(String message) {
        return message != null && (
                message.contains("No Presentation Context for Abstract Syntax")
                        || message.contains("Stored file is not a valid DICOM object")
                        || message.contains("EOFException")
                        || message.contains("Missing SOP Class UID")
                        || message.contains("Missing SOP Instance UID")
        );
    }

    private static void validateDicomObject(Path file, PreparedDicomObject dicomObject) {
        if (dicomObject.sopClassUid() == null || dicomObject.sopClassUid().isBlank()) {
            throw new IllegalArgumentException("Missing SOP Class UID in " + file.getFileName());
        }
        if (dicomObject.sopInstanceUid() == null || dicomObject.sopInstanceUid().isBlank()) {
            throw new IllegalArgumentException("Missing SOP Instance UID in " + file.getFileName());
        }
        if (dicomObject.transferSyntaxUid() == null || dicomObject.transferSyntaxUid().isBlank()) {
            throw new IllegalArgumentException("Missing Transfer Syntax UID in " + file.getFileName());
        }
    }

    record Arguments(
            boolean help,
            Path path,
            String host,
            int port,
            String calledAet,
            String callingAet
    ) {
        private static Arguments parse(String[] args) {
            boolean help = false;
            Path path = null;
            String host = DEFAULT_HOST;
            int port = DEFAULT_PORT;
            String calledAet = DEFAULT_CALLED_AET;
            String callingAet = DEFAULT_CALLING_AET;

            for (int index = 0; index < args.length; index++) {
                String argument = args[index];
                if ("--help".equals(argument) || "-h".equals(argument)) {
                    help = true;
                    continue;
                }
                if (!argument.startsWith("--")) {
                    throw new IllegalArgumentException("Unsupported argument: " + argument);
                }

                String name;
                String value;
                int equalsIndex = argument.indexOf('=');
                if (equalsIndex >= 0) {
                    name = argument.substring(2, equalsIndex);
                    value = argument.substring(equalsIndex + 1);
                } else {
                    name = argument.substring(2);
                    if (index + 1 >= args.length) {
                        throw new IllegalArgumentException("Missing value for argument: " + argument);
                    }
                    value = args[++index];
                }

                switch (name) {
                    case "path" -> path = Path.of(value);
                    case "host" -> host = value;
                    case "port" -> port = Integer.parseInt(value);
                    case "called-aet" -> calledAet = validateAet(name, value);
                    case "calling-aet" -> callingAet = validateAet(name, value);
                    default -> throw new IllegalArgumentException("Unsupported argument: --" + name);
                }
            }

            if (!help && path == null) {
                throw new IllegalArgumentException("The --path argument is required.");
            }
            return new Arguments(help, path, host, port, calledAet, callingAet);
        }

        private static String validateAet(String name, String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Argument --" + name + " must not be blank.");
            }
            if (value.length() > 16) {
                throw new IllegalArgumentException("Argument --" + name + " must be 16 characters or fewer.");
            }
            return value;
        }
    }
}
