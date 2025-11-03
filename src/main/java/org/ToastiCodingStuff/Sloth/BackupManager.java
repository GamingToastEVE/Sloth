package org.ToastiCodingStuff.Sloth;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages automated database backups.
 * Creates backups every 24 hours and maintains up to 3 most recent backups.
 */
public class BackupManager {
    private static final String BACKUP_DIR = "/backups";
    private static final int MAX_BACKUPS = 3;
    private static final long BACKUP_INTERVAL_HOURS = 24;
    
    private final ScheduledExecutorService scheduler;
    private final String dbHost;
    private final String dbPort;
    private final String dbName;
    private final String dbUser;
    private final String dbPassword;
    
    /**
     * Creates a new BackupManager instance.
     * 
     * @param dbHost Database host
     * @param dbPort Database port
     * @param dbName Database name
     * @param dbUser Database user
     * @param dbPassword Database password
     */
    public BackupManager(String dbHost, String dbPort, String dbName, String dbUser, String dbPassword) {
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.dbHost = dbHost;
        this.dbPort = dbPort;
        this.dbName = dbName;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        
        // Ensure backup directory exists
        ensureBackupDirectoryExists();
    }
    
    /**
     * Starts the backup scheduler.
     * Performs an initial backup immediately, then schedules backups every 24 hours.
     */
    public void startScheduler() {
        System.out.println("Starting backup scheduler...");
        
        // Schedule backups to run every 24 hours
        scheduler.scheduleAtFixedRate(
            this::performBackup,
            0, // Initial delay
            BACKUP_INTERVAL_HOURS,
            TimeUnit.HOURS
        );
        
        System.out.println("Backup scheduler started. Backups will run every " + BACKUP_INTERVAL_HOURS + " hours.");
    }
    
    /**
     * Stops the backup scheduler.
     */
    public void stopScheduler() {
        System.out.println("Stopping backup scheduler...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Ensures the backup directory exists, creating it if necessary.
     */
    private void ensureBackupDirectoryExists() {
        File backupDir = new File(BACKUP_DIR);
        if (!backupDir.exists()) {
            if (backupDir.mkdirs()) {
                System.out.println("Created backup directory: " + BACKUP_DIR);
            } else {
                System.err.println("Failed to create backup directory: " + BACKUP_DIR);
            }
        }
    }
    
    /**
     * Performs a database backup.
     * Creates a timestamped backup file and maintains only the 3 most recent backups.
     */
    public void performBackup() {
        try {
            System.out.println("Starting database backup...");
            
            // Generate backup filename with timestamp
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String backupFileName = String.format("%s_backup_%s.sql", dbName, timestamp);
            String backupFilePath = Paths.get(BACKUP_DIR, backupFileName).toString();
            
            // Execute mysqldump command
            ProcessBuilder processBuilder = new ProcessBuilder(
                "mysqldump",
                "-h", dbHost,
                "-P", dbPort,
                "-u", dbUser,
                "-p" + dbPassword,
                "--single-transaction",
                "--routines",
                "--triggers",
                dbName
            );
            
            // Redirect output to backup file
            processBuilder.redirectOutput(new File(backupFilePath));
            processBuilder.redirectErrorStream(true);
            
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                System.out.println("Database backup completed successfully: " + backupFilePath);
                
                // Clean up old backups
                cleanupOldBackups();
            } else {
                System.err.println("Database backup failed with exit code: " + exitCode);
            }
            
        } catch (IOException | InterruptedException e) {
            System.err.println("Error performing database backup: " + e.getMessage());
            e.printStackTrace();
            
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Removes old backups, keeping only the 3 most recent ones.
     */
    private void cleanupOldBackups() {
        try {
            File backupDir = new File(BACKUP_DIR);
            File[] backupFiles = backupDir.listFiles((dir, name) -> 
                name.startsWith(dbName + "_backup_") && name.endsWith(".sql")
            );
            
            if (backupFiles == null || backupFiles.length <= MAX_BACKUPS) {
                return;
            }
            
            // Sort by last modified date (newest first)
            Arrays.sort(backupFiles, Comparator.comparingLong(File::lastModified).reversed());
            
            // Delete backups beyond the maximum count
            for (int i = MAX_BACKUPS; i < backupFiles.length; i++) {
                if (backupFiles[i].delete()) {
                    System.out.println("Deleted old backup: " + backupFiles[i].getName());
                } else {
                    System.err.println("Failed to delete old backup: " + backupFiles[i].getName());
                }
            }
            
            System.out.println("Backup cleanup completed. Kept " + Math.min(MAX_BACKUPS, backupFiles.length) + " most recent backups.");
            
        } catch (Exception e) {
            System.err.println("Error cleaning up old backups: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
