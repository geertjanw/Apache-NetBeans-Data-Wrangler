/**
 * File-type actions for CSV and Parquet, and the New File &gt; Analytics templates.
 * Template registrations live on the package because {@code @TemplateRegistration}
 * on a class would make that class the wizard iterator.
 */
@ActionReferences({
    @ActionReference(id = @ActionID(category = "System", id = "org.openide.actions.OpenAction"), path = "Loaders/text/csv/Actions", position = 100),
    @ActionReference(id = @ActionID(category = "Edit", id = "org.openide.actions.CutAction"), path = "Loaders/text/csv/Actions", position = 300, separatorBefore = 290),
    @ActionReference(id = @ActionID(category = "Edit", id = "org.openide.actions.CopyAction"), path = "Loaders/text/csv/Actions", position = 400),
    @ActionReference(id = @ActionID(category = "Edit", id = "org.openide.actions.DeleteAction"), path = "Loaders/text/csv/Actions", position = 500, separatorAfter = 550),
    @ActionReference(id = @ActionID(category = "System", id = "org.openide.actions.RenameAction"), path = "Loaders/text/csv/Actions", position = 600, separatorAfter = 650),
    @ActionReference(id = @ActionID(category = "System", id = "org.openide.actions.PropertiesAction"), path = "Loaders/text/csv/Actions", position = 1400),

    @ActionReference(id = @ActionID(category = "System", id = "org.openide.actions.OpenAction"), path = "Loaders/application/x-parquet/Actions", position = 50),
    @ActionReference(id = @ActionID(category = "System", id = "org.openide.actions.OpenAction"), path = "Loaders/application/vnd.openxmlformats-officedocument.spreadsheetml.sheet/Actions", position = 50),
    @ActionReference(id = @ActionID(category = "Edit", id = "org.openide.actions.CutAction"), path = "Loaders/application/vnd.openxmlformats-officedocument.spreadsheetml.sheet/Actions", position = 300, separatorBefore = 290),
    @ActionReference(id = @ActionID(category = "Edit", id = "org.openide.actions.CopyAction"), path = "Loaders/application/vnd.openxmlformats-officedocument.spreadsheetml.sheet/Actions", position = 400),
    @ActionReference(id = @ActionID(category = "Edit", id = "org.openide.actions.DeleteAction"), path = "Loaders/application/vnd.openxmlformats-officedocument.spreadsheetml.sheet/Actions", position = 500, separatorAfter = 550),
    @ActionReference(id = @ActionID(category = "System", id = "org.openide.actions.RenameAction"), path = "Loaders/application/vnd.openxmlformats-officedocument.spreadsheetml.sheet/Actions", position = 600, separatorAfter = 650),
    @ActionReference(id = @ActionID(category = "System", id = "org.openide.actions.PropertiesAction"), path = "Loaders/application/vnd.openxmlformats-officedocument.spreadsheetml.sheet/Actions", position = 1400),

    @ActionReference(id = @ActionID(category = "Edit", id = "org.openide.actions.CutAction"), path = "Loaders/application/x-parquet/Actions", position = 300, separatorBefore = 290),
    @ActionReference(id = @ActionID(category = "Edit", id = "org.openide.actions.CopyAction"), path = "Loaders/application/x-parquet/Actions", position = 400),
    @ActionReference(id = @ActionID(category = "Edit", id = "org.openide.actions.DeleteAction"), path = "Loaders/application/x-parquet/Actions", position = 500, separatorAfter = 550),
    @ActionReference(id = @ActionID(category = "System", id = "org.openide.actions.RenameAction"), path = "Loaders/application/x-parquet/Actions", position = 600, separatorAfter = 650),
    @ActionReference(id = @ActionID(category = "System", id = "org.openide.actions.PropertiesAction"), path = "Loaders/application/x-parquet/Actions", position = 1400)
})
@TemplateRegistrations({
    // Scripts (SQL icon), in the order you would use them
    @TemplateRegistration(folder = "Analytics", displayName = "#TPL_DuckDBQuery", description = "templates/query.html",
 content = "templates/query.sql", iconBase = "org/data/wrangler/sql.png", position = 100, requireProject = false, scriptEngine = "freemarker"),
    @TemplateRegistration(folder = "Analytics", displayName = "#TPL_DuckDBExplore", description = "templates/explore.html",
 content = "templates/explore.sql", iconBase = "org/data/wrangler/sql.png", position = 110, requireProject = false, scriptEngine = "freemarker"),
    @TemplateRegistration(folder = "Analytics", displayName = "#TPL_DuckDBImport", description = "templates/import.html",
 content = "templates/import.sql", iconBase = "org/data/wrangler/sql.png", position = 120, requireProject = false, scriptEngine = "freemarker"),
    @TemplateRegistration(folder = "Analytics", displayName = "#TPL_DuckDBParquet", description = "templates/parquet.html",
 content = "templates/parquet.sql", iconBase = "org/data/wrangler/sql.png", position = 130, requireProject = false, scriptEngine = "freemarker"),
    @TemplateRegistration(folder = "Analytics", displayName = "#TPL_DuckDBExport", description = "templates/export.html",
 content = "templates/export.sql", iconBase = "org/data/wrangler/sql.png", position = 140, requireProject = false, scriptEngine = "freemarker"),
    // Sample files (format icons), in the order the formats are listed everywhere: CSV, Parquet, JSON, Excel
    @TemplateRegistration(folder = "Analytics", displayName = "#TPL_CsvFile", description = "templates/csv.html",
 content = "templates/data.csv", iconBase = "org/data/wrangler/csv.png", position = 300, requireProject = false),
    @TemplateRegistration(folder = "Analytics", displayName = "#TPL_ParquetFile", description = "templates/parquet-file.html",
 content = "templates/sample.parquet", iconBase = "org/data/wrangler/parquet.png", position = 310, requireProject = false),
    @TemplateRegistration(folder = "Analytics", displayName = "#TPL_JsonFile", description = "templates/json.html",
 content = "templates/orders.json", iconBase = "org/data/wrangler/json.png", position = 320, requireProject = false),
    @TemplateRegistration(folder = "Analytics", displayName = "#TPL_XlsxFile", description = "templates/xlsx.html",
 content = "templates/sample.xlsx", iconBase = "org/data/wrangler/xlsx.png", position = 330, requireProject = false)
})
@Messages({
    "TPL_DuckDBQuery=SQL Query",
    "TPL_DuckDBExplore=Data Exploration Script",
    "TPL_DuckDBImport=Import Script (files to tables)",
    "TPL_DuckDBParquet=Parquet Query Script",
    "TPL_DuckDBExport=Export Script (tables to files)",
    "TPL_CsvFile=Sample CSV File",
    "TPL_JsonFile=Sample JSON File (nested)",
    "TPL_XlsxFile=Sample Excel Workbook (two sheets)",
    "TPL_ParquetFile=Sample Parquet File (nested)"
})
package org.data.wrangler.files;

import org.netbeans.api.templates.TemplateRegistration;
import org.netbeans.api.templates.TemplateRegistrations;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.util.NbBundle.Messages;
