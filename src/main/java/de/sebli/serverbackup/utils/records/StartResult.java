package de.sebli.serverbackup.utils.records;

import com.dropbox.core.DbxException;

public record StartResult(String sessionId, long uploaded, DbxException exception) {
}