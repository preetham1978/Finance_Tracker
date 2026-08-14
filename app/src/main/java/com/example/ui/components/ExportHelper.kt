package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color as GColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.Transaction
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object ExportHelper {

    fun generateCsv(context: Context, transactions: List<Transaction>): Uri? {
        return try {
            val cacheDir = context.cacheDir
            val csvFile = File(cacheDir, "Vantage_Ledger_${System.currentTimeMillis()}.csv")
            val outputStream = FileOutputStream(csvFile)
            
            val writer = outputStream.bufferedWriter()
            // Write BOM for Excel UTF-8 support
            writer.write("\uFEFF")
            writer.write("ID,Date,Title,Amount,Currency,Category,Type,Payment Method,Bank Card,Notes,Recurring\n")
            
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            for (t in transactions) {
                val dateStr = sdf.format(Date(t.timestamp))
                val row = listOf(
                    t.id.toString(),
                    dateStr,
                    t.title.replace(",", " "),
                    t.amount.toString(),
                    t.currency,
                    t.category,
                    t.type,
                    t.paymentMethod,
                    t.creditCardBank?.replace(",", " ") ?: "N/A",
                    t.notes.replace(",", " ").replace("\n", " "),
                    if (t.isRecurring) "Monthly" else "No"
                ).joinToString(",")
                writer.write(row + "\n")
            }
            writer.flush()
            writer.close()
            outputStream.close()
            
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", csvFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun generatePdf(context: Context, transactions: List<Transaction>): Uri? {
        return try {
            val pdfDocument = PdfDocument()
            // standard A4 size is 595 x 842 points (72 points per inch)
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas
            
            val paint = Paint()
            val textPaint = Paint().apply {
                isAntiAlias = true
                textSize = 12f
                color = GColor.BLACK
            }
            
            // Draw header background
            paint.color = GColor.parseColor("#1A237E") // Deep Indigo Primary
            canvas.drawRect(0f, 0f, 595f, 90f, paint)
            
            // Header text
            textPaint.apply {
                color = GColor.WHITE
                textSize = 22f
                isFakeBoldText = true
            }
            canvas.drawText("VANTAGE FINANCE", 30f, 40f, textPaint)
            
            textPaint.apply {
                textSize = 10f
                isFakeBoldText = false
            }
            val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
            canvas.drawText("Ledger Statement • Generated on: $dateStr", 30f, 60f, textPaint)
            canvas.drawText("Total Transactions: ${transactions.size}", 30f, 75f, textPaint)
            
            // Draw summary cards
            val totalIncome = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
            val totalExpense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
            val netBalance = totalIncome - totalExpense
            
            paint.color = GColor.parseColor("#EEEEEE")
            canvas.drawRect(30f, 105f, 565f, 155f, paint)
            
            textPaint.apply {
                color = GColor.BLACK
                textSize = 9f
                isFakeBoldText = true
            }
            canvas.drawText("SUMMARY STATS", 45f, 120f, textPaint)
            
            textPaint.apply {
                textSize = 11f
                isFakeBoldText = false
            }
            canvas.drawText(String.format("Total Income: ₹%,.2f", totalIncome), 45f, 140f, textPaint)
            canvas.drawText(String.format("Total Expense: ₹%,.2f", totalExpense), 210f, 140f, textPaint)
            
            textPaint.apply {
                isFakeBoldText = true
                color = if (netBalance >= 0) GColor.parseColor("#1B5E20") else GColor.parseColor("#B71C1C")
            }
            canvas.drawText(String.format("Net Balance: ₹%,.2f", netBalance), 385f, 140f, textPaint)
            
            // Table columns headers
            paint.color = GColor.parseColor("#E0E0E0")
            canvas.drawRect(30f, 175f, 565f, 195f, paint)
            
            textPaint.apply {
                color = GColor.BLACK
                textSize = 9f
                isFakeBoldText = true
            }
            canvas.drawText("DATE", 35f, 188f, textPaint)
            canvas.drawText("PARTICULARS", 110f, 188f, textPaint)
            canvas.drawText("CATEGORY", 230f, 188f, textPaint)
            canvas.drawText("METHOD", 350f, 188f, textPaint)
            canvas.drawText("AMOUNT", 480f, 188f, textPaint)
            
            // Table content rows
            var currentY = 215f
            textPaint.apply {
                isFakeBoldText = false
                textSize = 9f
            }
            val sdf = SimpleDateFormat("dd-MM-yy", Locale.getDefault())
            
            for (i in 0 until Math.min(transactions.size, 25)) { // Draw up to 25 rows on page 1
                val t = transactions[i]
                val formattedDate = sdf.format(Date(t.timestamp))
                
                canvas.drawText(formattedDate, 35f, currentY, textPaint)
                
                // Particulars (truncate if too long)
                val particulars = if (t.title.length > 22) t.title.take(20) + ".." else t.title
                canvas.drawText(particulars, 110f, currentY, textPaint)
                
                canvas.drawText(t.category, 230f, currentY, textPaint)
                canvas.drawText(t.paymentMethod, 350f, currentY, textPaint)
                
                val amountText = String.format("%s₹%,.2f", if (t.type == "EXPENSE") "-" else "+", t.amount)
                textPaint.color = if (t.type == "EXPENSE") GColor.parseColor("#B71C1C") else GColor.parseColor("#1B5E20")
                textPaint.isFakeBoldText = true
                canvas.drawText(amountText, 480f, currentY, textPaint)
                
                // Reset text paint
                textPaint.color = GColor.BLACK
                textPaint.isFakeBoldText = false
                
                // Thin row separator line
                paint.color = GColor.parseColor("#F5F5F5")
                canvas.drawLine(30f, currentY + 6f, 565f, currentY + 6f, paint)
                
                currentY += 22f
            }
            
            if (transactions.size > 25) {
                textPaint.apply {
                    color = GColor.GRAY
                    textSize = 8f
                    isFakeBoldText = false
                }
                canvas.drawText("... and ${transactions.size - 25} more transactions truncated for visual beauty ...", 180f, 810f, textPaint)
            }
            
            // Footer Branding
            textPaint.apply {
                color = GColor.parseColor("#9E9E9E")
                textSize = 8f
            }
            canvas.drawText("Vantage Finance Ledger Report — Powered by Gemini AI Studio", 185f, 825f, textPaint)
            
            pdfDocument.finishPage(page)
            
            // Write PDF to cache directory
            val cacheDir = context.cacheDir
            val pdfFile = File(cacheDir, "Vantage_Ledger_${System.currentTimeMillis()}.pdf")
            val outputStream = FileOutputStream(pdfFile)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            outputStream.close()
            
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun shareFile(context: Context, uri: Uri, mimeType: String, message: String, targetWhatsApp: Boolean) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, message)
            putExtra(Intent.EXTRA_SUBJECT, "Vantage Finance Report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        if (targetWhatsApp) {
            shareIntent.setPackage("com.whatsapp")
            try {
                context.startActivity(Intent.createChooser(shareIntent, "Share with WhatsApp"))
            } catch (e: ActivityNotFoundException) {
                // If WhatsApp is not installed, clear package constraints and open the standard share sheet!
                shareIntent.setPackage(null)
                context.startActivity(Intent.createChooser(shareIntent, "Share Ledger Report"))
                Toast.makeText(context, "WhatsApp not installed. Showing general share options.", Toast.LENGTH_SHORT).show()
            }
        } else {
            context.startActivity(Intent.createChooser(shareIntent, "Share Ledger Report"))
        }
    }
}
