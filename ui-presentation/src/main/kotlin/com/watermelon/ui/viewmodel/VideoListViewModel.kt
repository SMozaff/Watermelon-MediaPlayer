/** Called by the UI to delete the currently selected videos. */
    fun buildDeleteRequest(rawUris: List<String>): IntentSender? {
        val uris = rawUris.filter { it.startsWith("content://") }
        if (uris.isEmpty()) {
            com.watermelon.common.util.FileLogger.e("Delete", "no valid URIs to delete from ${rawUris.size} selected")
            return null
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            com.watermelon.common.util.FileLogger.i("Delete", "createDeleteRequest for ${uris.size} uris")
            android.provider.MediaStore.createDeleteRequest(contentResolver, uris).intentSender
        } else {
            com.watermelon.common.util.FileLogger.i("Delete", "direct delete for ${uris.size} uris (pre-30)")
            viewModelScope.launch(Dispatchers.IO) {
                var deletedCount = 0
                var failedCount = 0
                uris.forEach { uri ->
                    try {
                        contentResolver.delete(uri, null, null)
                        deletedCount++
                    } catch (e: SecurityException) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            e is android.app.RecoverableSecurityException
                        ) {
                            // Recoverable SecurityException: user must grant permission via system dialog
                            // We'll still count it as a "delete attempted" but the actual deletion
                            // will complete if the user grants permission in the system dialog.
                            failedCount++
                            // Launch the intent sender for recoverable permission
                            val intent = android.content.Intent(android.provider.MediaStore.ACTION_PROVID_URI_PERMISSION_GRANTED)
                                .setData(Uri.parse(uri))
                            try {
                                val sender = android.app.PendingIntent.getActivity(
                                    applicationContext,
                                    uri.hashCode(),
                                    intent,
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                )
                                sender.send()
                            } catch (sendEx: Exception) {
                                FileLogger.e("Delete", "Failed to send recoverable permission intent for $uri", sendEx)
                            }
                        } else {
                            failedCount++
                            FileLogger.e("Delete", "Delete failed for $uri: ${e.message}", e)
                        }
                    } catch (e: Exception) {
                        failedCount++
                        FileLogger.e("Delete", "Delete failed for $uri: ${e.message}", e)
                    }
                }
                FileLogger.i("Delete", "Delete completed: $deletedCount succeeded, $failedCount failed")
                clearSelection()
                mediaRepository.refreshIndex()
            }
            null
        }
    }