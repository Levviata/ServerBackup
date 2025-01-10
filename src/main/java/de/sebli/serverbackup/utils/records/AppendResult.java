package de.sebli.serverbackup.utils.records;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.files.UploadSessionCursor;

public record AppendResult(UploadSessionCursor cursor, long uploaded, DbxException exception) {
}
