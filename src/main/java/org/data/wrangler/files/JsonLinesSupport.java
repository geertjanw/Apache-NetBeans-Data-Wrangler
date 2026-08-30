package org.data.wrangler.files;

import org.openide.filesystems.MIMEResolver;
import org.openide.util.NbBundle.Messages;

/**
 * JSON Lines (.jsonl, .ndjson) files are handled like .json files:
 * NetBeans' JSON MIME type, so they get the JSON icon and editor, and every
 * action registered for text/x-json (Query with DuckDB, Convert with DuckDB).
 * DuckDB's read_json_auto detects newline-delimited input by itself.
 */
@Messages("LBL_JsonLines=JSON Lines Files")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_JsonLines", mimeType = "text/x-json",
 extension = { "jsonl", "ndjson", "jsonlines" }, position = 13)
public final class JsonLinesSupport {
 private JsonLinesSupport() {}
}
