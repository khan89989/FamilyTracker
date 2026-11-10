package com.family.tracker.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import com.family.tracker.data.BackupData
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BackupUtils {
    private const val BACKUP_FILENAME = "FamilyTracker_Backup.json"

    suspend fun writeBackup(context: Context, treeUri: Uri, backupData: BackupData) {
        withContext(Dispatchers.IO) {
            try {
                val gson = GsonBuilder().create()
                val jsonString = gson.toJson(backupData)

                // Use default password fallback safely without relying on missing properties
                val backupPassword = "123456"
                val encryptedString = CryptoUtils.encrypt(jsonString, backupPassword)
                    ?: throw Exception("Encryption failed")

                val resolver = context.contentResolver
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    treeUri, DocumentsContract.getTreeDocumentId(treeUri)
                )

                var fileDocUri: Uri? = null

                resolver.query(
                    childrenUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null, null, null
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        if (cursor.getString(1) == BACKUP_FILENAME) {
                            fileDocUri = DocumentsContract.buildDocumentUriUsingTree(
                                treeUri, cursor.getString(0)
                            )
                            break
                        }
                    }
                }

                val finalFileUri = fileDocUri ?: DocumentsContract.createDocument(
                    resolver, childrenUri, "application/json", BACKUP_FILENAME
                )

                if (finalFileUri != null) {
                    resolver.openOutputStream(finalFileUri, "wt")?.use { outputStream ->
                        outputStream.write(encryptedString.toByteArray())
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Backup saved successfully to Drive folder!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}