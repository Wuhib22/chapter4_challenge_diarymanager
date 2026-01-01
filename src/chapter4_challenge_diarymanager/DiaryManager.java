import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.*;
import java.util.zip.*;

public class DiaryManager {

    private static final Path ENTRIES_DIR = Paths.get("entries");
    private static final Path BACKUPS_DIR = Paths.get("backups");
    private static final Path CONFIG_FILE = Paths.get("diary_config.ser");
    private static final DateTimeFormatter FILE_FORMATTER = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss");
    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // For bonus: serialized last used keyword (simple state)
    private String lastSearchKeyword = "";

    private final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        try {
            DiaryManager app = new DiaryManager();
            app.run();
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void run() {
        ensureDirectories();
        loadState(); // Bonus: load serialized state

        while (true) {
            System.out.println("\n=== Personal Diary Manager ===");
            System.out.println("1. Write new entry");
            System.out.println("2. Read previous entries");
            System.out.println("3. Search entries");
            System.out.println("4. List all entries");
            System.out.println("5. Create backup (ZIP)");
            System.out.println("6. Exit");
            System.out.print("Choose an option (1-6): ");

            String choice = scanner.nextLine().trim();

            try {
                switch (choice) {
                    case "1" -> writeNewEntry();
                    case "2" -> readEntry();
                    case "3" -> searchEntries();
                    case "4" -> listAllEntries();
                    case "5" -> createBackup();
                    case "6" -> {
                        saveState(); // Bonus: save state before exit
                        System.out.println("Goodbye! Your diary is safe.");
                        return;
                    }
                    default -> System.out.println("Invalid option. Please try again.");
                }
            } catch (IOException e) {
                System.out.println("Error during operation: " + e.getMessage());
            }
        }
    }

    private void ensureDirectories() {
        try {
            Files.createDirectories(ENTRIES_DIR);
            Files.createDirectories(BACKUPS_DIR);
        } catch (IOException e) {
            System.err.println("Failed to create required directories: " + e.getMessage());
        }
    }

    private void writeNewEntry() throws IOException {
        System.out.println("\n--- Write New Diary Entry ---");
        System.out.println("Enter your diary entry (type END on a new line to finish):");

        StringBuilder content = new StringBuilder();
        String line;
        while (!(line = scanner.nextLine()).equals("END")) {
            content.append(line).append(System.lineSeparator());
        }

        if (content.length() == 0) {
            System.out.println("Empty entry discarded.");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        String filename = "diary_" + now.format(FILE_FORMATTER) + ".txt";
        Path filePath = ENTRIES_DIR.resolve(filename);

        try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardOpenOption.CREATE_NEW)) {
            writer.write("Entry written on: " + now.format(DISPLAY_FORMATTER));
            writer.newLine();
            writer.write("--------------------------------------------------");
            writer.newLine();
            writer.write(content.toString());
        }

        System.out.println("Entry saved as: " + filename);
    }

    private void listAllEntries() throws IOException {
        List<Path> files = getEntryFilesSorted();

        if (files.isEmpty()) {
            System.out.println("No diary entries found.");
            return;
        }

        System.out.println("\n=== All Diary Entries ===");
        for (int i = 0; i < files.size(); i++) {
            Path file = files.get(i);
            String filename = file.getFileName().toString();
            String timestampStr = filename.substring(6, 25);
            LocalDateTime dateTime = LocalDateTime.parse(timestampStr, FILE_FORMATTER);
            System.out.printf("%2d. %s  →  %s%n", (i + 1), dateTime.format(DISPLAY_FORMATTER), filename);
        }
        System.out.println();
    }

    private void readEntry() throws IOException {
        List<Path> files = getEntryFilesSorted();
        if (files.isEmpty()) {
            System.out.println("No entries to read.");
            return;
        }

        listAllEntries();

        System.out.print("Enter the number of the entry to read: ");
        String input = scanner.nextLine().trim();

        try {
            int index = Integer.parseInt(input) - 1;
            if (index < 0 || index >= files.size()) {
                System.out.println("Invalid number.");
                return;
            }

            Path selected = files.get(index);
            System.out.println("\n--- Reading: " + selected.getFileName() + " ---");
            try (Stream<String> lines = Files.lines(selected)) {
                lines.forEach(System.out::println);
            }
            System.out.println("\n--- End of Entry ---\n");

        } catch (NumberFormatException e) {
            System.out.println("Please enter a valid number.");
        }
    }

