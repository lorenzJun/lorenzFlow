package com.lorenzflow.app.util;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.lorenzflow.app.R;
import com.lorenzflow.app.data.Task;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Generates a cleanly formatted PDF of active tasks and saves it to the Documents directory.
 */
public class PdfExportUtil {

    private static final int PAGE_WIDTH = 595;   // A4 width in points
    private static final int PAGE_HEIGHT = 842;  // A4 height in points
    private static final int MARGIN = 50;
    private static final int CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;

    /**
     * Exports the given task list as a PDF file.
     *
     * @param context Application context
     * @param tasks   List of active tasks to export
     * @return true if the PDF was saved successfully
     */
    public static boolean exportTasks(Context context, List<Task> tasks) {
        PdfDocument document = new PdfDocument();
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
        SimpleDateFormat fileSdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());

        // Paints
        Paint titlePaint = new Paint();
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        titlePaint.setTextSize(22);
        titlePaint.setColor(0xFF1B1B1F);
        titlePaint.setAntiAlias(true);

        Paint subtitlePaint = new Paint();
        subtitlePaint.setTextSize(10);
        subtitlePaint.setColor(0xFF777680);
        subtitlePaint.setAntiAlias(true);

        Paint headerPaint = new Paint();
        headerPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        headerPaint.setTextSize(12);
        headerPaint.setColor(0xFF46464F);
        headerPaint.setAntiAlias(true);

        Paint taskTitlePaint = new Paint();
        taskTitlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        taskTitlePaint.setTextSize(13);
        taskTitlePaint.setColor(0xFF1B1B1F);
        taskTitlePaint.setAntiAlias(true);

        Paint bodyPaint = new Paint();
        bodyPaint.setTextSize(11);
        bodyPaint.setColor(0xFF46464F);
        bodyPaint.setAntiAlias(true);

        Paint deadlinePaint = new Paint();
        deadlinePaint.setTextSize(10);
        deadlinePaint.setColor(0xFF5B5FE3);
        deadlinePaint.setAntiAlias(true);

        Paint linePaint = new Paint();
        linePaint.setColor(0xFFC7C5D0);
        linePaint.setStrokeWidth(0.5f);

        Paint bulletPaint = new Paint();
        bulletPaint.setColor(0xFF5B5FE3);
        bulletPaint.setAntiAlias(true);

        // Create page
        int pageNum = 1;
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        float y = MARGIN;

        // Title
        canvas.drawText(context.getString(R.string.pdf_title), MARGIN, y + 22, titlePaint);
        y += 32;

        // Date
        canvas.drawText(context.getString(R.string.pdf_generated_on, sdf.format(new Date())), MARGIN, y + 10, subtitlePaint);
        y += 20;

        // Divider
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint);
        y += 20;

        // Column headers
        canvas.drawText(context.getString(R.string.pdf_col_task), MARGIN + 15, y, headerPaint);
        canvas.drawText(context.getString(R.string.pdf_col_deadline), PAGE_WIDTH - MARGIN - 120, y, headerPaint);
        y += 8;
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint);
        y += 16;

        // Task rows
        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);

            // Check if we need a new page
            if (y > PAGE_HEIGHT - MARGIN - 60) {
                document.finishPage(page);
                pageNum++;
                pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create();
                page = document.startPage(pageInfo);
                canvas = page.getCanvas();
                y = MARGIN;
            }

            // Bullet
            canvas.drawCircle(MARGIN + 5, y - 4, 3, bulletPaint);

            // Task title
            String title = task.getTitle();
            if (title.length() > 40) title = title.substring(0, 37) + "...";
            canvas.drawText(title, MARGIN + 15, y, taskTitlePaint);

            // Deadline
            if (task.getDeadline() > 0) {
                String deadline = sdf.format(new Date(task.getDeadline()));
                canvas.drawText(deadline, PAGE_WIDTH - MARGIN - 120, y, deadlinePaint);
            } else {
                canvas.drawText(context.getString(R.string.pdf_no_deadline), PAGE_WIDTH - MARGIN - 120, y, bodyPaint);
            }

            y += 6;

            // Description (if present)
            if (task.getDescription() != null && !task.getDescription().isEmpty()) {
                String desc = task.getDescription();
                if (desc.length() > 80) desc = desc.substring(0, 77) + "...";
                canvas.drawText(desc, MARGIN + 15, y + 11, bodyPaint);
                y += 16;
            }

            y += 14;

            // Light separator
            canvas.drawLine(MARGIN + 15, y - 4, PAGE_WIDTH - MARGIN, y - 4, linePaint);
            y += 6;
        }

        // Footer
        if (y > PAGE_HEIGHT - MARGIN - 30) {
            document.finishPage(page);
            pageNum++;
            pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create();
            page = document.startPage(pageInfo);
            canvas = page.getCanvas();
            y = MARGIN;
        }
        y = PAGE_HEIGHT - MARGIN;
        canvas.drawText(context.getString(R.string.pdf_total_tasks, tasks.size()), MARGIN, y, subtitlePaint);
        canvas.drawText(context.getString(R.string.pdf_footer_version), PAGE_WIDTH - MARGIN - 85, y, subtitlePaint);

        document.finishPage(page);

        // Save the file
        String fileName = "LorenzFlow_Tasks_" + fileSdf.format(new Date()) + ".pdf";
        boolean success = false;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for API 29+
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS);

                Uri uri = context.getContentResolver().insert(
                        MediaStore.Files.getContentUri("external"), values);

                if (uri != null) {
                    try (OutputStream os = context.getContentResolver().openOutputStream(uri)) {
                        document.writeTo(os);
                        success = true;
                    }
                }
            } else {
                // Legacy file access for API 26-28
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                if (!dir.exists()) dir.mkdirs();

                File file = new File(dir, fileName);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    document.writeTo(fos);
                    success = true;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            document.close();
        }

        return success;
    }
}
