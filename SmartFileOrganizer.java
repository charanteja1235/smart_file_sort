import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class SmartFileOrganizer {

    public enum SortMode { TYPE, EXTENSION, DATE, CUSTOM }

    private final Path sourceDirectory;
    private final SortMode sortMode;
    private final Map<String, String> customRules;
    private final List<String> logHistory = new ArrayList<>();
    private final Path logFilePath;

    public SmartFileOrganizer(String directoryPath, SortMode mode, Map<String, String> rules) {
        this.sourceDirectory = Paths.get(directoryPath);
        this.sortMode = mode;
        this.customRules = rules;
        this.logFilePath = sourceDirectory.resolve("organizer_log.txt");
    }

    public void organize(boolean previewOnly) throws IOException {
        if (!Files.isDirectory(sourceDirectory)) {
            throw new IllegalArgumentException("Invalid directory: " + sourceDirectory);
        }

        System.out.println(previewOnly ? "\n🔍 PREVIEW MODE" : "\n🚀 ORGANIZING FILES...");

        Files.walk(sourceDirectory)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        if (path.equals(logFilePath)) return; // Skip log file
                        moveFile(path, previewOnly);
                    } catch (IOException e) {
                        System.err.println("Error moving " + path + ": " + e.getMessage());
                    }
                });

        if (!previewOnly) {
            Files.write(logFilePath, logHistory);
            System.out.println("\n✅ Organization complete! Log saved at: " + logFilePath);
        }
    }

    private void moveFile(Path filePath, boolean previewOnly) throws IOException {
        String folderName = determineFolder(filePath);
        Path targetDir = sourceDirectory.resolve(folderName);

        if (!Files.exists(targetDir) && !previewOnly) {
            Files.createDirectories(targetDir);
        }

        Path targetPath = targetDir.resolve(filePath.getFileName());

        if (previewOnly) {
            System.out.println("[PREVIEW] " + filePath.getFileName() + " → " + folderName);
        } else {
            Files.move(filePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            String logEntry = filePath + " → " + targetPath;
            logHistory.add(logEntry);
            System.out.println("Moved: " + filePath.getFileName() + " → " + folderName);
        }
    }

    private String determineFolder(Path filePath) throws IOException {
        switch (sortMode) {
            case TYPE -> {
                String type = Files.probeContentType(filePath);
                return (type != null) ? type.split("/")[0].toUpperCase() : "UNKNOWN";
            }
            case EXTENSION -> {
                return getFileExtension(filePath);
            }
            case DATE -> {
                return getFileDate(filePath);
            }
            case CUSTOM -> {
                String ext = getFileExtension(filePath).toLowerCase();
                return customRules.getOrDefault(ext, "OTHER");
            }
            default -> {
                return "UNCATEGORIZED";
            }
        }
    }

    private String getFileExtension(Path filePath) {
        String name = filePath.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(dot + 1).toUpperCase() : "NO_EXTENSION";
    }

    private String getFileDate(Path filePath) throws IOException {
        File file = filePath.toFile();
        long modifiedTime = file.lastModified();
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date(modifiedTime));
    }

    public void undoLastOrganization() throws IOException {
        if (!Files.exists(logFilePath)) {
            System.out.println("⚠️ No log file found. Nothing to undo.");
            return;
        }

        List<String> logLines = Files.readAllLines(logFilePath);
        Collections.reverse(logLines);

        for (String entry : logLines) {
            String[] parts = entry.split(" → ");
            if (parts.length == 2) {
                Path original = Paths.get(parts[0]);
                Path moved = Paths.get(parts[1]);
                if (Files.exists(moved)) {
                    Files.move(moved, original, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("Restored: " + moved.getFileName());
                }
            }
        }

        Files.deleteIfExists(logFilePath);
        System.out.println("\n♻️ Undo complete. Files restored to original locations.");
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("📁 Enter the folder path to organize:");
        String path = scanner.nextLine();

        System.out.println("""
                Choose sorting mode:
                1. TYPE
                2. EXTENSION
                3. DATE
                4. CUSTOM RULES
                """);

        int choice = scanner.nextInt();
        scanner.nextLine(); // consume newline

        SortMode mode = switch (choice) {
            case 1 -> SortMode.TYPE;
            case 2 -> SortMode.EXTENSION;
            case 3 -> SortMode.DATE;
            case 4 -> SortMode.CUSTOM;
            default -> SortMode.EXTENSION;
        };

        Map<String, String> rules = new HashMap<>();
        if (mode == SortMode.CUSTOM) {
            System.out.println("Enter custom rules (e.g. pdf=Documents, jpg=Images). Type 'done' when finished:");
            while (true) {
                System.out.print("> ");
                String line = scanner.nextLine();
                if (line.equalsIgnoreCase("done")) break;
                String[] parts = line.split("=");
                if (parts.length == 2) rules.put(parts[0].trim().toLowerCase(), parts[1].trim());
            }
        }

        SmartFileOrganizer organizer = new SmartFileOrganizer(path, mode, rules);

        System.out.println("Run preview before organizing? (y/n): ");
        boolean preview = scanner.nextLine().trim().equalsIgnoreCase("y");

        try {
            organizer.organize(preview);

            System.out.println("\nDo you want to undo last operation? (y/n): ");
            if (scanner.nextLine().trim().equalsIgnoreCase("y")) {
                organizer.undoLastOrganization();
            }

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
        }
    }
}
