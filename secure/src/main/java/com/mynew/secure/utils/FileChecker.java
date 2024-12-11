package com.mynew.secure.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class FileChecker {


    public boolean checkAreFilesContain(String directoryPath) {
        boolean containsFiles = false;
        // Specify the directory path

        // Create a File object for the directory
        File folder = new File(directoryPath);

        // Check if the folder exists and is a directory
        if (folder.exists() && folder.isDirectory()) {
            // Get the list of files in the directory
            File[] listOfFiles = folder.listFiles();

            if (listOfFiles != null && listOfFiles.length > 0) {
                containsFiles = false;

                // Iterate through the files
                for (File file : listOfFiles) {
                    // Check if the current file is a file and not a directory
                    if (file.isFile()) {
                        containsFiles = true;
                        System.out.println("File found: " + file.getName());
                    }
                }

                if (!containsFiles) {
                    System.out.println("The folder does not contain any files.");
                }
            } else {
                System.out.println("The folder is empty.");
            }
        } else {
            System.out.println("The specified path is not a folder or does not exist.");
        }
        return containsFiles;
    }

    public Bitmap getBitmapFromPath(String imagePath) {
        File imgFile = new File(imagePath);

        // Check if the file exists
        if (imgFile.exists()) {
            // Use BitmapFactory to decode the image from the provided path
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
            return bitmap;
        } else {
            // Handle the case where the file does not exist
            System.out.println("File does not exist at the specified path: " + imagePath);
            return null;
        }
    }

    public Bitmap getBitmapFromURL(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();

            InputStream input = connection.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            input.close(); // Close the stream to release resources

            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null; // Return null if there is an error
        }

    }
}

