package org.data.wrangler.driver;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.data.wrangler.DuckDB;
import org.openide.modules.InstalledFileLocator;

/**
 * Finds the DuckDB JDBC jar. First choice is the copy bundled with this module
 * (nbm-maven-plugin drops non-NetBeans dependencies into {@code modules/ext/}),
 * second choice is the user's local Maven repository.
 */
public final class DuckDBDriverLocator {

 private static final Logger LOG = Logger.getLogger(DuckDBDriverLocator.class.getName());

 private DuckDBDriverLocator() {}

    /** This module's own jar, needed on the driver class path for {@link NbDuckDBDriver}. */
 public static Optional<URL> locateModuleJar() {
        File jar = InstalledFileLocator.getDefault().locate("modules/org-data-wrangler.jar", DuckDB.CODE_NAME_BASE, false);
 if (jar == null) {
            // running from an IDE / unpacked build: fall back to the code source
 try {
 return Optional.of(NbDuckDBDriver.class.getProtectionDomain().getCodeSource().getLocation());
            } catch (RuntimeException ex) {
 return Optional.empty();
            }
        }
 try {
 return Optional.of(jar.toURI().toURL());
        } catch (MalformedURLException ex) {
 return Optional.empty();
        }
    }

 public static Optional<URL> locateDriverJar() {
        // 1. The jar bundled with this module: it is on the module's Class-Path, so the
        // module class loader can tell us where it lives. Works for NBM installs
        // and for nbm:run-ide clusters alike, with no assumptions about directory layout.
        URL cls = DuckDBDriverLocator.class.getClassLoader().getResource("org/duckdb/DuckDBDriver.class");
 if (cls != null && "jar".equals(cls.getProtocol())) {
            String spec = cls.toString();
 int bang = spec.indexOf("!/");
 if (bang > 0) {
 try {
 return Optional.of(new URL(spec.substring(4, bang)));
                } catch (MalformedURLException ignore) { }
            }
        }
        // 2. modules/ext of this module's cluster (searched recursively)
        File ext = InstalledFileLocator.getDefault().locate("modules/ext", DuckDB.CODE_NAME_BASE, false);
        Optional<File> jar = newestJarIn(ext);
 if (jar.isEmpty()) {
            File m2 = new File(System.getProperty("user.home"), ".m2/repository/org/duckdb/duckdb_jdbc");
            File[] versions = m2.listFiles(File::isDirectory);
 if (versions != null) {
 jar = Arrays.stream(versions)
                        .sorted(Comparator.comparing(File::getName).reversed())
                        .map(DuckDBDriverLocator::newestJarIn)
                        .flatMap(Optional::stream)
                        .findFirst();
            }
        }
 return jar.map(f -> {
 try {
 return f.toURI().toURL();
            } catch (MalformedURLException e) {
                LOG.log(Level.WARNING, "Cannot convert " + f + " to URL", e);
 return null;
            }
        });
    }

 private static Optional<File> newestJarIn(File dir) {
 if (dir == null || !dir.isDirectory()) return Optional.empty();
 try (java.util.stream.Stream<java.nio.file.Path> s = java.nio.file.Files.walk(dir.toPath(), 3)) {
 return s.map(java.nio.file.Path::toFile)
                    .filter(f -> f.getName().startsWith("duckdb_jdbc") && f.getName().endsWith(".jar") && !f.getName().contains("sources"))
                    .max(Comparator.comparing(File::getName));
        } catch (java.io.IOException ex) {
 return Optional.empty();
        }
    }
}