    private void searchEntries() throws IOException {
        System.out.print("Enter keyword to search for" +
                (lastSearchKeyword.isEmpty() ? "" : " (last: " + lastSearchKeyword + ")") +
                ": ");
        String keyword = scanner.nextLine().trim();
        if (keyword.isEmpty()) {
            System.out.println("Search cancelled.");
            return;
        }

        lastSearchKeyword = keyword; // For bonus state

        String lowerKeyword = keyword.toLowerCase();
        List<Path> files = getEntryFilesSorted();

        List<Path> matches = files.stream()
                .filter(file -> {
                    try {
                        return Files.lines(file)
                                .anyMatch(line -> line.toLowerCase().contains(lowerKeyword));
                    } catch (IOException e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());

        if (matches.isEmpty()) {
            System.out.println("No entries found containing: " + keyword);
            return;
        }

        System.out.println("Found " + matches.size() + " entr" + (matches.size() == 1 ? "y" : "ies") + " containing '" + keyword + "':");
        for (int i = 0; i < matches.size(); i++) {
            Path file = matches.get(i);
            String filename = file.getFileName().toString();
            LocalDateTime dateTime = extractDateTimeFromFilename(filename);
            System.out.printf("%2d. %s%n", (i + 1), dateTime.format(DISPLAY_FORMATTER));
        }

        System.out.print("\nEnter number to read one (or press Enter to skip): ");
        String input = scanner.nextLine().trim();
        if (input.isEmpty()) return;

        try {
            int index = Integer.parseInt(input) - 1;
            if (index >= 0 && index < matches.size()) {
                Path selected = matches.get(index);
                System.out.println("\n--- Match: " + selected.getFileName() + " ---");
                try (Stream<String> lines = Files.lines(selected)) {
                    lines.forEach(line -> {
                        if (line.toLowerCase().contains(lowerKeyword)) {
                            System.out.println(">>> " + line); // Highlight match
                        } else {
                            System.out.println("    " + line);
                        }
                    });
                }
                System.out.println("\n--- End ---\n");
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid number.");
        }
    }

    private void createBackup() throws IOException {
        List<Path> files = getEntryFilesSorted();
        if (files.isEmpty()) {
            System.out.println("No entries to back up.");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        String zipName = "diary_backup_" + now.format(FILE_FORMATTER) + ".zip";
        Path zipPath = BACKUPS_DIR.resolve(zipName);

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (Path file : files) {
                String entryName = file.getFileName().toString();
                ZipEntry zipEntry = new ZipEntry(entryName);
                zos.putNextEntry(zipEntry);

                Files.copy(file, zos);
                zos.closeEntry();
            }
        }

        System.out.println("Backup created successfully: " + zipPath.toAbsolutePath());
    }

    private List<Path> getEntryFilesSorted() throws IOException {
        if (!Files.exists(ENTRIES_DIR)) return Collections.emptyList();

        try (Stream<Path> stream = Files.list(ENTRIES_DIR)) {
            return stream
                    .filter(p -> p.getFileName().toString().startsWith("diary_") && p.toString().endsWith(".txt"))
                    .sorted(Comparator.comparingLong((Path p) -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis();
                        } catch (IOException e) {
                            return 0L;
                        }
                    }).reversed())
                    .collect(Collectors.toList());
        }
    }

    private LocalDateTime extractDateTimeFromFilename(String filename) {
        String timestamp = filename.substring(6, 25); // diary_YYYY_MM_DD_HH_MM_SS.txt
        return LocalDateTime.parse(timestamp, FILE_FORMATTER);
    }

    // Bonus: Simple object serialization for application state
    private void saveState() {
        try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(CONFIG_FILE))) {
            oos.writeObject(lastSearchKeyword);
        } catch (IOException e) {
            System.out.println("Could not save application state: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadState() {
        if (Files.exists(CONFIG_FILE)) {
            try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(CONFIG_FILE))) {
                lastSearchKeyword = (String) ois.readObject();
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Could not load previous state.");
            }
        }
    }
}
