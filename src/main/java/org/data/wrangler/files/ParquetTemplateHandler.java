package org.data.wrangler.files;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import org.netbeans.api.templates.CreateDescriptor;
import org.netbeans.api.templates.CreateFromTemplateHandler;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.lookup.ServiceProvider;

/**
 * Guarantees that binary templates (Parquet, Excel, DuckDB files) are copied byte-for-byte.
 * Text-oriented template processing (encoding conversion, ${...} substitution)
 * would corrupt a binary file, so this handler claims any template whose MIME
 * type is Parquet and does a plain stream copy.
 */
@ServiceProvider(service = CreateFromTemplateHandler.class, position = 100)
public final class ParquetTemplateHandler extends CreateFromTemplateHandler {

    @Override
 protected boolean accept(CreateDescriptor desc) {
        FileObject t = desc.getTemplate();
 if (t == null) return false;
        String ext = t.getExt().toLowerCase(java.util.Locale.ROOT);
 return ParquetDataObject.MIME.equals(t.getMIMEType()) || ext.equals("parquet") || ext.equals("pq")
                || ext.equals("xlsx") || ext.equals("xlsm") || ext.equals("duckdb");
    }

    @Override
 protected List<FileObject> createFromTemplate(CreateDescriptor desc) throws IOException {
        FileObject template = desc.getTemplate();
        FileObject folder = desc.getTarget();
        String ext = template.getExt().isEmpty() ? "parquet" : template.getExt();
        String name = FileUtil.findFreeFileName(folder, desc.getProposedName(), ext);
        FileObject out = folder.createData(name, ext);
 try (InputStream in = template.getInputStream(); OutputStream os = out.getOutputStream()) {
 in.transferTo(os);
        }
 return List.of(out);
    }
}
